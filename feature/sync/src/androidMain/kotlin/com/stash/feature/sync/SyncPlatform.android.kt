package com.stash.feature.sync

import android.text.format.DateUtils
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
actual fun syncViewModel(): SyncViewModel = hiltViewModel<SyncViewModel>()

@Composable
actual fun downloadManagementViewModel(): DownloadManagementViewModel = hiltViewModel<DownloadManagementViewModel>()

@Composable
actual fun failedDownloadsViewModel(): FailedDownloadsViewModel = hiltViewModel<FailedDownloadsViewModel>()

@Composable
actual fun failedMatchesViewModel(): FailedMatchesViewModel = hiltViewModel<FailedMatchesViewModel>()

actual fun formatRelativeTime(timestampMs: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        timestampMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

@Composable
internal actual fun SoundCloudMark() {
    androidx.compose.material3.Icon(
        androidx.compose.ui.res.painterResource(com.stash.core.ui.R.drawable.ic_brand_soundcloud),
        contentDescription = null,
        tint = androidx.compose.ui.graphics.Color(0xFFFF5500),
        modifier = androidx.compose.ui.Modifier.size(34.dp),
    )
}
