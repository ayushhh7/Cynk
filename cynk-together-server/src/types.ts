export const TOGETHER_PROTOCOL_VERSION = 1;

export interface TogetherTrack {
  id: string;
  title: string;
  artists?: string[];
  durationSec?: number;
  thumbnailUrl?: string | null;
}

export interface TogetherParticipant {
  id: string;
  name: string;
  isHost: boolean;
  isPending: boolean;
  isConnected: boolean;
}

export interface TogetherRoomSettings {
  allowGuestsToAddTracks: boolean;
  allowGuestsToControlPlayback: boolean;
  requireHostApprovalToJoin: boolean;
}

export interface TogetherRoomState {
  sessionId: string;
  hostId: string;
  participants: TogetherParticipant[];
  settings: TogetherRoomSettings;
  queue: TogetherTrack[];
  queueHash: string;
  currentIndex: number;
  isPlaying: boolean;
  positionMs: number;
  repeatMode: number;
  shuffleEnabled: boolean;
  sentAtElapsedRealtimeMs: number;
}

export type ServerRole = "HOST" | "GUEST";
export type AddTrackMode = "PLAY_NEXT" | "ADD_TO_QUEUE";

export type ControlAction =
  | { type: "play" }
  | { type: "pause" }
  | { type: "seek_to"; positionMs: number }
  | { type: "skip_next" }
  | { type: "skip_previous" }
  | { type: "seek_to_index"; index: number; positionMs?: number }
  | { type: "seek_to_track"; trackId: string; positionMs?: number }
  | { type: "set_repeat_mode"; repeatMode: number }
  | { type: "set_shuffle_enabled"; shuffleEnabled: boolean };

export interface ClientHello {
  type: "client_hello";
  protocolVersion: number;
  sessionId: string;
  sessionKey: string;
  clientId: string;
  displayName: string;
}

export interface ServerWelcome {
  type: "server_welcome";
  protocolVersion: number;
  sessionId: string;
  participantId: string;
  role: ServerRole;
  isPending: boolean;
  settings: TogetherRoomSettings;
}

export interface ServerError {
  type: "server_error";
  sessionId: string | null;
  message: string;
  code?: string | null;
}

export interface RoomStateMessage {
  type: "room_state";
  state: TogetherRoomState;
}

export interface ControlRequest {
  type: "control_request";
  sessionId: string;
  participantId: string;
  action: ControlAction;
}

export interface AddTrackRequest {
  type: "add_track_request";
  sessionId: string;
  participantId: string;
  track: TogetherTrack;
  mode: AddTrackMode;
}

export interface JoinDecision {
  type: "join_decision";
  sessionId: string;
  participantId: string;
  approved: boolean;
}

export interface JoinRequest {
  type: "join_request";
  sessionId: string;
  participant: TogetherParticipant;
}

export interface ParticipantJoined {
  type: "participant_joined";
  sessionId: string;
  participant: TogetherParticipant;
}

export interface ParticipantLeft {
  type: "participant_left";
  sessionId: string;
  participantId: string;
  reason?: string | null;
}

export interface HeartbeatPing {
  type: "heartbeat_ping";
  sessionId: string;
  pingId: number;
  clientElapsedRealtimeMs: number;
}

export interface HeartbeatPong {
  type: "heartbeat_pong";
  sessionId: string;
  pingId: number;
  clientElapsedRealtimeMs: number;
  serverElapsedRealtimeMs: number;
}

export interface ClientLeave {
  type: "client_leave";
  sessionId: string;
  participantId: string;
}

export interface KickParticipant {
  type: "kick";
  sessionId: string;
  participantId: string;
  reason?: string | null;
}

export interface BanParticipant {
  type: "ban";
  sessionId: string;
  participantId: string;
  reason?: string | null;
}

export type TogetherMessage =
  | ClientHello
  | ServerWelcome
  | ServerError
  | RoomStateMessage
  | ControlRequest
  | AddTrackRequest
  | JoinDecision
  | JoinRequest
  | ParticipantJoined
  | ParticipantLeft
  | HeartbeatPing
  | HeartbeatPong
  | ClientLeave
  | KickParticipant
  | BanParticipant;

export interface CreateSessionRequestBody {
  hostDisplayName?: string;
  settings?: Partial<TogetherRoomSettings>;
}

export interface CreateSessionResponseBody {
  sessionId: string;
  code: string;
  hostKey: string;
  guestKey: string;
  wsUrl: string;
  settings: TogetherRoomSettings;
}

export interface ResolveSessionRequestBody {
  code: string;
}

export interface ResolveSessionResponseBody {
  sessionId: string;
  guestKey: string;
  wsUrl: string;
  settings: TogetherRoomSettings;
}

export interface ApiErrorResponseBody {
  ok: boolean;
  error: string;
  code?: string;
}
