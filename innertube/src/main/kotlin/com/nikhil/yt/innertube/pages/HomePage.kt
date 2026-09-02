/*
 * Velune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.nikhil.yt.innertube.pages

import com.nikhil.yt.innertube.models.Album
import com.nikhil.yt.innertube.models.AlbumItem
import com.nikhil.yt.innertube.models.Artist
import com.nikhil.yt.innertube.models.ArtistItem
import com.nikhil.yt.innertube.models.BrowseEndpoint
import com.nikhil.yt.innertube.models.MusicCarouselShelfRenderer
import com.nikhil.yt.innertube.models.MusicResponsiveListItemRenderer
import com.nikhil.yt.innertube.models.MusicShelfRenderer
import com.nikhil.yt.innertube.models.MusicTwoRowItemRenderer
import com.nikhil.yt.innertube.models.PlaylistItem
import com.nikhil.yt.innertube.models.SectionListRenderer
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.innertube.models.YTItem
import com.nikhil.yt.innertube.models.oddElements
import com.nikhil.yt.innertube.models.filterExplicit

data class HomePage(
    val chips: List<Chip>?,
    val sections: List<Section>,
    val continuation: String? = null,
) {
    data class Chip(
        val title: String,
        val endpoint: BrowseEndpoint?,
        val deselectEndPoint: BrowseEndpoint?,
    ) {
        companion object {
            fun fromChipCloudChipRenderer(renderer: SectionListRenderer.Header.ChipCloudRenderer.Chip): Chip? {
                return Chip(
                    title = renderer.chipCloudChipRenderer.text?.runs?.firstOrNull()?.text ?: return null,
                    endpoint = renderer.chipCloudChipRenderer.navigationEndpoint.browseEndpoint,
                    deselectEndPoint = renderer.chipCloudChipRenderer.onDeselectedCommand?.browseEndpoint,
                )
            }
        }
    }

    data class Section(
        val title: String,
        val label: String?,
        val thumbnail: String?,
        val endpoint: BrowseEndpoint?,
        val items: List<YTItem>,
    ) {
        companion object {
            fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): Section? {
                // Parse items from musicTwoRowItemRenderer (playlists, albums, artists, some songs)
                val twoRowItems: List<YTItem> = renderer.contents.mapNotNull {
                    it.musicTwoRowItemRenderer
                }.mapNotNull {
                    fromMusicTwoRowItemRenderer(it)
                }

                // Parse items from musicResponsiveListItemRenderer (song-list shelves:
                // Quick Picks, Heard in Shorts/Reels, Trending songs, etc.)
                val responsiveListItems: List<YTItem> = renderer.contents.mapNotNull {
                    it.musicResponsiveListItemRenderer
                }.mapNotNull {
                    fromMusicResponsiveListItemRenderer(it)
                }

                val allItems = twoRowItems + responsiveListItems

                return Section(
                    title = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: return null,
                    label = renderer.header.musicCarouselShelfBasicHeaderRenderer.strapline?.runs?.firstOrNull()?.text,
                    thumbnail = renderer.header.musicCarouselShelfBasicHeaderRenderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),
                    endpoint = renderer.header.musicCarouselShelfBasicHeaderRenderer.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint,
                    items = allItems.ifEmpty {
                        return null
                    }
                )
            }

            fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): Section? {
                val songs = renderer.contents?.mapNotNull {
                    it.musicResponsiveListItemRenderer
                }?.mapNotNull {
                    fromMusicResponsiveListItemRenderer(it)
                }?.ifEmpty { return null } ?: return null

                return Section(
                    title = renderer.title?.runs?.firstOrNull()?.text ?: return null,
                    label = null,
                    thumbnail = null,
                    endpoint = renderer.bottomEndpoint?.browseEndpoint,
                    items = songs
                )
            }

            /**
             * Parse a musicResponsiveListItemRenderer into a SongItem.
             * Used for YouTube Home song-list shelves (Quick Picks,
             * Heard in Shorts/Reels, Trending songs, etc.)
             * Reuses the same field access pattern as RelatedPage.
             */
            private fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
                return SongItem(
                    id = renderer.playlistItemData?.videoId
                        ?: renderer.overlay?.musicItemThumbnailOverlayRenderer
                            ?.content?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint?.watchEndpoint?.videoId
                        ?: return null,
                    title = renderer.flexColumns.firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text?.runs?.firstOrNull()?.text ?: return null,
                    artists = renderer.flexColumns.getOrNull(1)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text?.runs?.oddElements()?.map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId,
                            )
                        } ?: return null,
                    album = renderer.flexColumns.getOrNull(2)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text?.runs?.firstOrNull()?.let {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId
                                    ?: return@let null,
                            )
                        },
                    duration = null,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: return null,
                    explicit = renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null,
                    endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer
                        ?.content?.musicPlayButtonRenderer
                        ?.playNavigationEndpoint?.watchEndpoint,
                )
            }

            private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
                return when {
                    renderer.isSong -> {
                        val subtitleRuns = renderer.subtitle?.runs ?: return null
                        val (artistRuns, albumRuns) = subtitleRuns.partition { run ->
                            run.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("UC") == true
                        }
                        val artists = artistRuns.map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return null
                            )
                        }
                        SongItem(
                            id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            artists = artists,
                            album = albumRuns.firstOrNull { run ->
                                run.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("MPREb_") == true
                            }?.let { run ->
                                val endpoint = run.navigationEndpoint?.browseEndpoint ?: return null
                                Album(
                                    name = run.text,
                                    id = endpoint.browseId
                                )
                            },
                            duration = null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                            explicit = renderer.subtitleBadges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true
                        )
                    }
                    renderer.isAlbum -> {
                        AlbumItem(
                            browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                            playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                                ?.musicPlayButtonRenderer?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint?.playlistId ?: return null,
                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            artists = renderer.subtitle?.runs?.oddElements()?.drop(1)?.map {
                                Artist(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId
                                )
                            },
                            year = null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                            explicit = renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null
                        )
                    }

                    renderer.isPlaylist -> {
                        PlaylistItem(
                            id = renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix("VL") ?: return null,
                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            author = Artist(
                                name = renderer.subtitle?.runs?.lastOrNull()?.text ?: return null,
                                id = null
                            ),
                            songCountText = null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                            playEndpoint = renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer?.content
                                ?.musicPlayButtonRenderer?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                            shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint ?: return null,
                            radioEndpoint = renderer.menu.menuRenderer.items.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                        )
                    }

                    renderer.isArtist -> {
                        ArtistItem(
                            id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                            title = renderer.title.runs?.lastOrNull()?.text ?: return null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                            shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint ?: return null,
                            radioEndpoint = renderer.menu.menuRenderer.items.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint ?: return null,
                        )
                    }

                    else -> null
                }
            }
        }
    }

    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) {
            copy(sections = sections.map {
                it.copy(items = it.items.filterExplicit())
            })
        } else this

}
