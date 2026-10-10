package com.nikhil.yt.innertube

import com.nikhil.yt.innertube.models.MusicResponsiveListItemRenderer
import com.nikhil.yt.innertube.models.Run
import com.nikhil.yt.innertube.models.Runs
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.innertube.pages.PlaylistPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistContinuationTest {

    @Test
    fun fromMusicResponsiveListItemRenderer_preservesItemWhenPlaylistSetVideoIdIsNull() {
        val renderer = MusicResponsiveListItemRenderer(
            badges = null,
            fixedColumns = null,
            flexColumns = listOf(
                MusicResponsiveListItemRenderer.FlexColumn(
                    musicResponsiveListItemFlexColumnRenderer = MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                        text = Runs(runs = listOf(Run(text = "Song Without SetVideoId", navigationEndpoint = null)))
                    )
                )
            ),
            thumbnail = null,
            menu = null,
            playlistItemData = MusicResponsiveListItemRenderer.PlaylistItemData(
                playlistSetVideoId = null,
                videoId = "vid_123"
            ),
            overlay = null,
            navigationEndpoint = null
        )

        val song = PlaylistPage.fromMusicResponsiveListItemRenderer(renderer)
        assertNotNull("Song should not be dropped even when playlistSetVideoId is null", song)
        assertEquals("vid_123", song?.id)
        assertEquals("Song Without SetVideoId", song?.title)
        assertNull(song?.setVideoId)
    }

    @Test
    fun fromMusicResponsiveListItemRenderer_preservesSetVideoIdWhenPresent() {
        val renderer = MusicResponsiveListItemRenderer(
            badges = null,
            fixedColumns = null,
            flexColumns = listOf(
                MusicResponsiveListItemRenderer.FlexColumn(
                    musicResponsiveListItemFlexColumnRenderer = MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                        text = Runs(runs = listOf(Run(text = "Track 1", navigationEndpoint = null)))
                    )
                )
            ),
            thumbnail = null,
            menu = null,
            playlistItemData = MusicResponsiveListItemRenderer.PlaylistItemData(
                playlistSetVideoId = "set_abc_999",
                videoId = "vid_abc"
            ),
            overlay = null,
            navigationEndpoint = null
        )

        val song = PlaylistPage.fromMusicResponsiveListItemRenderer(renderer)
        assertNotNull(song)
        assertEquals("vid_abc", song?.id)
        assertEquals("set_abc_999", song?.setVideoId)
    }

    @Test
    fun playlistBoundaryDeduplication_filtersOnlyMatchingSetVideoId() {
        val initialSongs = listOf(
            SongItem(id = "v1", title = "T1", artists = emptyList(), thumbnail = "", setVideoId = "set_1"),
            SongItem(id = "v2", title = "T2", artists = emptyList(), thumbnail = "", setVideoId = "set_2"),
        )

        val continuationSongs = listOf(
            // Overlapping duplicate from previous page boundary
            SongItem(id = "v2", title = "T2", artists = emptyList(), thumbnail = "", setVideoId = "set_2"),
            // Same song ID added intentionally at a different track position with a different setVideoId
            SongItem(id = "v1", title = "T1 Reprise", artists = emptyList(), thumbnail = "", setVideoId = "set_3"),
            // New distinct song
            SongItem(id = "v3", title = "T3", artists = emptyList(), thumbnail = "", setVideoId = "set_4"),
        )

        val seenSetVideoIds = mutableSetOf<String>()
        initialSongs.forEach { it.setVideoId?.let(seenSetVideoIds::add) }

        val filteredNewSongs = continuationSongs.filter { song ->
            val setVid = song.setVideoId
            if (setVid != null) {
                seenSetVideoIds.add(setVid)
            } else {
                true
            }
        }

        val allSongs = initialSongs + filteredNewSongs

        // Expected: set_2 duplicate is filtered out, but set_3 (intentional repeat) and set_4 are preserved
        assertEquals(4, allSongs.size)
        assertEquals(listOf("set_1", "set_2", "set_3", "set_4"), allSongs.map { it.setVideoId })
    }
}
