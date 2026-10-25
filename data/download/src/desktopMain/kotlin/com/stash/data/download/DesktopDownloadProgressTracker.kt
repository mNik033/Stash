package com.stash.data.download

import com.stash.data.download.model.DownloadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopDownloadProgressTracker @Inject constructor() : DownloadProgressTracker {
    override val progress: Flow<DownloadProgress> = emptyFlow()
}
