package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.db.dao.DownloadManagementRow
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.sync.DownloadQueueTrigger
import com.stash.core.model.DownloadFailureType
import com.stash.core.model.DownloadStatus as QueueStatus
import com.stash.core.model.DownloadWaitReason
import com.stash.data.download.DownloadProgressTracker
import com.stash.data.download.model.DownloadProgress
import com.stash.data.download.model.DownloadStatus as PipelineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

actual open class DownloadManagementViewModel(
    private val downloadQueueDao: DownloadQueueDao? = null,
    private val downloadProgressTracker: DownloadProgressTracker? = null,
    private val downloadQueueTrigger: DownloadQueueTrigger? = null,
) : ViewModel() {
    private val selectedTab = MutableStateFlow(DownloadManagementTab.QUEUE)
    private val progressByTrackId = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    private val message = MutableStateFlow<String?>(null)

    private val historyItems = downloadQueueDao?.observeDownloadHistory(100)
        ?.map { rows -> rows.map { it.toItem(null) } }
        ?: flowOf(emptyList())

    actual val uiState: StateFlow<DownloadManagementUiState> = if (downloadQueueDao != null) {
        combine(
            downloadQueueDao.observeActiveDownloadRows(),
            historyItems,
            selectedTab,
            progressByTrackId,
            message,
        ) { active, history, tab, progress, currentMessage ->
            DownloadManagementUiState(
                isLoading = false,
                selectedTab = tab,
                active = active.map { it.toItem(progress[it.trackId]) },
                history = history,
                message = currentMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DownloadManagementUiState(),
        )
    } else {
        MutableStateFlow(DownloadManagementUiState(isLoading = false))
    }

    init {
        if (downloadProgressTracker != null) {
            viewModelScope.launch {
                downloadProgressTracker.progress.collect { update ->
                    progressByTrackId.value = when (update.status) {
                        PipelineStatus.COMPLETED,
                        PipelineStatus.FAILED,
                        PipelineStatus.UNMATCHED,
                        PipelineStatus.DEFERRED,
                        -> progressByTrackId.value - update.trackId
                        else -> progressByTrackId.value + (update.trackId to update)
                    }
                }
            }
        }
    }

    actual fun selectTab(tab: DownloadManagementTab) {
        selectedTab.value = tab
    }

    actual fun cancel(queueId: Long) {
        viewModelScope.launch {
            val count = downloadQueueDao?.markCancelledIfActive(queueId, System.currentTimeMillis()) ?: 0
            message.value = if (count > 0) "Download cancelled" else "Download could not be cancelled"
        }
    }

    actual fun clearQueue() {
        viewModelScope.launch {
            val dao = downloadQueueDao ?: return@launch
            val now = System.currentTimeMillis()
            val cancelled = dao.activeIds().count { dao.markCancelledIfActive(it, now) > 0 }
            message.value = if (cancelled == 0) "Nothing to cancel" else "Cancelled $cancelled ${if (cancelled == 1) "download" else "downloads"}"
        }
    }

    actual fun clearHistory() {
        viewModelScope.launch {
            downloadQueueDao?.deleteCompleted()
            message.value = "History cleared"
        }
    }

    actual fun retry(queueId: Long) {
        viewModelScope.launch {
            if (downloadQueueDao?.atomicallyClaimForRetry(queueId) == 1) {
                downloadQueueTrigger?.triggerDrain()
                message.value = "Download queued"
            }
        }
    }

    actual fun dismissMessage() {
        message.value = null
    }
}

private fun DownloadManagementRow.toItem(progress: DownloadProgress?): DownloadManagementItem {
    val pipeline = progress?.status
    val waitReason = DownloadWaitReason.fromCode(errorMessage).takeIf { status == QueueStatus.WAITING_FOR_LOSSLESS }
    return DownloadManagementItem(
        queueId = queueId,
        trackId = trackId,
        title = title,
        artist = artist,
        albumArtUrl = albumArtUrl,
        playlistName = album.takeIf(String::isNotBlank),
        status = status,
        progress = progress?.progress?.coerceIn(0f, 1f),
        phaseLabel = when {
            status == QueueStatus.SKIPPED -> "Cancelled"
            pipeline == PipelineStatus.MATCHING -> "Finding a match"
            pipeline == PipelineStatus.DOWNLOADING -> "Downloading"
            pipeline == PipelineStatus.PROCESSING -> "Processing"
            pipeline == PipelineStatus.TAGGING -> "Adding metadata"
            status == QueueStatus.IN_PROGRESS -> "In progress"
            status == QueueStatus.PENDING -> "Queued"
            status == QueueStatus.WAITING_FOR_LOSSLESS -> waitLabel(waitReason)
            status == QueueStatus.COMPLETED -> "Downloaded"
            status == QueueStatus.FAILED && failureType == DownloadFailureType.NO_MATCH -> "Needs review"
            status == QueueStatus.FAILED -> "Failed"
            else -> status.name.lowercase().replaceFirstChar(Char::titlecase)
        },
        createdAt = createdAt,
        completedAt = completedAt,
        errorMessage = errorMessage.takeIf { waitReason == null },
        waitReason = waitReason,
        action = when {
            status == QueueStatus.PENDING ||
                status == QueueStatus.IN_PROGRESS ||
                status == QueueStatus.WAITING_FOR_LOSSLESS -> DownloadManagementAction.CANCEL
            status == QueueStatus.SKIPPED -> DownloadManagementAction.RETRY
            status == QueueStatus.FAILED && failureType != DownloadFailureType.NO_MATCH -> DownloadManagementAction.RETRY
            else -> DownloadManagementAction.NONE
        },
    )
}
