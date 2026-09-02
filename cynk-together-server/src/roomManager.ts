import { randomBytes, randomUUID } from "node:crypto";
import type { WebSocket } from "ws";
import type {
  TogetherRoomSettings,
  TogetherRoomState,
  TogetherParticipant,
  TogetherTrack,
} from "./types.js";

export interface ConnectedClient {
  participantId: string;
  clientId: string;
  displayName: string;
  role: "HOST" | "GUEST";
  isPending: boolean;
  ws: WebSocket;
  sessionId: string;
}

export interface Room {
  sessionId: string;
  code: string;
  hostKey: string;
  guestKey: string;
  hostDisplayName: string;
  settings: TogetherRoomSettings;
  createdAt: number;
  lastActivityAt: number;
  hostClient?: ConnectedClient;
  guests: Map<string, ConnectedClient>; // participantId -> ConnectedClient
  bannedClientIds: Set<string>;
  latestRoomState?: TogetherRoomState;
}

const CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 32 unambiguous chars

export class RoomManager {
  private roomsBySessionId = new Map<string, Room>();
  private roomsByCode = new Map<string, Room>();
  private sweepInterval: NodeJS.Timeout | null = null;

  constructor() {
    this.sweepInterval = setInterval(() => this.sweepExpiredRooms(), 60_000);
  }

  public stop() {
    if (this.sweepInterval) {
      clearInterval(this.sweepInterval);
      this.sweepInterval = null;
    }
  }

  public generateRoomCode(): string {
    for (let attempts = 0; attempts < 100; attempts++) {
      const bytes = randomBytes(6);
      let code = "";
      for (let i = 0; i < 6; i++) {
        code += CODE_CHARS[bytes[i] % CODE_CHARS.length];
      }
      if (!this.roomsByCode.has(code)) {
        return code;
      }
    }
    // Fallback if 100 collisions happen
    return randomUUID().substring(0, 6).toUpperCase();
  }

  public createRoom(
    hostDisplayName: string,
    initialSettings?: Partial<TogetherRoomSettings>
  ): Room {
    const sessionId = randomUUID();
    const code = this.generateRoomCode();
    const hostKey = randomUUID();
    const guestKey = randomUUID();

    const settings: TogetherRoomSettings = {
      allowGuestsToAddTracks: initialSettings?.allowGuestsToAddTracks ?? true,
      allowGuestsToControlPlayback:
        initialSettings?.allowGuestsToControlPlayback ?? false,
      requireHostApprovalToJoin:
        initialSettings?.requireHostApprovalToJoin ?? false,
    };

    const room: Room = {
      sessionId,
      code,
      hostKey,
      guestKey,
      hostDisplayName: hostDisplayName.trim() || "Host",
      settings,
      createdAt: Date.now(),
      lastActivityAt: Date.now(),
      guests: new Map(),
      bannedClientIds: new Set(),
    };

    this.roomsBySessionId.set(sessionId, room);
    this.roomsByCode.set(code.toUpperCase(), room);
    return room;
  }

  public getRoomBySessionId(sessionId: string): Room | undefined {
    return this.roomsBySessionId.get(sessionId);
  }

  public getRoomByCode(code: string): Room | undefined {
    return this.roomsByCode.get(code.trim().toUpperCase());
  }

  public removeRoom(sessionId: string): boolean {
    const room = this.roomsBySessionId.get(sessionId);
    if (!room) return false;

    this.roomsBySessionId.delete(sessionId);
    this.roomsByCode.delete(room.code);
    return true;
  }

  public getParticipantsList(room: Room): TogetherParticipant[] {
    const list: TogetherParticipant[] = [];

    if (room.hostClient) {
      list.push({
        id: room.hostClient.participantId,
        name: room.hostDisplayName,
        isHost: true,
        isPending: false,
        isConnected: true,
      });
    } else {
      list.push({
        id: "host",
        name: room.hostDisplayName,
        isHost: true,
        isPending: false,
        isConnected: false,
      });
    }

    const sortedGuests = Array.from(room.guests.values()).sort((a, b) =>
      a.displayName.localeCompare(b.displayName, undefined, { sensitivity: "base" })
    );

    for (const g of sortedGuests) {
      list.push({
        id: g.participantId,
        name: g.displayName,
        isHost: false,
        isPending: g.isPending,
        isConnected: true,
      });
    }

    return list;
  }

  public getSanitizedRoomState(
    state: TogetherRoomState,
    isPending: boolean
  ): TogetherRoomState {
    if (isPending) {
      return {
        ...state,
        queue: [],
        queueHash: "",
        currentIndex: 0,
        isPlaying: false,
        positionMs: 0,
      };
    }
    return state;
  }

  private sweepExpiredRooms() {
    const now = Date.now();
    const maxInactiveMs = 24 * 60 * 60 * 1000; // 24 hours
    const maxHostlessMs = 5 * 60 * 1000; // 5 min after host disconnects

    for (const [sessionId, room] of this.roomsBySessionId.entries()) {
      const isOld = now - room.lastActivityAt > maxInactiveMs;
      const isAbandoned = !room.hostClient && now - room.lastActivityAt > maxHostlessMs;

      if (isOld || isAbandoned) {
        // Disconnect any remaining guests
        for (const guest of room.guests.values()) {
          try {
            guest.ws.close(1000, "Session expired");
          } catch {}
        }
        this.removeRoom(sessionId);
      }
    }
  }
}
