package com.stash.feature.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.stash.core.common.Log

@Composable
actual fun PlayerMessagesToaster(viewModel: NowPlayingViewModel) {
    LaunchedEffect(viewModel) {
        viewModel.userMessages.collect { msg ->
            Log.i("PlayerMessagesToaster", msg)
        }
    }
}
