/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.together

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object TogetherOnlineEndpoint {
    // Cynk Together Relay Server URL
    private const val DEFAULT_RELAY_URL = "https://cynk-lczv.onrender.com"

    fun baseUrlOrNull(
        dataStore: DataStore<Preferences>,
    ): String {
        val cached = runCatching {
            runBlocking {
                dataStore.data.first()[com.nikhil.yt.constants.TogetherOnlineEndpointCacheKey]
            }
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }

        return cached ?: DEFAULT_RELAY_URL
    }

    fun onlineWebSocketUrlOrNull(
        rawWsUrl: String,
        baseUrl: String,
    ): String? {
        val trimmed = rawWsUrl.trim()
        if (trimmed.isNotBlank() && (trimmed.startsWith("ws://") || trimmed.startsWith("wss://"))) {
            return trimmed
        }

        val baseUri = runCatching { java.net.URI(baseUrl) }.getOrNull()
        if (baseUri != null && baseUri.host != null) {
            val scheme = if (baseUri.scheme == "https") "wss" else "ws"
            val portPart = if (baseUri.port != -1 && baseUri.port != 80 && baseUri.port != 443) ":${baseUri.port}" else ""
            return "$scheme://${baseUri.host}$portPart/v1/together/ws"
        }

        return "wss://cynk-lczv.onrender.com/v1/together/ws"
    }
}
