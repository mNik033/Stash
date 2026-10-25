package com.stash.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter

val LocalSearchViewModel = staticCompositionLocalOf<SearchViewModel?> { null }
val LocalArtistProfileViewModel = staticCompositionLocalOf<ArtistProfileViewModel?> { null }
val LocalAlbumDiscoveryViewModel = staticCompositionLocalOf<AlbumDiscoveryViewModel?> { null }

@Composable
actual fun searchViewModel(): SearchViewModel =
    LocalSearchViewModel.current ?: error("LocalSearchViewModel not provided")

@Composable
actual fun artistProfileViewModel(): ArtistProfileViewModel =
    LocalArtistProfileViewModel.current ?: error("LocalArtistProfileViewModel not provided")

@Composable
actual fun albumDiscoveryViewModel(): AlbumDiscoveryViewModel =
    LocalAlbumDiscoveryViewModel.current ?: error("LocalAlbumDiscoveryViewModel not provided")

@Composable
actual fun rememberSocialPainter(kind: String): Painter? = null

@Composable
actual fun StatusBarIcons(light: Boolean) {
    // Desktop has no status bar
}
