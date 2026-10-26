package com.stash.desktop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stash.core.common.ArtUrlUpgrader
import com.stash.core.data.discovery.GenreCatalog
import com.stash.core.model.Playlist
import com.stash.core.model.PlaylistType
import com.stash.core.model.Track
import com.stash.core.ui.components.AlbumSquareCard
import com.stash.core.ui.components.CardRail
import com.stash.core.ui.components.CrispChipRow
import com.stash.core.ui.components.DiscoverHeroCard
import com.stash.core.ui.components.SectionHeader
import com.stash.core.ui.components.streaming.StreamingModeChip
import com.stash.core.ui.theme.StashTheme
import com.stash.data.ytmusic.model.AlbumSummary

@Composable
fun DesktopHomeScreen(
    viewModel: DesktopHomeViewModel,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToAlbumDiscovery: (AlbumSummary) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val extendedColors = StashTheme.extendedColors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ── 1. Top Header Bar ───────────────────────────────────────────────
        item(key = "home_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Your mixes, library highlights, and new music discovery",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StreamingModeChip(
                        streamingEnabled = state.streamingEnabled,
                        onClick = viewModel::toggleStreamingMode,
                    )
                    IconButton(
                        onClick = viewModel::refreshDiscovery,
                        modifier = Modifier
                            .size(38.dp)
                            .background(extendedColors.glassBackground, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Discovery",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // ── 2. Hero Card (Daily Discover, Quick Mix, or Welcome Banner) ─────
        item(key = "home_hero") {
            val daily = state.dailyDiscover
            val fallbackHeroTrack = state.mostPlayed.firstOrNull() ?: state.recentlyAdded.firstOrNull()
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                when {
                    daily != null -> {
                        DiscoverHeroCard(
                            label = "Updated daily",
                            title = daily.name,
                            subtitle = "${daily.trackCount} tracks tailored for you",
                            artUrl = daily.artUrl,
                            onPlay = { viewModel.playPlaylist(daily.id) },
                            onOpen = { onNavigateToPlaylist(daily.id) },
                        )
                    }
                    fallbackHeroTrack != null -> {
                        DiscoverHeroCard(
                            label = "Jump back in",
                            title = "Your Quick Mix",
                            subtitle = "Featuring ${fallbackHeroTrack.artist} and your recent favorites",
                            artUrl = fallbackHeroTrack.albumArtUrl ?: fallbackHeroTrack.albumArtPath,
                            onPlay = { viewModel.playQuickMix() },
                            onOpen = { viewModel.playQuickMix() },
                        )
                    }
                    else -> {
                        WelcomeDesktopHeroCard(
                            onExploreSearch = onNavigateToSearch,
                            onConnectSync = onNavigateToSync,
                        )
                    }
                }
            }
        }

        // ── 3. Quick-Access Pinned & Top Playlists Grid ─────────────────────
        if (state.quickAccessPlaylists.isNotEmpty()) {
            item(key = "quick_access_playlists") {
                Spacer(Modifier.height(16.dp))
                SectionHeader(title = "Your Playlists")
                QuickAccessPlaylistsGrid(
                    playlists = state.quickAccessPlaylists,
                    onOpenPlaylist = onNavigateToPlaylist,
                    onPlayPlaylist = viewModel::playPlaylist,
                )
            }
        }

        // ── 4. Stash Mixes: Made For You / Radios / Mood & Decades ──────────
        if (state.madeForYouMixes.isNotEmpty()) {
            item(key = "made_for_you") {
                CardRail(title = "Made For You") {
                    items(state.madeForYouMixes, key = { it.id }) { mix ->
                        PlaylistItemSquareCard(
                            title = mix.name,
                            subtitle = "${mix.trackCount} tracks",
                            artUrl = mix.artUrl,
                            onClick = { onNavigateToPlaylist(mix.id) },
                        )
                    }
                }
            }
        }

        if (state.radioMixes.isNotEmpty()) {
            item(key = "radios") {
                CardRail(title = "Radios") {
                    items(state.radioMixes, key = { it.id }) { mix ->
                        PlaylistItemSquareCard(
                            title = mix.name,
                            subtitle = "${mix.trackCount} tracks",
                            artUrl = mix.artUrl,
                            onClick = { onNavigateToPlaylist(mix.id) },
                        )
                    }
                }
            }
        }

        if (state.moodAndDecadeMixes.isNotEmpty()) {
            item(key = "mood_decades") {
                CardRail(title = "Mood & Decades") {
                    items(state.moodAndDecadeMixes, key = { it.id }) { mix ->
                        PlaylistItemSquareCard(
                            title = mix.name,
                            subtitle = "${mix.trackCount} tracks",
                            artUrl = mix.artUrl,
                            onClick = { onNavigateToPlaylist(mix.id) },
                        )
                    }
                }
            }
        }

        if (state.stashMixes.isNotEmpty()) {
            item(key = "stash_mixes") {
                CardRail(title = "Your Stash Mixes") {
                    items(state.stashMixes, key = { it.id }) { mix ->
                        PlaylistItemSquareCard(
                            title = mix.name,
                            subtitle = "${mix.trackCount} tracks",
                            artUrl = mix.artUrl,
                            onClick = { onNavigateToPlaylist(mix.id) },
                        )
                    }
                }
            }
        }

        // ── 5. Recently Added & Most Played Rails ───────────────────────────
        if (state.recentlyAdded.isNotEmpty()) {
            item(key = "recently_added") {
                CardRail(title = "Recently Added") {
                    itemsIndexed(state.recentlyAdded, key = { _, t -> "recent_${t.id}" }) { index, track ->
                        TrackSquareCard(
                            track = track,
                            onClick = { viewModel.playTrackList(state.recentlyAdded, index) },
                        )
                    }
                }
            }
        }

        if (state.mostPlayed.isNotEmpty()) {
            item(key = "most_played") {
                CardRail(title = "Most Played") {
                    itemsIndexed(state.mostPlayed, key = { _, t -> "most_${t.id}" }) { index, track ->
                        TrackSquareCard(
                            track = track,
                            onClick = { viewModel.playTrackList(state.mostPlayed, index) },
                        )
                    }
                }
            }
        }

        // ── 6. Genre Discovery Chips + New Releases & Top Albums Rails ──────
        item(key = "genre_chips") {
            Spacer(Modifier.height(20.dp))
            SectionHeader(title = "Discover Music")
            CrispChipRow(
                chips = GenreCatalog.GENRES.map { it.label },
                selected = state.selectedGenre,
                onSelect = viewModel::selectGenre,
            )
        }

        if (state.isDiscoveryLoading && state.newReleases.isEmpty() && state.topAlbums.isEmpty()) {
            item(key = "discovery_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            if (state.newReleases.isNotEmpty()) {
                item(key = "new_releases") {
                    CardRail(title = "New Releases") {
                        items(state.newReleases, key = { "new_${it.id}" }) { album ->
                            AlbumSquareCard(
                                title = album.title,
                                artist = album.artist,
                                thumbnailUrl = album.thumbnailUrl,
                                year = album.year,
                                onClick = { onNavigateToAlbumDiscovery(album) },
                            )
                        }
                    }
                }
            }

            if (state.topAlbums.isNotEmpty()) {
                item(key = "top_albums") {
                    CardRail(title = "Top Albums") {
                        items(state.topAlbums, key = { "top_${it.id}" }) { album ->
                            AlbumSquareCard(
                                title = album.title,
                                artist = album.artist,
                                thumbnailUrl = album.thumbnailUrl,
                                year = album.year,
                                onClick = { onNavigateToAlbumDiscovery(album) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeDesktopHeroCard(
    onExploreSearch: () -> Unit,
    onConnectSync: () -> Unit,
) {
    val extendedColors = StashTheme.extendedColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = extendedColors.glassBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, extendedColors.glassBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WELCOME TO STASH",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "High-Resolution Streaming & Library Sync",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Search and stream any song or album right away, or connect Spotify & YouTube Music in Sync to import your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onExploreSearch,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Explore Music")
                    }
                    OutlinedButton(
                        onClick = onConnectSync,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sync Library")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAccessPlaylistsGrid(
    playlists: List<Playlist>,
    onOpenPlaylist: (Long) -> Unit,
    onPlayPlaylist: (Long) -> Unit,
) {
    val extendedColors = StashTheme.extendedColors
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        val columns = when {
            maxWidth >= 960.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        val rows = playlists.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { playlist ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenPlaylist(playlist.id) },
                            color = extendedColors.elevatedSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, extendedColors.glassBorder),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val art = ArtUrlUpgrader.upgrade(playlist.artUrl)
                                    if (art != null) {
                                        AsyncImage(
                                            model = art,
                                            contentDescription = playlist.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (playlist.type == PlaylistType.LIKED_SONGS) {
                                                Icons.Default.Favorite
                                            } else {
                                                Icons.Default.MusicNote
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                ) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${playlist.trackCount} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                IconButton(
                                    onClick = { onPlayPlaylist(playlist.id) },
                                    modifier = Modifier.padding(end = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play ${playlist.name}",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistItemSquareCard(
    title: String,
    subtitle: String,
    artUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val art = ArtUrlUpgrader.upgrade(artUrl)
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrackSquareCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val art = ArtUrlUpgrader.upgrade(track.albumArtPath ?: track.albumArtUrl)
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

