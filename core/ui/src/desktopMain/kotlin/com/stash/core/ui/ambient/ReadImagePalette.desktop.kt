package com.stash.core.ui.ambient

import coil3.Image
import coil3.toBitmap

actual fun readImagePalette(image: Image, key: String): AmbientPalette? =
    try {
        val bitmap = image.toBitmap()
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) {
            null
        } else {
            val px = IntArray(32 * 32)
            for (y in 0 until 32) {
                val srcY = (y * h) / 32
                val rowOffset = y * 32
                for (x in 0 until 32) {
                    val srcX = (x * w) / 32
                    px[rowOffset + x] = bitmap.getColor(srcX, srcY)
                }
            }
            readCoverPalette(px, key) ?: AmbientPalette.Default
        }
    } catch (_: Exception) {
        null
    }
