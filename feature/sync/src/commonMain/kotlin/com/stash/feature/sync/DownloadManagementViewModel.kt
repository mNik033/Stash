package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import com.stash.core.model.DownloadWaitReason
import com.stash.core.model.DownloadStatus as QueueStatus
import kotlinx.coroutines.flow.StateFlow

enum class DownloadManagementTab { QUEUE, HISTORY }

enum class DownloadManagementAction { CANCEL, RETRY, NONE }

data class DownloadManagementItem(
    val queueId: Long,
    val trackId: Long,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val playlistName: String?,
    val status: QueueStatus,
    val progress: Float?,
    val phaseLabel: String,
    val createdAt: Long,
    val completedAt: Long?,
    val errorMessage: String?,
    val action: DownloadManagementAction,
    /** Why a waiting row waits (kept on the row), or null: not waiting, or from before reasons were kept. */
    val waitReason: DownloadWaitReason? = null,
)

data class DownloadManagementUiState(
    val isLoading: Boolean = true,
    val selectedTab: DownloadManagementTab = DownloadManagementTab.QUEUE,
    val active: List<DownloadManagementItem> = emptyList(),
    val history: List<DownloadManagementItem> = emptyList(),
    val message: String? = null,
) {
    val downloading: List<DownloadManagementItem>
        get() = active.filter { it.status == QueueStatus.IN_PROGRESS }
    val queued: List<DownloadManagementItem>
        get() = active.filter { it.status == QueueStatus.PENDING }
    val waiting: List<DownloadManagementItem>
        get() = active.filter { it.status == QueueStatus.WAITING_FOR_LOSSLESS }

    /** The waiting section's heading: what they all wait for, or just "Waiting" when it differs. */
    val waitingLabel: String
        get() {
            // A row from before reasons were kept waits for lossless.
            val reasons = waiting.map { it.waitReason ?: DownloadWaitReason.LOSSLESS }.distinct()
            return if (reasons.size > 1) "Waiting" else when (reasons.singleOrNull()) {
                DownloadWaitReason.AAC -> "Not available as AAC yet"
                DownloadWaitReason.RESET -> "Waiting for the daily reset"
                DownloadWaitReason.TIDAL_BUSY -> "Waiting for Tidal"
                DownloadWaitReason.FALLBACK -> "Waiting for Lossy fallback"
                DownloadWaitReason.LOSSLESS, null -> "Waiting for lossless"
            }
        }
}

/** What a waiting row says, by why it waits. */
internal fun waitLabel(reason: DownloadWaitReason?): String = when (reason) {
    DownloadWaitReason.AAC -> "Not available as AAC yet"
    DownloadWaitReason.RESET -> "Waiting for the daily reset"
    DownloadWaitReason.TIDAL_BUSY -> "Tidal is busy. Trying again shortly."
    DownloadWaitReason.FALLBACK -> "Waiting for Lossy fallback to be on"
    DownloadWaitReason.LOSSLESS, null -> "Waiting for a lossless source"
}

expect open class DownloadManagementViewModel : ViewModel {
    val uiState: StateFlow<DownloadManagementUiState>
    fun selectTab(tab: DownloadManagementTab)
    fun cancel(queueId: Long)
    fun retry(queueId: Long)
    fun dismissMessage()
}
