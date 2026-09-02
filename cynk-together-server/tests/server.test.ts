import assert from "node:assert";
import { createServer } from "node:http";
import { WebSocketServer, WebSocket } from "ws";
import { RoomManager } from "../src/roomManager.js";
import { handleHttpRequest } from "../src/apiRoutes.js";
import { handleWebSocketConnection } from "../src/wsHandler.js";
import type {
  CreateSessionResponseBody,
  ResolveSessionResponseBody,
  ServerWelcome,
  RoomStateMessage,
  JoinRequest,
  ParticipantJoined,
  ParticipantLeft,
  HeartbeatPong,
  TogetherRoomState,
  ControlRequest,
  AddTrackRequest,
  KickParticipant,
  BanParticipant,
} from "../src/types.js";

const PORT = 8085;
const TEST_TOKEN = "test_token_123";

let roomManager: RoomManager;
let server: ReturnType<typeof createServer>;
let wss: WebSocketServer;

function startServer(): Promise<void> {
  return new Promise((resolve) => {
    roomManager = new RoomManager();
    server = createServer((req, res) => {
      handleHttpRequest(req, res, roomManager, TEST_TOKEN);
    });

    wss = new WebSocketServer({ noServer: true });

    server.on("upgrade", (req, socket, head) => {
      const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
      const pathname = url.pathname.replace(/\/+$/, "") || "/";
      if (pathname === "/v1/together/ws" || pathname === "/together/ws") {
        wss.handleUpgrade(req, socket, head, (ws) => {
          handleWebSocketConnection(ws, roomManager);
        });
      } else {
        socket.destroy();
      }
    });

    server.listen(PORT, "127.0.0.1", () => {
      resolve();
    });
  });
}

function stopServer(): Promise<void> {
  return new Promise((resolve) => {
    roomManager.stop();
    wss.close(() => {
      server.close(() => resolve());
    });
  });
}

async function postJson<T>(path: string, body: unknown, token = TEST_TOKEN): Promise<{ status: number; data: T }> {
  const resp = await fetch(`http://127.0.0.1:${PORT}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
  const data = (await resp.json()) as T;
  return { status: resp.status, data };
}

function createWsClient(): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://127.0.0.1:${PORT}/v1/together/ws`, {
      headers: { Authorization: `Bearer ${TEST_TOKEN}` },
    });
    ws.on("open", () => resolve(ws));
    ws.on("error", reject);
  });
}

function waitForMessage<T>(ws: WebSocket, type: string, timeoutMs = 3000): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      ws.off("message", onMsg);
      reject(new Error(`Timeout waiting for message type: ${type}`));
    }, timeoutMs);

    const onMsg = (raw: Buffer) => {
      try {
        const parsed = JSON.parse(raw.toString());
        if (parsed && parsed.type === type) {
          clearTimeout(timer);
          ws.off("message", onMsg);
          resolve(parsed as T);
        }
      } catch {}
    };

    ws.on("message", onMsg);
  });
}

