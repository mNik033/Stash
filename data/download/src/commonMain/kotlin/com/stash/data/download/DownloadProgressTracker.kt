package com.stash.data.download

import com.stash.data.download.model.DownloadProgress
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for observing download progress across all platforms.
 */
interface DownloadProgressTracker {
    val progress: Flow<DownloadProgress>
}
