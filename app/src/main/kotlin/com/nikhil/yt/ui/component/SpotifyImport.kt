package com.nikhil.yt.ui.component

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nikhil.yt.LocalDatabase
import com.nikhil.yt.R
import com.nikhil.yt.db.entities.PlaylistEntity
import com.nikhil.yt.db.entities.PlaylistSongMap
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.models.toMediaMetadata
import com.nikhil.yt.utils.SpotifyPlaylistData
import com.nikhil.yt.utils.SpotifyPlaylistParser
import com.nikhil.yt.utils.SpotifyTrack
import com.nikhil.yt.utils.makeTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

private val SpotifyGreen = Color(0xFF1DB954)

data class MatchedTrackItem(
    val spotifyTrack: SpotifyTrack,
    val matchedSong: SongItem?,
)

sealed interface SpotifyImportStep {
    data object Input : SpotifyImportStep
    data class Fetching(val message: String) : SpotifyImportStep
    data class Matching(
        val playlist: SpotifyPlaylistData,
        val currentTrack: String,
        val progress: Float,
        val matchedCount: Int,
        val totalCount: Int,
    ) : SpotifyImportStep
    data class Preview(
        val playlist: SpotifyPlaylistData,
        val results: List<MatchedTrackItem>,
    ) : SpotifyImportStep
    data object Saving : SpotifyImportStep
}

/**
 * Clickable row/card appearing on the Library screen to trigger Spotify playlist importing.
 */
