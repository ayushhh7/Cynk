/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.together

import androidx.compose.runtime.Immutable
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

@Immutable
sealed class TogetherOnlineHostState {
    data object Idle : TogetherOnlineHostState()
    data object Connecting : TogetherOnlineHostState()
    data class Connected(
        val wsUrl: String,
        val sessionId: String,
        val hostParticipantId: String,
    ) : TogetherOnlineHostState()
}

class TogetherOnlineHost(
    externalScope: CoroutineScope,
    val sessionId: String,
    private val sessionKey: String,
    private val hostId: String,
    private val hostDisplayName: String,
    initialSettings: TogetherRoomSettings,
    clientId: String = UUID.randomUUID().toString(),
    private val bearerToken: String? = null,
) {
    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
            install(WebSockets) {
                pingIntervalMillis = 25_000
            }
        }

    private val scope = CoroutineScope(externalScope.coroutineContext + SupervisorJob())
    private val mutex = Mutex()
    private var settings: TogetherRoomSettings = initialSettings

    private var session: WebSocketSession? = null
    private var loopJob: Job? = null
    private var hostParticipantId: String? = null

    private val clientId = clientId.trim().ifBlank { UUID.randomUUID().toString() }.take(64)
    private val normalizedBearerToken: String? = bearerToken?.trim()?.takeIf { it.isNotBlank() }

    private data class Guest(
        val participantId: String,
        val clientId: String,
        val name: String,
        var pending: Boolean,
    )

    private val guests = LinkedHashMap<String, Guest>()

    @Volatile
    private var lastParticipants: List<TogetherParticipant> = emptyList()

    var onEvent: ((TogetherServerEvent) -> Unit)? = null

    private var isExplicitDisconnect = false
    private var lastWsUrl: String? = null
    private var reconnectJob: Job? = null

    suspend fun connect(wsUrl: String) {
        isExplicitDisconnect = false
        lastWsUrl = wsUrl
        disconnectInternal()
        hostParticipantId = null
        guests.clear()
        lastParticipants = emptyList()

        val trimmed = wsUrl.trim()
        val urls = listOfNotNull(trimmed, alternateWebSocketSchemeOrNull(trimmed)).distinct()

        val token = normalizedBearerToken
        if (token == null) {
            onEvent?.invoke(TogetherServerEvent.Error("Together token is missing"))
            return
        }

        var lastError: Throwable? = null
        for (candidate in urls) {
            try {
                client.webSocket(
                    urlString = candidate,
                    request = {
                        header("Authorization", "Bearer $token")
                    },
                ) {
                    session = this
                    val hello =
                        ClientHello(
                            protocolVersion = TogetherProtocolVersion,
                            sessionId = sessionId,
                            sessionKey = sessionKey,
                            clientId = clientId,
                            displayName = hostDisplayName.trim(),
                        )
                    send(TogetherJson.json.encodeToString(TogetherMessage.serializer(), hello))
                    runLoop(this, candidate)
                }
                return
            } catch (t: Throwable) {
                lastError = t
            }
        }

        onEvent?.invoke(TogetherServerEvent.Error(connectionFailureMessage(lastError), lastError))
    }

    private fun alternateWebSocketSchemeOrNull(url: String): String? {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("ws://") -> "wss://${trimmed.removePrefix("ws://")}"
            trimmed.startsWith("wss://") -> "ws://${trimmed.removePrefix("wss://")}"
            else -> null
        }
    }

    private fun connectionFailureMessage(t: Throwable?): String {
        val root = generateSequence(t) { it.cause }.lastOrNull()
        val raw = root?.message?.trim().orEmpty()
        val reason =
            when (root) {
                is java.net.UnknownHostException -> "Server not found"
                is java.net.ConnectException -> "Connection refused"
                is java.net.SocketTimeoutException -> "Connection timed out"
                is javax.net.ssl.SSLHandshakeException -> "Secure connection failed"
                is IllegalArgumentException ->
                    if (raw.contains("ws", ignoreCase = true) && raw.contains("scheme", ignoreCase = true)) "Invalid server websocket URL" else null
                else -> null
            }

        val detail = reason ?: raw.takeIf { it.isNotBlank() }
        return if (detail == null) "Connection failed" else "Connection failed: $detail"
    }

    suspend fun disconnect() {
        isExplicitDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        disconnectInternal()
    }

    private suspend fun disconnectInternal() {
        loopJob?.cancel()
        loopJob?.cancelAndJoin()
        loopJob = null
        runCatching { session?.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnect")) }
        session = null
        hostParticipantId = null
        guests.clear()
        lastParticipants = emptyList()
    }

    fun currentParticipants(): List<TogetherParticipant> = lastParticipants

    suspend fun currentSettings(): TogetherRoomSettings = mutex.withLock { settings }

    suspend fun updateSettings(newSettings: TogetherRoomSettings) {
        mutex.withLock { settings = newSettings }
    }

    suspend fun approveParticipant(participantId: String, approved: Boolean) {
        val guest = guests[participantId]
        if (guest != null) {
            guest.pending = !approved
            rebuildParticipantsSnapshot()
        }
        runCatching {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    JoinDecision(
                        sessionId = sessionId,
                        participantId = participantId,
                        approved = approved,
                    ),
                ),
            )
        }
    }

    suspend fun kickParticipant(participantId: String, reason: String? = null) {
        guests.remove(participantId)
        rebuildParticipantsSnapshot()
        runCatching {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    KickParticipant(
                        sessionId = sessionId,
                        participantId = participantId,
                        reason = reason ?: "Kicked by host",
                    ),
                ),
            )
        }
    }

    suspend fun banParticipant(participantId: String, reason: String? = null) {
        guests.remove(participantId)
        rebuildParticipantsSnapshot()
        runCatching {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    BanParticipant(
                        sessionId = sessionId,
                        participantId = participantId,
                        reason = reason ?: "Banned by host",
                    ),
                ),
            )
        }
    }

    suspend fun broadcastRoomState(state: TogetherRoomState) {
        val snapshotSettings = mutex.withLock { settings }
        rebuildParticipantsSnapshot()
        val roomState =
            state.copy(
                hostId = hostId,
                settings = snapshotSettings,
                participants = lastParticipants,
            )

        runCatching {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    RoomStateMessage(roomState),
                ),
            )
        }
    }

    private fun rebuildParticipantsSnapshot() {
        val host =
            TogetherParticipant(
                id = hostId,
                name = hostDisplayName,
                isHost = true,
                isPending = false,
                isConnected = true,
            )

        val guestList =
            guests.values
                .sortedBy { it.name.lowercase() }
                .map {
                    TogetherParticipant(
                        id = it.participantId,
                        name = it.name,
                        isHost = false,
                        isPending = it.pending,
                        isConnected = true,
                    )
                }

        lastParticipants = buildList {
            add(host)
            addAll(guestList)
        }
    }

    private suspend fun runLoop(session: WebSocketSession, wsUrl: String) {
        var shouldTryReconnect = false
        loopJob =
            scope.launch {
                try {
                    while (true) {
                        val frame =
                            try {
                                session.incoming.receive()
                            } catch (_: ClosedReceiveChannelException) {
                                shouldTryReconnect = !isExplicitDisconnect
                                break
                            }

                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        val message =
                            runCatching { TogetherJson.json.decodeFromString(TogetherMessage.serializer(), text) }
                                .getOrElse {
                                    onEvent?.invoke(TogetherServerEvent.Error("Failed to decode message", it))
                                    continue
                                }

                        when (message) {
                            is ServerWelcome -> {
                                if (message.sessionId == sessionId) {
                                    hostParticipantId = message.participantId
                                    mutex.withLock { settings = message.settings }
                                    onEvent?.invoke(TogetherServerEvent.ServerWelcomeReceived(message))
                                }
                            }

                            is JoinRequest -> {
                                if (message.sessionId == sessionId) {
                                    val participant = message.participant.copy(isHost = false, isConnected = true, isPending = true)
                                    guests[participant.id] =
                                        Guest(
                                            participantId = participant.id,
                                            clientId = "",
                                            name = participant.name,
                                            pending = true,
                                        )
                                    rebuildParticipantsSnapshot()
                                    onEvent?.invoke(TogetherServerEvent.JoinRequested(participant))
                                }
                            }

                            is ParticipantJoined -> {
                                if (message.sessionId == sessionId) {
                                    val participant = message.participant.copy(isHost = false, isConnected = true, isPending = false)
                                    guests[participant.id] =
                                        Guest(
                                            participantId = participant.id,
                                            clientId = "",
                                            name = participant.name,
                                            pending = false,
                                        )
                                    rebuildParticipantsSnapshot()
                                    onEvent?.invoke(TogetherServerEvent.ParticipantJoined(participant))
                                }
                            }

                            is ParticipantLeft -> {
                                if (message.sessionId == sessionId) {
                                    guests.remove(message.participantId)
                                    rebuildParticipantsSnapshot()
                                    onEvent?.invoke(TogetherServerEvent.ParticipantLeft(message.participantId, message.reason))
                                }
                            }

                            is ControlRequest -> {
                                if (message.sessionId == sessionId) onEvent?.invoke(TogetherServerEvent.ControlRequested(message))
                            }

                            is AddTrackRequest -> {
                                if (message.sessionId == sessionId) onEvent?.invoke(TogetherServerEvent.AddTrackRequested(message))
                            }

                            is ServerError -> {
                                onEvent?.invoke(TogetherServerEvent.Error(message.message, null))
                            }

                            else -> Unit
                        }
                    }
                } catch (t: Throwable) {
                    shouldTryReconnect = !isExplicitDisconnect
                    onEvent?.invoke(TogetherServerEvent.Error("Connection loop failed", t))
                } finally {
                    if (shouldTryReconnect && !isExplicitDisconnect) {
                        scheduleHostReconnect()
                    } else {
                        hostParticipantId = null
                        guests.clear()
                        lastParticipants = emptyList()
                        runCatching { session.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnected")) }
                        onEvent?.invoke(TogetherServerEvent.Error("Disconnected", null))
                    }
                }
            }
        loopJob?.join()
    }

    private fun scheduleHostReconnect() {
        val targetUrl = lastWsUrl ?: return
        reconnectJob?.cancel()
        reconnectJob =
            scope.launch {
                var attempt = 0
                while (attempt < 4 && !isExplicitDisconnect) {
                    attempt++
                    val delayMs = (1000L * (1 shl (attempt - 1))).coerceAtMost(6000L)
                    kotlinx.coroutines.delay(delayMs)
                    if (isExplicitDisconnect) break

                    val token = normalizedBearerToken ?: break
                    val urls = listOfNotNull(targetUrl.trim(), alternateWebSocketSchemeOrNull(targetUrl.trim())).distinct()
                    for (candidate in urls) {
                        try {
                            client.webSocket(
                                urlString = candidate,
                                request = {
                                    header("Authorization", "Bearer $token")
                                },
                            ) {
                                session = this
                                val hello =
                                    ClientHello(
                                        protocolVersion = TogetherProtocolVersion,
                                        sessionId = sessionId,
                                        sessionKey = sessionKey,
                                        clientId = clientId,
                                        displayName = hostDisplayName.trim(),
                                    )
                                send(TogetherJson.json.encodeToString(TogetherMessage.serializer(), hello))
                                runLoop(this, candidate)
                            }
                            return@launch
                        } catch (_: Throwable) {
                            // Retry next
                        }
                    }
                }

                if (!isExplicitDisconnect) {
                    hostParticipantId = null
                    guests.clear()
                    lastParticipants = emptyList()
                    onEvent?.invoke(TogetherServerEvent.Error("Disconnected", null))
                }
            }
    }
}
