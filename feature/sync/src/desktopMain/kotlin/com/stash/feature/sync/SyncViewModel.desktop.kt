package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import com.stash.core.data.mix.LastFmRecommendationState
import com.stash.core.data.sync.AuthExpiryState
import com.stash.core.data.sync.SyncLog
import com.stash.core.model.SyncMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual open class SyncViewModel : ViewModel() {
    actual val uiState: StateFlow<SyncUiState> = MutableStateFlow(SyncUiState())
    actual val blockedCount: StateFlow<Int> = MutableStateFlow(0)
    actual val failedDownloadsCount: StateFlow<Int> = MutableStateFlow(0)
    actual val undoCard: StateFlow<UndoCard?> = MutableStateFlow(null)
    actual val syncLogLines: StateFlow<List<SyncLog.Line>> = MutableStateFlow(emptyList())
    actual val undoInProgress: StateFlow<Boolean> = MutableStateFlow(false)
    actual val undoResult: StateFlow<String?> = MutableStateFlow(null)
    actual val authExpiry: StateFlow<AuthExpiryState> = MutableStateFlow(AuthExpiryState(false, false))
    actual val streamingEnabled: StateFlow<Boolean> = MutableStateFlow(false)
    actual val lastFmState: StateFlow<LastFmRecommendationState> = MutableStateFlow(LastFmRecommendationState())

    actual fun syncLogAsText(): String = ""
    actual fun onUndoLastSync() {}
    actual fun onUndoResultShown() {}
    actual fun setStreamingEnabled(enabled: Boolean) {}
    actual fun onSyncNow() {}
    actual fun onStopSync() {}
    actual fun onTogglePlaylistSync(playlistId: Long, enabled: Boolean) {}
    actual fun onToggleHideFromHome(playlistId: Long, hidden: Boolean) {}
    actual fun onSetSyncTime(hour: Int, minute: Int) {}
    actual fun onToggleAutoSync() {}
    actual fun onToggleWifiOnly() {}
    actual fun onSpotifySyncModeChanged(mode: SyncMode) {}
    actual fun onYoutubeSyncModeChanged(mode: SyncMode) {}
    actual fun onRequestSpotifyRefresh() {}
    actual fun onRequestYoutubeRefresh() {}
    actual fun confirmRefreshMode() {}
    actual fun cancelRefreshMode() {}
    actual fun onYoutubeLikedStudioOnlyChanged(enabled: Boolean) {}
    actual fun onDiscoverAutoMixesChanged(enabled: Boolean) {}
    actual fun onSyncDaysChanged(bitmask: Int) {}
    actual fun onLastFmRecommendationsToggled(enabled: Boolean) {}
}
