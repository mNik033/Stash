package com.stash.feature.nowplaying

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PlayerMessagesToaster(viewModel: NowPlayingViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.userMessages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
