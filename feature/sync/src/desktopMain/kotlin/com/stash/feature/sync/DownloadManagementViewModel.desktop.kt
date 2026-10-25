package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual open class DownloadManagementViewModel : ViewModel() {
    actual val uiState: StateFlow<DownloadManagementUiState> =
        MutableStateFlow(DownloadManagementUiState(isLoading = false))

    actual fun selectTab(tab: DownloadManagementTab) {}
    actual fun cancel(queueId: Long) {}
    actual fun retry(queueId: Long) {}
    actual fun dismissMessage() {}
}
