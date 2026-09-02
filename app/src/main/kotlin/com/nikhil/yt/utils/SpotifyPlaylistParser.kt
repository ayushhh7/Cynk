package com.nikhil.yt.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class SpotifyTrack(
    val name: String,
    val artist: String,
    val durationMs: Long? = null,
    val isExplicit: Boolean = false,
)

data class SpotifyPlaylistData(
    val id: String,
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val tracks: List<SpotifyTrack>,
)

object SpotifyPlaylistParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Extracts the 22-character Spotify playlist ID from various URL/URI formats.
     */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Format: spotify:playlist:37i9dQZF1DXcBWIGoYBM5M
        val uriMatch = "spotify:playlist:([a-zA-Z0-9]{22})".toRegex().find(trimmed)
        if (uriMatch != null) return uriMatch.groupValues[1]

        // Format: https://open.spotify.com/.../playlist/37i9dQZF1DXcBWIGoYBM5M?...
        val urlMatch = "playlist/([a-zA-Z0-9]{22})".toRegex().find(trimmed)
        if (urlMatch != null) return urlMatch.groupValues[1]

        // Raw 22-char ID
        if (trimmed.matches("^[a-zA-Z0-9]{22}$".toRegex())) {
            return trimmed
        }

        return null
    }

    /**
     * Fetches public Spotify playlist details and tracklist from Spotify's embed endpoint.
     */
    suspend fun fetchPlaylist(urlOrId: String): Result<SpotifyPlaylistData> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = extractPlaylistId(urlOrId)
                ?: throw IllegalArgumentException("Invalid Spotify playlist URL or ID")

            val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch playlist (HTTP ${response.code})")
            }

            val html = response.body?.string() ?: throw IllegalStateException("Empty response from Spotify")
            val doc = Jsoup.parse(html)

            var title: String? = null
            var coverUrl: String? = null
            var description: String? = null
            val tracks = mutableListOf<SpotifyTrack>()

            // 1. Try parsing structured JSON in __NEXT_DATA__
            val nextDataScript = doc.selectFirst("script#__NEXT_DATA__")
            if (nextDataScript != null) {
                try {
                    val json = JSONObject(nextDataScript.data())
                    val entity = json.optJSONObject("props")
                        ?.optJSONObject("pageProps")
                        ?.optJSONObject("state")
                        ?.optJSONObject("data")
                        ?.optJSONObject("entity")

                    if (entity != null) {
                        title = entity.optString("name").takeIf { it.isNotBlank() }
                        description = entity.optString("description").takeIf { it.isNotBlank() }

                        val coverArt = entity.optJSONObject("coverArt")
                        val sources = coverArt?.optJSONArray("sources")
                        if (sources != null && sources.length() > 0) {
                            coverUrl = sources.getJSONObject(0).optString("url").takeIf { it.isNotBlank() }
                        }

                        val trackList = entity.optJSONArray("trackList")
                        if (trackList != null) {
                            for (i in 0 until trackList.length()) {
                                val trackObj = trackList.getJSONObject(i)
                                val trackTitle = trackObj.optString("title")
                                val trackSubtitle = trackObj.optString("subtitle")
                                val durationMs = trackObj.optLong("duration", 0L)
                                val isExplicit = trackObj.optBoolean("isExplicit", false)

                                if (trackTitle.isNotBlank()) {
                                    tracks.add(
                                        SpotifyTrack(
                                            name = trackTitle.trim(),
                                            artist = trackSubtitle.trim(),
                                            durationMs = if (durationMs > 0) durationMs else null,
                                            isExplicit = isExplicit
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fallback to DOM parsing
                }
            }

            // 2. DOM Fallback for title and cover
            if (title.isNullOrBlank()) {
                title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                    ?: doc.title().removeSuffix(" - Spotify Playlist").removeSuffix(" | Spotify")
            }
            if (coverUrl.isNullOrBlank()) {
                coverUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
            }

            // 3. DOM Fallback for tracks if __NEXT_DATA__ yielded nothing
            if (tracks.isEmpty()) {
                val rows = doc.select("li[class*='TracklistRow']")
                for (row in rows) {
                    val trackTitle = row.selectFirst("h3")?.text()?.trim() ?: continue
                    val artist = row.selectFirst("h4")?.text()?.trim() ?: ""
                    val isExplicit = row.selectFirst("[data-testid=tag]") != null
                    tracks.add(
                        SpotifyTrack(
                            name = trackTitle,
                            artist = artist,
                            isExplicit = isExplicit
                        )
                    )
                }
            }

            if (tracks.isEmpty()) {
                throw IllegalStateException("No tracks could be found in this Spotify playlist. The playlist might be private or empty.")
            }

            SpotifyPlaylistData(
                id = playlistId,
                title = title?.ifBlank { "Spotify Playlist" } ?: "Spotify Playlist",
                description = description,
                coverUrl = coverUrl,
                tracks = tracks
            )
        }
    }
}
