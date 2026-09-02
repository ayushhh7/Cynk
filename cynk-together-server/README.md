# Cynk Together Relay Server

Standalone, real-time synchronization relay server for **Cynk Together** (group listening across any network/internet).

## Architecture

```
Android Host (Cynk App)
       │
       │ HTTPS (POST /v1/together/sessions)
       │ WSS   (WSS  /v1/together/ws)
       ▼
Cynk Together Relay Server (State & Sync Only)
       ▲
       │ HTTPS (POST /v1/together/sessions/resolve)
       │ WSS   (WSS  /v1/together/ws)
       │
Android Guests (Cynk App)
```

> **Important**: The server **never** proxies or streams music audio. Each client device streams music independently from YouTube. The relay server synchronizes room state, queue, timestamps, play/pause, seek, skip, participants, and host permissions.

---

## API Endpoints

### 1. `GET /health`
Returns service status.
```json
{
  "status": "ok",
  "service": "cynk-together-relay",
  "version": "1.0.0",
  "timestamp": 1724941234567
}
```

### 2. `POST /v1/together/sessions`
Creates a new Together room.
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Body**:
  ```json
  {
    "hostDisplayName": "Alice",
    "settings": {
      "allowGuestsToAddTracks": true,
      "allowGuestsToControlPlayback": false,
      "requireHostApprovalToJoin": false
    }
  }
  ```
* **Response (200)**:
  ```json
  {
    "sessionId": "b8f0475b-40fa-400e-9533-3d0277df607d",
    "code": "ABC123",
    "hostKey": "8f8efcfc-ff8f-4ad1-a9f3-8b7a0f7dcfa8",
    "guestKey": "a933f78d-192a-43c3-8f0c-b262f7902d33",
    "wsUrl": "wss://cynk-server.onrender.com/v1/together/ws",
    "settings": { ... }
  }
  ```

### 3. `POST /v1/together/sessions/resolve`
Resolves a 6-character room code to join info.
* **Headers**: `Authorization: Bearer <token>`, `Content-Type: application/json`
* **Body**:
  ```json
  {
    "code": "ABC123"
  }
  ```
* **Response (200)**:
  ```json
  {
    "sessionId": "b8f0475b-40fa-400e-9533-3d0277df607d",
    "guestKey": "a933f78d-192a-43c3-8f0c-b262f7902d33",
    "wsUrl": "wss://cynk-server.onrender.com/v1/together/ws",
    "settings": { ... }
  }
  ```
* **Response (404)**:
  ```json
  {
    "ok": false,
    "error": "Session not found",
    "code": "NOT_FOUND"
  }
  ```

### 4. `WSS /v1/together/ws`
Full-duplex real-time relay for room states, heartbeats, control requests, and moderation.

---

## Local Development & Testing

```bash
# Install dependencies
npm install

# Run automated tests
npm test

# Build TypeScript
npm run build

# Start server
npm start
```

---

## Deployment to Render.com (1-Click)

1. Create a **New Web Service** on Render.com connected to your repository (or deploy from `cynk-together-server` folder).
2. Configuration:
   * **Runtime**: Node
   * **Build Command**: `npm install && npm run build`
   * **Start Command**: `npm start`
   * **Environment Variables**:
     * `NODE_ENV`: `production`
     * `PORT`: `10000`
3. After deployment, note your Render URL (e.g. `https://cynk-together.onrender.com`).
4. Update `TogetherOnlineEndpoint.kt` or set `TOGETHER_ONLINE_ENDPOINT_CACHE` in Cynk app to point to your deployed URL.
