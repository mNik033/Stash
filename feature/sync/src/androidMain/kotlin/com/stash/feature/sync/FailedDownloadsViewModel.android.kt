package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.blocklist.BlockSource
import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.sync.SingleTrackDownloadEnqueuer
import com.stash.core.model.DownloadFailureType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
actual open class FailedDownloadsViewModel @Inject constructor(
    private val downloadQueueDao: DownloadQueueDao,
    private val trackDao: TrackDao,
    private val downloadEnqueuer: SingleTrackDownloadEnqueuer,
    private val blocklistGuard: BlocklistGuard,
) : ViewModel() {

    actual val uiState: StateFlow<FailedDownloadsUiState> =
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

    actual fun retry(queueId: Long) {
        viewModelScope.launch {
            val claimed = downloadQueueDao.atomicallyClaimForRetry(queueId)
            if (claimed == 1) downloadEnqueuer.enqueue(queueId)
        }
    }

    actual fun retryGroup(type: DownloadFailureType) {
        viewModelScope.launch {
            downloadQueueDao.atomicallyClaimGroupForRetry(type)
                .forEach { downloadEnqueuer.enqueue(it) }
        }
    }

    actual fun retryAll() {
        viewModelScope.launch {
            downloadQueueDao.atomicallyClaimAllForRetry()
                .forEach { downloadEnqueuer.enqueue(it) }
        }
    }

    actual fun block(trackId: Long) {
        viewModelScope.launch {
            val track = trackDao.getById(trackId) ?: return@launch
            blocklistGuard.block(track, BlockSource.FAILED_DOWNLOADS)
        }
    }
}
