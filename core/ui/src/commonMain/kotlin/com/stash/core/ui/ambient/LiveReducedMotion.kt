package com.stash.core.ui.ambient

import androidx.compose.runtime.Composable

/**
 * [rememberReducedMotion], kept live: the system "Remove animations" switch is re-read whenever the
 * setting changes, so an endless animation (the radio Signal glyph's breath) stops, or starts, the
 * moment it is flipped, not when the screen is next opened. Watches the setting only while the
 * caller is composed.
 *
 * Stands still under Android's Battery Saver too ([LocalBatterySaverOn], #783), like [rememberReducedMotion].
 */
@Composable
expect fun rememberLiveReducedMotion(): Boolean
