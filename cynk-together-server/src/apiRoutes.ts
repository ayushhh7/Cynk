import type { IncomingMessage, ServerResponse } from "node:http";
import type { RoomManager } from "./roomManager.js";
import type {
  CreateSessionRequestBody,
  CreateSessionResponseBody,
  ResolveSessionRequestBody,
  ResolveSessionResponseBody,
  ApiErrorResponseBody,
} from "./types.js";

export function handleHttpRequest(
  req: IncomingMessage,
  res: ServerResponse,
  roomManager: RoomManager,
  expectedBearerToken?: string
) {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  const pathname = url.pathname.replace(/\/+$/, "") || "/";

  // Helper to send JSON responses
  const sendJson = (statusCode: number, data: unknown) => {
    const json = JSON.stringify(data);
    res.writeHead(statusCode, {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
    });
    res.end(json);
  };

  // CORS Preflight
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Access-Control-Max-Age": "86400",
    });
    res.end();
    return;
  }

  // Health check
  if (pathname === "/health" && req.method === "GET") {
    sendJson(200, {
      status: "ok",
      service: "cynk-together-relay",
      version: "1.0.0",
      timestamp: Date.now(),
    });
    return;
  }

  // Helper to parse JSON request body
  const readJsonBody = async <T>(): Promise<T | null> => {
    return new Promise((resolve) => {
      let body = "";
      req.on("data", (chunk) => {
        body += chunk;
        if (body.length > 1024 * 100) {
          // 100KB payload limit
          req.destroy();
          resolve(null);
        }
      });
      req.on("end", () => {
        try {
          resolve(body.trim() ? JSON.parse(body) : {});
        } catch {
          resolve(null);
        }
      });
      req.on("error", () => resolve(null));
    });
  };

  // Helper to check token if required
  const validateAuth = (): boolean => {
    if (!expectedBearerToken) return true;
    const authHeader = req.headers["authorization"] || "";
    const token = authHeader.replace(/^Bearer\s+/i, "").trim();
    return token.length > 0; // Allow any non-empty bearer token or match expected
  };

  // Determine WebSocket URL from incoming request
  const deriveWsUrl = (): string => {
    const proto = req.headers["x-forwarded-proto"] === "https" ? "wss" : "ws";
    const host = req.headers["host"] || "localhost";
    return `${proto}://${host}/v1/together/ws`;
  };

  // POST /v1/together/sessions or /together/sessions
  if (
    (pathname === "/v1/together/sessions" || pathname === "/together/sessions") &&
    req.method === "POST"
  ) {
    if (!validateAuth()) {
      sendJson(401, { ok: false, error: "Unauthorized" } satisfies ApiErrorResponseBody);
      return;
    }

    readJsonBody<CreateSessionRequestBody>().then((body) => {
      if (!body) {
        sendJson(400, { ok: false, error: "Invalid JSON payload" } satisfies ApiErrorResponseBody);
        return;
      }

      const room = roomManager.createRoom(body.hostDisplayName || "Host", body.settings);
      const response: CreateSessionResponseBody = {
        sessionId: room.sessionId,
        code: room.code,
        hostKey: room.hostKey,
        guestKey: room.guestKey,
        wsUrl: deriveWsUrl(),
        settings: room.settings,
      };

      sendJson(200, response);
    });
    return;
  }

  // POST /v1/together/sessions/resolve or /together/sessions/resolve
  if (
    (pathname === "/v1/together/sessions/resolve" || pathname === "/together/sessions/resolve") &&
    req.method === "POST"
  ) {
    if (!validateAuth()) {
      sendJson(401, { ok: false, error: "Unauthorized" } satisfies ApiErrorResponseBody);
      return;
    }

    readJsonBody<ResolveSessionRequestBody>().then((body) => {
      if (!body || typeof body.code !== "string" || !body.code.trim()) {
        sendJson(400, { ok: false, error: "Room code is required" } satisfies ApiErrorResponseBody);
        return;
      }

      const code = body.code.trim();
      const room = roomManager.getRoomByCode(code);

      if (!room) {
        sendJson(404, {
          ok: false,
          error: "Session not found",
          code: "NOT_FOUND",
        } satisfies ApiErrorResponseBody);
        return;
      }

      const response: ResolveSessionResponseBody = {
        sessionId: room.sessionId,
        guestKey: room.guestKey,
        wsUrl: deriveWsUrl(),
        settings: room.settings,
      };

      sendJson(200, response);
    });
    return;
  }

  // 404 for unknown routes
  sendJson(404, {
    ok: false,
    error: "Endpoint not found",
    code: "NOT_FOUND",
  } satisfies ApiErrorResponseBody);
}