@Composable
fun SpotifyImportCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpotifyGreen.copy(alpha = 0.16f))
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_spotify),
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Import your Spotify playlist",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Bring your playlists to Cynk",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Full bottom sheet modal handling Spotify URL validation, track extraction,
 * YouTube Music song matching, preview, and Cynk playlist creation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportBottomSheet(
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf<SpotifyImportStep>(SpotifyImportStep.Input) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun pasteFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString().orEmpty().trim()
                if (text.isNotBlank()) {
                    urlInput = text
                    errorMessage = null
                }
            }
        } catch (_: Exception) {}
    }

    fun startImport(url: String) {
        val playlistId = SpotifyPlaylistParser.extractPlaylistId(url)
        if (playlistId == null) {
            errorMessage = "Please enter a valid Spotify playlist link (e.g. open.spotify.com/playlist/...)"
            return
        }

        errorMessage = null
        currentStep = SpotifyImportStep.Fetching("Retrieving Spotify playlist...")

        coroutineScope.launch {
            val fetchResult = SpotifyPlaylistParser.fetchPlaylist(url)
            fetchResult.onFailure { err ->
                errorMessage = err.message ?: "Failed to load Spotify playlist. Please check if the link is public."
                currentStep = SpotifyImportStep.Input
            }.onSuccess { playlistData ->
                val totalTracks = playlistData.tracks.size
                val matchedItems = mutableListOf<MatchedTrackItem>()

                for (i in playlistData.tracks.indices) {
                    val track = playlistData.tracks[i]
                    val progress = (i + 1).toFloat() / totalTracks
                    val matchedCount = matchedItems.count { it.matchedSong != null }

                    currentStep = SpotifyImportStep.Matching(
                        playlist = playlistData,
                        currentTrack = "${track.name} - ${track.artist}",
                        progress = progress,
                        matchedCount = matchedCount,
                        totalCount = totalTracks
                    )

                    val query = "${track.name} ${track.artist}".trim()
                    val searchResult = withContext(Dispatchers.IO) {
                        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    }
                    val songCandidates = searchResult?.items?.filterIsInstance<SongItem>().orEmpty()
                    
                    // Match best candidate or top result
                    val bestMatch = songCandidates.firstOrNull { candidate ->
                        candidate.title.contains(track.name, ignoreCase = true) ||
                                track.name.contains(candidate.title, ignoreCase = true)
                    } ?: songCandidates.firstOrNull()

                    matchedItems.add(
                        MatchedTrackItem(
                            spotifyTrack = track,
                            matchedSong = bestMatch
                        )
                    )
                }

                currentStep = SpotifyImportStep.Preview(
                    playlist = playlistData,
                    results = matchedItems
                )
            }
        }
    }

    fun savePlaylist(playlistData: SpotifyPlaylistData, items: List<MatchedTrackItem>) {
        val validSongs = items.mapNotNull { it.matchedSong }
        if (validSongs.isEmpty()) {
            Toast.makeText(context, "No matched songs to import", Toast.LENGTH_SHORT).show()
            return
        }

        currentStep = SpotifyImportStep.Saving

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val playlistEntity = PlaylistEntity(
                    name = playlistData.title,
                    thumbnailUrl = playlistData.coverUrl ?: validSongs.firstOrNull()?.thumbnail,
                    isEditable = true,
                    bookmarkedAt = LocalDateTime.now()
                )

                database.transaction {
                    insert(playlistEntity)
                    validSongs.map { it.toMediaMetadata() }.forEach(::insert)
                    validSongs.forEachIndexed { index, song ->
                        insert(
                            PlaylistSongMap(
                                songId = song.id,
                                playlistId = playlistEntity.id,
                                position = index
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Imported '${playlistData.title}' (${validSongs.size} songs)",
                        Toast.LENGTH_LONG
                    ).show()
                    onDismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to save playlist: ${e.message}"
                    currentStep = SpotifyImportStep.Preview(playlistData, items)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen.copy(alpha = 0.16f))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_spotify),
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Import from Spotify",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "Close"
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (val step = currentStep) {
                is SpotifyImportStep.Input -> {
                    Text(
                        text = "Paste a public Spotify playlist link to import its tracks into your Cynk library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            errorMessage = null
                        },
                        label = { Text("Spotify Playlist URL") },
                        placeholder = { Text("https://open.spotify.com/playlist/...") },
                        singleLine = true,
                        trailingIcon = {
                            if (urlInput.isBlank()) {
                                TextButton(onClick = { pasteFromClipboard() }) {
                                    Text("Paste", color = SpotifyGreen, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                IconButton(onClick = { urlInput = ""; errorMessage = null }) {
                                    Icon(painter = painterResource(R.drawable.close), contentDescription = "Clear")
                                }
                            }
                        },
                        isError = errorMessage != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen,
                            focusedLabelColor = SpotifyGreen,
                            cursorColor = SpotifyGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { startImport(urlInput) },
                        enabled = urlInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Fetch Playlist", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                is SpotifyImportStep.Fetching -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    ) {
                        CircularProgressIndicator(color = SpotifyGreen)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = step.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is SpotifyImportStep.Matching -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!step.playlist.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = step.playlist.coverUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.playlist.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Matching songs on YouTube Music...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        LinearProgressIndicator(
                            progress = { step.progress },
                            color = SpotifyGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = step.currentTrack,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${(step.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = SpotifyGreen
                            )
                        }
                    }
                }

                is SpotifyImportStep.Preview -> {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!step.playlist.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = step.playlist.coverUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.playlist.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val matchedCount = step.results.count { it.matchedSong != null }
                                val totalCount = step.results.size
                                Text(
                                    text = "$matchedCount of $totalCount songs matched",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (matchedCount > 0) SpotifyGreen else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            itemsIndexed(step.results) { index, item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    if (item.matchedSong != null) {
                                        AsyncImage(
                                            model = item.matchedSong.thumbnail,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.matchedSong.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.matchedSong.artists.joinToString(", ") { it.name },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            painter = painterResource(R.drawable.check),
                                            contentDescription = "Matched",
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.close),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.spotifyTrack.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.spotifyTrack.artist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = { currentStep = SpotifyImportStep.Input },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Back")
                            }

                            val matchedCount = step.results.count { it.matchedSong != null }
                            Button(
                                onClick = { savePlaylist(step.playlist, step.results) },
                                enabled = matchedCount > 0,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SpotifyGreen,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp)
                            ) {
                                Text("Import ($matchedCount songs)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                is SpotifyImportStep.Saving -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    ) {
                        CircularProgressIndicator(color = SpotifyGreen)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Saving playlist to your library...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
