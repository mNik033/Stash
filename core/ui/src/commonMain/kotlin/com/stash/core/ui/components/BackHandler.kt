package com.stash.core.ui.components

import androidx.compose.runtime.Composable

/**
 * Multiplatform system back button handler.
 * On Android, intercepts the system back press / gesture.
 * On Desktop, acts as a no-op.
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
