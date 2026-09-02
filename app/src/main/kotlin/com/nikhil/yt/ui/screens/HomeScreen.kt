/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.MaterialTheme
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nikhil.yt.innertube.models.AlbumItem
import com.nikhil.yt.innertube.models.ArtistItem
import com.nikhil.yt.innertube.models.PlaylistItem
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.innertube.utils.parseCookieString
import com.nikhil.yt.LocalDatabase
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.LocalPlayerConnection
import com.nikhil.yt.R
import com.nikhil.yt.constants.InnerTubeCookieKey
import com.nikhil.yt.constants.DisableBlurKey
import com.nikhil.yt.constants.ShowHomeCategoryChipsKey
import com.nikhil.yt.db.entities.Album
import com.nikhil.yt.db.entities.Artist
import com.nikhil.yt.db.entities.Playlist
import com.nikhil.yt.db.entities.Song
import com.nikhil.yt.models.toMediaMetadata
import com.nikhil.yt.playback.queues.LocalAlbumRadio
import com.nikhil.yt.playback.queues.YouTubeAlbumRadio
import com.nikhil.yt.playback.queues.YouTubeQueue
import com.nikhil.yt.ui.component.HideOnScrollFAB
import com.nikhil.yt.ui.component.LocalBottomSheetPageState
import com.nikhil.yt.ui.component.LocalMenuState
import com.nikhil.yt.ui.component.NavigationTitle
import com.nikhil.yt.utils.rememberPreference
import com.nikhil.yt.viewmodels.HomeViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import com.nikhil.yt.together.TogetherSessionState
import com.nikhil.yt.ui.utils.isScrollingUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random



