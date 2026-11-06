package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.preview.PreviewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

actual open class FailedMatchesViewModel(
    private val musicRepository: MusicRepository? = null,
    private val trackDao: TrackDao? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FailedMatchesUiState())
    actual val uiState: StateFlow<FailedMatchesUiState> =
        musicRepository?.getUnmatchedTracks()
            ?.map { tracks -> FailedMatchesUiState(tracks = tracks, isLoading = false) }
            ?.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = FailedMatchesUiState(),
            ) ?: _uiState

    actual val previewState: StateFlow<PreviewState> = MutableStateFlow(PreviewState.Idle)
    actual val approvedDownloads: StateFlow<ApprovedDownloads> = MutableStateFlow(ApprovedDownloads())
    actual val userMessages: Flow<String> = emptyFlow()
    actual val manualSearch: StateFlow<ManualSearch?> = MutableStateFlow(null)

    actual fun resync() {}
    actual fun approveAll() {}
    actual fun dismissAll() {}
    actual fun previewRejectedMatch(videoId: String) {}
    actual fun stopPreview() {}
    actual fun approveMatch(trackId: Long, queueEntryId: Long, candidate: ResyncCandidate, userPicked: Boolean) {}
    actual fun approveSwap(row: FlaggedTrackRow, candidate: ResyncCandidate) {}
    actual fun unflagTrack(trackId: Long) {
        viewModelScope.launch {
            trackDao?.updateMatchFlagged(trackId, false)
        }
    }
    actual fun dismissTrack(trackId: Long) {
        viewModelScope.launch {
            musicRepository?.dismissMatch(trackId)
        }
    }
    actual fun openFinderFor(trackId: Long) {}
    actual fun toggleManualSearch(trackId: Long, artist: String, title: String) {}
    actual fun searchManually(query: String) {}
    actual fun pickManualMatch(candidate: ResyncCandidate) {}
    actual fun closeManualSearch() {}
}
