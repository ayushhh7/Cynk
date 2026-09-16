package com.nikhil.yt.utils

import com.nikhil.yt.innertube.models.Album
import com.nikhil.yt.innertube.models.Artist
import com.nikhil.yt.innertube.models.SongItem
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
}
