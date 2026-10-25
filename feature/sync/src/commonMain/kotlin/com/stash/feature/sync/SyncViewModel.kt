package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import com.stash.core.data.mix.LastFmRecommendationState
import com.stash.core.data.sync.AuthExpiryState
import com.stash.core.data.sync.SyncLog
import com.stash.core.model.SyncMode
import kotlinx.coroutines.flow.StateFlow

expect open class SyncViewModel : ViewModel {
    val uiState: StateFlow<SyncUiState>
    val blockedCount: StateFlow<Int>
    val failedDownloadsCount: StateFlow<Int>
    val undoCard: StateFlow<UndoCard?>
    val syncLogLines: StateFlow<List<SyncLog.Line>>
    val undoInProgress: StateFlow<Boolean>
    val undoResult: StateFlow<String?>
    val authExpiry: StateFlow<AuthExpiryState>
    val streamingEnabled: StateFlow<Boolean>
    val lastFmState: StateFlow<LastFmRecommendationState>

    fun syncLogAsText(): String
    fun onUndoLastSync()
    fun onUndoResultShown()
    fun setStreamingEnabled(enabled: Boolean)
    fun onSyncNow()
    fun onStopSync()
    fun onTogglePlaylistSync(playlistId: Long, enabled: Boolean)
    fun onToggleHideFromHome(playlistId: Long, hidden: Boolean)
    fun onSetSyncTime(hour: Int, minute: Int)
    fun onToggleAutoSync()
    fun onToggleWifiOnly()
    fun onSpotifySyncModeChanged(mode: SyncMode)
    fun onYoutubeSyncModeChanged(mode: SyncMode)
    fun onRequestSpotifyRefresh()
    fun onRequestYoutubeRefresh()
    fun confirmRefreshMode()
    fun cancelRefreshMode()
    fun onYoutubeLikedStudioOnlyChanged(enabled: Boolean)
    fun onDiscoverAutoMixesChanged(enabled: Boolean)
    fun onSyncDaysChanged(bitmask: Int)
    fun onLastFmRecommendationsToggled(enabled: Boolean)
}
