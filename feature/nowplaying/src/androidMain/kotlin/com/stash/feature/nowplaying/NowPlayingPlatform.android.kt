package com.stash.feature.nowplaying

import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.stash.feature.nowplaying.cast.AndroidCastViewModel
import com.stash.feature.nowplaying.cast.CastViewModel
import com.stash.feature.nowplaying.listen.AndroidListenTogetherViewModel
import com.stash.feature.nowplaying.listen.ListenTogetherViewModel
import com.stash.feature.nowplaying.listen.copyInvite
import com.stash.feature.nowplaying.listen.shareInvite
import com.stash.feature.nowplaying.ui.AndroidSignalPathViewModel
import com.stash.feature.nowplaying.ui.SignalPathViewModel

@Composable
actual fun nowPlayingViewModel(): NowPlayingViewModel = hiltViewModel<AndroidNowPlayingViewModel>()

@Composable
actual fun listenTogetherViewModel(): ListenTogetherViewModel = hiltViewModel<AndroidListenTogetherViewModel>()

@Composable
actual fun castViewModel(): CastViewModel = hiltViewModel<AndroidCastViewModel>()

@Composable
actual fun signalPathViewModel(): SignalPathViewModel = hiltViewModel<AndroidSignalPathViewModel>()

@Composable
actual fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        am?.isTouchExplorationEnabled == true
    }
}

@Composable
actual fun platformShowToast(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
actual fun platformShareInvite(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url: String -> shareInvite(context, url) }
    }
}

@Composable
actual fun platformCopyInvite(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url: String -> copyInvite(context, url) }
    }
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val wasKeeping = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = wasKeeping }
    }
}

@Composable
actual fun rememberScreenWidthDp(): Float =
    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()

@Composable
actual fun platformAnnounce(): (String) -> Unit {
    val view = androidx.compose.ui.platform.LocalView.current
    return remember(view) {
        { msg: String ->
            @Suppress("DEPRECATION")
            view.announceForAccessibility(msg)
        }
    }
}
