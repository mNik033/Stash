package com.stash.core.ui.components

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

internal actual fun Modifier.thumbGestureExclusion(
    thumbFraction: Float,
    progress: Float,
    thumbHeightOffset: Float,
    grabTolerancePx: Float,
): Modifier = systemGestureExclusion { coords ->
    val trackHeight = coords.size.height.toFloat()
    val thumbHeight = (trackHeight * thumbFraction) + thumbHeightOffset
    val thumbOffsetY = progress * (trackHeight - thumbHeight)
    Rect(
        left = 0f,
        top = (thumbOffsetY - grabTolerancePx).coerceAtLeast(0f),
        right = coords.size.width.toFloat(),
        bottom = (thumbOffsetY + thumbHeight + grabTolerancePx).coerceAtMost(trackHeight),
    )
}
