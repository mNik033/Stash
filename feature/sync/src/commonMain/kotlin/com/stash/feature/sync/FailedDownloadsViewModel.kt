package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import com.stash.core.data.db.dao.FailedDownloadRow
import com.stash.core.model.DownloadFailureType
import kotlinx.coroutines.flow.StateFlow

data class FailedDownloadsGroup(
    val type: DownloadFailureType,
    val rows: List<FailedDownloadRow>,
)

data class FailedDownloadsUiState(
    val isLoading: Boolean = true,
    val groups: List<FailedDownloadsGroup> = emptyList(),
) {
    val totalCount: Int get() = groups.sumOf { it.rows.size }
}

expect open class FailedDownloadsViewModel : ViewModel {
    val uiState: StateFlow<FailedDownloadsUiState>
    fun retry(queueId: Long)
    fun retryGroup(type: DownloadFailureType)
    fun retryAll()
    fun block(trackId: Long)
}
