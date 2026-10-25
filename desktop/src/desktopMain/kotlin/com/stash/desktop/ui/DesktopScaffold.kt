package com.stash.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stash.core.model.PlayerState
import com.stash.core.model.RepeatMode
import com.stash.core.model.Track
import com.stash.core.ui.theme.StashCyan
import com.stash.core.ui.theme.StashGlassBorder
import com.stash.core.ui.theme.StashPurple
import com.stash.core.ui.theme.StashSurface
import com.stash.desktop.di.DesktopComponent
import com.stash.feature.library.LibraryScreen
import com.stash.feature.library.LocalAllSongsViewModel
import com.stash.feature.library.LocalLibraryViewModel
import com.stash.feature.search.LocalSearchViewModel
import com.stash.feature.search.SearchScreen
import com.stash.feature.sync.LocalSyncViewModel
import com.stash.feature.sync.SyncScreen
import kotlinx.coroutines.launch

enum class DesktopNavTab(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Default.Home),
    Search("Search", Icons.Default.Search),
    Sync("Sync", Icons.Default.Sync),
    Settings("Settings", Icons.Default.Settings),
}

@Composable
fun DesktopScaffold(
    component: DesktopComponent,
    isDarkTheme: Boolean,
    isAmoledTheme: Boolean,
    onThemeChanged: (dark: Boolean, amoled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentTab by remember { mutableStateOf(DesktopNavTab.Library) }
    var showLyricsPanel by remember { mutableStateOf(false) }

    val playerRepository = remember { component.playerRepository() }
    val playerState by playerRepository.playerState.collectAsState()
    val currentPositionMs by playerRepository.currentPosition.collectAsState(initial = 0L)
    val isConnected = remember { component.connectivity().isConnected() }
    val scope = rememberCoroutineScope()

    // Provide ViewModel CompositionLocals so screens can find their ViewModel
    CompositionLocalProvider(
        LocalLibraryViewModel provides remember { component.libraryViewModel() },
        LocalAllSongsViewModel provides remember { component.allSongsViewModel() },
        LocalSearchViewModel provides remember { component.searchViewModel() },
        LocalSyncViewModel provides remember { component.syncViewModel() },
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Main workspace (Navigation rail + Content stage + optional lyrics panel)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // ── Left Navigation Rail ──────────────────────────────────────────
                DesktopNavRail(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    isConnected = isConnected,
                    modifier = Modifier.width(220.dp),
                )

                // ── Center Stage ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    when (currentTab) {
                        DesktopNavTab.Library -> {
                            LibraryScreen()
                        }
                        DesktopNavTab.Search -> {
                            SearchScreen(
                                onNavigateToArtist = { _, _, _ -> },
                                onNavigateToAlbum = { _ -> },
                            )
                        }
                        DesktopNavTab.Sync -> {
                            SyncScreen()
                        }
                        DesktopNavTab.Settings -> {
                            DesktopSettingsScreen(
                                appPaths = component.appPaths(),
                                platformInfo = component.platformInfo(),
                                mpvStatusMessage = if (component.mpvPlaybackEngine().isNativeEngineAvailable) {
                                    "libmpv initialized and active"
                                } else {
                                    "libmpv not detected (fallback mode)"
                                },
                                isDarkTheme = isDarkTheme,
                                isAmoledTheme = isAmoledTheme,
                                onThemeChanged = onThemeChanged,
                            )
                        }
                    }
                }

                // ── Right Lyrics Side Panel ───────────────────────────────────────
                AnimatedVisibility(
                    visible = showLyricsPanel,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                ) {
                    DesktopLyricsSidePanel(
                        currentTrack = playerState.currentTrack,
                        onClose = { showLyricsPanel = false },
                        modifier = Modifier.width(320.dp),
                    )
                }
            }

            // ── Persistent Bottom Player Bar ──────────────────────────────────────
            DesktopBottomPlayerBar(
                playerState = playerState,
                currentPositionMs = currentPositionMs,
                onPlay = { scope.launch { playerRepository.play() } },
                onPause = { scope.launch { playerRepository.pause() } },
                onSkipNext = { scope.launch { playerRepository.skipNext() } },
                onSkipPrevious = { scope.launch { playerRepository.skipPrevious() } },
                onSeekTo = { pos -> scope.launch { playerRepository.seekTo(pos) } },
                onToggleShuffle = { scope.launch { playerRepository.toggleShuffle() } },
                onCycleRepeat = { scope.launch { playerRepository.cycleRepeatMode() } },
                onSetVolume = { vol -> playerRepository.setVolume(vol) },
                showLyrics = showLyricsPanel,
                onToggleLyrics = { showLyricsPanel = !showLyricsPanel },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
            )
        }
    }
}

