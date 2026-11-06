package com.stash.feature.nowplaying.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberNpSpacing(): NpSpacing = remember { NpSpacing() }

