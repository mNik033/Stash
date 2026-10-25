package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import com.stash.core.data.db.dao.UnmatchedTrackView
import com.stash.core.media.preview.PreviewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

actual open class FailedMatchesViewModel : ViewModel() {
    actual val uiState: StateFlow<FailedMatchesUiState> = MutableStateFlow(FailedMatchesUiState())
    actual val previewState: StateFlow<PreviewState> = MutableStateFlow(PreviewState.Idle)
    actual val approvedDownloads: StateFlow<ApprovedDownloads> = MutableStateFlow(ApprovedDownloads())
    actual val userMessages: Flow<String> = emptyFlow()

    actual fun resync() {}
    actual fun approveAll() {}
    actual fun dismissAll() {}
    actual fun previewRejectedMatch(videoId: String) {}
    actual fun stopPreview() {}
    actual fun approveMatch(trackId: Long, queueEntryId: Long, candidate: ResyncCandidate) {}
    actual fun approveSwap(row: FlaggedTrackRow, candidate: ResyncCandidate) {}
    actual fun unflagTrack(trackId: Long) {}
    actual fun dismissTrack(trackId: Long) {}
}
