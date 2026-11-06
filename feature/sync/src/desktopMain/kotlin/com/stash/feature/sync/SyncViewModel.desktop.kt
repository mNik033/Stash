package com.stash.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.auth.TokenManager
import com.stash.core.auth.model.AuthState
import com.stash.core.data.blocklist.BlocklistGuard
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.PlaylistDao
import com.stash.core.data.db.dao.SyncHistoryDao
import com.stash.core.data.db.dao.SyncUndoDao
import com.stash.core.data.db.entity.SyncHistoryEntity
import com.stash.core.data.mix.LastFmRecommendationState
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.data.sync.AuthExpiryState
import com.stash.core.data.sync.DownloadQueueTrigger
import com.stash.core.data.sync.SyncLog
import com.stash.core.data.sync.SyncStateManager
import com.stash.core.data.sync.toDisplayStatus
import com.stash.core.model.SyncDisplayStatus
import com.stash.core.model.SyncMode
import com.stash.data.download.files.LibrarySizeHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

actual open class SyncViewModel(
    private val tokenManager: TokenManager? = null,
    private val streamingPreference: StreamingPreference? = null,
    private val playlistDao: PlaylistDao? = null,
    private val downloadQueueDao: DownloadQueueDao? = null,
    private val syncHistoryDao: SyncHistoryDao? = null,
    private val syncUndoDao: SyncUndoDao? = null,
    private val musicRepository: MusicRepository? = null,
    private val librarySizeHolder: LibrarySizeHolder? = null,
    private val downloadQueueTrigger: DownloadQueueTrigger? = null,
    private val blocklistGuard: BlocklistGuard? = null,
    private val syncStateManager: SyncStateManager? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    actual val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    actual val downloadsSummary: StateFlow<DownloadsSummary> =
        MutableStateFlow(DownloadsSummary())

    actual val blockedCount: StateFlow<Int> =
        blocklistGuard?.observeCount()?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        ) ?: MutableStateFlow(0)

    actual val failedDownloadsCount: StateFlow<Int> =
        downloadQueueDao?.getFailedDownloads()?.map { it.size }?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        ) ?: MutableStateFlow(0)

    actual val undoCard: StateFlow<UndoCard?> =
        syncUndoDao?.latestPoint()?.map { point ->
            point?.let {
                UndoCard(
                    syncId = it.syncId,
                    createdAtEpochMs = it.createdAtEpochMs,
                    changedPlaylists = it.playlistCount,
                )
            }
        }?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        ) ?: MutableStateFlow(null)

    actual val syncLogLines: StateFlow<List<SyncLog.Line>> = MutableStateFlow(emptyList())

    private val _undoInProgress = MutableStateFlow(false)
    actual val undoInProgress: StateFlow<Boolean> = _undoInProgress.asStateFlow()

    private val _undoResult = MutableStateFlow<String?>(null)
    actual val undoResult: StateFlow<String?> = _undoResult.asStateFlow()

    actual val authExpiry: StateFlow<AuthExpiryState> =
        syncStateManager?.authExpiry ?: MutableStateFlow(AuthExpiryState(false, false))

    actual val streamingEnabled: StateFlow<Boolean> =
        streamingPreference?.enabled?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        ) ?: MutableStateFlow(true)

    actual val lastFmState: StateFlow<LastFmRecommendationState> = MutableStateFlow(LastFmRecommendationState())

    init {
        observeAuth()
        observeHistory()
        observeLastSync()
        observePlaylists()
        observeCounts()
        observeSyncStatusCard()
    }

    private fun observeAuth() {
        if (tokenManager == null) return
        viewModelScope.launch {
            combine(
                tokenManager.spotifyAuthState,
                tokenManager.youTubeAuthState,
            ) { sp, yt ->
                Pair(sp is AuthState.Connected, yt is AuthState.Connected)
            }.collect { (spotifyConnected, youTubeConnected) ->
                _uiState.update {
                    it.copy(
                        spotifyConnected = spotifyConnected,
                        youTubeConnected = youTubeConnected,
                    )
                }
            }
        }
    }

    private fun observeHistory() {
        if (syncHistoryDao == null) return
        viewModelScope.launch {
            syncHistoryDao.getRecentSyncs(limit = 10).collect { list ->
                _uiState.update { state ->
                    state.copy(recentSyncs = list.map { it.toInfo() })
                }
            }
        }
    }

    private fun observeLastSync() {
        if (syncHistoryDao == null) return
        viewModelScope.launch {
            syncHistoryDao.getRecentSyncs(limit = 1).collect { list ->
                val latest = list.firstOrNull()?.toInfo()
                val relativeTime = latest?.let { formatRelativeTime(it.startedAt) } ?: ""
                val healthLabel = when (latest?.displayStatus) {
                    null -> ""
                    SyncDisplayStatus.Success -> "✓"
                    is SyncDisplayStatus.PartialSuccess,
                    is SyncDisplayStatus.Interrupted -> "!"
                    SyncDisplayStatus.Cancelled -> "⊘"
                    is SyncDisplayStatus.Failed -> "×"
                    else -> ""
                }
                val healthColor = when (latest?.displayStatus) {
                    SyncDisplayStatus.Success -> androidx.compose.ui.graphics.Color(0xFF10B981)
                    is SyncDisplayStatus.PartialSuccess,
                    is SyncDisplayStatus.Interrupted -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
                    SyncDisplayStatus.Cancelled -> androidx.compose.ui.graphics.Color(0xFF9CA3AF)
                    is SyncDisplayStatus.Failed -> androidx.compose.ui.graphics.Color(0xFFEF4444)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
                _uiState.update { state ->
                    state.copy(
                        lastSyncRelativeTime = relativeTime,
                        lastSyncTrackCount = latest?.newTracksFound,
                        lastSyncHealthLabel = healthLabel,
                        lastSyncHealthColor = healthColor,
                    )
                }
            }
        }
    }

    private fun observePlaylists() {
        if (playlistDao == null) return
        viewModelScope.launch {
            playlistDao.getSpotifyPlaylistsForPreferences().collect { entities ->
                _uiState.update { state ->
                    state.copy(
                        spotifyPlaylists = entities.map { e ->
                            SpotifySyncPlaylist(
                                id = e.id,
                                name = e.name,
                                trackCount = e.trackCount,
                                type = e.type,
                                syncEnabled = e.syncEnabled,
                                artUrl = e.artUrl,
                                hideFromHome = e.hideFromHome,
                                pinnedToHomeAt = e.pinnedToHomeAt,
                            )
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            playlistDao.getYouTubePlaylistsForPreferences().collect { entities ->
                _uiState.update { state ->
                    state.copy(
                        youTubePlaylists = entities.map { e ->
                            YouTubeSyncPlaylist(
                                id = e.id,
                                name = e.name,
                                trackCount = e.trackCount,
                                type = e.type,
                                syncEnabled = e.syncEnabled,
                                artUrl = e.artUrl,
                                hideFromHome = e.hideFromHome,
                                pinnedToHomeAt = e.pinnedToHomeAt,
                            )
                        }
                    )
                }
            }
        }
    }

    private fun observeCounts() {
        if (musicRepository == null) return
        viewModelScope.launch {
            musicRepository.getUnmatchedCount().collect { count ->
                _uiState.update { it.copy(unmatchedCount = count) }
            }
        }
        viewModelScope.launch {
            musicRepository.getFlaggedCount().collect { count ->
                _uiState.update { it.copy(flaggedCount = count) }
            }
        }
    }

    private fun observeSyncStatusCard() {
        if (musicRepository == null || librarySizeHolder == null) return
        val syncStatusFlow = musicRepository.observeLatestSync().map { latestSync ->
            if (latestSync != null) {
                SyncStatusInfo(
                    lastSyncTime = latestSync.startedAt.toEpochMilli(),
                    nextSyncTime = latestSync.completedAt?.toEpochMilli()?.plus(6 * 3_600_000L),
                    state = latestSync.status,
                    displayStatus = latestSync.toDisplayStatus(),
                )
            } else {
                SyncStatusInfo(displayStatus = SyncDisplayStatus.Idle)
            }
        }

        viewModelScope.launch {
            combine(
                syncStatusFlow,
                musicRepository.getTrackCount(),
                musicRepository.getSpotifyDownloadedCount(),
                musicRepository.getYouTubeDownloadedCount(),
                librarySizeHolder.size,
            ) { base, trackCount, spotifyCount, youtubeCount, librarySize ->
                base.copy(
                    totalTracks = trackCount,
                    spotifyTracks = spotifyCount,
                    youTubeTracks = youtubeCount,
                    storageUsedBytes = librarySize.totalBytes,
                    flacTracks = librarySize.losslessFileCount,
                    flacStorageBytes = librarySize.losslessBytes,
                )
            }.collect { status ->
                _uiState.update {
                    it.copy(
                        syncStatus = status,
                        hasEverSynced = status.lastSyncTime != null,
                    )
                }
            }
        }
    }

    actual fun syncLogAsText(): String = ""

    actual fun onUndoLastSync() {
        if (syncUndoDao == null) return
        viewModelScope.launch {
            val point = syncUndoDao.latestPointNow() ?: return@launch
            _undoInProgress.value = true
            try {
                val count = syncUndoDao.restore(point.syncId)
                _undoResult.value = "Restored $count playlists."
            } catch (e: Exception) {
                _undoResult.value = "Undo failed: ${e.message}"
            } finally {
                _undoInProgress.value = false
            }
        }
    }

    actual fun onUndoResultShown() {
        _undoResult.value = null
    }

    actual fun setStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            streamingPreference?.setEnabled(enabled)
            if (!enabled) {
                musicRepository?.queueKeptPlaylists()
                downloadQueueTrigger?.triggerDrain()
            }
        }
    }

    actual fun onSyncNow() {
        viewModelScope.launch {
            downloadQueueTrigger?.triggerDrain()
            val streamOnly = streamingPreference?.enabled?.first() ?: true
            if (!streamOnly) {
                musicRepository?.queueKeptPlaylists()
                downloadQueueTrigger?.triggerDrain()
            }
        }
    }

    actual fun onStopSync() {
        // Desktop queue processor drains on IO; triggering no-op or drain cancel
    }

    actual fun onSetSyncTime(hour: Int, minute: Int) {}
    actual fun onToggleAutoSync() {}
    actual fun onToggleWifiOnly() {}
    actual fun onSpotifySyncModeChanged(mode: SyncMode) {
        _uiState.update { it.copy(spotifySyncMode = mode) }
    }
    actual fun onYoutubeSyncModeChanged(mode: SyncMode) {
        _uiState.update { it.copy(youtubeSyncMode = mode) }
    }
    actual fun onRequestSpotifyRefresh() {}
    actual fun onRequestYoutubeRefresh() {}
    actual fun confirmRefreshMode() {}
    actual fun cancelRefreshMode() {}
    actual fun onYoutubeLikedStudioOnlyChanged(enabled: Boolean) {
        _uiState.update { it.copy(youtubeLikedStudioOnly = enabled) }
    }
    actual fun onDiscoverAutoMixesChanged(enabled: Boolean) {
        _uiState.update { it.copy(discoverAutoMixes = enabled) }
    }
    actual fun onSyncSpotifySavedAlbumsChanged(enabled: Boolean) {
        _uiState.update { it.copy(syncSpotifySavedAlbums = enabled) }
    }
    actual fun onSyncDaysChanged(bitmask: Int) {
        _uiState.update { it.copy(syncDays = bitmask) }
    }
    actual fun onLastFmRecommendationsToggled(enabled: Boolean) {}

    private fun SyncHistoryEntity.toInfo() = SyncHistoryInfo(
        id = id,
        startedAt = startedAt.toEpochMilli(),
        completedAt = completedAt?.toEpochMilli(),
        status = status.name,
        tracksDownloaded = tracksDownloaded,
        tracksFailed = tracksFailed,
        newTracksFound = newTracksFound,
        playlistsChecked = playlistsChecked,
        bytesDownloaded = bytesDownloaded,
        trigger = trigger,
        streamingMode = streamingMode,
        errorMessage = errorMessage,
        diagnostics = diagnostics,
        displayStatus = toDisplayStatus(),
    )

    private fun formatRelativeTime(epochMs: Long): String {
        val diffSec = (System.currentTimeMillis() - epochMs) / 1000
        return when {
            diffSec < 60 -> "Just now"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            diffSec < 86400 -> "${diffSec / 3600}h ago"
            diffSec < 604800 -> "${diffSec / 86400}d ago"
            else -> "${diffSec / 604800}w ago"
        }
    }

    actual fun onArrivalChosen(streamEverything: Boolean) {
        _uiState.update { it.copy(showArrivalSheet = false) }
        viewModelScope.launch {
            streamingPreference?.setEnabled(streamEverything)
            onSyncNow()
        }
    }

    actual fun onArrivalDismissed() {
        _uiState.update { it.copy(showArrivalSheet = false) }
    }

    actual fun onManageOpened() {
        _uiState.update { it.copy(openManageFor = null) }
    }

    actual fun onPlayPlaylist(playlistId: Long, name: String) {
        // Desktop handles playback through desktop player
    }
}
