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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import com.stash.feature.library.AlbumDetailScreen
import com.stash.feature.library.ArtistDetailScreen
import com.stash.feature.library.LocalAlbumDetailViewModel
import com.stash.feature.library.LocalArtistDetailViewModel
import com.stash.feature.library.LocalPlaylistDetailViewModel
import com.stash.feature.library.PlaylistDetailScreen
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.stash.data.ytmusic.model.AlbumSource
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.feature.search.AlbumDiscoveryScreen
import com.stash.feature.search.ArtistProfileScreen
import com.stash.feature.search.LocalAlbumDiscoveryViewModel
import com.stash.feature.search.LocalArtistProfileViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.TextButton
import com.stash.data.lyrics.LyricsRepository
import com.stash.data.lyrics.parser.LrcLine
import com.stash.data.lyrics.parser.LrcParser
import com.stash.data.lyrics.source.LyricsQuery
import com.stash.data.lyrics.source.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class DesktopNavTab(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Default.Home),
    Search("Search", Icons.Default.Search),
    Sync("Sync", Icons.Default.Sync),
    Settings("Settings", Icons.Default.Settings),
}

sealed interface DesktopScreen {
    data object Library : DesktopScreen
    data object Search : DesktopScreen
    data object Sync : DesktopScreen
    data object Settings : DesktopScreen
    data class PlaylistDetail(val playlistId: Long) : DesktopScreen
    data class AlbumDetail(
        val albumId: String,
        val albumName: String,
        val artUrl: String?,
        val artistName: String,
    ) : DesktopScreen
    data class ArtistDetail(val artistName: String) : DesktopScreen
    data class ArtistProfile(
        val artistId: String,
        val name: String,
        val avatarUrl: String?,
        val focusAlbum: String? = null,
    ) : DesktopScreen
    data class AlbumDiscovery(
        val browseId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String?,
        val year: String?,
        val source: AlbumSource = AlbumSource.YOUTUBE,
        val qobuzTrackCount: Int? = null,
    ) : DesktopScreen
}

