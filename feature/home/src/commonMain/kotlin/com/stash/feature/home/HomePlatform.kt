package com.stash.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import com.stash.feature.home.shelves.HomeShelvesViewModel

val LocalHomeViewModel = staticCompositionLocalOf<HomeViewModel?> { null }
val LocalHomeShelvesViewModel = staticCompositionLocalOf<HomeShelvesViewModel?> { null }

@Composable
expect fun homeViewModel(): HomeViewModel

@Composable
expect fun homeShelvesViewModel(): HomeShelvesViewModel

@Composable
expect fun playlistBrowseViewModel(genre: String): PlaylistBrowseViewModel

@Composable
expect fun rememberWordmarkPainter(): Painter?

@Composable
expect fun rememberVinylRecordPainter(): Painter?

@Composable
expect fun rememberDiscordPainter(): Painter?

@Composable
expect fun HandleUserMessages(viewModel: HomeViewModel)

object HomeStrings {
    const val NEW_FROM_ARTISTS = "New from your artists"
    const val BECAUSE_YOU_LIKE = "Because you like"
    fun becauseYouLikeSong(song: String) = "Because you like $song"
    fun songCard(title: String, artist: String) = "$title by $artist"
}

