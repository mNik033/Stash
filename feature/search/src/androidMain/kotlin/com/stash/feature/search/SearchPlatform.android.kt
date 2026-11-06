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

@Composable
actual fun moodBrowseViewModel(): MoodBrowseViewModel = hiltViewModel<AndroidMoodBrowseViewModel>()

@DrawableRes
fun socialIconFor(kind: String): Int? = when (kind.lowercase()) {
    "instagram" -> R.drawable.ic_social_instagram
    "x" -> R.drawable.ic_social_x
    "tiktok" -> R.drawable.ic_social_tiktok
    "youtube" -> R.drawable.ic_social_youtube
    "facebook" -> R.drawable.ic_social_facebook
    "soundcloud" -> com.stash.core.ui.R.drawable.ic_brand_soundcloud
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

@Composable
actual fun monthlyListenersLabel(count: Long?): String {
    if (count == null) return ""
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
    val short = remember(count, locale) { shortMonthlyListeners(count, locale) }
    return androidx.compose.ui.res.pluralStringResource(
        R.plurals.artist_monthly_listeners,
        short.shown.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        short.text,
    )
}

internal actual fun shortMonthlyListeners(count: Long, locale: java.util.Locale): ShortCount {
    val format = android.icu.text.CompactDecimalFormat.getInstance(locale, android.icu.text.CompactDecimalFormat.CompactStyle.SHORT)
    format.minimumSignificantDigits = 1
    format.maximumSignificantDigits = 3
    val separator = format.decimalFormatSymbols.decimalSeparator
    val wholeDigits = format.format(count).substringBefore(separator).count(Char::isDigit)
    val digits = maxOf(wholeDigits, 2)
    format.maximumSignificantDigits = digits
    val shown = java.math.BigDecimal.valueOf(count).round(java.math.MathContext(digits, java.math.RoundingMode.HALF_EVEN)).toLong()
    return ShortCount(text = format.format(count), shown = shown)
}
