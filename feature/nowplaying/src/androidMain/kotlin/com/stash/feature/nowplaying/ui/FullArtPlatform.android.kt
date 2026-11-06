package com.stash.feature.nowplaying.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import coil3.Image
import coil3.toBitmap
import kotlin.math.roundToInt

internal actual fun supportsBlurEffect(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
internal actual fun LightStatusBarIcons(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        if (on) controller?.isAppearanceLightStatusBars = false
        onDispose { if (on && before != null) controller?.isAppearanceLightStatusBars = before }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal actual fun processPageImage(image: Image): ProcessedPageImages {
    val bitmap = image.toBitmap()
    val readable = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    val h = (BLEND_DECODE_PX * readable.height.toFloat() / readable.width).roundToInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(readable, BLEND_DECODE_PX, h, true)
    val solid = bottomEdge(small)
    return ProcessedPageImages(bitmap.asImageBitmap(), small.asImageBitmap(), solid)
}

private fun bottomEdge(small: Bitmap): Color {
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0
    for (y in (small.height - 2).coerceAtLeast(0) until small.height) for (x in 0 until small.width) {
        val p = small.getPixel(x, y)
        r += (p shr 16) and 0xFF
        g += (p shr 8) and 0xFF
        b += p and 0xFF
        n++
    }
    if (n == 0) return Color.Black
    val k = SOLID_BLEND_DARKEN / 255f
    return Color(r / n * k, g / n * k, b / n * k)
}