@Composable
private fun DesktopNavRail(
    currentTab: DesktopNavTab,
    onTabSelected: (DesktopNavTab) -> Unit,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = StashSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, StashGlassBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // Brand Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                brush = Brush.linearGradient(listOf(StashPurple, StashCyan)),
                                shape = RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "STASH",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Navigation Items
                DesktopNavTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val bg = if (isSelected) StashPurple.copy(alpha = 0.18f) else Color.Transparent
                    val contentColor = if (isSelected) StashCyan else Color(0xFF94A3B8)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = tab.label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        )
                    }
                }
            }

            // Bottom Rail Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF34D399) else Color(0xFFF87171),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConnected) "Online" else "Offline",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                )
            }
        }
    }
}

@Composable
private fun DesktopBottomPlayerBar(
    playerState: PlayerState,
    currentPositionMs: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetVolume: (Float) -> Unit,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playerState.currentTrack
    val durationMs = (playerState.durationMs.takeIf { it > 0 } ?: track?.durationMs ?: 0L).coerceAtLeast(0L)
    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val displayProgress = if (isDraggingSlider) dragProgress else progress
    val displayPos = if (isDraggingSlider) (dragProgress * durationMs).toLong() else currentPositionMs

    var volume by remember { mutableFloatStateOf(0.85f) }
    var isMuted by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = StashSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, StashGlassBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── Left: Track Details ──────────────────────────────────────
            Row(
                modifier = Modifier.width(260.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Artwork thumbnail
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center,
                ) {
                    val artUrl = track?.albumArtUrl ?: track?.albumArtPath
                    if (artUrl != null) {
                        AsyncImage(
                            model = artUrl,
                            contentDescription = track?.album ?: "Album Artwork",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "No Track Playing",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track?.artist ?: "Select a song from Library or Search",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = if (track?.stashLikedAt != null) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (track?.stashLikedAt != null) StashCyan else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ── Center: Transport Controls & Scrubber ───────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Controls Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.isShuffleEnabled) StashCyan else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onSkipPrevious) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    // Prominent Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                brush = Brush.linearGradient(listOf(StashPurple, StashCyan)),
                                shape = CircleShape,
                            )
                            .clickable {
                                if (playerState.isPlaying) onPause() else onPlay()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = onSkipNext) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = onCycleRepeat) {
                        val icon = if (playerState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                        Icon(
                            imageVector = icon,
                            contentDescription = "Repeat",
                            tint = if (playerState.repeatMode != RepeatMode.OFF) StashCyan else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Scrubber Progress Row
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDuration(displayPos),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                    )
                    Slider(
                        value = displayProgress,
                        onValueChange = { newProgress ->
                            isDraggingSlider = true
                            dragProgress = newProgress
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0) {
                                onSeekTo((dragProgress * durationMs).toLong())
                            }
                            isDraggingSlider = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .height(18.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = StashCyan,
                            activeTrackColor = StashPurple,
                            inactiveTrackColor = Color(0xFF334155),
                        ),
                    )
                    Text(
                        text = formatDuration(durationMs),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                    )
                }
            }

            // ── Right: Quality & Volume Controls ─────────────────────────
            Row(
                modifier = Modifier.width(260.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                // Quality badge pill
                val qualityLabel = when {
                    track?.fileFormat?.lowercase() == "flac" -> "FLAC 24/96"
                    track?.fileFormat?.lowercase() == "opus" -> "Opus"
                    else -> "Lossless"
                }
                Box(
                    modifier = Modifier
                        .background(StashPurple.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp))
                        .border(1.dp, StashPurple.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = qualityLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StashCyan,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Lyrics toggle
                IconButton(onClick = onToggleLyrics) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (showLyrics) StashCyan else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))

                // Volume Control
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        onSetVolume(if (isMuted) 0f else volume)
                    },
                ) {
                    Icon(
                        imageVector = if (isMuted || volume == 0f) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Slider(
                    value = if (isMuted) 0f else volume,
                    onValueChange = { newVol ->
                        isMuted = false
                        volume = newVol
                        onSetVolume(newVol)
                    },
                    modifier = Modifier
                        .width(90.dp)
                        .height(18.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = StashCyan,
                        activeTrackColor = StashCyan,
                        inactiveTrackColor = Color(0xFF334155),
                    ),
                )
            }
        }
    }
}

@Composable
private fun DesktopLyricsSidePanel(
    currentTrack: Track?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = StashSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, StashGlassBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Lyrics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Lyrics",
                        tint = Color(0xFF94A3B8),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (currentTrack != null) {
                Text(
                    text = currentTrack.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StashCyan,
                )
                Text(
                    text = currentTrack.artist,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Synced lyrics display active.",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Play a song to view lyrics",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
