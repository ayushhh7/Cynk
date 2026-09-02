package com.nikhil.yt.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPlaylistParserTest {

    @Test
    fun testExtractPlaylistId() {
        // Standard web URL
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyPlaylistParser.extractPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
        )

        // Web URL with query params
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyPlaylistParser.extractPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abcd1234efgh5678")
        )

        // Internationalized URL
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyPlaylistParser.extractPlaylistId("https://open.spotify.com/intl-en/playlist/37i9dQZF1DXcBWIGoYBM5M")
        )

        // Spotify URI
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyPlaylistParser.extractPlaylistId("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")
        )

        // Raw 22-char ID
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyPlaylistParser.extractPlaylistId("37i9dQZF1DXcBWIGoYBM5M")
        )
    }

    @Test
    fun testFetchPlaylistReal() = runBlocking {
        val result = SpotifyPlaylistParser.fetchPlaylist("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
        assertTrue("Fetch should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val data = result.getOrNull()
        assertNotNull(data)
        println("Fetched playlist title: ${data!!.title}")
        println("Fetched tracks count: ${data.tracks.size}")
        assertTrue("Should have tracks", data.tracks.isNotEmpty())

        val firstTrack = data.tracks.first()
        println("First track: ${firstTrack.name} by ${firstTrack.artist}")
        assertTrue("Track title should not be blank", firstTrack.name.isNotBlank())
    }
}
