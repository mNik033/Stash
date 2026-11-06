package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import com.stash.core.data.db.dao.UnmatchedTrackView
import com.stash.core.media.preview.PreviewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

expect open class FailedMatchesViewModel : ViewModel {
    val uiState: StateFlow<FailedMatchesUiState>
    val previewState: StateFlow<PreviewState>
    val approvedDownloads: StateFlow<ApprovedDownloads>
    val userMessages: Flow<String>
    val manualSearch: StateFlow<ManualSearch?>

    fun resync()
    fun approveAll()
    fun dismissAll()
    fun previewRejectedMatch(videoId: String)
    fun stopPreview()
    fun approveMatch(trackId: Long, queueEntryId: Long, candidate: ResyncCandidate, userPicked: Boolean = false)
    fun approveSwap(row: FlaggedTrackRow, candidate: ResyncCandidate)
    fun unflagTrack(trackId: Long)
    fun dismissTrack(trackId: Long)
    fun openFinderFor(trackId: Long)
    fun toggleManualSearch(trackId: Long, artist: String, title: String)
    fun searchManually(query: String)
    fun pickManualMatch(candidate: ResyncCandidate)
    fun closeManualSearch()
}
