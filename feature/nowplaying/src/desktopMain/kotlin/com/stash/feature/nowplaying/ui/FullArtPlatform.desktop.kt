package com.stash.feature.nowplaying.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.toBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image as SkiaImage
import kotlin.math.roundToInt

internal actual fun supportsBlurEffect(): Boolean = true

@Composable
internal actual fun LightStatusBarIcons(on: Boolean) {}

internal actual fun processPageImage(image: Image): ProcessedPageImages {
    val skiaBitmap = image.toBitmap()
    val imageBitmap = skiaBitmap.asComposeImageBitmap()
    val w = BLEND_DECODE_PX
    val h = (BLEND_DECODE_PX * skiaBitmap.height.toFloat() / skiaBitmap.width).roundToInt().coerceAtLeast(1)

    val scaled = Bitmap().apply {
        allocN32Pixels(w, h)
    }
    val srcImage = SkiaImage.makeFromBitmap(skiaBitmap)
    val canvas = org.jetbrains.skia.Canvas(scaled)
    canvas.drawImageRect(
        srcImage,
        org.jetbrains.skia.Rect.makeWH(skiaBitmap.width.toFloat(), skiaBitmap.height.toFloat()),
        org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()),
        org.jetbrains.skia.SamplingMode.LINEAR,
        null,
        true
    )

    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0
    for (y in (scaled.height - 2).coerceAtLeast(0) until scaled.height) {
        for (x in 0 until scaled.width) {
            val p = scaled.getColor(x, y)
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            n++
        }
    }
    val solid = if (n == 0) Color.Black else {
        val k = SOLID_BLEND_DARKEN / 255f
        Color(r / n * k, g / n * k, b / n * k)
    }
    return ProcessedPageImages(imageBitmap, scaled.asComposeImageBitmap(), solid)
}

