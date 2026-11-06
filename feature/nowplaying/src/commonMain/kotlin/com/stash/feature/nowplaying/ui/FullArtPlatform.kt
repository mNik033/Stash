package com.stash.feature.nowplaying.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image

internal const val BLEND_DECODE_PX = 48
internal const val SOLID_BLEND_DARKEN = 0.62f

internal data class ProcessedPageImages(
    val image: ImageBitmap,
    val small: ImageBitmap,
    val solid: Color,
)

internal expect fun processPageImage(image: Image): ProcessedPageImages

internal expect fun supportsBlurEffect(): Boolean

@Composable
internal expect fun LightStatusBarIcons(on: Boolean)

