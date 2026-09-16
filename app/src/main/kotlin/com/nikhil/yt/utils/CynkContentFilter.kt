/*
 * Cynk - Music App
 * Centralized Content Relevance, Language Classification, and Ranking Filter
 */

package com.nikhil.yt.utils

import com.nikhil.yt.db.entities.Song
import com.nikhil.yt.innertube.models.AlbumItem
import com.nikhil.yt.innertube.models.PlaylistItem
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.innertube.models.YTItem
import java.util.Locale
import java.util.regex.Pattern

object CynkContentFilter {

    // --- SCRIPT REGEX PATTERNS ---
    private val TAMIL_REGEX = Pattern.compile("[\\u0B80-\\u0BFF]")
    private val TELUGU_REGEX = Pattern.compile("[\\u0C00-\\u0C7F]")
    private val KANNADA_REGEX = Pattern.compile("[\\u0C80-\\u0CFF]")
    private val MALAYALAM_REGEX = Pattern.compile("[\\u0D00-\\u0D7F]")
    private val GURMUKHI_REGEX = Pattern.compile("[\\u0A00-\\u0A7F]")
    private val BENGALI_REGEX = Pattern.compile("[\\u0980-\\u09FF]")
    private val GUJARATI_REGEX = Pattern.compile("[\\u0A80-\\u0AFF]")
    private val ODIA_REGEX = Pattern.compile("[\\u0B00-\\u0B7F]")
    private val DEVANAGARI_REGEX = Pattern.compile("[\\u0900-\\u097F]")

    // --- DEVOTIONAL & RELIGIOUS KEYWORDS ---
    private val DEVOTIONAL_TERMS = listOf(
        "bhajan", "bhajans", "aarti", "arti", "chalisa", "mantra", "stotram", "stuti",
        "kirtan", "keertan", "bhakti", "devotional", "shlok", "shloka", "prarthana",
        "dhun", "satsang", "amritwani", "namavali", "katha", "hanuman", "shiva",
        "shiv tandav", "shiv shankar", "bholenath", "mahadiv", "mahadev", "krishna",
        "radha", "radhe", "ganesh", "ganpati", "durga", "mata", "sai baba", "satyanarayan",
        "iskcon", "sadhguru", "ram stuti", "ram bhajan", "har har mahadev", "jai shri ram",
        "radhe radhe", "shri krishna", "t-series bhakti", "shemaroo bhakti", "bhakti sagar",
        "gayatri mantra", "maha mrityunjaya", "spiritual", "navratri", "bhajan sandhya",
        "govinda", "vrindavan", "anup jalota", "anuradha paudwal", "lakhbir singh", "jaya kishori"
    )

    // --- BHOJPURI TERMS & ARTISTS ---
    private val BHOJPURI_TERMS = listOf(
        "bhojpuri", "bhojpuriya", "arkesta", "orchestra", "bhatar", "kamariya", "chath puja",
        "chath geet", "dewar bhabhi", "patna se", "bhojpuri song", "bhojpuri gana", "bhojpuri hit",
        "pawan singh", "khesari lal", "khesari", "silpi raj", "shilpi raj", "pramod premi",
        "arvind akela", "kallu", "ritesh pandey", "samar singh", "ankush raja", "neelkamal singh",
        "gunjan singh", "antra singh", "chandan chanchal", "tuntun yadav", "wave music",
        "aadishakti", "srk music", "yashi films", "team films bhojpuri", "speed records bhojpuri"
    )

    // --- REGIONAL SOUTH & OTHER REGIONAL TERMS ---
    private val REGIONAL_TERMS = listOf(
        "tamil", "telugu", "kannada", "malayalam", "kollywood", "tollywood", "sandlewood",
        "mollywood", "tamil song", "telugu song", "kannada song", "malayalam song",
        "tamil lyrical", "telugu lyrical", "aditya music", "lahari music", "sony music south",
        "saregama south", "think music", "mango music", "divo movies", "sun tv", "zee music south",
        "haryanvi", "rajasthani", "odia", "marathi song", "gujarati song", "assamese"
    )

