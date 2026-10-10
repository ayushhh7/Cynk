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
        "vishal-shekhar", "vishal shekhar", "amit trivedi", "armaan malik", "amaal mallik",
        "darshan raval", "neha kakkar", "jubin nautiyal", "mohit chauhan", "kk", "sonu nigam",
        "sunidhi chauhan", "shaan", "udit narayan", "alka yagnik", "kumar sanu", "kishore kumar",
        "lata mangeshkar", "mohammed rafi", "r.d. burman", "rd burman", "a.r. rahman", "ar rahman",
        "badshah", "yo yo honey singh", "honey singh", "raftaar", "divine", "seedhe maut", "king",
        "mc stan", "karan aujla", "ap dhillon", "diljit dosanjh", "prateek kuhad", "anuv jain",
        "jasleen royal", "b praak", "jaani", "mithoon", "tanishk bagchi", "sachet-parampara",
        "sachet tandon", "parampara tandon", "stebin ben", "vishal mishra", "anupam roy",
        "shankar-ehsaan-loy", "atif aslam", "monali thakur", "papon", "neeti mohan",
        "shankar mahadevan", "harshdeep kaur", "zaeden", "ritviz", "guru randhawa",
        "dhvani bhanushali", "tulsi kumar", "jass manak", "himesh reshammiya", "mika singh",
        "daler mehndi", "sukhwinder singh", "shilpa rao", "jonita gandhi", "palak muchhal",
        "kanika kapoor", "shirley setia", "sanam", "twin strings", "ali zafar", "rahat fateh ali khan",
        "nusrat fateh ali khan", "kailash kher", "adnan sami", "javed ali", "lucky ali",
        "tony kakkar", "akhil", "hardy sandhu", "parmish verma", "sidhu moose wala"
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

        // If classified as Hindi (Bollywood / Hindi keywords / Hindi artist), it is NOT English
        if (isHindi(title, artistName)) return false

        // Normalize curly quotes, apostrophes, accents, and dashes
        val normalized = full
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('´', '\'')
            .replace('`', '\'')
            .replace('—', '-')
            .replace('–', '-')

        // Check for common English word tokens
        val tokens = normalized.split(Regex("[\\s,;:.!?()\\[\\]\\-_/'\"]+")).map { it.trim() }
        val hasEnglishWords = tokens.any { it in ENGLISH_STOP_WORDS }
        
        // Pure ASCII or Latin scripts (with diacritics like é, ö in 0..255 or Latin-1 Supplement / Extended)
        val isLatinOrAscii = normalized.all {
            val c = it.code
            c in 0..127 || c in 0x00C0..0x024F
        }

        return hasEnglishWords || isLatinOrAscii
    }

    /**
     * Check if an artist name belongs to popular Hindi/Bollywood creators.
     */
    fun isPopularHindiArtist(artistName: String): Boolean {
        val norm = artistName.lowercase(Locale.ROOT).trim()
        return POPULAR_HINDI_ARTISTS.any { hindiArtist -> norm.contains(hindiArtist) }
    }

    /**
     * Check if user's local listening history has preferred Hindi artists.
     */
    fun hasUserHindiPreference(userPreferredArtists: Set<String>): Boolean {
        if (userPreferredArtists.isEmpty()) return false
        return userPreferredArtists.any { isPopularHindiArtist(it) }
    }

    /**
     * Check if a song/playlist is classified as Hindi (popular/Bollywood).
     */
    fun isHindi(title: String, artistName: String, albumName: String? = null): Boolean {
        if (isPopularHindiArtist(artistName)) return true

        val full = "$title $artistName ${albumName.orEmpty()}".lowercase(Locale.ROOT)
        // If it has Devanagari script and is not devotional or Bhojpuri, it's Hindi!
        if (DEVANAGARI_REGEX.matcher(full).find()) return true

        // Bollywood label and creator indicators
        if (full.contains("t-series") || full.contains("zee music") || full.contains("yrf") ||
            full.contains("sony music india") || full.contains("tips official") ||
            full.contains("bollywood") || full.contains("hindi") ||
            full.contains("desi music factory") || full.contains("play dmf") ||
            full.contains("vyrl originals")
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
     * an English-majority (~75%, target 70-80%) + popular Hindi (~25%, target 20-30%) recommendation set.
     * Prevents Hindi from filling beyond its quota when English candidates are scarce,
     * unless the user has explicit Hindi listening history.
     */
    fun filterAndRankSongs(
        songs: List<SongItem>,
        userPreferredArtists: Set<String> = emptySet(),
        targetEnglishRatio: Float = 0.75f,
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
        val sortedEnglish = englishSongs.sortedByDescending { scoreSong(it, userPreferredArtists) }.toMutableList()
        val sortedHindi = (hindiSongs + otherSongs).sortedByDescending { scoreSong(it, userPreferredArtists) }.toMutableList()

        // Determine Hindi quota: capped at ~25-30% for default users; relaxed if user prefers Hindi
        val userHasHindiPref = hasUserHindiPreference(userPreferredArtists)
        val maxHindiAllowed = if (userHasHindiPref) limit else ((limit * (1f - targetEnglishRatio)).toInt()).coerceAtLeast(1)

        val result = mutableListOf<SongItem>()
        var eIndex = 0
        var hIndex = 0
        var hindiAdded = 0

        // Interleave with 3 English : 1 Hindi pattern (75% English, 25% Hindi)
        while (result.size < limit && (eIndex < sortedEnglish.size || (hIndex < sortedHindi.size && hindiAdded < maxHindiAllowed))) {
            // Add up to 3 English songs
            for (i in 0 until 3) {
                if (result.size < limit && eIndex < sortedEnglish.size) {
                    result.add(sortedEnglish[eIndex++])
                }
            }
            // Add 1 Hindi song if within quota
            if (result.size < limit && hIndex < sortedHindi.size && hindiAdded < maxHindiAllowed) {
                result.add(sortedHindi[hIndex++])
                hindiAdded++
            }

            // If English exhausted: only add Hindi up to maxHindiAllowed!
            if (eIndex >= sortedEnglish.size && hIndex < sortedHindi.size && hindiAdded < maxHindiAllowed && result.size < limit) {
                result.add(sortedHindi[hIndex++])
                hindiAdded++
            }
            // If Hindi exhausted or capped: fill remaining slots with English
            if ((hIndex >= sortedHindi.size || hindiAdded >= maxHindiAllowed) && eIndex < sortedEnglish.size && result.size < limit) {
                result.add(sortedEnglish[eIndex++])
            }
        }

        return result
    }

    /**
     * Filter local database Song entities.
     * Interleaves 75% English / 25% Hindi with strict quota capping.
     */
    fun filterAndRankLocalSongs(
        songs: List<Song>,
        userPreferredArtists: Set<String> = emptySet(),
        targetEnglishRatio: Float = 0.75f,
        limit: Int = 24
    ): List<Song> {
        val cleanSongs = songs.filter { song ->
            val artists = song.artists.joinToString(" ") { it.name }
            isAllowed(song.song.title, artists, song.song.albumName, userPreferredArtists)
        }.distinctBy { it.id }

        val englishSongs = cleanSongs.filter { song ->
            val artists = song.artists.joinToString(" ") { it.name }
            isEnglish(song.song.title, artists)
        }.toMutableList()

        val hindiSongs = cleanSongs.filter { song ->
            val artists = song.artists.joinToString(" ") { it.name }
            isHindi(song.song.title, artists, song.song.albumName)
        }.toMutableList()

        val otherSongs = cleanSongs.filter { it !in englishSongs && it !in hindiSongs }.toMutableList()

        val sortedEnglish = englishSongs.sortedByDescending { song ->
            scoreLocalSong(song, userPreferredArtists)
        }.toMutableList()

        val sortedHindi = (hindiSongs + otherSongs).sortedByDescending { song ->
            scoreLocalSong(song, userPreferredArtists)
        }.toMutableList()

        val userHasHindiPref = hasUserHindiPreference(userPreferredArtists)
        val maxHindiAllowed = if (userHasHindiPref) limit else ((limit * (1f - targetEnglishRatio)).toInt()).coerceAtLeast(1)

        val result = mutableListOf<Song>()
        var eIndex = 0
        var hIndex = 0
        var hindiAdded = 0

        while (result.size < limit && (eIndex < sortedEnglish.size || (hIndex < sortedHindi.size && hindiAdded < maxHindiAllowed))) {
            for (i in 0 until 3) {
                if (result.size < limit && eIndex < sortedEnglish.size) {
                    result.add(sortedEnglish[eIndex++])
                }
            }
            if (result.size < limit && hIndex < sortedHindi.size && hindiAdded < maxHindiAllowed) {
                result.add(sortedHindi[hIndex++])
                hindiAdded++
            }

            if (eIndex >= sortedEnglish.size && hIndex < sortedHindi.size && hindiAdded < maxHindiAllowed && result.size < limit) {
                result.add(sortedHindi[hIndex++])
                hindiAdded++
            }
            if ((hIndex >= sortedHindi.size || hindiAdded >= maxHindiAllowed) && eIndex < sortedEnglish.size && result.size < limit) {
                result.add(sortedEnglish[eIndex++])
            }
        }

        return result
    }

    private fun scoreLocalSong(song: Song, userPreferredArtists: Set<String>): Float {
        val artists = song.artists.joinToString(" ") { it.name }
        val title = song.song.title
        var score = 0f
        val normArtist = artists.lowercase(Locale.ROOT)
        if (userPreferredArtists.any { it.isNotBlank() && normArtist.contains(it.lowercase(Locale.ROOT)) }) {
            score += 200f
        }
        when {
            isEnglish(title, artists) -> score += 100f
            isHindi(title, artists, song.song.albumName) -> score += 60f
            else -> score += 10f
        }
        return score
    }

    /**
     * Filter playlist items for relevance (PlaylistItem).
     * Eliminates devotional, Bhojpuri, and unwanted regional playlists.
     * Interleaves 75% English / 25% Hindi with strict quota capping.
     */
    fun filterPlaylists(
        playlists: List<PlaylistItem>,
        userPreferredArtists: Set<String> = emptySet(),
        targetEnglishRatio: Float = 0.75f,
        limit: Int = 20
    ): List<PlaylistItem> {
        val filtered = playlists.filter { playlist ->
            val authorName = playlist.author?.name.orEmpty()
            isAllowed(playlist.title, authorName, null, userPreferredArtists)
        }.distinctBy { it.id }

        // Partition into English and Hindi
        val english = filtered.filter { isEnglish(it.title, it.author?.name.orEmpty()) }.toMutableList()
        val hindi = filtered.filter { !isEnglish(it.title, it.author?.name.orEmpty()) }.toMutableList()

        val userHasHindiPref = hasUserHindiPreference(userPreferredArtists)
        val maxHindiAllowed = if (userHasHindiPref) limit else ((limit * (1f - targetEnglishRatio)).toInt()).coerceAtLeast(1)

        val result = mutableListOf<PlaylistItem>()
        var eIdx = 0
        var hIdx = 0
        var hindiAdded = 0

        while (result.size < limit && (eIdx < english.size || (hIdx < hindi.size && hindiAdded < maxHindiAllowed))) {
            for (i in 0 until 3) {
                if (result.size < limit && eIdx < english.size) {
                    result.add(english[eIdx++])
                }
            }
            if (result.size < limit && hIdx < hindi.size && hindiAdded < maxHindiAllowed) {
                result.add(hindi[hIdx++])
                hindiAdded++
            }

            if (eIdx >= english.size && hIdx < hindi.size && hindiAdded < maxHindiAllowed && result.size < limit) {
                result.add(hindi[hIdx++])
                hindiAdded++
            }
            if ((hIdx >= hindi.size || hindiAdded >= maxHindiAllowed) && eIdx < english.size && result.size < limit) {
                result.add(english[eIdx++])
            }
        }

        return result
    }

    /**
     * Filter album items (AlbumItem).
     * Eliminates devotional, Bhojpuri, and unwanted regional albums.
     * Interleaves 75% English / 25% Hindi with strict quota capping.
     */
    fun filterAlbums(
        albums: List<AlbumItem>,
        userPreferredArtists: Set<String> = emptySet(),
        targetEnglishRatio: Float = 0.75f,
        limit: Int = 20
    ): List<AlbumItem> {
        val filtered = albums.filter { album ->
            val artists = album.artists.orEmpty().joinToString(" ") { it.name }
            isAllowed(album.title, artists, null, userPreferredArtists)
        }.distinctBy { it.id }

        val english = filtered.filter { album ->
            val artists = album.artists.orEmpty().joinToString(" ") { it.name }
            isEnglish(album.title, artists)
        }.toMutableList()
        val hindi = filtered.filter { album ->
            val artists = album.artists.orEmpty().joinToString(" ") { it.name }
            !isEnglish(album.title, artists)
        }.toMutableList()

        val userHasHindiPref = hasUserHindiPreference(userPreferredArtists)
        val maxHindiAllowed = if (userHasHindiPref) limit else ((limit * (1f - targetEnglishRatio)).toInt()).coerceAtLeast(1)

        val result = mutableListOf<AlbumItem>()
        var eIdx = 0
        var hIdx = 0
        var hindiAdded = 0

        while (result.size < limit && (eIdx < english.size || (hIdx < hindi.size && hindiAdded < maxHindiAllowed))) {
            for (i in 0 until 3) {
                if (result.size < limit && eIdx < english.size) {
                    result.add(english[eIdx++])
                }
            }
            if (result.size < limit && hIdx < hindi.size && hindiAdded < maxHindiAllowed) {
                result.add(hindi[hIdx++])
                hindiAdded++
            }

            if (eIdx >= english.size && hIdx < hindi.size && hindiAdded < maxHindiAllowed && result.size < limit) {
                result.add(hindi[hIdx++])
                hindiAdded++
            }
            if ((hIdx >= hindi.size || hindiAdded >= maxHindiAllowed) && eIdx < english.size && result.size < limit) {
                result.add(english[eIdx++])
            }
        }

        return result
    }
}
