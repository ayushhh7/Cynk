import { randomUUID } from "node:crypto";
import type { WebSocket } from "ws";
import type { RoomManager, ConnectedClient, Room } from "./roomManager.js";
import {
  TOGETHER_PROTOCOL_VERSION,
  type TogetherMessage,
  type ClientHello,
  type ServerWelcome,
  type ServerError,
  type RoomStateMessage,
  type ControlRequest,
  type AddTrackRequest,
  type JoinDecision,
  type JoinRequest,
  type ParticipantJoined,
  type ParticipantLeft,
  type HeartbeatPing,
  type HeartbeatPong,
  type KickParticipant,
  type BanParticipant,
} from "./types.js";

export function handleWebSocketConnection(
  ws: WebSocket,
  roomManager: RoomManager
) {
  let boundClient: ConnectedClient | null = null;
  let boundRoom: Room | null = null;

  const sendMessage = (targetWs: WebSocket, msg: TogetherMessage) => {
    if (targetWs.readyState === targetWs.OPEN) {
      try {
        targetWs.send(JSON.stringify(msg));
      } catch {}
    }
  };

  const sendError = (message: string, code?: string, sessionId?: string) => {
    sendMessage(ws, {
      type: "server_error",
      sessionId: sessionId ?? null,
      message,
      code: code ?? null,
    } satisfies ServerError);
  };

  ws.on("message", (raw) => {
    let msg: TogetherMessage;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      sendError("Invalid JSON message");
      return;
    }

    if (!msg || typeof msg !== "object" || !("type" in msg)) {
      sendError("Malformed message: missing type discriminator");
      return;
    }

    // 1. Handshake: client_hello
    if (msg.type === "client_hello") {
      const hello = msg as ClientHello;

      if (hello.protocolVersion !== TOGETHER_PROTOCOL_VERSION) {
        sendError("Unsupported protocol version", "PROTOCOL_MISMATCH", hello.sessionId);
        ws.close(1002, "Unsupported protocol version");
        return;
      }

      const room = roomManager.getRoomBySessionId(hello.sessionId);
      if (!room) {
        sendError("Session not found", "SESSION_NOT_FOUND", hello.sessionId);
        ws.close(1008, "Session not found");
        return;
      }

      const isHost = hello.sessionKey === room.hostKey;
      const isGuest = hello.sessionKey === room.guestKey;

      if (!isHost && !isGuest) {
        sendError("Invalid session key", "INVALID_KEY", hello.sessionId);
        ws.close(1008, "Invalid session key");
        return;
      }

      const participantId = randomUUID();
      const displayName = (hello.displayName || "").trim() || (isHost ? "Host" : "Guest");

      if (isHost) {
        // Register Host
        boundClient = {
          participantId,
          clientId: hello.clientId,
          displayName: room.hostDisplayName || displayName,
          role: "HOST",
          isPending: false,
          ws,
          sessionId: room.sessionId,
        };
        boundRoom = room;
        room.hostClient = boundClient;
        room.lastActivityAt = Date.now();

        const welcome: ServerWelcome = {
          type: "server_welcome",
          protocolVersion: TOGETHER_PROTOCOL_VERSION,
          sessionId: room.sessionId,
          participantId,
          role: "HOST",
          isPending: false,
          settings: room.settings,
        };
        sendMessage(ws, welcome);

        // If guests were already in room, broadcast updated state
        if (room.latestRoomState) {
          sendMessage(ws, {
            type: "room_state",
            state: room.latestRoomState,
          } satisfies RoomStateMessage);
        }
        return;
      }

      // Guest Registration
      if (room.bannedClientIds.has(hello.clientId)) {
        sendError("You have been banned from this session", "BANNED", room.sessionId);
        ws.close(1008, "Banned");
        return;
      }

      const isPending = room.settings.requireHostApprovalToJoin;
      boundClient = {
        participantId,
        clientId: hello.clientId,
        displayName,
        role: "GUEST",
        isPending,
        ws,
        sessionId: room.sessionId,
      };
      boundRoom = room;
      room.guests.set(participantId, boundClient);
      room.lastActivityAt = Date.now();

      const welcome: ServerWelcome = {
        type: "server_welcome",
        protocolVersion: TOGETHER_PROTOCOL_VERSION,
        sessionId: room.sessionId,
        participantId,
        role: "GUEST",
        isPending,
        settings: room.settings,
      };
      sendMessage(ws, welcome);

      const participantPayload = {
        id: participantId,
        name: displayName,
        isHost: false,
        isPending,
        isConnected: true,
      };

      if (isPending) {
        // Notify host that guest wants to join
        if (room.hostClient) {
          sendMessage(room.hostClient.ws, {
            type: "join_request",
            sessionId: room.sessionId,
            participant: participantPayload,
          } satisfies JoinRequest);
        }
      } else {
        // Notify host and all other guests
        const joinedNotice: ParticipantJoined = {
          type: "participant_joined",
          sessionId: room.sessionId,
          participant: participantPayload,
        };
        if (room.hostClient) sendMessage(room.hostClient.ws, joinedNotice);
        for (const [gId, g] of room.guests.entries()) {
          if (gId !== participantId) sendMessage(g.ws, joinedNotice);
        }

        // Send latest room state if available
        if (room.latestRoomState) {
          sendMessage(ws, {
            type: "room_state",
            state: room.latestRoomState,
          } satisfies RoomStateMessage);
        }
      }
      return;
    }

    // All subsequent messages require an authenticated session
    if (!boundClient || !boundRoom) {
      sendError("Client not authenticated. Send client_hello first");
      return;
    }

    const room = boundRoom;
    room.lastActivityAt = Date.now();

    // 2. Heartbeat Ping
    if (msg.type === "heartbeat_ping") {
      const ping = msg as HeartbeatPing;
      sendMessage(ws, {
        type: "heartbeat_pong",
        sessionId: room.sessionId,
        pingId: ping.pingId,
        clientElapsedRealtimeMs: ping.clientElapsedRealtimeMs,
        serverElapsedRealtimeMs: Date.now(),
      } satisfies HeartbeatPong);
      return;
    }

    // 3. Room State Broadcast (Host only)
    if (msg.type === "room_state") {
      if (boundClient.role !== "HOST") {
        sendError("Only host can broadcast room state");
        return;
      }

      const stateMsg = msg as RoomStateMessage;
      room.latestRoomState = stateMsg.state;
      room.settings = stateMsg.state.settings || room.settings;

      // Broadcast to all connected guests
      for (const guest of room.guests.values()) {
        const payload = roomManager.getSanitizedRoomState(stateMsg.state, guest.isPending);
        sendMessage(guest.ws, {
          type: "room_state",
          state: payload,
        } satisfies RoomStateMessage);
      }
      return;
    }

    // 4. Control Request (Guest -> Host)
    if (msg.type === "control_request") {
      if (boundClient.isPending) return; // Ignore pending guest actions

      if (room.hostClient) {
        sendMessage(room.hostClient.ws, msg as ControlRequest);
      }
      return;
    }

    // 5. Add Track Request (Guest -> Host)
    if (msg.type === "add_track_request") {
      if (boundClient.isPending) return;

      if (room.hostClient) {
        sendMessage(room.hostClient.ws, msg as AddTrackRequest);
      }
      return;
    }

    // 6. Join Decision (Host -> Guest)
    if (msg.type === "join_decision") {
      if (boundClient.role !== "HOST") return;
      const decision = msg as JoinDecision;
      const targetGuest = room.guests.get(decision.participantId);
      if (!targetGuest) return;

      if (decision.approved) {
        targetGuest.isPending = false;
        sendMessage(targetGuest.ws, decision);

        const joinedNotice: ParticipantJoined = {
          type: "participant_joined",
          sessionId: room.sessionId,
          participant: {
            id: targetGuest.participantId,
            name: targetGuest.displayName,
            isHost: false,
            isPending: false,
            isConnected: true,
          },
        };
        if (room.hostClient) sendMessage(room.hostClient.ws, joinedNotice);
        for (const [gId, g] of room.guests.entries()) {
          if (gId !== targetGuest.participantId) sendMessage(g.ws, joinedNotice);
        }

        if (room.latestRoomState) {
          sendMessage(targetGuest.ws, {
            type: "room_state",
            state: room.latestRoomState,
          } satisfies RoomStateMessage);
        }
      } else {
        sendMessage(targetGuest.ws, decision);
        targetGuest.ws.close(1000, "Not approved by host");
        room.guests.delete(decision.participantId);
      }
      return;
    }

    // 7. Kick Participant (Host only)
    if (msg.type === "kick") {
      if (boundClient.role !== "HOST") return;
      const kick = msg as KickParticipant;
      const targetGuest = room.guests.get(kick.participantId);
      if (targetGuest) {
        sendMessage(targetGuest.ws, {
          type: "kick",
          sessionId: room.sessionId,
          participantId: kick.participantId,
          reason: kick.reason || "Kicked by host",
        } satisfies KickParticipant);
        targetGuest.ws.close(1000, kick.reason || "Kicked by host");
        room.guests.delete(kick.participantId);

        const leftNotice: ParticipantLeft = {
          type: "participant_left",
          sessionId: room.sessionId,
          participantId: kick.participantId,
          reason: kick.reason || "Kicked",
        };
        if (room.hostClient) sendMessage(room.hostClient.ws, leftNotice);
        for (const g of room.guests.values()) {
          sendMessage(g.ws, leftNotice);
        }
      }
      return;
    }

    // 8. Ban Participant (Host only)
    if (msg.type === "ban") {
      if (boundClient.role !== "HOST") return;
      const ban = msg as BanParticipant;
      const targetGuest = room.guests.get(ban.participantId);
      if (targetGuest) {
        room.bannedClientIds.add(targetGuest.clientId);
        sendMessage(targetGuest.ws, {
          type: "ban",
          sessionId: room.sessionId,
          participantId: ban.participantId,
          reason: ban.reason || "Banned by host",
        } satisfies BanParticipant);
        targetGuest.ws.close(1000, ban.reason || "Banned by host");
        room.guests.delete(ban.participantId);

        const leftNotice: ParticipantLeft = {
          type: "participant_left",
          sessionId: room.sessionId,
          participantId: ban.participantId,
          reason: ban.reason || "Banned",
        };
        if (room.hostClient) sendMessage(room.hostClient.ws, leftNotice);
        for (const g of room.guests.values()) {
          sendMessage(g.ws, leftNotice);
        }
      }
      return;
    }

    // 9. Client Leave
    if (msg.type === "client_leave") {
      ws.close(1000, "User left");
      return;
    }
  });

  ws.on("close", () => {
    if (!boundClient || !boundRoom) return;
    const room = boundRoom;
    const client = boundClient;

    if (client.role === "HOST") {
      room.hostClient = undefined;
      // Notify all guests that host left
      const leftNotice: ParticipantLeft = {
        type: "participant_left",
        sessionId: room.sessionId,
        participantId: client.participantId,
        reason: "Host disconnected",
      };
      for (const g of room.guests.values()) {
        sendMessage(g.ws, leftNotice);
      }
    } else {
      room.guests.delete(client.participantId);
      const leftNotice: ParticipantLeft = {
        type: "participant_left",
        sessionId: room.sessionId,
        participantId: client.participantId,
        reason: "Disconnected",
      };
      if (room.hostClient) sendMessage(room.hostClient.ws, leftNotice);
      for (const g of room.guests.values()) {
        sendMessage(g.ws, leftNotice);
      }
    }
  });

  ws.on("error", () => {
    try {
      ws.close();
    } catch {}
  });
}