@Composable
fun DesktopScaffold(
    component: DesktopComponent,
    isDarkTheme: Boolean,
    isAmoledTheme: Boolean,
    onThemeChanged: (dark: Boolean, amoled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = remember { mutableStateListOf<DesktopScreen>(DesktopScreen.Library) }
    val currentScreen = backStack.lastOrNull() ?: DesktopScreen.Library
    var showLyricsPanel by remember { mutableStateOf(false) }

    val activeNavTab = when (currentScreen) {
        DesktopScreen.Library,
        is DesktopScreen.PlaylistDetail,
        is DesktopScreen.AlbumDetail,
        is DesktopScreen.ArtistDetail -> DesktopNavTab.Library
        DesktopScreen.Search,
        is DesktopScreen.ArtistProfile,
        is DesktopScreen.AlbumDiscovery -> DesktopNavTab.Search
        DesktopScreen.Sync -> DesktopNavTab.Sync
        DesktopScreen.Settings -> DesktopNavTab.Settings
    }

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
                .background(MaterialTheme.colorScheme.background)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                    when (keyEvent.key) {
                        Key.Escape -> if (backStack.size > 1) {
                            backStack.removeAt(backStack.lastIndex)
                            true
                        } else {
                            false
                        }
                        Key.MediaPlayPause -> if (!com.sun.jna.Platform.isMac()) {
                            scope.launch {
                                if (playerState.isPlaying) playerRepository.pause() else playerRepository.play()
                            }
                            true
                        } else false
                        Key.MediaPlay -> if (!com.sun.jna.Platform.isMac()) {
                            scope.launch { playerRepository.play() }
                            true
                        } else false
                        Key.MediaPause, Key.MediaStop -> if (!com.sun.jna.Platform.isMac()) {
                            scope.launch { playerRepository.pause() }
                            true
                        } else false
                        Key.MediaNext -> if (!com.sun.jna.Platform.isMac()) {
                            scope.launch { playerRepository.skipNext() }
                            true
                        } else false
                        Key.MediaPrevious -> if (!com.sun.jna.Platform.isMac()) {
                            scope.launch { playerRepository.skipPrevious() }
                            true
                        } else false
                        else -> false
                    }
                },
        ) {
            // Main workspace (Navigation rail + Content stage + optional lyrics panel)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // ── Left Navigation Rail ──────────────────────────────────────────
                DesktopNavRail(
                    currentTab = activeNavTab,
                    onTabSelected = { tab ->
                        val target = when (tab) {
                            DesktopNavTab.Library -> DesktopScreen.Library
                            DesktopNavTab.Search -> DesktopScreen.Search
                            DesktopNavTab.Sync -> DesktopScreen.Sync
                            DesktopNavTab.Settings -> DesktopScreen.Settings
                        }
                        backStack.clear()
                        backStack.add(target)
                    },
                    isConnected = isConnected,
                    modifier = Modifier.width(220.dp),
                )

                // ── Center Stage ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    when (val screen = currentScreen) {
                        DesktopScreen.Library -> {
                            LibraryScreen(
                                onNavigateToPlaylist = { playlistId ->
                                    backStack.add(DesktopScreen.PlaylistDetail(playlistId))
                                },
                                onNavigateToArtist = { artistName ->
                                    backStack.add(DesktopScreen.ArtistDetail(artistName))
                                },
                                onNavigateToAlbum = { albumId, name, artUrl, artistName ->
                                    backStack.add(
                                        DesktopScreen.AlbumDetail(
                                            albumId = albumId,
                                            albumName = name,
                                            artUrl = artUrl,
                                            artistName = artistName,
                                        )
                                    )
                                },
                                onNavigateToRemoteAlbum = { albumId, name, artUrl, artistName ->
                                    backStack.add(
                                        DesktopScreen.AlbumDiscovery(
                                            browseId = albumId,
                                            title = name,
                                            artist = artistName,
                                            thumbnailUrl = artUrl,
                                            year = null,
                                        )
                                    )
                                },
                                onNavigateToSync = {
                                    backStack.clear()
                                    backStack.add(DesktopScreen.Sync)
                                },
                                onNavigateToSearch = {
                                    backStack.clear()
                                    backStack.add(DesktopScreen.Search)
                                },
                                onNavigateToAccounts = {
                                    backStack.clear()
                                    backStack.add(DesktopScreen.Settings)
                                },
                            )
                        }
                        is DesktopScreen.PlaylistDetail -> {
                            val playlistVm = remember(screen.playlistId) {
                                component.playlistDetailViewModelFactory().create(screen.playlistId)
                            }
                            DisposableEffect(playlistVm) {
                                onDispose { playlistVm.viewModelScope.cancel() }
                            }
                            CompositionLocalProvider(LocalPlaylistDetailViewModel provides playlistVm) {
                                PlaylistDetailScreen(
                                    onBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                    },
                                )
                            }
                        }
                        is DesktopScreen.AlbumDetail -> {
                            val albumDetailVm = remember(screen.albumName, screen.artistName) {
                                component.albumDetailViewModelFactory().create(
                                    albumName = screen.albumName,
                                    artistName = screen.artistName,
                                )
                            }
                            DisposableEffect(albumDetailVm) {
                                onDispose { albumDetailVm.viewModelScope.cancel() }
                            }
                            CompositionLocalProvider(LocalAlbumDetailViewModel provides albumDetailVm) {
                                AlbumDetailScreen(
                                    onBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                    },
                                )
                            }
                        }
                        is DesktopScreen.ArtistDetail -> {
                            val artistDetailVm = remember(screen.artistName) {
                                component.artistDetailViewModelFactory().create(screen.artistName)
                            }
                            DisposableEffect(artistDetailVm) {
                                onDispose { artistDetailVm.viewModelScope.cancel() }
                            }
                            CompositionLocalProvider(LocalArtistDetailViewModel provides artistDetailVm) {
                                ArtistDetailScreen(
                                    onBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                    },
                                )
                            }
                        }
                        DesktopScreen.Search -> {
                            SearchScreen(
                                onNavigateToArtist = { id, name, avatar ->
                                    backStack.add(
                                        DesktopScreen.ArtistProfile(
                                            artistId = id,
                                            name = name,
                                            avatarUrl = avatar,
                                        ),
                                    )
                                },
                                onNavigateToAlbum = { album ->
                                    backStack.add(
                                        DesktopScreen.AlbumDiscovery(
                                            browseId = album.id,
                                            title = album.title,
                                            artist = album.artist,
                                            thumbnailUrl = album.thumbnailUrl,
                                            year = album.year,
                                            source = album.source,
                                            qobuzTrackCount = album.trackCount,
                                        ),
                                    )
                                },
                            )
                        }
                        DesktopScreen.Sync -> {
                            SyncScreen()
                        }
                        DesktopScreen.Settings -> {
                            DesktopSettingsScreen(
                                appPaths = component.appPaths(),
                                platformInfo = component.platformInfo(),
                                mpvStatusMessage = if (component.mpvPlaybackEngine().isNativeEngineAvailable) {
                                    "libmpv initialized and active"
                                } else {
                                    "libmpv not detected (fallback mode)"
                                } ,
                                isDarkTheme = isDarkTheme,
                                isAmoledTheme = isAmoledTheme,
                                onThemeChanged = onThemeChanged,
                            )
                        }
                        is DesktopScreen.ArtistProfile -> {
                            val vm = remember(screen.artistId) {
                                component.artistProfileViewModelFactory().create(
                                    artistId = screen.artistId,
                                    name = screen.name,
                                    avatarUrl = screen.avatarUrl,
                                    focusAlbum = screen.focusAlbum,
                                )
                            }
                            DisposableEffect(vm) {
                                onDispose { vm.viewModelScope.cancel() }
                            }
                            CompositionLocalProvider(
                                LocalArtistProfileViewModel provides vm,
                            ) {
                                ArtistProfileScreen(
                                    onBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                    },
                                    onNavigateToAlbum = { album ->
                                        backStack.add(
                                            DesktopScreen.AlbumDiscovery(
                                                browseId = album.id,
                                                title = album.title,
                                                artist = album.artist,
                                                thumbnailUrl = album.thumbnailUrl,
                                                year = album.year,
                                                source = album.source,
                                                qobuzTrackCount = album.trackCount,
                                            ),
                                        )
                                    },
                                    onNavigateToArtist = { id, name, avatar ->
                                        backStack.add(
                                            DesktopScreen.ArtistProfile(
                                                artistId = id,
                                                name = name,
                                                avatarUrl = avatar,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        is DesktopScreen.AlbumDiscovery -> {
                            val vm = remember(screen.browseId, screen.source) {
                                component.albumDiscoveryViewModelFactory().create(
                                    browseId = screen.browseId,
                                    title = screen.title,
                                    artist = screen.artist,
                                    thumbnailUrl = screen.thumbnailUrl,
                                    year = screen.year,
                                    source = screen.source,
                                    qobuzTrackCount = screen.qobuzTrackCount,
                                )
                            }
                            DisposableEffect(vm) {
                                onDispose { vm.viewModelScope.cancel() }
                            }
                            CompositionLocalProvider(
                                LocalAlbumDiscoveryViewModel provides vm,
                            ) {
                                AlbumDiscoveryScreen(
                                    onBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                    },
                                    onNavigateToAlbum = { album ->
                                        backStack.add(
                                            DesktopScreen.AlbumDiscovery(
                                                browseId = album.id,
                                                title = album.title,
                                                artist = album.artist,
                                                thumbnailUrl = album.thumbnailUrl,
                                                year = album.year,
                                                source = album.source,
                                                qobuzTrackCount = album.trackCount,
                                            ),
                                        )
                                    },
                                )
                            }
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
                        currentPositionMs = currentPositionMs,
                        onSeek = { pos -> scope.launch { playerRepository.seekTo(pos) } },
                        lyricsRepository = remember { component.lyricsRepository() },
                        trackDao = component.database().trackDao(),
                        onClose = { showLyricsPanel = false },
                        modifier = Modifier.width(360.dp),
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
                onToggleLike = {
                    val track = playerState.currentTrack ?: return@DesktopBottomPlayerBar
                    val isCurrentlyLiked = track.stashLikedAt != null
                    val newLikedAt = if (isCurrentlyLiked) null else System.currentTimeMillis()
                    scope.launch(Dispatchers.IO) {
                        val persistedId = component.musicRepository().ensureTrackPersisted(track)
                        val dao = component.database().trackDao()
                        if (isCurrentlyLiked) {
                            dao.clearStashLiked(persistedId)
                        } else {
                            dao.markStashLiked(persistedId, newLikedAt ?: System.currentTimeMillis())
                        }
                    }
                    val updated = track.copy(stashLikedAt = newLikedAt)
                    playerRepository.updateCurrentTrack(updated)
                },
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
    onToggleLike: () -> Unit,
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
                IconButton(onClick = onToggleLike) {
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
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
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
                val qualityLabel = when (val fmt = track?.fileFormat?.lowercase()) {
                    "flac" -> {
                        val depth = track.bitsPerSample
                        val rateKhz = track.sampleRateHz?.let { it / 1000 }
                        if (depth != null && rateKhz != null) "FLAC $depth/$rateKhz" else "FLAC"
                    }
                    "wav" -> "WAV"
                    "opus" -> "Opus"
                    "mp3" -> "MP3"
                    "m4a", "aac" -> "AAC"
                    "ogg" -> "OGG"
                    else -> if (track?.isDownloaded == true) fmt?.uppercase() ?: "Local" else "Stream"
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

sealed interface DesktopLyricsState {
    data object Loading : DesktopLyricsState
    data class Synced(val lines: List<LrcLine>) : DesktopLyricsState
    data class Plain(val text: String) : DesktopLyricsState
    data object Instrumental : DesktopLyricsState
    data object None : DesktopLyricsState
    data class Error(val message: String) : DesktopLyricsState
}

@Composable
private fun DesktopLyricsSidePanel(
    currentTrack: Track?,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    lyricsRepository: LyricsRepository,
    trackDao: com.stash.core.data.db.dao.TrackDao,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var retryCount by remember { mutableStateOf(0) }
    var lyricsState by remember(currentTrack?.title, currentTrack?.artist, retryCount) {
        mutableStateOf<DesktopLyricsState>(DesktopLyricsState.Loading)
    }

    LaunchedEffect(currentTrack?.title, currentTrack?.artist, retryCount) {
        val track = currentTrack
        if (track == null) {
            lyricsState = DesktopLyricsState.None
            return@LaunchedEffect
        }

        lyricsState = DesktopLyricsState.Loading

        try {
            val persistedTrackId = withContext(Dispatchers.IO) {
                if (track.id > 0L && trackDao.getById(track.id) != null) {
                    track.id
                } else if (!track.youtubeId.isNullOrBlank()) {
                    trackDao.findByYoutubeId(track.youtubeId!!)?.id ?: 0L
                } else {
                    0L
                }
            }

            val query = LyricsQuery(
                trackId = persistedTrackId,
                title = track.title,
                artist = track.artist,
                album = track.album.ifBlank { null },
                albumArtist = track.albumArtist.ifBlank { null },
                durationMs = track.durationMs.takeIf { it > 0 },
                youtubeVideoId = track.youtubeId,
                isrc = track.isrc?.takeIf { it.isNotBlank() },
                interactive = true,
                onDemand = true,
            )

            // Check local database first if library track
            val localLyrics = if (persistedTrackId > 0L) {
                withContext(Dispatchers.IO) { lyricsRepository.get(persistedTrackId) }
            } else null

            if (localLyrics != null && (localLyrics.instrumental || !localLyrics.syncedLrc.isNullOrBlank() || !localLyrics.plainText.isNullOrBlank())) {
                lyricsState = when {
                    localLyrics.instrumental -> DesktopLyricsState.Instrumental
                    !localLyrics.syncedLrc.isNullOrBlank() -> {
                        val lines = LrcParser.parse(localLyrics.syncedLrc!!)
                        if (lines.isNotEmpty()) DesktopLyricsState.Synced(lines)
                        else if (!localLyrics.plainText.isNullOrBlank()) DesktopLyricsState.Plain(localLyrics.plainText!!)
                        else DesktopLyricsState.None
                    }
                    !localLyrics.plainText.isNullOrBlank() -> DesktopLyricsState.Plain(localLyrics.plainText!!)
                    else -> DesktopLyricsState.None
                }
            } else {
                // Resolve online via lyrics repository
                val result: LyricsResult? = if (persistedTrackId > 0L) {
                    val entity = withContext(Dispatchers.IO) {
                        lyricsRepository.resolveAndStore(query, fresh = (retryCount > 0))
                    }
                    if (entity != null) {
                        LyricsResult(
                            sourceId = entity.source.orEmpty(),
                            plainText = entity.plainText,
                            syncedLrc = entity.syncedLrc,
                            instrumental = entity.instrumental,
                            language = entity.language,
                            sourceLyricsId = entity.sourceLyricsId,
                            ttml = entity.ttml,
                        )
                    } else null
                } else {
                    withContext(Dispatchers.IO) {
                        lyricsRepository.resolveTransient(query, fresh = (retryCount > 0))
                    }
                }

                lyricsState = when {
                    result == null -> DesktopLyricsState.None
                    result.instrumental -> DesktopLyricsState.Instrumental
                    !result.syncedLrc.isNullOrBlank() -> {
                        val lines = LrcParser.parse(result.syncedLrc!!)
                        if (lines.isNotEmpty()) DesktopLyricsState.Synced(lines)
                        else if (!result.plainText.isNullOrBlank()) DesktopLyricsState.Plain(result.plainText!!)
                        else DesktopLyricsState.None
                    }
                    !result.plainText.isNullOrBlank() -> DesktopLyricsState.Plain(result.plainText!!)
                    else -> DesktopLyricsState.None
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            lyricsState = DesktopLyricsState.Error(e.message ?: "Failed to load lyrics")
        }
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = StashSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, StashGlassBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            // Header: Cover + Title/Artist + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentTrack?.albumArtUrl != null) {
                    AsyncImage(
                        model = currentTrack.albumArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack?.title ?: "No track playing",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = currentTrack?.artist ?: "Unknown artist",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Lyrics",
                        tint = Color(0xFF94A3B8),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body
            when (val state = lyricsState) {
                DesktopLyricsState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = StashCyan,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Searching lyrics...",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }
                }
                is DesktopLyricsState.Synced -> {
                    val lines = state.lines
                    val activeIndex = remember(lines, currentPositionMs) {
                        var idx = -1
                        for (i in lines.indices) {
                            if (lines[i].timestampMs <= currentPositionMs) {
                                idx = i
                            } else {
                                break
                            }
                        }
                        idx
                    }

                    val listState = rememberLazyListState()
                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0) {
                            val target = (activeIndex - 2).coerceAtLeast(0)
                            listState.animateScrollToItem(target)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(lines) { index, line ->
                            val isActive = (index == activeIndex)
                            val isPast = (index < activeIndex)
                            val text = line.text.ifBlank { "• • •" }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isActive) StashCyan.copy(alpha = 0.14f)
                                        else Color.Transparent,
                                    )
                                    .clickable { onSeek(line.timestampMs) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = text,
                                    fontSize = if (isActive) 16.sp else 14.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isActive -> StashCyan
                                        isPast -> Color.White.copy(alpha = 0.85f)
                                        else -> Color(0xFF64748B)
                                    },
                                    lineHeight = 22.sp,
                                )
                            }
                        }
                    }
                }
                is DesktopLyricsState.Plain -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.text.lines()) { line ->
                            Text(
                                text = line.ifBlank { " " },
                                fontSize = 14.sp,
                                color = Color(0xFFE2E8F0),
                                lineHeight = 22.sp,
                            )
                        }
                    }
                }
                DesktopLyricsState.Instrumental -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = StashCyan,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Instrumental Track",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This track does not have lyrics.",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }
                }
                DesktopLyricsState.None,
                is DesktopLyricsState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (state is DesktopLyricsState.Error) "Couldn't load lyrics" else "No lyrics found",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (state is DesktopLyricsState.Error) state.message else "Lyrics are unavailable for this track.",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            TextButton(
                                onClick = { retryCount++ },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = StashCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Try Again", color = StashCyan)
                            }
                        }
                    }
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