    // --- POPULAR HINDI / BOLLYWOOD ARTISTS & INDICATORS ---
    private val POPULAR_HINDI_ARTISTS = setOf(
        "arijit singh", "shreya ghoshal", "pritam", "sachin-jigar", "sachin jigar",
        "vishal-shekhar", "vishal shekhar", "amit trivedi", "armaan malik", "darshan raval",
        "neha kakkar", "jubin nautiyal", "mohit chauhan", "kk", "sonu nigam", "sunidhi chauhan",
        "shaan", "udit narayan", "alka yagnik", "kumar sanu", "kishore kumar", "lata mangeshkar",
        "mohammed rafi", "r.d. burman", "rd burman", "a.r. rahman", "ar rahman", "badshah",
        "yo yo honey singh", "honey singh", "raftaar", "divine", "seedhe maut", "king",
        "mc stan", "karan aujla", "ap dhillon", "diljit dosanjh", "prateek kuhad", "anuv jain",
        "jasleen royal", "b praak", "jaani", "mithoon", "tanishk bagchi", "sachet-parampara",
        "sachet tandon", "parampara tandon", "stebin ben", "vishal mishra", "anupam roy",
        "shankar-ehsaan-loy", "atif aslam", "monali thakur", "papon", "neeti mohan",
        "shankar mahadevan", "harshdeep kaur", "zaeden", "ritviz"
    )

    // --- COMMON ENGLISH WORDS (for linguistic detection) ---
    private val ENGLISH_STOP_WORDS = setOf(
        "the", "a", "an", "and", "in", "on", "of", "to", "with", "you", "me", "my", "your",
        "we", "us", "love", "feel", "night", "day", "heart", "away", "never", "time", "world",
        "all", "be", "is", "are", "don't", "can't", "won't", "feat", "ft", "remix", "version",
        "acoustic", "live", "edit", "radio", "girl", "boy", "good", "bad", "stay", "back",
        "eyes", "life", "like", "just", "know", "see", "come", "go", "one", "more", "now"
    )

    /**
     * Check if text contains devotional or religious keywords.
     */
    fun isDevotional(title: String, artistName: String, albumName: String? = null): Boolean {
        val fullText = "$title $artistName ${albumName.orEmpty()}".lowercase(Locale.ROOT)
        return DEVOTIONAL_TERMS.any { term ->
            if (term.length <= 4) {
                // Whole word boundary check for short terms like "shiv", "ram", "arti"
                fullText.contains("\\b$term\\b".toRegex())
            } else {
                fullText.contains(term)
            }
        }
    }

    /**
     * Check if text is related to Bhojpuri content.
     */
    fun isBhojpuri(title: String, artistName: String, albumName: String? = null): Boolean {
        val fullText = "$title $artistName ${albumName.orEmpty()}".lowercase(Locale.ROOT)
        return BHOJPURI_TERMS.any { term -> fullText.contains(term) }
    }

    /**
     * Check if text indicates unwanted regional language (Tamil, Telugu, Kannada, Malayalam, etc.)
     * unless explicitly favored by the user's history.
     */
    fun isRegionalUnwanted(
        title: String,
        artistName: String,
        albumName: String? = null,
        userPreferredArtists: Set<String> = emptySet()
    ): Boolean {
        // If the artist is in the user's preferred artists from local history, DO NOT filter!
        val normalizedArtist = artistName.lowercase(Locale.ROOT).trim()
        if (userPreferredArtists.any { it.isNotBlank() && normalizedArtist.contains(it.lowercase(Locale.ROOT)) }) {
            return false
        }

        val fullText = "$title $artistName ${albumName.orEmpty()}".lowercase(Locale.ROOT)

        // Script checks: Non-Latin South Indian and other regional scripts
        if (TAMIL_REGEX.matcher(fullText).find() ||
            TELUGU_REGEX.matcher(fullText).find() ||
            KANNADA_REGEX.matcher(fullText).find() ||
            MALAYALAM_REGEX.matcher(fullText).find() ||
            ODIA_REGEX.matcher(fullText).find() ||
            GUJARATI_REGEX.matcher(fullText).find() ||
            BENGALI_REGEX.matcher(fullText).find()
        ) {
            return true
        }

        // Keyword checks for regional music terms
        return REGIONAL_TERMS.any { term -> fullText.contains(term) }
    }

