/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.models.AlbumItem
import com.nikhil.yt.innertube.models.PlaylistItem
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.innertube.models.WatchEndpoint
import com.nikhil.yt.innertube.models.YTItem
import com.nikhil.yt.innertube.models.filterExplicit
import com.nikhil.yt.innertube.models.filterVideo
import com.nikhil.yt.innertube.pages.ChartsPage
import com.nikhil.yt.innertube.pages.ExplorePage
import com.nikhil.yt.innertube.pages.HomePage
import com.nikhil.yt.innertube.utils.completed
import com.nikhil.yt.innertube.utils.parseCookieString
import com.nikhil.yt.constants.HideExplicitKey
import com.nikhil.yt.constants.HideVideoKey
import com.nikhil.yt.constants.InnerTubeCookieKey
import com.nikhil.yt.constants.QuickPicks
import com.nikhil.yt.constants.QuickPicksKey
import com.nikhil.yt.constants.YtmSyncKey
import com.nikhil.yt.db.MusicDatabase
import com.nikhil.yt.db.entities.*
import com.nikhil.yt.extensions.toEnum
import com.nikhil.yt.models.SimilarRecommendation
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.get
import com.nikhil.yt.utils.getAsync
import com.nikhil.yt.utils.SyncUtils
import com.nikhil.yt.utils.CynkContentFilter
import com.nikhil.yt.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val forYouEngine: com.nikhil.yt.utils.ForYouSuggestionEngine,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    private val isInitialLoadComplete = MutableStateFlow(false)
    val forYouSuggestions = MutableStateFlow<List<com.nikhil.yt.innertube.models.SongItem>?>(null)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val trendingSongs = MutableStateFlow<List<SongItem>?>(cachedTrendingSongs)
    val reelsSongs = MutableStateFlow<List<SongItem>?>(cachedReelsSongs)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(cachedHomePage)
    val explorePage = MutableStateFlow<ExplorePage?>(cachedExplorePage)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val recentActivity = MutableStateFlow<List<YTItem>?>(null)
    val recentPlaylistsDb = MutableStateFlow<List<Playlist>?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    // Account display info
    val accountName = MutableStateFlow<String?>(null)
    val accountImageUrl = MutableStateFlow<String?>(null)
    
    // Track last processed cookie to avoid unnecessary updates
    private var lastProcessedCookie: String? = null
    
    // Track if we're currently processing account data
    private var isProcessingAccountData = false
    private var wasLoggedIn = false

    private fun filterHomeChips(chips: List<HomePage.Chip>?): List<HomePage.Chip>? {
        return chips?.filterNot { it.title.contains("podcasts", ignoreCase = true) }
    }

    private suspend fun getUserPreferredArtists(): Set<String> {
        return runCatching {
            database.allArtistsByPlayTime().first().take(20).map { it.artist.name }.toSet()
        }.getOrDefault(emptySet())
    }

    private suspend fun getQuickPicks(){
        val qpType = context.dataStore.getAsync(QuickPicksKey, QuickPicks.QUICK_PICKS.name).toEnum(QuickPicks.QUICK_PICKS)
        val userArtists = getUserPreferredArtists()
        when (qpType) {
            QuickPicks.QUICK_PICKS -> {
                val raw = database.quickPicks().first()
                quickPicks.value = CynkContentFilter.filterAndRankLocalSongs(raw, userArtists).shuffled().take(20)
            }
            QuickPicks.LAST_LISTEN -> songLoad()
        }
    }

    private suspend fun load() {
        if (isLoading.value) return
        isLoading.value = true
        
        try {
            val hideExplicit = context.dataStore.getAsync(HideExplicitKey, false)
            val hideVideo = context.dataStore.getAsync(HideVideoKey, false)
            val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

            // Stage 0: Instant Disk Cache Emission (cold-start acceleration)
            if (homePage.value == null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val userArtists = getUserPreferredArtists()
                    val cacheFile = java.io.File(context.cacheDir, "cynk_home_cache.json")
                    if (cacheFile.exists()) {
                        val raw = runCatching { cacheFile.readText() }.getOrNull()
                        if (!raw.isNullOrBlank() && homePage.value == null) {
                            YouTube.parseHomeFromRawJson(raw).onSuccess { cachedPage ->
                                if (homePage.value == null) {
                                    val filteredSections = cachedPage.sections.mapNotNull { section ->
                                        val cleanItems = section.items
                                            .filterExplicit(hideExplicit)
                                            .filterVideo(hideVideo)
                                            .filter { item ->
                                                when (item) {
                                                    is SongItem -> CynkContentFilter.isAllowed(item.title, item.artists.joinToString { it.name }, item.album?.name, userArtists)
                                                    is PlaylistItem -> CynkContentFilter.isAllowed(item.title, item.author?.name.orEmpty(), null, userArtists)
                                                    is AlbumItem -> CynkContentFilter.isAllowed(item.title, item.artists.orEmpty().joinToString { it.name }, null, userArtists)
                                                    else -> true
                                                }
                                            }
                                        if (cleanItems.isNotEmpty()) section.copy(items = cleanItems) else null
                                    }
                                    val readyPage = cachedPage.copy(
                                        chips = filterHomeChips(cachedPage.chips),
                                        sections = filteredSections
                                    )
                                    homePage.value = readyPage
                                    cachedHomePage = readyPage
                                    allYtItems.value = filteredSections.flatMap { it.items }
                                    Timber.d("CYNK_DIAG: Loaded homePage from disk cache in ~10ms")
                                }
                            }
                        }
                    }
                }
            }

            // Stage 0b: Instant Disk Cache for Trending
            if (trendingSongs.value == null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val userArtists = getUserPreferredArtists()
                    val trendingCache = java.io.File(context.cacheDir, "cynk_trending_cache.json")
                    if (trendingCache.exists()) {
                        val raw = runCatching { trendingCache.readText() }.getOrNull()
                        if (!raw.isNullOrBlank() && trendingSongs.value == null) {
                            YouTube.parseChartsFromRawJson(raw).onSuccess { charts ->
                                if (trendingSongs.value == null) {
                                    val songs = charts.sections.flatMap { it.items }
                                        .filterIsInstance<SongItem>()
                                        .filterExplicit(hideExplicit)
                                        .filterVideo(hideVideo)
                                    val ranked = CynkContentFilter.filterAndRankSongs(
                                        songs = songs,
                                        userPreferredArtists = userArtists,
                                        targetEnglishRatio = 0.70f,
                                        limit = 24
                                    )
                                    if (ranked.isNotEmpty()) {
                                        trendingSongs.value = ranked
                                        cachedTrendingSongs = ranked
                                        Timber.d("CYNK_DIAG: Loaded trendingSongs from disk cache in ~10ms")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Stage 1: Fast local Quick Picks (Local DB)
            viewModelScope.launch(Dispatchers.IO) {
                getQuickPicks()
                allLocalItems.value = (quickPicks.value.orEmpty() + keepListening.value.orEmpty())
                    .filter { it is Song || it is Album }
            }

            // Stage 1 & 2: Primary Home Feed (YouTube.home) -> emits immediately when ready!
            viewModelScope.launch(Dispatchers.IO) {
                YouTube.home().onSuccess { page ->
                    Timber.d("CYNK_DIAG: home() SUCCESS - ${page.sections.size} sections")
                    val userArtists = getUserPreferredArtists()
                    val filteredSections = page.sections.mapNotNull { section ->
                        val cleanItems = section.items
                            .filterExplicit(hideExplicit)
                            .filterVideo(hideVideo)
                            .filter { item ->
                                when (item) {
                                    is SongItem -> CynkContentFilter.isAllowed(item.title, item.artists.joinToString { it.name }, item.album?.name, userArtists)
                                    is PlaylistItem -> CynkContentFilter.isAllowed(item.title, item.author?.name.orEmpty(), null, userArtists)
                                    is AlbumItem -> CynkContentFilter.isAllowed(item.title, item.artists.orEmpty().joinToString { it.name }, null, userArtists)
                                    else -> true
                                }
                            }
                        if (cleanItems.isNotEmpty()) section.copy(items = cleanItems) else null
                    }
                    val readyPage = page.copy(
                        chips = filterHomeChips(page.chips),
                        sections = filteredSections
                    )
                    homePage.value = readyPage
                    cachedHomePage = readyPage
                    allYtItems.value = filteredSections.flatMap { it.items }
                    Timber.d("CYNK_DIAG: homePage.value set with ${filteredSections.size} sections")

                    // Persist to disk cache asynchronously for instant cold-start on next open
                    val raw = YouTube.lastHomeRawResponse
                    if (!raw.isNullOrBlank()) {
                        runCatching {
                            java.io.File(context.cacheDir, "cynk_home_cache.json").writeText(raw)
                        }
                    }
                }.onFailure {
                    Timber.e(it, "CYNK_DIAG: home() FAILED")
                    reportException(it)
                }
            }

            // Stage 3: Progressive Charts (Trending & Reels) - Concurrent Global US + India charts
            viewModelScope.launch(Dispatchers.IO) {
                val userArtists = getUserPreferredArtists()

                coroutineScope {
                    val globalChartsDeferred = async { YouTube.getChartsPage(countryCode = "US").getOrNull() }
                    val regionalChartsDeferred = async { YouTube.getChartsPage(countryCode = "IN").getOrNull() }

                    val globalCharts = globalChartsDeferred.await()
                    val regionalCharts = regionalChartsDeferred.await()

                    val candidateSongs = mutableListOf<SongItem>()

                    // 1. Collect songs from Global/US charts (high-priority English)
                    globalCharts?.sections?.forEach { sec ->
                        candidateSongs.addAll(sec.items.filterIsInstance<SongItem>())
                    }

                    // 2. Collect songs from Regional/India charts
                    regionalCharts?.sections?.forEach { sec ->
                        candidateSongs.addAll(sec.items.filterIsInstance<SongItem>())
                    }

                    val cleanCandidates = candidateSongs
                        .filterExplicit(hideExplicit)
                        .filterVideo(hideVideo)

                    val rankedTrending = CynkContentFilter.filterAndRankSongs(
                        songs = cleanCandidates,
                        userPreferredArtists = userArtists,
                        targetEnglishRatio = 0.70f,
                        limit = 24
                    )

                    if (rankedTrending.isNotEmpty()) {
                        trendingSongs.value = rankedTrending
                        cachedTrendingSongs = rankedTrending

                        // Save raw charts json to disk cache
                        val chartsRaw = YouTube.lastChartsRawResponse
                        if (!chartsRaw.isNullOrBlank()) {
                            runCatching {
                                java.io.File(context.cacheDir, "cynk_trending_cache.json").writeText(chartsRaw)
                            }
                        }
                    }

                    // Reels / Viral songs from charts
                    val chartsToSearch = listOfNotNull(globalCharts, regionalCharts)
                    val sSection = chartsToSearch.firstNotNullOfOrNull { c ->
                        c.sections.firstOrNull {
                            it.title.contains("Shorts", ignoreCase = true) ||
                                    it.title.contains("Reels", ignoreCase = true) ||
                                    it.title.contains("Viral", ignoreCase = true)
                        }
                    }
                    val rSongs = sSection?.items?.filterIsInstance<SongItem>().orEmpty()
                        .filterExplicit(hideExplicit).filterVideo(hideVideo)
                    val filteredReels = CynkContentFilter.filterAndRankSongs(
                        songs = rSongs,
                        userPreferredArtists = userArtists,
                        targetEnglishRatio = 0.70f,
                        limit = 20
                    )
                    if (filteredReels.isNotEmpty()) {
                        reelsSongs.value = filteredReels
                        cachedReelsSongs = filteredReels
                    }
                }
            }

            // Stage 4: Recents (Local database history - parallelized)
            viewModelScope.launch(Dispatchers.IO) {
                coroutineScope {
                    val songsDeferred = async { database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5).first() }
                    val albumsDeferred = async { database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first() }
                    val artistsDeferred = async { database.mostPlayedArtists(fromTimeStamp).first() }

                    val keepListeningSongs = songsDeferred.await().shuffled().take(10)
                    val keepListeningAlbums = albumsDeferred.await().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
                    val keepListeningArtists = artistsDeferred.await().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
                        .shuffled().take(5)
                    val combined = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()
                    keepListening.value = combined

                    allLocalItems.value = (quickPicks.value.orEmpty() + combined)
                        .filter { it is Song || it is Album }
                }
            }

            // Stage 4: For You suggestions (Deferred background)
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val userArtists = getUserPreferredArtists()
                    val rawSuggestions = forYouEngine.getSuggestions(hideExplicit, hideVideo)
                    forYouSuggestions.value = CynkContentFilter.filterAndRankSongs(
                        songs = rawSuggestions,
                        userPreferredArtists = userArtists,
                        targetEnglishRatio = 0.70f,
                        limit = 50
                    )
                } catch (_: Exception) {}
            }

            // Stage 5: Explore Page (Deferred background)
            viewModelScope.launch(Dispatchers.IO) {
                YouTube.explore().onSuccess { page ->
                    val artists: MutableMap<Int, String> = mutableMapOf()
                    val favouriteArtists: MutableMap<Int, String> = mutableMapOf()
                    database.allArtistsByPlayTime().first().let { list ->
                        var favIndex = 0
                        for ((artistsIndex, artist) in list.withIndex()) {
                            artists[artistsIndex] = artist.id
                            if (artist.artist.bookmarkedAt != null) {
                                favouriteArtists[favIndex] = artist.id
                                favIndex++
                            }
                        }
                    }
                    val readyExplore = page.copy(
                        newReleaseAlbums = page.newReleaseAlbums
                            .sortedBy { album ->
                                val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                val firstArtistKey = artistIds.firstNotNullOfOrNull { artistId ->
                                    if (artistId in favouriteArtists.values) {
                                        favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                                    } else {
                                        artists.entries.firstOrNull { it.value == artistId }?.key
                                    }
                                } ?: Int.MAX_VALUE
                                firstArtistKey
                            }.filterExplicit(hideExplicit)
                    )
                    explorePage.value = readyExplore
                    cachedExplorePage = readyExplore
                }.onFailure { reportException(it) }
            }

            isInitialLoadComplete.value = true
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isLoading.value = false
        }
    }

    private suspend fun loadSimilarRecommendations() {
        val hideExplicit = context.dataStore.getAsync(HideExplicitKey, false)
        val hideVideo = context.dataStore.getAsync(HideVideoKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
        
        val artistRecommendations = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
            .mapNotNull {
                val items = mutableListOf<YTItem>()
                YouTube.artist(it.id).onSuccess { page ->
                    items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                    items += page.sections.lastOrNull()?.items.orEmpty()
                }
                SimilarRecommendation(
                    title = it,
                    items = items.filterExplicit(hideExplicit).filterVideo(hideVideo).shuffled().ifEmpty { return@mapNotNull null }
                )
            }

        val songRecommendations = database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
            .filter { it.album != null }
            .shuffled().take(2)
            .mapNotNull { song ->
                val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                    ?: return@mapNotNull null
                val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                SimilarRecommendation(
                    title = song,
                    items = (page.songs.shuffled().take(8) +
                            page.albums.shuffled().take(4) +
                            page.artists.shuffled().take(4) +
                            page.playlists.shuffled().take(4))
                        .filterExplicit(hideExplicit).filterVideo(hideVideo)
                        .shuffled()
                        .ifEmpty { return@mapNotNull null }
                )
            }

        similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()
        
        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty()
    }

    private suspend fun songLoad() {
        val song = database.events().first().firstOrNull()?.song
        if (song != null) {
            if (database.hasRelatedSongs(song.id)) {
                val relatedSongs = database.getRelatedSongs(song.id).first()
                val userArtists = getUserPreferredArtists()
                val filtered = CynkContentFilter.filterAndRankLocalSongs(relatedSongs, userArtists, limit = 20)
                quickPicks.value = filtered
            }
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val hideExplicit = context.dataStore.getAsync(HideExplicitKey, false)
            val hideVideo = context.dataStore.getAsync(HideVideoKey, false)
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            val userArtists = getUserPreferredArtists()
            val filteredNextSections = nextSections.sections.mapNotNull { section ->
                val cleanItems = section.items
                    .filterExplicit(hideExplicit)
                    .filterVideo(hideVideo)
                    .filter { item ->
                        when (item) {
                            is SongItem -> CynkContentFilter.isAllowed(item.title, item.artists.joinToString { it.name }, item.album?.name, userArtists)
                            is PlaylistItem -> CynkContentFilter.isAllowed(item.title, item.author?.name.orEmpty(), null, userArtists)
                            is AlbumItem -> CynkContentFilter.isAllowed(item.title, item.artists.orEmpty().joinToString { it.name }, null, userArtists)
                            else -> true
                        }
                    }
                if (cleanItems.isNotEmpty()) section.copy(items = cleanItems) else null
            }

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + filteredNextSections)
            )
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.getAsync(HideExplicitKey, false)
            val hideVideo = context.dataStore.getAsync(HideVideoKey, false)
            val nextSections = YouTube.home(params = chip?.endpoint?.params).getOrNull() ?: return@launch
            val userArtists = getUserPreferredArtists()

            val filteredSections = nextSections.sections.mapNotNull { section ->
                val cleanItems = section.items
                    .filterExplicit(hideExplicit)
                    .filterVideo(hideVideo)
                    .filter { item ->
                        when (item) {
                            is SongItem -> CynkContentFilter.isAllowed(item.title, item.artists.joinToString { it.name }, item.album?.name, userArtists)
                            is PlaylistItem -> CynkContentFilter.isAllowed(item.title, item.author?.name.orEmpty(), null, userArtists)
                            is AlbumItem -> CynkContentFilter.isAllowed(item.title, item.artists.orEmpty().joinToString { it.name }, null, userArtists)
                            else -> true
                        }
                    }
                if (cleanItems.isNotEmpty()) section.copy(items = cleanItems) else null
            }

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = filteredSections
            )
            selectedChip.value = chip
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    fun refreshAccountData() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isProcessingAccountData) return@launch
            
            isProcessingAccountData = true
            try {
                val cookie = context.dataStore.get(InnerTubeCookieKey, "")
                if (cookie.isNotEmpty()) {
                    YouTube.cookie = cookie
                    
                    YouTube.accountInfo().onSuccess { info ->
                        accountName.value = info.name
                        accountImageUrl.value = info.thumbnailUrl
                    }.onFailure {
                        timber.log.Timber.w(it, "Failed to fetch account info")
                    }

                    launch {
                        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
                            val lists = it.items.filterIsInstance<PlaylistItem>().filterNot { it.id == "SE" }
                            accountPlaylists.value = lists
                        }.onFailure {
                            timber.log.Timber.w(it, "Failed to fetch playlists")
                        }
                    }
                } else {
                    accountName.value = "Guest"
                    accountImageUrl.value = null
                    accountPlaylists.value = null
                }
            } finally {
                isProcessingAccountData = false
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }

        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(3000)
            
            syncUtils.cleanupDuplicatePlaylists()
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    if (isProcessingAccountData) return@collect
                    
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true
                    
                    try {
                        val isLoggedIn = cookie?.let { "SAPISID" in parseCookieString(it) } ?: false
                        val loginTransition = isLoggedIn && !wasLoggedIn
                        wasLoggedIn = isLoggedIn
                        
                        if (isLoggedIn && cookie != null && cookie.isNotEmpty()) {
                            try {
                                YouTube.cookie = cookie
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to set YouTube cookie")
                                return@collect
                            }

                            if (loginTransition) {
                                launch {
                                    try {
                                        if (context.dataStore.get(YtmSyncKey, true)) {
                                            syncUtils.performFullSync()
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error during login-triggered sync")
                                        reportException(e)
                                    }
                                }
                            }
                            
                            kotlinx.coroutines.delay(100)
                            
                            try {
                                YouTube.accountInfo().onSuccess { info ->
                                    accountName.value = info.name
                                    accountImageUrl.value = info.thumbnailUrl
                                }.onFailure { e ->
                                    timber.log.Timber.w(e, "Failed to fetch account info")
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Exception fetching account info")
                            }

                            launch {
                                try {
                                    YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
                                        val lists = it.items.filterIsInstance<PlaylistItem>().filterNot { it.id == "SE" }
                                        accountPlaylists.value = lists
                                    }.onFailure { e ->
                                        timber.log.Timber.w(e, "Failed to fetch account playlists")
                                    }
                                } catch (e: Exception) {
                                    timber.log.Timber.e(e, "Exception fetching account playlists")
                                }
                            }
                        } else {
                            accountName.value = "Guest"
                            accountImageUrl.value = null
                            accountPlaylists.value = null
                        }
                    } catch (e: Exception) {
                        timber.log.Timber.e(e, "Error processing cookie change")
                        accountName.value = "Guest"
                        accountImageUrl.value = null
                        accountPlaylists.value = null
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }
    }

    companion object {
        private var cachedHomePage: HomePage? = null
        private var cachedTrendingSongs: List<SongItem>? = null
        private var cachedReelsSongs: List<SongItem>? = null
        private var cachedExplorePage: ExplorePage? = null
    }
}
