package com.stash.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.stash.core.model.UiScale

/**
 * The largest Display size this window can take (Settings › Appearance), provided once at the app
 * root by `MainActivity`, which works it out from the window's unscaled width before applying the
 * scale. A screen can't work it out itself: by the time it composes, `LocalConfiguration` already
 * reports the scaled width. Static: it only changes when the activity is recreated.
 */
val LocalUiScaleMax = staticCompositionLocalOf { UiScale.MAX }
