package com.stash.feature.sync

import androidx.compose.runtime.Composable

@Composable
expect fun syncViewModel(): SyncViewModel

@Composable
expect fun downloadManagementViewModel(): DownloadManagementViewModel

@Composable
expect fun failedDownloadsViewModel(): FailedDownloadsViewModel

@Composable
expect fun failedMatchesViewModel(): FailedMatchesViewModel

expect fun formatRelativeTime(timestampMs: Long): String
