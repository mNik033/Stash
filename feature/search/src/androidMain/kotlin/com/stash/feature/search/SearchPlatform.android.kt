package com.stash.feature.search

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
actual fun searchViewModel(): SearchViewModel = hiltViewModel<AndroidSearchViewModel>()

@Composable
actual fun artistProfileViewModel(): ArtistProfileViewModel = hiltViewModel<AndroidArtistProfileViewModel>()

@Composable
actual fun albumDiscoveryViewModel(): AlbumDiscoveryViewModel = hiltViewModel<AndroidAlbumDiscoveryViewModel>()

@DrawableRes
fun socialIconFor(kind: String): Int? = when (kind.lowercase()) {
    "instagram" -> R.drawable.ic_social_instagram
    "x" -> R.drawable.ic_social_x
    "tiktok" -> R.drawable.ic_social_tiktok
    "youtube" -> R.drawable.ic_social_youtube
    "facebook" -> R.drawable.ic_social_facebook
    "soundcloud" -> R.drawable.ic_social_soundcloud
    "bandcamp" -> R.drawable.ic_social_bandcamp
    else -> null
}

@Composable
actual fun rememberSocialPainter(kind: String): Painter? {
    val resId = socialIconFor(kind) ?: return null
    return painterResource(resId)
}

@Composable
actual fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current
    val controller = remember(view) {
        view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
    }
    DisposableEffect(controller) {
        val before = controller?.isAppearanceLightStatusBars
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
    SideEffect { controller?.isAppearanceLightStatusBars = !light }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
