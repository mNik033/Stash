package com.stash.core.ui.ambient

import coil3.Image
import coil3.toBitmap

actual fun readImagePalette(image: Image, key: String): AmbientPalette? =
    try {
        readBitmapPalette(image.toBitmap(), key)
    } catch (_: Exception) {
        null
    }