    /**
     * Centralized allowed filter:
     * Disallows Devotional, Bhojpuri, and Unwanted Regional content.
     */
    fun isAllowed(
        title: String,
        artistName: String,
        albumName: String? = null,
        userPreferredArtists: Set<String> = emptySet()
    ): Boolean {
        if (isDevotional(title, artistName, albumName)) return false
        if (isBhojpuri(title, artistName, albumName)) return false
        if (isRegionalUnwanted(title, artistName, albumName, userPreferredArtists)) return false
        return true
    }

    /**
     * Check if a song/playlist is classified as English.
     */
    fun isEnglish(title: String, artistName: String): Boolean {
        val full = "$title $artistName".lowercase(Locale.ROOT)

        // Cannot be English if it contains non-Latin South Indian/Devanagari scripts
        if (DEVANAGARI_REGEX.matcher(full).find() ||
            TAMIL_REGEX.matcher(full).find() ||
            TELUGU_REGEX.matcher(full).find() ||
            GURMUKHI_REGEX.matcher(full).find()
        ) {
            return false
        }

        // If artist is known Hindi/Bollywood, classify as Hindi, not English
        if (isPopularHindiArtist(artistName)) return false

        // Check for common English word tokens
        val tokens = full.split(Regex("[\\s,;:.!?()\\[\\]\\-_/]+")).map { it.trim() }
        val hasEnglishWords = tokens.any { it in ENGLISH_STOP_WORDS }
        val isPureAscii = full.all { it.code in 0..127 }

        return hasEnglishWords || isPureAscii
    }

    /**
     * Check if an artist name belongs to popular Hindi/Bollywood creators.
     */
    fun isPopularHindiArtist(artistName: String): Boolean {
        val norm = artistName.lowercase(Locale.ROOT).trim()
        return POPULAR_HINDI_ARTISTS.any { hindiArtist -> norm.contains(hindiArtist) }
    }

    /**
     * Check if a song/playlist is classified as Hindi (popular/Bollywood).
     */
    fun isHindi(title: String, artistName: String, albumName: String? = null): Boolean {
        if (isPopularHindiArtist(artistName)) return true

        val full = "$title $artistName ${albumName.orEmpty()}".lowercase(Locale.ROOT)
        // If it has Devanagari script and is not devotional or Bhojpuri, it's Hindi!
        if (DEVANAGARI_REGEX.matcher(full).find()) return true

        // Bollywood label checks
        if (full.contains("t-series") || full.contains("zee music") || full.contains("yrf") ||
            full.contains("sony music india") || full.contains("tips official")
        ) {
            return true
        }

        return false
    }

    /**
     * Score a song item for ranking:
     * Higher score = higher priority in the feed.
     */
    fun scoreSong(
        song: SongItem,
        userPreferredArtists: Set<String> = emptySet()
    ): Float {
        val artistNames = song.artists.joinToString(" ") { it.name }
        val title = song.title
        val album = song.album?.name

        if (!isAllowed(title, artistNames, album, userPreferredArtists)) {
            return -1000f
        }

        var score = 0f

        // User preference bonus from listening history
        val normArtist = artistNames.lowercase(Locale.ROOT)
        if (userPreferredArtists.any { it.isNotBlank() && normArtist.contains(it.lowercase(Locale.ROOT)) }) {
            score += 200f
        }

        // Language prioritization
        when {
            isEnglish(title, artistNames) -> score += 100f
            isHindi(title, artistNames, album) -> score += 60f
            else -> score += 10f
        }

        // Popularity indicator from chart position if available
        song.chartPosition?.let { pos ->
            score += (100 - pos).coerceAtLeast(0) * 0.5f
        }

        return score
    }

