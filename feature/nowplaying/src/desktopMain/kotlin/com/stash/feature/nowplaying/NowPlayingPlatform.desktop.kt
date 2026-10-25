package com.stash.feature.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.stash.core.common.Log
import com.stash.core.ui.components.rememberTextSharer
import com.stash.feature.nowplaying.cast.CastViewModel
import com.stash.feature.nowplaying.listen.ListenTogetherViewModel
import com.stash.feature.nowplaying.ui.SignalPathViewModel

val LocalNowPlayingViewModel = staticCompositionLocalOf<NowPlayingViewModel?> { null }
val LocalListenTogetherViewModel = staticCompositionLocalOf<ListenTogetherViewModel?> { null }
val LocalCastViewModel = staticCompositionLocalOf<CastViewModel?> { null }
val LocalSignalPathViewModel = staticCompositionLocalOf<SignalPathViewModel?> { null }

@Composable
actual fun nowPlayingViewModel(): NowPlayingViewModel {
    return LocalNowPlayingViewModel.current
        ?: error("LocalNowPlayingViewModel not provided in Desktop composition hierarchy")
}

@Composable
actual fun listenTogetherViewModel(): ListenTogetherViewModel {
    return LocalListenTogetherViewModel.current
        ?: error("LocalListenTogetherViewModel not provided in Desktop composition hierarchy")
}

@Composable
actual fun castViewModel(): CastViewModel {
    return LocalCastViewModel.current
        ?: error("LocalCastViewModel not provided in Desktop composition hierarchy")
}

@Composable
actual fun signalPathViewModel(): SignalPathViewModel {
    return LocalSignalPathViewModel.current
        ?: error("LocalSignalPathViewModel not provided in Desktop composition hierarchy")
}

@Composable
actual fun rememberTouchExplorationEnabled(): Boolean = false

@Composable
actual fun platformShowToast(): (String) -> Unit {
    return { msg: String -> Log.i("Toast", msg) }
}

@Composable
actual fun platformShareInvite(): (String) -> Unit {
    val sharer = rememberTextSharer()
    return { url: String -> sharer("Listen with me on Stash: $url", "Invite friends") }
}

@Composable
actual fun platformCopyInvite(): (String) -> Unit {
    val sharer = rememberTextSharer()
    return { url: String -> sharer(url, "Copy link") }
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop: no-op (Esc can be handled by window if needed)
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    // Desktop: display sleep handling managed by OS / window
}

@Composable
actual fun rememberScreenWidthDp(): Float {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    return with(density) { windowInfo.containerSize.width.toDp().value }
}

@Composable
actual fun platformAnnounce(): (String) -> Unit {
    return { msg: String -> Log.i("A11y", msg) }
}
