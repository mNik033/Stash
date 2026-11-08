package com.stash.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import com.stash.core.common.Log
import com.stash.feature.home.shelves.HomeShelvesViewModel

val LocalPlaylistBrowseViewModelFactory = staticCompositionLocalOf<(String) -> PlaylistBrowseViewModel> {
    { genre -> error("PlaylistBrowseViewModel not provided on Desktop") }
}

@Composable
actual fun homeViewModel(): HomeViewModel =
    LocalHomeViewModel.current ?: error("LocalHomeViewModel not provided")

@Composable
actual fun homeShelvesViewModel(): HomeShelvesViewModel =
    LocalHomeShelvesViewModel.current ?: error("LocalHomeShelvesViewModel not provided")

@Composable
actual fun playlistBrowseViewModel(genre: String): PlaylistBrowseViewModel =
    LocalPlaylistBrowseViewModelFactory.current(genre)

@Composable
actual fun rememberWordmarkPainter(): Painter? = null

@Composable
actual fun rememberVinylRecordPainter(): Painter? = null

@Composable
actual fun rememberDiscordPainter(): Painter? = null

@Composable
actual fun HandleUserMessages(viewModel: HomeViewModel) {
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.userMessages.collect { msg ->
            Log.i("Home", msg)
        }
    }
}

