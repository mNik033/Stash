package com.stash.feature.nowplaying

import androidx.compose.runtime.Composable
import com.stash.feature.nowplaying.cast.CastViewModel
import com.stash.feature.nowplaying.listen.ListenTogetherViewModel
import com.stash.feature.nowplaying.ui.SignalPathViewModel

@Composable
expect fun nowPlayingViewModel(): NowPlayingViewModel

@Composable
expect fun listenTogetherViewModel(): ListenTogetherViewModel

@Composable
expect fun castViewModel(): CastViewModel

@Composable
expect fun signalPathViewModel(): SignalPathViewModel

@Composable
expect fun rememberTouchExplorationEnabled(): Boolean

@Composable
expect fun platformShowToast(): (String) -> Unit

@Composable
expect fun platformShareInvite(): (String) -> Unit

@Composable
expect fun platformCopyInvite(): (String) -> Unit

@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun KeepScreenOn(enabled: Boolean)

@Composable
expect fun rememberScreenWidthDp(): Float

@Composable
expect fun platformAnnounce(): (String) -> Unit