async function runAllTests() {
  console.log("=== Starting Cynk Together Relay Server Test Suite ===");
  await startServer();

  try {
    // 1. Health check
    console.log("[Test 1] Health Check GET /health");
    const healthResp = await fetch(`http://127.0.0.1:${PORT}/health`);
    assert.strictEqual(healthResp.status, 200);
    const healthJson = (await healthResp.json()) as any;
    assert.strictEqual(healthJson.status, "ok");
    assert.strictEqual(healthJson.service, "cynk-together-relay");
    console.log("  ✓ Health check passed");

    // 2. Create Session
    console.log("[Test 2] Create Session POST /v1/together/sessions");
    const createRes = await postJson<CreateSessionResponseBody>("/v1/together/sessions", {
      hostDisplayName: "Alice Host",
      settings: {
        allowGuestsToAddTracks: true,
        allowGuestsToControlPlayback: true,
        requireHostApprovalToJoin: false,
      },
    });
    assert.strictEqual(createRes.status, 200);
    assert.ok(createRes.data.sessionId, "Missing sessionId");
    assert.strictEqual(createRes.data.code.length, 6, "Code must be 6 characters");
    assert.ok(createRes.data.hostKey, "Missing hostKey");
    assert.ok(createRes.data.guestKey, "Missing guestKey");
    assert.ok(createRes.data.wsUrl.includes("/v1/together/ws"), "Invalid wsUrl");
    const { sessionId, code, hostKey, guestKey } = createRes.data;
    console.log(`  ✓ Session created: sessionId=${sessionId}, code=${code}`);

    // 3. Resolve Session with valid code
    console.log("[Test 3] Resolve Session POST /v1/together/sessions/resolve");
    const resolveRes = await postJson<ResolveSessionResponseBody>("/v1/together/sessions/resolve", {
      code,
    });
    assert.strictEqual(resolveRes.status, 200);
    assert.strictEqual(resolveRes.data.sessionId, sessionId);
    assert.strictEqual(resolveRes.data.guestKey, guestKey);
    console.log("  ✓ Resolve session passed");

    // 4. Invalid room code returns 404
    console.log("[Test 4] Invalid Room Code Resolution");
    const invalidRes = await postJson<{ ok: boolean; error: string }>("/v1/together/sessions/resolve", {
      code: "ZZZZZZ",
    });
    assert.strictEqual(invalidRes.status, 404);
    assert.strictEqual(invalidRes.data.error, "Session not found");
    console.log("  ✓ Invalid code returns 404 'Session not found'");

    // 5. Host WebSocket connection & Welcome
    console.log("[Test 5] Host WebSocket Connection & Handshake");
    const hostWs = await createWsClient();
    hostWs.send(
      JSON.stringify({
        type: "client_hello",
        protocolVersion: 1,
        sessionId,
        sessionKey: hostKey,
        clientId: "host_client_1",
        displayName: "Alice Host",
      })
    );
    const hostWelcome = await waitForMessage<ServerWelcome>(hostWs, "server_welcome");
    assert.strictEqual(hostWelcome.sessionId, sessionId);
    assert.strictEqual(hostWelcome.role, "HOST");
    assert.strictEqual(hostWelcome.isPending, false);
    console.log("  ✓ Host received server_welcome (role=HOST)");

    // 6. Guest WebSocket connection & Welcome
    console.log("[Test 6] Guest WebSocket Connection & Handshake");
    const guest1Ws = await createWsClient();
    guest1Ws.send(
      JSON.stringify({
        type: "client_hello",
        protocolVersion: 1,
        sessionId,
        sessionKey: guestKey,
        clientId: "guest_client_1",
        displayName: "Bob Guest",
      })
    );
    const guestWelcome = await waitForMessage<ServerWelcome>(guest1Ws, "server_welcome");
    assert.strictEqual(guestWelcome.sessionId, sessionId);
    assert.strictEqual(guestWelcome.role, "GUEST");
    assert.strictEqual(guestWelcome.isPending, false);
    const guest1ParticipantId = guestWelcome.participantId;
    console.log(`  ✓ Guest 1 received server_welcome (participantId=${guest1ParticipantId})`);

    // 7. Host receives ParticipantJoined notification
    console.log("[Test 7] Host Receives ParticipantJoined Notification");
    const joinedMsg = await waitForMessage<ParticipantJoined>(hostWs, "participant_joined");
    assert.strictEqual(joinedMsg.sessionId, sessionId);
    assert.strictEqual(joinedMsg.participant.name, "Bob Guest");
    assert.strictEqual(joinedMsg.participant.id, guest1ParticipantId);
    console.log("  ✓ Host successfully notified of Bob Guest join");

    // 8. Room state broadcast from Host to Guest
    console.log("[Test 8] Room State Playback Sync (Host -> Guest)");
    const mockState: TogetherRoomState = {
      sessionId,
      hostId: hostWelcome.participantId,
      participants: [
        { id: hostWelcome.participantId, name: "Alice Host", isHost: true, isPending: false, isConnected: true },
        { id: guest1ParticipantId, name: "Bob Guest", isHost: false, isPending: false, isConnected: true },
      ],
      settings: { allowGuestsToAddTracks: true, allowGuestsToControlPlayback: true, requireHostApprovalToJoin: false },
      queue: [{ id: "song_1", title: "Song 1", artists: ["Artist 1"], durationSec: 180, thumbnailUrl: null }],
      queueHash: "hash_123",
      currentIndex: 0,
      isPlaying: true,
      positionMs: 35000,
      repeatMode: 0,
      shuffleEnabled: false,
      sentAtElapsedRealtimeMs: 100000,
    };
    hostWs.send(JSON.stringify({ type: "room_state", state: mockState }));
    const guestStateMsg = await waitForMessage<RoomStateMessage>(guest1Ws, "room_state");
    assert.strictEqual(guestStateMsg.state.isPlaying, true);
    assert.strictEqual(guestStateMsg.state.positionMs, 35000);
    assert.strictEqual(guestStateMsg.state.queue[0].title, "Song 1");
    console.log("  ✓ Guest received synchronized room state");

    // 9. Guest sends Play/Pause Control Request -> Host receives it
    console.log("[Test 9] Play/Pause Control Request (Guest -> Host)");
    guest1Ws.send(
      JSON.stringify({
        type: "control_request",
        sessionId,
        participantId: guest1ParticipantId,
        action: { type: "pause" },
      })
    );
    const hostControlMsg = await waitForMessage<ControlRequest>(hostWs, "control_request");
    assert.strictEqual(hostControlMsg.action.type, "pause");
    console.log("  ✓ Host received pause control request from guest");

    // 10. Guest sends Seek Control Request -> Host receives it
    console.log("[Test 10] Seek Control Request (Guest -> Host)");
    guest1Ws.send(
      JSON.stringify({
        type: "control_request",
        sessionId,
        participantId: guest1ParticipantId,
        action: { type: "seek_to", positionMs: 65000 },
      })
    );
    const hostSeekMsg = await waitForMessage<ControlRequest>(hostWs, "control_request");
    assert.strictEqual(hostSeekMsg.action.type, "seek_to");
    assert.strictEqual((hostSeekMsg.action as any).positionMs, 65000);
    console.log("  ✓ Host received seek_to control request from guest");

    // 11. Guest sends Skip Control Request -> Host receives it
    console.log("[Test 11] Skip Control Request (Guest -> Host)");
    guest1Ws.send(
      JSON.stringify({
        type: "control_request",
        sessionId,
        participantId: guest1ParticipantId,
        action: { type: "skip_next" },
      })
    );
    const hostSkipMsg = await waitForMessage<ControlRequest>(hostWs, "control_request");
    assert.strictEqual(hostSkipMsg.action.type, "skip_next");
    console.log("  ✓ Host received skip_next control request from guest");

    // 12. Guest sends Add Track Request -> Host receives it
    console.log("[Test 12] Add Track Request (Guest -> Host)");
    guest1Ws.send(
      JSON.stringify({
        type: "add_track_request",
        sessionId,
        participantId: guest1ParticipantId,
        track: { id: "song_2", title: "Song 2", artists: ["Artist 2"], durationSec: 210 },
        mode: "ADD_TO_QUEUE",
      })
    );
    const hostAddTrackMsg = await waitForMessage<AddTrackRequest>(hostWs, "add_track_request");
    assert.strictEqual(hostAddTrackMsg.track.id, "song_2");
    assert.strictEqual(hostAddTrackMsg.mode, "ADD_TO_QUEUE");
    console.log("  ✓ Host received add_track_request from guest");

    // 13. Heartbeat Ping / Pong
    console.log("[Test 13] Heartbeat Ping/Pong for Clock Sync");
    guest1Ws.send(
      JSON.stringify({
        type: "heartbeat_ping",
        sessionId,
        pingId: 9999,
        clientElapsedRealtimeMs: 12345000,
      })
    );
    const pongMsg = await waitForMessage<HeartbeatPong>(guest1Ws, "heartbeat_pong");
    assert.strictEqual(pongMsg.pingId, 9999);
    assert.strictEqual(pongMsg.clientElapsedRealtimeMs, 12345000);
    assert.ok(pongMsg.serverElapsedRealtimeMs > 0, "serverElapsedRealtimeMs must be set");
    console.log("  ✓ Guest received heartbeat_pong with server timestamp");

    // 14. Multiple Guests
    console.log("[Test 14] Multiple Guests in Same Session");
    const guest2Ws = await createWsClient();
    guest2Ws.send(
      JSON.stringify({
        type: "client_hello",
        protocolVersion: 1,
        sessionId,
        sessionKey: guestKey,
        clientId: "guest_client_2",
        displayName: "Charlie Guest",
      })
    );
    const guest2Welcome = await waitForMessage<ServerWelcome>(guest2Ws, "server_welcome");
    assert.strictEqual(guest2Welcome.role, "GUEST");
    const guest2JoinedNoticeOnGuest1 = await waitForMessage<ParticipantJoined>(guest1Ws, "participant_joined");
    assert.strictEqual(guest2JoinedNoticeOnGuest1.participant.name, "Charlie Guest");
    console.log("  ✓ Multiple guests supported and cross-notified");

    // 15. Host Approval Flow
    console.log("[Test 15] Host Approval Flow (requireHostApprovalToJoin)");
    const approvalRoomRes = await postJson<CreateSessionResponseBody>("/v1/together/sessions", {
      hostDisplayName: "Approval Host",
      settings: { requireHostApprovalToJoin: true, allowGuestsToAddTracks: true, allowGuestsToControlPlayback: false },
    });
    const appSessionId = approvalRoomRes.data.sessionId;
    const appHostWs = await createWsClient();
    appHostWs.send(
      JSON.stringify({
        type: "client_hello",
        protocolVersion: 1,
        sessionId: appSessionId,
        sessionKey: approvalRoomRes.data.hostKey,
        clientId: "host_app",
        displayName: "Approval Host",
      })
    );
    await waitForMessage<ServerWelcome>(appHostWs, "server_welcome");

    const pendingGuestWs = await createWsClient();
    pendingGuestWs.send(
      JSON.stringify({
        type: "client_hello",
        protocolVersion: 1,
        sessionId: appSessionId,
        sessionKey: approvalRoomRes.data.guestKey,
        clientId: "pending_guest",
        displayName: "Pending Dave",
      })
    );
    const pendingWelcome = await waitForMessage<ServerWelcome>(pendingGuestWs, "server_welcome");
    assert.strictEqual(pendingWelcome.isPending, true, "Guest must start as pending");

    const hostJoinReq = await waitForMessage<JoinRequest>(appHostWs, "join_request");
    assert.strictEqual(hostJoinReq.participant.name, "Pending Dave");

    // Host approves guest
    appHostWs.send(
      JSON.stringify({
        type: "join_decision",
        sessionId: appSessionId,
        participantId: pendingWelcome.participantId,
        approved: true,
      })
    );
    const approvedDecision = await waitForMessage<any>(pendingGuestWs, "join_decision");
    assert.strictEqual(approvedDecision.approved, true);
    console.log("  ✓ Host approval workflow successfully verified");

    // 16. Kick Participant
    console.log("[Test 16] Kick Participant by Host");
    appHostWs.send(
      JSON.stringify({
        type: "kick",
        sessionId: appSessionId,
        participantId: pendingWelcome.participantId,
        reason: "Violated rules",
      })
    );
    const kickMsg = await waitForMessage<KickParticipant>(pendingGuestWs, "kick");
    assert.strictEqual(kickMsg.reason, "Violated rules");
    console.log("  ✓ Host successfully kicked participant");

    // 17. Ban Participant
    console.log("[Test 17] Ban Participant by Host & Block Re-join");
    const banGuestWs = await createWsClient();
    banGuestWs.send(
      JSON.stringify({
        type: "client_hello",
        protocolVersion: 1,
        sessionId: appSessionId,
        sessionKey: approvalRoomRes.data.guestKey,
        clientId: "troublemaker_id",
        displayName: "Troublemaker",
      })
    );
    const banGuestWelcome = await waitForMessage<ServerWelcome>(banGuestWs, "server_welcome");
    appHostWs.send(
      JSON.stringify({
        type: "ban",
        sessionId: appSessionId,
        participantId: banGuestWelcome.participantId,
        reason: "Spamming",
      })
    );
    await waitForMessage<BanParticipant>(banGuestWs, "ban");

    // Attempt to re-join with banned clientId
    const bannedRejoinWs = await createWsClient();
    bannedRejoinWs.send(
      JSON.stringify({
        type: "client_hello",
        protocolVersion: 1,
        sessionId: appSessionId,
        sessionKey: approvalRoomRes.data.guestKey,
        clientId: "troublemaker_id",
        displayName: "Troublemaker",
      })
    );
    const banError = await waitForMessage<any>(bannedRejoinWs, "server_error");
    assert.strictEqual(banError.code, "BANNED");
    console.log("  ✓ Banned client is blocked from rejoining room");

    // 18. Guest Disconnect Notification
    console.log("[Test 18] Guest Disconnect Notification");
    guest2Ws.close();
    const leftMsg = await waitForMessage<ParticipantLeft>(hostWs, "participant_left");
    assert.strictEqual(leftMsg.sessionId, sessionId);
    console.log("  ✓ Disconnection broadcasted to host");

    // 19. Room Isolation (Two separate rooms cannot interfere)
    console.log("[Test 19] Room Isolation");
    const room2Res = await postJson<CreateSessionResponseBody>("/v1/together/sessions", {
      hostDisplayName: "Room 2 Host",
    });
    const room2Ws = await createWsClient();
    room2Ws.send(
      JSON.stringify({
        type: "client_hello",
        protocolVersion: 1,
        sessionId: room2Res.data.sessionId,
        sessionKey: room2Res.data.hostKey,
        clientId: "room2_host_client",
        displayName: "Room 2 Host",
      })
    );
    await waitForMessage<ServerWelcome>(room2Ws, "server_welcome");

    // Broadcast in room 1, ensure room 2 does NOT receive it
    let room2ReceivedRoom1 = false;
    room2Ws.on("message", (raw) => {
      const p = JSON.parse(raw.toString());
      if (p.type === "room_state" && p.state.sessionId === sessionId) {
        room2ReceivedRoom1 = true;
      }
    });
    hostWs.send(JSON.stringify({ type: "room_state", state: mockState }));
    await new Promise((r) => setTimeout(r, 200));
    assert.strictEqual(room2ReceivedRoom1, false, "Room 2 must not receive Room 1 state");
    console.log("  ✓ Rooms are completely isolated");

    // 20. Malformed WebSocket messages
    console.log("[Test 20] Malformed WebSocket message rejection");
    const testWs = await createWsClient();
    testWs.send("NOT_JSON_AT_ALL");
    const err = await waitForMessage<any>(testWs, "server_error");
    assert.ok(err.message.includes("Invalid JSON"));
    console.log("  ✓ Malformed non-JSON message gracefully rejected");

    // Cleanup sockets
    hostWs.close();
    guest1Ws.close();
    appHostWs.close();
    room2Ws.close();
    testWs.close();

    console.log("\n=======================================================");
    console.log("ALL 20 AUTOMATED TESTS PASSED SUCCESSFULLY! ✓");
    console.log("=======================================================\n");
  } finally {
    await stopServer();
  }
}

runAllTests().catch((err) => {
  console.error("Test failed with error:", err);
  process.exit(1);
});
