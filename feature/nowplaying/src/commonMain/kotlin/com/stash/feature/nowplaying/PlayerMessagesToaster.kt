package com.stash.feature.nowplaying

import androidx.compose.runtime.Composable

/**
 * Toasts the player's one-shot confirmations ([NowPlayingViewModel.userMessages]: "Playing next",
 * "Saved to …", "Downloading…") once, app-wide.
 */
@Composable
expect fun PlayerMessagesToaster(viewModel: NowPlayingViewModel = nowPlayingViewModel())
