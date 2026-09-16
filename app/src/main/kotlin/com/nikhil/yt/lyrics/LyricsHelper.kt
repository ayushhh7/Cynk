/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.nikhil.yt.utils.GlobalLog
import com.nikhil.yt.constants.PreferredLyricsProvider
import com.nikhil.yt.constants.PreferredLyricsProviderKey
import com.nikhil.yt.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.nikhil.yt.extensions.toEnum
import com.nikhil.yt.models.MediaMetadata
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.reportException
import com.nikhil.yt.utils.NetworkConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    private val baseProviders =
        listOf(
            SimpMusicLyricsProvider,
            BetterLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            YouTubeSubtitleLyricsProvider,
            YouTubeLyricsProvider,
        )

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata, preferredProviderOnly: Boolean = false): String {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            GlobalLog.append(Log.DEBUG, "LyricsHelper", "Found lyrics in cache for ${mediaMetadata.title}")
            return cached.lyrics
        }
        
        GlobalLog.append(Log.DEBUG, "LyricsHelper", "Fetching lyrics for ${mediaMetadata.title} (Artist: ${mediaMetadata.artists.joinToString { it.name }}, Album: ${mediaMetadata.album?.title})")

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        
        if (!isNetworkAvailable) {
            GlobalLog.append(Log.WARN, "LyricsHelper", "Network unavailable, aborting lyrics fetch")
            return LYRICS_NOT_FOUND
        }

        val ordered = orderedProviders()
        val providers = if (preferredProviderOnly) listOf(ordered.first()) else ordered
        val enabledProviders = providers.filter { it.isEnabled(context) }
        if (enabledProviders.isEmpty()) return LYRICS_NOT_FOUND

        val artistsString = mediaMetadata.artists.joinToString { it.name }
        val albumTitle = mediaMetadata.album?.title
        val duration = mediaMetadata.duration

        suspend fun queryProvider(provider: LyricsProvider): String? {
            return try {
                val result = provider.getLyrics(
                    mediaMetadata.id,
                    mediaMetadata.title,
                    artistsString,
                    albumTitle,
                    duration,
                )
                val lyrics = result.getOrNull()
                if (lyrics != null && isMeaningfulLyrics(lyrics)) lyrics else null
            } catch (e: Exception) {
                reportException(e)
                null
            }
        }

        if (enabledProviders.size == 1) {
            val provider = enabledProviders[0]
            val lyrics = queryProvider(provider)
            if (lyrics != null) {
                cache.put(mediaMetadata.id, listOf(LyricsResult(provider.name, lyrics)))
                return lyrics
            }
            return LYRICS_NOT_FOUND
        }

        val preferred = enabledProviders[0]
        val fallbacks = enabledProviders.drop(1)

        val lyrics = coroutineScope {
            val preferredDeferred = async(Dispatchers.IO) { queryProvider(preferred) }
            val fallbackDeferreds = fallbacks.take(2).map { provider ->
                provider to async(Dispatchers.IO) { queryProvider(provider) }
            }

            // Give the preferred provider a prioritized 850ms window
            val prefResult = withTimeoutOrNull(850L) {
                preferredDeferred.await()
            }

            if (prefResult != null) {
                fallbackDeferreds.forEach { it.second.cancel() }
                cache.put(mediaMetadata.id, listOf(LyricsResult(preferred.name, prefResult)))
                return@coroutineScope prefResult
            }

            // Preferred was slow, timed out, or returned null/failed.
            // Check if any concurrently running fallback has already completed!
            for ((provider, deferred) in fallbackDeferreds) {
                if (deferred.isCompleted) {
                    val fbResult = deferred.await()
                    if (fbResult != null) {
                        preferredDeferred.cancel()
                        cache.put(mediaMetadata.id, listOf(LyricsResult(provider.name, fbResult)))
                        return@coroutineScope fbResult
                    }
                }
            }

            // Race whichever of preferred and fallbacks finishes first with valid lyrics
            val allActive = listOf(preferred to preferredDeferred) + fallbackDeferreds
            val resultChannel = Channel<Pair<String, String>>(allActive.size)

            allActive.forEach { (prov, def) ->
                launch(Dispatchers.IO) {
                    val res = try { def.await() } catch (_: Exception) { null }
                    if (res != null) {
                        resultChannel.trySend(prov.name to res)
                    }
                }
            }

            var foundResult: Pair<String, String>? = null
            try {
                withTimeout(3000L) {
                    foundResult = resultChannel.receiveCatching().getOrNull()
                }
            } catch (_: Exception) {}

            if (foundResult != null) {
                cache.put(mediaMetadata.id, listOf(LyricsResult(foundResult!!.first, foundResult!!.second)))
                return@coroutineScope foundResult!!.second
            }

            // If top providers all failed, sequentially try remaining providers as last resort
            val remaining = fallbacks.drop(2)
            for (prov in remaining) {
                val remResult = queryProvider(prov)
                if (remResult != null) {
                    cache.put(mediaMetadata.id, listOf(LyricsResult(prov.name, remResult)))
                    return@coroutineScope remResult
                }
            }

            LYRICS_NOT_FOUND
        }

        return lyrics
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        songAlbum: String?,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = mediaId
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        
        if (!isNetworkAvailable) {
            return
        }

        val allResult = mutableListOf<LyricsResult>()
        val providers = orderedProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
            providers.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, songAlbum, duration) lyricsCallback@{ lyrics ->
                            if (!isMeaningfulLyrics(lyrics)) return@lyricsCallback
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    private suspend fun orderedProviders(): List<LyricsProvider> {
        val preferred =
            context.dataStore.data
                .first()[PreferredLyricsProviderKey]
                .toEnum(PreferredLyricsProvider.LRCLIB)

        val first =
            when (preferred) {
                PreferredLyricsProvider.LRCLIB -> LrcLibLyricsProvider
                PreferredLyricsProvider.KUGOU -> KuGouLyricsProvider
                PreferredLyricsProvider.BETTER_LYRICS -> BetterLyricsProvider
                PreferredLyricsProvider.SIMPMUSIC -> SimpMusicLyricsProvider
            }

        return listOf(first) + baseProviders.filterNot { provider -> provider == first }
    }

    private fun isMeaningfulLyrics(lyrics: String): Boolean {
        val normalized =
            lyrics
                .replace("\uFEFF", "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == '\u00A0' }

        if (normalized.isEmpty()) return false
        if (normalized == LYRICS_NOT_FOUND) return false

        val remaining =
            TIMESTAMP_REGEX
                .replace(normalized, "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == '\u00A0' }

        return remaining.any { !it.isWhitespace() && it != '\u00A0' }
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
        private val TIMESTAMP_REGEX = Regex("""\[[0-9]{1,2}:[0-9]{2}(?:\.[0-9]{1,3})?]""")
        private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD]""")
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
