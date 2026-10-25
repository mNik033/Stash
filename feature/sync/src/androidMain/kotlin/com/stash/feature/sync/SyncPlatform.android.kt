package com.stash.feature.sync

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
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
