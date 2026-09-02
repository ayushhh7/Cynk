import { createServer } from "node:http";
import { WebSocketServer } from "ws";
import { RoomManager } from "./roomManager.js";
import { handleHttpRequest } from "./apiRoutes.js";
import { handleWebSocketConnection } from "./wsHandler.js";

const PORT = parseInt(process.env.PORT || "8080", 10);
const HOST = process.env.HOST || "0.0.0.0";
const BEARER_TOKEN = process.env.TOGETHER_BEARER_TOKEN;

const roomManager = new RoomManager();

const server = createServer((req, res) => {
  handleHttpRequest(req, res, roomManager, BEARER_TOKEN);
});

const wss = new WebSocketServer({ noServer: true });

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

server.listen(PORT, HOST, () => {
  console.log(`[Cynk Together Relay] Server listening on http://${HOST}:${PORT}`);
  console.log(`[Cynk Together Relay] Health endpoint: http://${HOST}:${PORT}/health`);
  console.log(`[Cynk Together Relay] Sessions endpoint: http://${HOST}:${PORT}/v1/together/sessions`);
  console.log(`[Cynk Together Relay] WebSocket endpoint: ws://${HOST}:${PORT}/v1/together/ws`);
});

// Graceful shutdown
const shutdown = () => {
  console.log("[Cynk Together Relay] Shutting down gracefully...");
  roomManager.stop();
  wss.close();
  server.close(() => {
    console.log("[Cynk Together Relay] Server closed.");
    process.exit(0);
  });
};

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
