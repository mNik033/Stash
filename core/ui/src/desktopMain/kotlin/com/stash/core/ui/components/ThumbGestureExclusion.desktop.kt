package com.stash.core.ui.components

import androidx.compose.ui.Modifier

internal actual fun Modifier.thumbGestureExclusion(
    thumbFraction: Float,
    progress: Float,
    thumbHeightOffset: Float,
    grabTolerancePx: Float,
): Modifier = this
