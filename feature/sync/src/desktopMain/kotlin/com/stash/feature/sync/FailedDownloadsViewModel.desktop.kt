package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.blocklist.BlockSource
import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.sync.DownloadQueueTrigger
import com.stash.core.model.DownloadFailureType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

actual open class FailedDownloadsViewModel(
    private val downloadQueueDao: DownloadQueueDao? = null,
    private val trackDao: TrackDao? = null,
    private val downloadQueueTrigger: DownloadQueueTrigger? = null,
    private val blocklistGuard: BlocklistGuard? = null,
) : ViewModel() {

    actual val uiState: StateFlow<FailedDownloadsUiState> =
        if (downloadQueueDao != null) {
            downloadQueueDao.getFailedDownloads()
                .map { rows ->
                    FailedDownloadsUiState(
                        isLoading = false,
                        groups = FailureReasonDisplayOrder.mapNotNull { type ->
                            val groupRows = rows.filter { it.failureType == type }
                            if (groupRows.isEmpty()) null
                            else FailedDownloadsGroup(type, groupRows)
                        },
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = FailedDownloadsUiState(),
                )
        } else {
            MutableStateFlow(FailedDownloadsUiState(isLoading = false))
        }

    actual fun retry(queueId: Long) {
        viewModelScope.launch {
            val claimed = downloadQueueDao?.atomicallyClaimForRetry(queueId) ?: 0
            if (claimed == 1) downloadQueueTrigger?.triggerDrain()
        }
    }

    actual fun retryGroup(type: DownloadFailureType) {
        viewModelScope.launch {
            val list = downloadQueueDao?.atomicallyClaimGroupForRetry(type) ?: emptyList()
            if (list.isNotEmpty()) downloadQueueTrigger?.triggerDrain()
        }
    }

    actual fun retryAll() {
        viewModelScope.launch {
            val list = downloadQueueDao?.atomicallyClaimAllForRetry() ?: emptyList()
            if (list.isNotEmpty()) downloadQueueTrigger?.triggerDrain()
        }
    }

    actual fun block(trackId: Long) {
        viewModelScope.launch {
            val track = trackDao?.getById(trackId) ?: return@launch
            blocklistGuard?.block(track, BlockSource.FAILED_DOWNLOADS)
        }
    }
}
