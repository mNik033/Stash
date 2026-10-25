package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import com.stash.core.model.DownloadFailureType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual open class FailedDownloadsViewModel : ViewModel() {
    actual val uiState: StateFlow<FailedDownloadsUiState> =
        MutableStateFlow(FailedDownloadsUiState(isLoading = false))

    actual fun retry(queueId: Long) {}
    actual fun retryGroup(type: DownloadFailureType) {}
    actual fun retryAll() {}
    actual fun block(trackId: Long) {}
}
