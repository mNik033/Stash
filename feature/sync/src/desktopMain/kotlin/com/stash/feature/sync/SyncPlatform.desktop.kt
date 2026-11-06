package com.stash.feature.sync

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalSyncViewModel = staticCompositionLocalOf<SyncViewModel?> { null }
val LocalDownloadManagementViewModel = staticCompositionLocalOf<DownloadManagementViewModel?> { null }
val LocalFailedDownloadsViewModel = staticCompositionLocalOf<FailedDownloadsViewModel?> { null }
val LocalFailedMatchesViewModel = staticCompositionLocalOf<FailedMatchesViewModel?> { null }

@Composable
actual fun syncViewModel(): SyncViewModel =
    LocalSyncViewModel.current ?: remember { SyncViewModel() }

@Composable
actual fun downloadManagementViewModel(): DownloadManagementViewModel =
    LocalDownloadManagementViewModel.current ?: remember { DownloadManagementViewModel() }

@Composable
actual fun failedDownloadsViewModel(): FailedDownloadsViewModel =
    LocalFailedDownloadsViewModel.current ?: remember { FailedDownloadsViewModel() }

@Composable
actual fun failedMatchesViewModel(): FailedMatchesViewModel =
    LocalFailedMatchesViewModel.current ?: remember { FailedMatchesViewModel() }

actual fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        diff < 60_000 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}

@Composable
internal actual fun SoundCloudMark() {
    androidx.compose.material3.Icon(
        Icons.Filled.PlayArrow,
        contentDescription = null,
        tint = androidx.compose.ui.graphics.Color(0xFFFF5500),
        modifier = androidx.compose.ui.Modifier.size(34.dp),
    )
}
