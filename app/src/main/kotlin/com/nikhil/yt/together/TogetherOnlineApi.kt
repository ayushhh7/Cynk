/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.together

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TogetherOnlineCreateSessionRequest(
    val hostDisplayName: String,
    val settings: TogetherRoomSettings,
)

@Serializable
data class TogetherOnlineCreateSessionResponse(
    val sessionId: String,
    val code: String,
    val hostKey: String,
    val guestKey: String,
    val wsUrl: String,
    val settings: TogetherRoomSettings,
)

@Serializable
data class TogetherOnlineResolveRequest(
    val code: String,
)

@Serializable
data class TogetherOnlineResolveResponse(
    val sessionId: String,
    val guestKey: String,
    val wsUrl: String,
    val settings: TogetherRoomSettings,
)

class TogetherOnlineApiException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class TogetherOnlineApi(
    private val baseUrl: String,
    private val bearerToken: String? = null,
) {
    private val v1BaseUrl: String =
        baseUrl
            .trimEnd('/')
            .let { if (it.endsWith("/v1")) it else "$it/v1" }

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
        }

    private suspend fun <T> withRetry(
        maxAttempts: Int = 2,
        initialDelayMs: Long = 800,
        block: suspend () -> T,
    ): T {
        var lastException: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (t: Throwable) {
                lastException = t
                if (attempt < maxAttempts && isRetryable(t)) {
                    delay(initialDelayMs * attempt)
                } else {
                    throw t
                }
            }
        }
        throw lastException!!
    }

    private fun isRetryable(t: Throwable): Boolean {
        val root = generateSequence(t) { it.cause }.lastOrNull() ?: t
        return root is java.net.SocketTimeoutException ||
            root is java.net.ConnectException ||
            root is java.io.IOException
    }

    @Serializable
    private data class TogetherOnlineErrorResponse(
        val ok: Boolean? = null,
        val error: String? = null,
        val code: String? = null,
    )

    private fun errorMessageFromResponse(
        status: Int,
        rawBody: String,
        action: String = "request",
    ): String {
        val parsedError =
            runCatching { json.decodeFromString(TogetherOnlineErrorResponse.serializer(), rawBody) }
                .getOrNull()
        val specific = parsedError?.error?.trim()?.takeIf { it.isNotBlank() }
        if (specific != null) return specific

        if (rawBody.contains("no-server", ignoreCase = true) || rawBody.trim() == "Not Found") {
            return "Relay server is offline or unreachable ($status)"
        }

        return when (status) {
            400 -> "Bad request ($action)"
            401 -> "Unauthorized: invalid Together token"
            403 -> "Forbidden ($action)"
            404 -> if (action == "createSession") "Relay server endpoint not found (HTTP 404)" else "Session not found"
            429 -> "Too many requests, please try again later"
            in 500..599 -> "Relay server error ($status)"
            else -> "Unexpected server response ($status)"
        }
    }

    private fun normalizedBearerTokenOrNull(): String? = bearerToken?.trim()?.takeIf { it.isNotBlank() }

    suspend fun createSession(
        hostDisplayName: String,
        settings: TogetherRoomSettings,
    ): TogetherOnlineCreateSessionResponse =
        withRetry {
            val token = normalizedBearerTokenOrNull() ?: throw TogetherOnlineApiException("Together token is missing")
            val payload =
                json.encodeToString(
                    TogetherOnlineCreateSessionRequest.serializer(),
                    TogetherOnlineCreateSessionRequest(
                        hostDisplayName = hostDisplayName,
                        settings = settings,
                    ),
                )
            val resp =
                client.post("$v1BaseUrl/together/sessions") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            val status = resp.status.value
            val raw = resp.bodyAsText()
            if (status !in 200..299) throw TogetherOnlineApiException(errorMessageFromResponse(status, raw, "createSession"), statusCode = status)
            json.decodeFromString(TogetherOnlineCreateSessionResponse.serializer(), raw)
        }

    suspend fun resolveCode(
        code: String,
    ): TogetherOnlineResolveResponse =
        withRetry {
            val token = normalizedBearerTokenOrNull() ?: throw TogetherOnlineApiException("Together token is missing")
            val payload =
                json.encodeToString(
                    TogetherOnlineResolveRequest.serializer(),
                    TogetherOnlineResolveRequest(code = code.trim()),
                )
            val resp =
                client.post("$v1BaseUrl/together/sessions/resolve") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            val status = resp.status.value
            val raw = resp.bodyAsText()
            if (status !in 200..299) throw TogetherOnlineApiException(errorMessageFromResponse(status, raw, "resolveCode"), statusCode = status)
            json.decodeFromString(TogetherOnlineResolveResponse.serializer(), raw)
        }
}
