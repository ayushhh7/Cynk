package com.nikhil.yt.utils

import com.nikhil.yt.innertube.models.Album
import com.nikhil.yt.innertube.models.AlbumItem
import com.nikhil.yt.innertube.models.Artist
import com.nikhil.yt.innertube.models.PlaylistItem
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.db.entities.Song
import com.nikhil.yt.db.entities.SongEntity
import com.nikhil.yt.db.entities.ArtistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CynkContentFilterTest {

    private fun createSong(id: String, title: String, artistName: String, albumName: String? = null): SongItem {
        return SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(name = artistName, id = null)),
            album = albumName?.let { Album(name = it, id = "album_$id") },
            duration = 200,
            thumbnail = "https://example.com/$id.jpg"
        )
    }

    @Test
    fun testDevotionalFiltering() {
        assertTrue(CynkContentFilter.isDevotional("Shiv Tandav Stotram", "Various Artists"))
        assertTrue(CynkContentFilter.isDevotional("Shri Hanuman Chalisa", "Hariharan"))
        assertTrue(CynkContentFilter.isDevotional("Aarti Kunj Bihari Ki", "Anuradha Paudwal"))
        assertTrue(CynkContentFilter.isDevotional("Radhe Radhe Barsane Wali", "Gaurav Krishna"))
        assertTrue(CynkContentFilter.isDevotional("Maha Mrityunjaya Mantra", "Shankar Sahney"))
        assertTrue(CynkContentFilter.isDevotional("Morning Bhajans", "Anup Jalota"))

        assertFalse(CynkContentFilter.isAllowed("Shiv Tandav Stotram", "Hariharan"))
        assertFalse(CynkContentFilter.isAllowed("Radhe Krishna Bhakti", "Various"))
    }

    @Test
    fun testBhojpuriFiltering() {
        assertTrue(CynkContentFilter.isBhojpuri("Lollypop Lagelu", "Pawan Singh"))
        assertTrue(CynkContentFilter.isBhojpuri("Kamariya Dole", "Khesari Lal Yadav"))
        assertTrue(CynkContentFilter.isBhojpuri("Bhojpuri Hit Song 2024", "Singer"))
        assertTrue(CynkContentFilter.isBhojpuri("Arkesta Dance Video", "Shilpi Raj"))

        assertFalse(CynkContentFilter.isAllowed("Patna Se Aai", "Pawan Singh"))
        assertFalse(CynkContentFilter.isAllowed("Superhit Bhojpuriya Gaana", "Arvind Akela Kallu"))
    }

    @Test
    fun testRegionalUnwantedFiltering() {
        // Tamil and Telugu keywords
        assertTrue(CynkContentFilter.isRegionalUnwanted("Arabic Kuthu - Tamil", "Anirudh Ravichander"))
        assertTrue(CynkContentFilter.isRegionalUnwanted("Oo Antava Telugu Lyrical", "Devi Sri Prasad"))
        assertTrue(CynkContentFilter.isRegionalUnwanted("Top Kannada Hits", "Various Artists"))
        assertTrue(CynkContentFilter.isRegionalUnwanted("Malayalam Melody 2024", "Job Kurian"))

        // Tamil script
        assertTrue(CynkContentFilter.isRegionalUnwanted("வணக்கம்", "Artist"))
        // Telugu script
        assertTrue(CynkContentFilter.isRegionalUnwanted("తెలుగు పాట", "Artist"))

        // Disallowed by default
        assertFalse(CynkContentFilter.isAllowed("Tamil Romantic Songs", "Aditya Music"))
    }

    @Test
    fun testUserPreferredArtistOverridesRegionalFilter() {
        val userArtists = setOf("Anirudh Ravichander", "Sid Sriram")

        // Allowed because Anirudh is in user's preferred history
        assertFalse(CynkContentFilter.isRegionalUnwanted("Hukum - Tamil Song", "Anirudh Ravichander", userPreferredArtists = userArtists))
        assertTrue(CynkContentFilter.isAllowed("Hukum - Tamil Song", "Anirudh Ravichander", userPreferredArtists = userArtists))

        // Still disallowed if NOT in user's history
        assertTrue(CynkContentFilter.isRegionalUnwanted("Pushpa Telugu Song", "Devi Sri Prasad", userPreferredArtists = userArtists))
        assertFalse(CynkContentFilter.isAllowed("Pushpa Telugu Song", "Devi Sri Prasad", userPreferredArtists = userArtists))
    }

    @Test
    fun testEnglishAndPopularHindiAllowed() {
        // English songs
        assertTrue(CynkContentFilter.isAllowed("Blinding Lights", "The Weeknd"))
        assertTrue(CynkContentFilter.isAllowed("Shape of You", "Ed Sheeran"))
        assertTrue(CynkContentFilter.isAllowed("Starboy", "The Weeknd feat. Daft Punk"))
        assertTrue(CynkContentFilter.isAllowed("As It Was", "Harry Styles"))
        assertTrue(CynkContentFilter.isEnglish("Blinding Lights", "The Weeknd"))

        // Popular Hindi songs
        assertTrue(CynkContentFilter.isAllowed("Kesariya", "Pritam, Arijit Singh"))
        assertTrue(CynkContentFilter.isAllowed("Tum Hi Ho", "Arijit Singh"))
        assertTrue(CynkContentFilter.isAllowed("Apna Bana Le", "Sachin-Jigar, Arijit Singh"))
        assertTrue(CynkContentFilter.isAllowed("Chaleya", "Anirudh Ravichander, Arijit Singh"))
        assertTrue(CynkContentFilter.isHindi("Kesariya", "Arijit Singh"))
    }

    @Test
    fun testFilterAndRankSongsInterleaving() {
        val candidates = listOf(
            createSong("1", "Blinding Lights", "The Weeknd"),
            createSong("2", "Shiv Tandav Stotram", "Devotional Artist"), // devotional - should be removed
            createSong("3", "Kesariya", "Arijit Singh"),               // hindi
            createSong("4", "Lollypop Lagelu", "Pawan Singh"),         // bhojpuri - should be removed
            createSong("5", "Shape of You", "Ed Sheeran"),             // english
            createSong("6", "Tamil Love Melody", "Unknown Tamil"),     // regional - should be removed
            createSong("7", "Starboy", "The Weeknd"),                  // english
            createSong("8", "Apna Bana Le", "Sachin-Jigar"),           // hindi
            createSong("9", "As It Was", "Harry Styles"),              // english
            createSong("10", "Chaleya", "Arijit Singh"),               // hindi
            createSong("11", "Levitating", "Dua Lipa"),                // english
            createSong("12", "Stay", "The Kid LAROI, Justin Bieber")   // english
        )

        val ranked = CynkContentFilter.filterAndRankSongs(
            songs = candidates,
            userPreferredArtists = emptySet(),
            targetEnglishRatio = 0.70f,
            limit = 10
        )

        // Verify devotional, bhojpuri, and unwanted regional were filtered out
        assertTrue(ranked.none { it.id == "2" })
        assertTrue(ranked.none { it.id == "4" })
        assertTrue(ranked.none { it.id == "6" })

        // Verify remaining items are English and popular Hindi
        assertTrue(ranked.any { it.title == "Blinding Lights" })
        assertTrue(ranked.any { it.title == "Kesariya" })
        assertTrue(ranked.any { it.title == "Shape of You" })

        // English should be the majority
        val englishCount = ranked.count { CynkContentFilter.isEnglish(it.title, it.artists.joinToString { a -> a.name }) }
        val hindiCount = ranked.count { CynkContentFilter.isHindi(it.title, it.artists.joinToString { a -> a.name }) }
        assertTrue("English count ($englishCount) should be greater than or equal to Hindi count ($hindiCount)", englishCount >= hindiCount)
    }

    @Test
    fun testFilterAndRankSongs75_25Ratio() {
        val englishList = (1..20).map { createSong("e$it", "English Song $it", "Taylor Swift") }
        val hindiList = (1..20).map { createSong("h$it", "Hindi Song $it", "Arijit Singh") }

        val ranked = CynkContentFilter.filterAndRankSongs(
            songs = englishList + hindiList,
            userPreferredArtists = emptySet(),
            targetEnglishRatio = 0.75f,
            limit = 24
        )

        assertEquals(24, ranked.size)
        val englishCount = ranked.count { CynkContentFilter.isEnglish(it.title, it.artists.joinToString { a -> a.name }) }
        val hindiCount = ranked.count { CynkContentFilter.isHindi(it.title, it.artists.joinToString { a -> a.name }) }

        assertEquals("Should have exactly 18 English songs (75%)", 18, englishCount)
        assertEquals("Should have exactly 6 Hindi songs (25%)", 6, hindiCount)
    }

    @Test
    fun testAsymmetricPoolHindiCapProtection() {
        // Only 2 English songs, but 50 Hindi songs!
        val englishList = listOf(
            createSong("e1", "Blinding Lights", "The Weeknd"),
            createSong("e2", "Shape of You", "Ed Sheeran")
        )
        val hindiList = (1..50).map { createSong("h$it", "Hindi Hit $it", "Arijit Singh") }

        val ranked = CynkContentFilter.filterAndRankSongs(
            songs = englishList + hindiList,
            userPreferredArtists = emptySet(),
            targetEnglishRatio = 0.75f,
            limit = 24
        )

        val hindiCount = ranked.count { CynkContentFilter.isHindi(it.title, it.artists.joinToString { a -> a.name }) }
        val englishCount = ranked.count { CynkContentFilter.isEnglish(it.title, it.artists.joinToString { a -> a.name }) }

        // Max Hindi allowed is (24 * 0.25) = 6! Hindi MUST NOT overwhelm the feed!
        assertTrue("Hindi songs count ($hindiCount) must not exceed quota cap of 6", hindiCount <= 6)
        assertEquals("English songs should all be included", 2, englishCount)
    }

    @Test
    fun testPersonalizedHindiHistoryAllowsMoreHindi() {
        // When user has explicit Hindi listening history, the quota cap is relaxed to respect their taste
        val userArtists = setOf("Arijit Singh", "Pritam")
        val englishList = listOf(
            createSong("e1", "Blinding Lights", "The Weeknd"),
            createSong("e2", "Shape of You", "Ed Sheeran")
        )
        val hindiList = (1..50).map { createSong("h$it", "Hindi Hit $it", "Arijit Singh") }

        val ranked = CynkContentFilter.filterAndRankSongs(
            songs = englishList + hindiList,
            userPreferredArtists = userArtists,
            targetEnglishRatio = 0.75f,
            limit = 24
        )

        val hindiCount = ranked.count { CynkContentFilter.isHindi(it.title, it.artists.joinToString { a -> a.name }) }
        assertTrue("Personalized Hindi history allows more Hindi songs (found $hindiCount)", hindiCount > 6)
    }

    @Test
    fun testFilterPlaylistsRatio() {
        val englishPlaylists = (1..15).map { PlaylistItem(id = "ep$it", title = "Top Hits $it", author = Artist(name = "YouTube Music", id = null), songCountText = "50", thumbnail = null, playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null) }
        val hindiPlaylists = (1..15).map { PlaylistItem(id = "hp$it", title = "Bollywood Romance $it", author = Artist(name = "T-Series", id = null), songCountText = "50", thumbnail = null, playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null) }

        val result = CynkContentFilter.filterPlaylists(
            playlists = englishPlaylists + hindiPlaylists,
            userPreferredArtists = emptySet(),
            targetEnglishRatio = 0.75f,
            limit = 20
        )

        val englishCount = result.count { CynkContentFilter.isEnglish(it.title, it.author?.name.orEmpty()) }
        val hindiCount = result.count { !CynkContentFilter.isEnglish(it.title, it.author?.name.orEmpty()) }

        assertEquals("Should have 15 English playlists (75%)", 15, englishCount)
        assertEquals("Should have 5 Hindi playlists (25%)", 5, hindiCount)
    }

    @Test
    fun testFilterAlbumsRatio() {
        val englishAlbums = (1..15).map { AlbumItem(browseId = "ea$it", playlistId = "eap$it", title = "Global Album $it", artists = listOf(Artist(name = "Taylor Swift", id = null)), year = 2024, thumbnail = "https://example.com/a.jpg", explicit = false) }
        val hindiAlbums = (1..15).map { AlbumItem(browseId = "ha$it", playlistId = "hap$it", title = "Bollywood Album $it", artists = listOf(Artist(name = "Arijit Singh", id = null)), year = 2024, thumbnail = "https://example.com/b.jpg", explicit = false) }

        val result = CynkContentFilter.filterAlbums(
            albums = englishAlbums + hindiAlbums,
            userPreferredArtists = emptySet(),
            targetEnglishRatio = 0.75f,
            limit = 20
        )

        val englishCount = result.count { CynkContentFilter.isEnglish(it.title, it.artists.orEmpty().joinToString { a -> a.name }) }
        val hindiCount = result.count { !CynkContentFilter.isEnglish(it.title, it.artists.orEmpty().joinToString { a -> a.name }) }

        assertEquals("Should have 15 English albums (75%)", 15, englishCount)
        assertEquals("Should have 5 Hindi albums (25%)", 5, hindiCount)
    }

    @Test
    fun testYouTubeSearchDynamicEnglish() = kotlinx.coroutines.runBlocking {
        val searchResult = com.nikhil.yt.innertube.YouTube.search(
            "Top Global Hits",
            com.nikhil.yt.innertube.YouTube.SearchFilter.FILTER_SONG
        ).getOrNull()

        val songs = searchResult?.items?.filterIsInstance<SongItem>().orEmpty()
        assertTrue("Should fetch dynamic songs from YouTube search", songs.isNotEmpty())
        val englishSongs = songs.count { CynkContentFilter.isEnglish(it.title, it.artists.joinToString { a -> a.name }) }
        assertTrue("Majority of songs should be English ($englishSongs / ${songs.size})", englishSongs >= songs.size / 2)
    }

    private fun createLocalSong(id: String, title: String, artistName: String): Song {
        return Song(
            song = SongEntity(
                id = id,
                title = title,
                duration = 180,
            ),
            artists = listOf(ArtistEntity(id = "a_$id", name = artistName)),
            album = null
        )
    }

    @Test
    fun testUnicodePunctuationAndDiacriticsEnglishRecognition() {
        assertTrue("Don’t Start Now should be English", CynkContentFilter.isEnglish("Don’t Start Now", "Dua Lipa"))
        assertTrue("I’m Good should be English", CynkContentFilter.isEnglish("I’m Good (Blue)", "David Guetta & Bebe Rexha"))
        assertTrue("Beyoncé with accent should be English", CynkContentFilter.isEnglish("CUFF IT", "Beyoncé"))
        assertTrue("Måneskin should be English", CynkContentFilter.isEnglish("Beggin'", "Måneskin"))
    }

    @Test
    fun testFilterAndRankLocalSongsRatio() {
        val englishList = (1..20).map { createLocalSong("le$it", "English Local $it", "Ed Sheeran") }
        val hindiList = (1..20).map { createLocalSong("lh$it", "Hindi Local $it", "Arijit Singh") }

        val ranked = CynkContentFilter.filterAndRankLocalSongs(
            songs = englishList + hindiList,
            userPreferredArtists = emptySet(),
            targetEnglishRatio = 0.75f,
            limit = 20
        )

        assertEquals(20, ranked.size)
        val englishCount = ranked.count { CynkContentFilter.isEnglish(it.song.title, it.artists.joinToString { a -> a.name }) }
        val hindiCount = ranked.count { CynkContentFilter.isHindi(it.song.title, it.artists.joinToString { a -> a.name }) }

        assertEquals("Should have 15 English songs (75%)", 15, englishCount)
        assertEquals("Should have 5 Hindi songs (25%)", 5, hindiCount)
    }

    @Test
    fun testFilterAndRankLocalSongsHindiCapProtection() {
        // Asymmetric pool in local DB (e.g. 2 English songs, 30 Hindi songs)
        val englishList = listOf(
            createLocalSong("le1", "Blinding Lights", "The Weeknd"),
            createLocalSong("le2", "Shape of You", "Ed Sheeran")
        )
        val hindiList = (1..30).map { createLocalSong("lh$it", "Hindi Local $it", "Arijit Singh") }

        val ranked = CynkContentFilter.filterAndRankLocalSongs(
            songs = englishList + hindiList,
            userPreferredArtists = emptySet(),
            targetEnglishRatio = 0.75f,
            limit = 20
        )

        val hindiCount = ranked.count { CynkContentFilter.isHindi(it.song.title, it.artists.joinToString { a -> a.name }) }
        val englishCount = ranked.count { CynkContentFilter.isEnglish(it.song.title, it.artists.joinToString { a -> a.name }) }

        assertTrue("Hindi songs count ($hindiCount) must not exceed quota cap of 5", hindiCount <= 5)
        assertEquals("English songs should all be included", 2, englishCount)
    }

    @Test
    fun testCrossSectionDeduplicationRatio() {
        // Quick picks consumed 18 English songs and 6 Hindi songs
        val qpSongs = (1..18).map { createSong("qp_e$it", "QP English $it", "Taylor Swift") } +
                (1..6).map { createSong("qp_h$it", "QP Hindi $it", "Arijit Singh") }
        val quickPickIds = qpSongs.map { it.id }.toSet()

        // Trending has distinct pool of 20 English and 20 Hindi songs (different IDs)
        val trendingSongs = (1..20).map { createSong("tr_e$it", "Trending English $it", "Billie Eilish") } +
                (1..20).map { createSong("tr_h$it", "Trending Hindi $it", "Neha Kakkar") }

        val deduped = trendingSongs.filterNot { it.id in quickPickIds }
        val finalTrending = CynkContentFilter.filterAndRankSongs(
            songs = deduped,
            userPreferredArtists = emptySet(),
            targetEnglishRatio = 0.75f,
            limit = 24
        )

        val englishCount = finalTrending.count { CynkContentFilter.isEnglish(it.title, it.artists.joinToString { a -> a.name }) }
        val hindiCount = finalTrending.count { CynkContentFilter.isHindi(it.title, it.artists.joinToString { a -> a.name }) }

        assertEquals("Should maintain 18 English songs in final Trending (75%)", 18, englishCount)
        assertEquals("Should maintain 6 Hindi songs in final Trending (25%)", 6, hindiCount)
    }
}