    /**
     * Filter, rank, and interleave a list of candidate SongItems to produce
     * an English-majority (~70%) + popular Hindi (~30%) recommendation set.
     */
    fun filterAndRankSongs(
        songs: List<SongItem>,
        userPreferredArtists: Set<String> = emptySet(),
        targetEnglishRatio: Float = 0.70f,
        limit: Int = 24
    ): List<SongItem> {
        val allowedSongs = songs.filter { song ->
            val artists = song.artists.joinToString(" ") { it.name }
            isAllowed(song.title, artists, song.album?.name, userPreferredArtists)
        }.distinctBy { it.id }

        val englishSongs = mutableListOf<SongItem>()
        val hindiSongs = mutableListOf<SongItem>()
        val otherSongs = mutableListOf<SongItem>()

        for (song in allowedSongs) {
            val artists = song.artists.joinToString(" ") { it.name }
            when {
                isEnglish(song.title, artists) -> englishSongs.add(song)
                isHindi(song.title, artists, song.album?.name) -> hindiSongs.add(song)
                else -> otherSongs.add(song)
            }
        }

        // Sort each category by relevance score
        val sortedEnglish = englishSongs.sortedByDescending { scoreSong(it, userPreferredArtists) }
        val sortedHindi = (hindiSongs + otherSongs).sortedByDescending { scoreSong(it, userPreferredArtists) }

        // Interleave to maintain the ~70% English, ~30% Hindi ratio
        val result = mutableListOf<SongItem>()
        var eIndex = 0
        var hIndex = 0

        while (result.size < limit && (eIndex < sortedEnglish.size || hIndex < sortedHindi.size)) {
            // Pattern: 2 English, 1 Hindi (approx 67% English, 33% Hindi)
            if (eIndex < sortedEnglish.size) result.add(sortedEnglish[eIndex++])
            if (result.size < limit && eIndex < sortedEnglish.size) result.add(sortedEnglish[eIndex++])
            if (result.size < limit && hIndex < sortedHindi.size) result.add(sortedHindi[hIndex++])

            // If one list exhausted, fill with the remaining
            if (eIndex >= sortedEnglish.size && hIndex < sortedHindi.size && result.size < limit) {
                result.add(sortedHindi[hIndex++])
            } else if (hIndex >= sortedHindi.size && eIndex < sortedEnglish.size && result.size < limit) {
                result.add(sortedEnglish[eIndex++])
            }
        }

        return result
    }

    /**
     * Filter local database Song entities.
     */
    fun filterAndRankLocalSongs(
        songs: List<Song>,
        userPreferredArtists: Set<String> = emptySet(),
        limit: Int = 24
    ): List<Song> {
        return songs.filter { song ->
            val artists = song.artists.joinToString(" ") { it.name }
            isAllowed(song.song.title, artists, song.song.albumName, userPreferredArtists)
        }.distinctBy { it.id }.take(limit)
    }

    /**
     * Filter playlist items for relevance (PlaylistItem).
     * Eliminates devotional, Bhojpuri, and unwanted regional playlists.
     */
    fun filterPlaylists(
        playlists: List<PlaylistItem>,
        userPreferredArtists: Set<String> = emptySet(),
        limit: Int = 20
    ): List<PlaylistItem> {
        val filtered = playlists.filter { playlist ->
            val authorName = playlist.author?.name.orEmpty()
            isAllowed(playlist.title, authorName, null, userPreferredArtists)
        }.distinctBy { it.id }

        // Partition into English and Hindi
        val english = filtered.filter { isEnglish(it.title, it.author?.name.orEmpty()) }
        val hindi = filtered.filter { !isEnglish(it.title, it.author?.name.orEmpty()) }

        // Interleave English priority + Hindi
        val result = mutableListOf<PlaylistItem>()
        var eIdx = 0
        var hIdx = 0

        while (result.size < limit && (eIdx < english.size || hIdx < hindi.size)) {
            if (eIdx < english.size) result.add(english[eIdx++])
            if (result.size < limit && eIdx < english.size) result.add(english[eIdx++])
            if (result.size < limit && hIdx < hindi.size) result.add(hindi[hIdx++])

            if (eIdx >= english.size && hIdx < hindi.size && result.size < limit) {
                result.add(hindi[hIdx++])
            } else if (hIdx >= hindi.size && eIdx < english.size && result.size < limit) {
                result.add(english[eIdx++])
            }
        }

        return result
    }

    /**
     * Filter album items (AlbumItem).
     */
    fun filterAlbums(
        albums: List<AlbumItem>,
        userPreferredArtists: Set<String> = emptySet(),
        limit: Int = 20
    ): List<AlbumItem> {
        return albums.filter { album ->
            val artists = album.artists.orEmpty().joinToString(" ") { it.name }
            isAllowed(album.title, artists, null, userPreferredArtists)
        }.distinctBy { it.id }.take(limit)
    }
}