@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val togetherSessionState by playerConnection.service.togetherSessionState.collectAsState()

    val quickPicks by viewModel.quickPicks.collectAsState()
    val trendingSongs by viewModel.trendingSongs.collectAsState()
    val reelsSongs by viewModel.reelsSongs.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val forYouSuggestions by viewModel.forYouSuggestions.collectAsState()

    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()
    val selectedChip by viewModel.selectedChip.collectAsState()
    val recentPlayedAlbums = remember(keepListening) { keepListening.orEmpty().filterIsInstance<Album>().take(10) }

    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val (disableBlur) = rememberPreference(DisableBlurKey, true)
    val (showHomeCategoryChips) = rememberPreference(ShowHomeCategoryChipsKey, true)
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (!isLoading && lastVisibleIndex != null && lastVisibleIndex >= len - 2) {
                    viewModel.loadMoreYouTubeItems(homePage?.continuation)
                }
            }
    }



    if (selectedChip != null) {
        BackHandler {
            viewModel.toggleChip(selectedChip)
        }
    }

    LaunchedEffect(showHomeCategoryChips, selectedChip) {
        if (!showHomeCategoryChips && selectedChip != null) {
            viewModel.toggleChip(selectedChip)
        }
    }

    val color1 = MaterialTheme.colorScheme.primary
    val color2 = MaterialTheme.colorScheme.secondary
    val color3 = MaterialTheme.colorScheme.tertiary
    val color4 = MaterialTheme.colorScheme.primaryContainer
    val color5 = MaterialTheme.colorScheme.secondaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        if (!disableBlur) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.7f)
                    .align(Alignment.TopCenter)
                    .zIndex(-1f) // Place behind all content
                    .drawWithCache {
                        val width = this.size.width
                        val height = this.size.height


                        val brush1 = Brush.radialGradient(
                            colors = listOf(
                                color1.copy(alpha = 0.38f),
                                color1.copy(alpha = 0.24f),
                                color1.copy(alpha = 0.14f),
                                color1.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.15f, height * 0.1f),
                            radius = width * 0.55f
                        )


                        val brush2 = Brush.radialGradient(
                            colors = listOf(
                                color2.copy(alpha = 0.34f),
                                color2.copy(alpha = 0.2f),
                                color2.copy(alpha = 0.11f),
                                color2.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.85f, height * 0.2f),
                            radius = width * 0.65f
                        )


                        val brush3 = Brush.radialGradient(
                            colors = listOf(
                                color3.copy(alpha = 0.3f),
                                color3.copy(alpha = 0.17f),
                                color3.copy(alpha = 0.09f),
                                color3.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.3f, height * 0.45f),
                            radius = width * 0.6f
                        )


                        val brush4 = Brush.radialGradient(
                            colors = listOf(
                                color4.copy(alpha = 0.26f),
                                color4.copy(alpha = 0.14f),
                                color4.copy(alpha = 0.08f),
                                color4.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.7f, height * 0.5f),
                            radius = width * 0.7f
                        )


                        val brush5 = Brush.radialGradient(
                            colors = listOf(
                                color5.copy(alpha = 0.22f),
                                color5.copy(alpha = 0.12f),
                                color5.copy(alpha = 0.06f),
                                color5.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.5f, height * 0.75f),
                            radius = width * 0.8f
                        )


                        val overlayBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                surfaceColor.copy(alpha = 0.22f),
                                surfaceColor.copy(alpha = 0.55f),
                                surfaceColor
                            ),
                            startY = height * 0.4f,
                            endY = height
                        )

                        onDrawBehind {
                            drawRect(brush = brush1)
                            drawRect(brush = brush2)
                            drawRect(brush = brush3)
                            drawRect(brush = brush4)
                            drawRect(brush = brush5)
                            drawRect(brush = overlayBrush)
                        }
                    }
            ) {}
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refresh
                )
        ) {
            val homeSections: List<com.nikhil.yt.innertube.pages.HomePage.Section> = remember(homePage?.sections) {
                homePage?.sections.orEmpty()
            }

            fun sectionMatches(
                section: com.nikhil.yt.innertube.pages.HomePage.Section,
                vararg terms: String
            ): Boolean {
                val normalized = section.title.lowercase()
                return terms.any { normalized.contains(it) }
            }

            val quickPicksYtSection = remember(homeSections) {
                homeSections.firstOrNull { sectionMatches(it, "quick picks", "quick pick") }
            }

            val featuredPlaylistsSection = remember(homeSections) {
                homeSections.firstOrNull {
                    sectionMatches(
                        it,
                        "featured playlists",
                        "featured playlist",
                        "top weekly",
                    )
                }
            }

            val albumsForYouSection = remember(homeSections) {
                homeSections.firstOrNull {
                    sectionMatches(it, "albums for you")
                }
            }

            val albumsFeaturingSection = remember(homeSections) {
                homeSections.firstOrNull {
                    sectionMatches(it, "albums featuring songs you like")
                }
            }

            val newReleasesSection = remember(homeSections) {
                homeSections.firstOrNull {
                    sectionMatches(it, "new releases", "new release")
                }
            }

            LazyColumn(
                state = lazylistState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                // 1. Header Mood Chips
                if (showHomeCategoryChips) {
                    item(key = "mood_chips") {
                        CynkMoodChipsRow(
                            availableChips = homePage?.chips.orEmpty(),
                            selectedChip = selectedChip,
                            onExistingChipSelected = viewModel::toggleChip,
                            onCustomMoodSelected = { mood ->
                                navController.navigate(
                                    "search/${java.net.URLEncoder.encode("$mood music", "UTF-8")}"
                                )
                            },
                        )
                    }
                }

                // 2. Quick Picks (SONG section, exactly once)
                quickPicks?.takeIf { it.isNotEmpty() }?.let { picks ->
                    item(key = "quick_picks_local") {
                        QuickPicksListSection(
                            quickPicks = picks,
                            accountImageUrl = url,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            modifier = Modifier.animateItem(),
                        )
                    }
                } ?: run {
                    val quickPicksFromSections = quickPicksYtSection?.items?.filterIsInstance<SongItem>()

                    val fallbackQuickPicks: List<SongItem> = quickPicksFromSections
                        ?: allYtItems.filterIsInstance<SongItem>().distinctBy { it.id }.take(24)

                    if (fallbackQuickPicks.isNotEmpty()) {
                        item(key = "quick_picks_yt") {
                            CynkYouTubeSongListSection(
                                title = "Quick picks",
                                avatarUrl = url,
                                showAvatar = true,
                                showPlayAll = false,
                                songs = fallbackQuickPicks,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

                // 3. Featured playlists for you
                featuredPlaylistsSection?.let { section ->
                    item(key = "featured_playlists_title") {
                        HomePageSectionTitle(
                            section = section.copy(title = "Featured playlists for you"),
                            navController = navController,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "featured_playlists_content") {
                        HomePageSectionContent(
                            section = section,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            scope = scope,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // 4. Albums for you
                albumsForYouSection?.let { section ->
                    val albums = section.items.filterIsInstance<AlbumItem>()
                    if (albums.isNotEmpty()) {
                        item(key = "albums_for_you_title") {
                            HomePageSectionTitle(
                                section = section.copy(title = "Albums for you"),
                                navController = navController,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "albums_for_you_content") {
                            CynkAlbumCardsSection(
                                albums = albums,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

                // 5. Trending — SONGS
                trendingSongs?.takeIf { it.isNotEmpty() }?.let { songs ->
                    item(key = "trending_songs") {
                        CynkYouTubeSongListSection(
                            title = "Trending",
                            showPlayAll = true,
                            songs = songs,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // 6. Heard in Reels — SONGS
                reelsSongs?.takeIf { it.isNotEmpty() }?.let { songs ->
                    item(key = "reels_songs") {
                        CynkYouTubeSongListSection(
                            title = "Heard in Reels",
                            showPlayAll = true,
                            songs = songs,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // 7. Recents (Recently played) — conditional
                keepListening?.takeIf { it.isNotEmpty() }?.let { items ->
                    item(key = "recents_title") {
                        NavigationTitle(
                            title = "Recents",
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "recents_content") {
                        KeepListeningSection(
                            keepListening = items,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            scope = scope,
                        )
                    }
                }

                // 8. More of what you like — conditional
                forYouSuggestions?.takeIf { it.isNotEmpty() }?.let { suggestions ->
                    item(key = "more_of_what_you_like") {
                        ForYouSection(
                            suggestions = suggestions,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // 9. Albums featuring songs you like — conditional
                albumsFeaturingSection?.let { section ->
                    val albums = section.items.filterIsInstance<AlbumItem>()
                    if (albums.isNotEmpty()) {
                        item(key = "albums_featuring_title") {
                            HomePageSectionTitle(
                                section = section.copy(title = "Albums featuring songs you like"),
                                navController = navController,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "albums_featuring_content") {
                            CynkAlbumCardsSection(
                                albums = albums,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                } ?: run {
                    if (recentPlayedAlbums.isNotEmpty()) {
                        item(key = "local_albums_featuring_title") {
                            NavigationTitle(
                                title = "Albums featuring songs you like",
                                modifier = Modifier.animateItem(),
                            )
                        }

                        item(key = "local_albums_featuring_content") {
                            LocalAlbumSuggestionsSection(
                                albums = recentPlayedAlbums,
                                navController = navController,
                                playerConnection = playerConnection,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                            )
                        }
                    }
                }

                // 10. New Releases
                newReleasesSection?.let { section ->
                    item(key = "new_releases_title") {
                        HomePageSectionTitle(
                            section = section.copy(title = "New Releases"),
                            navController = navController,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "new_releases_content") {
                        HomePageSectionContent(
                            section = section,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            scope = scope,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // 11. Explore — LAST
                item(key = "explore_home") {
                    ExploreHomeSection(navController = navController)
                }
            }

            HideOnScrollFAB(
                visible = allLocalItems.isNotEmpty() || allYtItems.isNotEmpty(),
                lazyListState = lazylistState,
                icon = R.drawable.shuffle,
                onClick = {
                    val local = when {
                        allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5
                        allLocalItems.isNotEmpty() -> true
                        else -> false
                    }
                    scope.launch(Dispatchers.Main) {
                        if (local) {
                            when (val luckyItem = allLocalItems.random()) {
                                is Song -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                                is Album -> {
                                    val albumWithSongs = withContext(Dispatchers.IO) {
                                        database.albumWithSongs(luckyItem.id).first()
                                    }
                                    albumWithSongs?.let {
                                        playerConnection.playQueue(LocalAlbumRadio(it))
                                    }
                                }
                                is Artist -> {}
                                is Playlist -> {}
                            }
                        } else {
                            when (val luckyItem = allYtItems.random()) {
                                is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                                is AlbumItem -> playerConnection.playQueue(YouTubeAlbumRadio(luckyItem.playlistId))
                                is ArtistItem -> luckyItem.radioEndpoint?.let {
                                    playerConnection.playQueue(YouTubeQueue(it))
                                }
                                is PlaylistItem -> luckyItem.playEndpoint?.let {
                                    playerConnection.playQueue(YouTubeQueue(it))
                                }
                            }
                        }
                    }
                }
            )

            AnimatedVisibility(
                visible = lazylistState.isScrollingUp(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    )
                    .padding(start = 16.dp, bottom = 16.dp),
            ) {
                val isJamActive = togetherSessionState is TogetherSessionState.HostingOnline ||
                    togetherSessionState is TogetherSessionState.Joined ||
                    togetherSessionState is TogetherSessionState.Reconnecting ||
                    togetherSessionState is TogetherSessionState.JoiningOnline

                val jamLabel = when (val state = togetherSessionState) {
                    is TogetherSessionState.HostingOnline -> {
                        val count = state.roomState?.participants?.size ?: 1
                        "Jam • $count"
                    }
                    is TogetherSessionState.Joined -> {
                        val count = state.roomState.participants.size
                        "Jam • $count"
                    }
                    is TogetherSessionState.Reconnecting -> "Jam • Reconnecting"
                    is TogetherSessionState.JoiningOnline -> "Jam • Joining"
                    else -> "Jam"
                }

                ExtendedFloatingActionButton(
                    onClick = {
                        navController.navigate("settings/music_together")
                    },
                    icon = {
                        Icon(
                            painter = painterResource(if (isJamActive) R.drawable.fire else R.drawable.multi_user),
                            contentDescription = "Cynk Together Jam",
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    text = {
                        Text(
                            text = jamLabel,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    containerColor = if (isJamActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isJamActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(20.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                )
            }

            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
    }
}
