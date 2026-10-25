package com.stash.feature.sync

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.core.auth.TokenManager
import com.stash.core.auth.model.AuthState
import com.stash.core.data.db.dao.SyncHistoryDao
import com.stash.core.data.db.entity.SyncHistoryEntity
import com.stash.core.data.sync.AuthExpiryState
import com.stash.core.data.sync.SyncPhase
import com.stash.core.data.sync.SyncPreferences
import com.stash.core.data.sync.SyncPreferencesManager
import com.stash.core.model.MusicSource
import com.stash.core.model.SyncMode
import com.stash.core.data.sync.DayOfWeekSet
import com.stash.core.data.sync.SyncScheduler
import com.stash.core.data.sync.SyncStateManager
import com.stash.core.data.sync.toDisplayStatus
import com.stash.core.model.SyncDisplayStatus
import com.stash.data.download.files.LibrarySizeHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Presentation-layer model for a single sync history row.
 *
 * Decoupled from the Room entity so the UI has no transitive Room dependency.
 */
/**
 * ViewModel backing the Sync screen.
 *
 * Combines live sync phase, user preferences, auth state for each service,
 * and recent sync history into a single reactive [SyncUiState].
 */
@HiltViewModel
actual open class SyncViewModel @Inject constructor(
    private val syncScheduler: SyncScheduler,
    private val syncStateManager: SyncStateManager,
    private val syncPreferencesManager: SyncPreferencesManager,
    private val tokenManager: TokenManager,
    private val syncHistoryDao: SyncHistoryDao,
    private val playlistDao: com.stash.core.data.db.dao.PlaylistDao,
    private val syncUndoDao: com.stash.core.data.db.dao.SyncUndoDao,
    private val syncLog: com.stash.core.data.sync.SyncLog,
    private val downloadQueueDao: com.stash.core.data.db.dao.DownloadQueueDao,
    private val musicRepository: com.stash.core.data.repository.MusicRepository,
    private val blocklistGuard: com.stash.core.data.blocklist.BlocklistGuard,
    /**
     * Disk-walked library size (storage-mode-aware: internal File walk
     * OR SAF DocumentFile traversal). Drives the SyncStatusCard's
     * Storage column. Mirrors HomeViewModel's injection — the Room
     * `file_size_bytes` column is bypassed because legacy libraries
     * have it stuck at 0 for thousands of rows.
     */
    private val librarySizeHolder: LibrarySizeHolder,
    /**
     * Online-vs-offline preference. Drives the Sync Now button label so
     * users can tell whether tapping it will download tracks to disk
     * (offline mode) or merely surface the library for streaming
     * playback (online mode). See [streamingEnabled].
     */
    private val streamingPreference: com.stash.core.data.prefs.StreamingPreference,
    /**
     * Last.fm-as-a-source manager (issue #255). Owns the "Recommended by
     * Last.fm" mix recipe lifecycle; the Sync tab only renders its state
     * and forwards the user's toggle.
     */
    private val lastFmRecommendationSource: com.stash.core.data.mix.LastFmRecommendationSource,
) : ViewModel() {

    /**
     * Reactive count of blocked songs, displayed as a badge on the
     * "Blocked Songs" row in the Sync screen's Library section.
     * v0.9.15: sources from BlocklistGuard (track_blocklist) instead
     * of the dropped tracks.is_blacklisted flag.
     */
    actual val blockedCount: StateFlow<Int> =
        blocklistGuard.observeCount()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    /**
     * Reactive count of FAILED rows in download_queue. Drives the
     * "Failed Downloads" card on the Sync tab — the card hides itself
     * when this is 0 so a healthy library shows no clutter.
     */
    actual val failedDownloadsCount: StateFlow<Int> =
        downloadQueueDao.getFailedDownloads()
            .map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    /**
     * The "Undo last sync" card: the last sync's restore point and how many
     * playlists that sync changed, or null when there's nothing to undo. Absent
     * means the card isn't rendered at all, so the control only exists when it
     * can actually do something: no point, a sync that changed nothing, or a
     * sync still running (undoing under it would race its writes).
     *
     * The count is worked out against the library when the point appears and
     * when a sync stops, not stored with the point, which is taken before the
     * sync has changed anything.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    actual val undoCard: StateFlow<UndoCard?> =
        combine(
            syncUndoDao.latestPoint(),
            syncStateManager.phase.map { it.isActive() }.distinctUntilChanged(),
        ) { point, syncing -> point.takeUnless { syncing } }
            .mapLatest { point -> point?.let { undoCardFor(it) } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    private suspend fun undoCardFor(point: com.stash.core.data.db.entity.SyncUndoPointEntity): UndoCard? {
        val changed = try {
            syncUndoDao.countChangedPlaylists(point.syncId, point.createdAtEpochMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SyncViewModel", "Couldn't count what sync ${point.syncId} changed", e)
            return null
        }
        return if (changed > 0) UndoCard(point.syncId, point.createdAtEpochMs, changed) else null
    }

    /**
     * Live account of what the running sync is finding. Drives the terminal on
     * the Sync tab; empty until the first sync of this app session.
     */
    actual val syncLogLines: StateFlow<List<com.stash.core.data.sync.SyncLog.Line>> =
        syncLog.lines
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** The log as plain text, for the copy-to-clipboard action (bug reports). */
    actual fun syncLogAsText(): String = syncLog.asPlainText()

    /** True while an undo is applying — the button disables so it can't double-fire. */
    private val _undoInProgress = MutableStateFlow(false)
    actual val undoInProgress: StateFlow<Boolean> = _undoInProgress

    /** One-shot result message after an undo ("Restored 2,919 tracks…"), or null. */
    private val _undoResult = MutableStateFlow<String?>(null)
    actual val undoResult: StateFlow<String?> = _undoResult

    /**
     * Roll the library back to how it looked before the last sync: playlists it
     * hid come back, playlists it added are hidden, playlists it emptied are
     * refilled in order. Tracks the user added or removed by hand, and Download
     * switches flipped since, are left untouched.
     *
     * Then pauses Auto-sync until the next "Sync now", and drops a sync queued
     * meanwhile: either would otherwise redo the sync the user just undid.
     *
     * Doesn't restore deleted audio files — nothing can — but sync no longer
     * deletes them while any source is on Accumulate.
     */
    actual fun onUndoLastSync() {
        if (_undoInProgress.value || _uiState.value.isSyncing) return
        viewModelScope.launch {
            _undoInProgress.value = true
            try {
                _undoResult.value = undoLastSync()
            } finally {
                _undoInProgress.value = false
            }
        }
    }

    private suspend fun undoLastSync(): String {
        val point = syncUndoDao.latestPointNow() ?: return "Nothing to undo."
        val changed = try {
            val changed = syncUndoDao.countChangedPlaylists(point.syncId, point.createdAtEpochMs)
            syncScheduler.cancelPendingSync()
            syncUndoDao.restore(point.syncId)
            changed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SyncViewModel", "Undo of sync ${point.syncId} failed", e)
            return "Couldn't undo that sync. Nothing was changed."
        }
        // After the restore, and never failing it: the library is already back.
        try {
            syncPreferencesManager.setAutoSyncPausedAfterUndo(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SyncViewModel", "Couldn't pause Auto-sync after the undo", e)
        }
        val playlists = if (changed == 1) "1 playlist is" else "$changed playlists are"
        val paused = if (_uiState.value.syncPreferences.autoSyncEnabled) {
            " Auto-sync is paused until you tap Sync."
        } else {
            ""
        }
        return "Undone — $playlists back to how they were before that sync.$paused"
    }

    /** Clear the one-shot undo message once the UI has shown it. */
    actual fun onUndoResultShown() { _undoResult.value = null }

    /**
     * Per-source auth expiry state from SyncStateManager. The Sync tab's
     * AuthExpiredBanner subscribes to this flow and renders nothing when
     * `anyExpired == false`, so re-auth surfaces only when probes flag a
     * problem at sync start.
     */
    actual val authExpiry: StateFlow<AuthExpiryState> =
        syncStateManager.authExpiry
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AuthExpiryState(false, false),
            )

    /**
     * Reactive online-streaming-mode flag. The Sync Now button reads
     * this to pick its label: "Surface Library for Streaming" in Online
     * mode, "Download Tracks to Device" in Offline mode. Initial value
     * matches `StreamingPreference.enabled`'s default (false / Offline)
     * so a not-yet-loaded flow displays the safer download-mode label.
     */
    actual val streamingEnabled: StateFlow<Boolean> =
        streamingPreference.enabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    /**
     * Live Last.fm source state (issue #255): connection, Recommendations
     * toggle, and the materialized playlist's track count. Drives the
     * Last.fm card in the Sync tab's Sources section and its manage screen.
     */
    actual val lastFmState: StateFlow<com.stash.core.data.mix.LastFmRecommendationState> =
        lastFmRecommendationSource.observeState()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = com.stash.core.data.mix.LastFmRecommendationState(),
            )

    /**
     * User toggled the "Recommended by Last.fm" playlist on/off from the
     * Sync surface. Delegates to [LastFmRecommendationSource], which
     * persists the choice, activates/deactivates recipe + playlist, and —
     * when enabling — kicks a one-shot refresh so the mix starts building.
     */
    actual fun onLastFmRecommendationsToggled(enabled: Boolean) {
        viewModelScope.launch {
            lastFmRecommendationSource.setRecommendationsEnabled(enabled)
        }
    }

    /**
     * Persist the user's choice between Online (streaming) and Offline
     * (download) mode. Hoisted from the Sync-tab segmented toggle so the
     * user can flip modes without leaving the Sync screen.
     */
    actual fun setStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            streamingPreference.setEnabled(enabled)
        }
    }

    private val _uiState = MutableStateFlow(SyncUiState())
    actual val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        observeSyncPhase()
        observePreferences()
        observeSyncMode()
        observeAuthStates()
        observeHistory()
        observeLastSync()
        observeSpotifyPlaylists()
        observeYouTubePlaylists()
        observeUnmatchedCount()
        observeFlaggedCount()
        observeSyncStatusCard()
    }

    // -- Public actions -------------------------------------------------------

    /** Trigger an immediate sync, replacing any pending schedule. No-ops
     *  while a sync is already in progress — see [SyncScheduler.triggerManualSync]
     *  for why re-triggering mid-sync is actively harmful, not just redundant.
     *  Offline it queues the sync until a network returns ("Waiting for network"). */
    actual fun onSyncNow() {
        if (_uiState.value.isSyncing) {
            android.util.Log.i("SyncViewModel", "onSyncNow ignored — sync already in progress")
            return
        }
        // The Wi-Fi rule this screen shows: what a sync queued offline downloads under.
        val wifiOnly = _uiState.value.syncPreferences.wifiOnly
        val pausedAfterUndo = _uiState.value.syncPreferences.autoSyncPausedAfterUndo
        viewModelScope.launch {
            // A manual Sync ends the pause "Undo last sync" put on Auto-sync.
            if (pausedAfterUndo) syncPreferencesManager.setAutoSyncPausedAfterUndo(false)
            syncScheduler.triggerManualSync(wifiOnly)
        }
    }

    /**
     * Cancel any in-flight sync. Backed by `WorkManager.cancelUniqueWork`,
     * which cancels the work chain and any worker currently running — the
     * worker sees a cancellation exception and exits. In-progress track
     * downloads that finish before the cancellation is observed will still
     * complete, but no new tracks are enqueued. The daily schedule stays.
     * The run's history row is closed as cancelled too, so the status card at
     * the top of the tab stops saying "Syncing...".
     */
    actual fun onStopSync() {
        viewModelScope.launch { syncScheduler.cancelSync() }
    }

    /** Toggle sync_enabled for a specific Spotify playlist. */
    actual fun onTogglePlaylistSync(playlistId: Long, enabled: Boolean) {
        viewModelScope.launch {
            // The switch decides Home too: sync on pins the playlist to the Your
            // playlists rail, sync off unpins it (the user's rule, 2026-09-07).
            playlistDao.setSyncEnabledAndPin(playlistId, enabled, System.currentTimeMillis())
            // v0.9.21: when DISABLING, sweep pending download_queue rows
            // whose tracks no longer belong to any sync-enabled playlist.
            // Without this, deselecting a playlist leaves its in-flight
            // downloads draining — confusing because the user expects
            // "deselect" to actually stop downloads. The query is
            // DELETE-with-NOT-IN so tracks shared with another enabled
            // playlist (e.g. same track in Liked Songs on both services)
            // stay queued.
            if (!enabled) {
                val cancelled = downloadQueueDao.cancelDownloadsWithNoEnabledPlaylist()
                if (cancelled > 0) {
                    android.util.Log.i(
                        "SyncToggle",
                        "cancelled $cancelled orphan PENDING download(s) after disable",
                    )
                }
            }
        }
    }

    /**
     * Toggle hide_from_home for a specific playlist. Unlike [onTogglePlaylistSync]
     * this does NOT sweep downloads — hiding a mix from Home has nothing to do
     * with what's queued or synced.
     */
    actual fun onToggleHideFromHome(playlistId: Long, hidden: Boolean) {
        // Showing pins the mix so it leads its Home rail; hiding unpins it. The
        // rail shows only its 12 freshest mixes otherwise, and "Shown on Home"
        // was a promise it kept for none of the others (Cup Noodle Radio, 2026-09-07).
        viewModelScope.launch {
            playlistDao.setHomeVisibility(playlistId, hidden, if (hidden) null else System.currentTimeMillis())
        }
    }

    /**
     * Update the daily sync schedule time and reschedule.
     *
     * @param hour   Hour of day (0-23).
     * @param minute Minute of hour (0-59).
     */
    actual fun onSetSyncTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            syncPreferencesManager.setSyncTime(hour, minute)
            val prefs = _uiState.value.syncPreferences
            if (prefs.autoSyncEnabled) {
                syncScheduler.scheduleDailySync(
                    hour,
                    minute,
                    wifiOnly = prefs.wifiOnly,
                    days = DayOfWeekSet(prefs.syncDays),
                )
            }
        }
    }

    /** Toggle the daily auto-sync on or off. */
    actual fun onToggleAutoSync() {
        viewModelScope.launch {
            val current = _uiState.value.syncPreferences.autoSyncEnabled
            val newValue = !current
            syncPreferencesManager.setAutoSyncEnabled(newValue)
            if (newValue) {
                val prefs = _uiState.value.syncPreferences
                syncScheduler.scheduleDailySync(
                    prefs.syncHour,
                    prefs.syncMinute,
                    wifiOnly = prefs.wifiOnly,
                    days = DayOfWeekSet(prefs.syncDays),
                )
            } else {
                syncScheduler.cancelDailySync()
            }
        }
    }

    /** Toggle the Wi-Fi-only constraint on or off. */
    actual fun onToggleWifiOnly() {
        viewModelScope.launch {
            val current = _uiState.value.syncPreferences.wifiOnly
            syncPreferencesManager.setWifiOnly(!current)
            // A scheduled sync still waiting (say, for Wi-Fi) takes the new rule now.
            syncScheduler.applyWifiOnlyToWaitingSync(!current)
        }
    }

    /** Switch Spotify's Refresh/Accumulate mode. */
    actual fun onSpotifySyncModeChanged(mode: SyncMode) {
        viewModelScope.launch {
            syncPreferencesManager.setSpotifySyncMode(mode)
        }
    }

    /** Switch YouTube's Refresh/Accumulate mode. */
    actual fun onYoutubeSyncModeChanged(mode: SyncMode) {
        viewModelScope.launch {
            syncPreferencesManager.setYoutubeSyncMode(mode)
        }
    }

    /** Refresh chip tapped for Spotify. If currently ACCUMULATE, confirm first
     *  (Refresh deletes rotated-out downloads); if already REFRESH, no-op. */
    actual fun onRequestSpotifyRefresh() {
        if (_uiState.value.spotifySyncMode == SyncMode.ACCUMULATE) {
            _uiState.update { it.copy(pendingRefreshSource = MusicSource.SPOTIFY) }
        }
    }

    /** Refresh chip tapped for YouTube — see [onRequestSpotifyRefresh]. */
    actual fun onRequestYoutubeRefresh() {
        if (_uiState.value.youtubeSyncMode == SyncMode.ACCUMULATE) {
            _uiState.update { it.copy(pendingRefreshSource = MusicSource.YOUTUBE) }
        }
    }

    /** Confirm the pending Refresh switch — applies REFRESH to that source. */
    actual fun confirmRefreshMode() {
        val source = _uiState.value.pendingRefreshSource ?: return
        viewModelScope.launch {
            when (source) {
                MusicSource.YOUTUBE -> syncPreferencesManager.setYoutubeSyncMode(SyncMode.REFRESH)
                MusicSource.SPOTIFY -> syncPreferencesManager.setSpotifySyncMode(SyncMode.REFRESH)
                MusicSource.LOCAL, MusicSource.BOTH, MusicSource.CLOUD -> Unit
            }
        }
        _uiState.update { it.copy(pendingRefreshSource = null) }
    }

    /** Dismiss the dialog — keep the current (Accumulate) mode. */
    actual fun cancelRefreshMode() {
        _uiState.update { it.copy(pendingRefreshSource = null) }
    }

    /** Persists the user's choice for the studio-only Liked Songs filter. */
    actual fun onYoutubeLikedStudioOnlyChanged(enabled: Boolean) {
        viewModelScope.launch {
            syncPreferencesManager.setYoutubeLikedStudioOnly(enabled)
        }
    }

    /**
     * Turn discovery of the service's own algorithmic mixes on/off (#335, #344).
     * Off stops them being fetched at all, which also shortens every sync.
     * Mixes already in the library are left alone — this is not a delete.
     */
    actual fun onDiscoverAutoMixesChanged(enabled: Boolean) {
        viewModelScope.launch {
            syncPreferencesManager.setDiscoverAutoMixes(enabled)
        }
    }

    /**
     * Persists the days-of-week selection and reschedules, so the first run
     * lands on the next chosen day and a schedule that had no days (so no
     * trigger) starts again. UI passes the new bitmask.
     */
    actual fun onSyncDaysChanged(bitmask: Int) {
        viewModelScope.launch {
            syncPreferencesManager.setSyncDays(bitmask)
            val prefs = _uiState.value.syncPreferences
            if (prefs.autoSyncEnabled) {
                syncScheduler.scheduleDailySync(
                    prefs.syncHour,
                    prefs.syncMinute,
                    wifiOnly = prefs.wifiOnly,
                    days = DayOfWeekSet(bitmask),
                )
            }
        }
    }

    // -- Internal observers ---------------------------------------------------

    private fun observeSyncPhase() {
        viewModelScope.launch {
            syncStateManager.phase.collect { phase ->
                _uiState.update {
                    it.copy(
                        syncPhase = phase,
                        overallProgress = phase.progress,
                        isSyncing = phase.isActive(),
                    )
                }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            syncPreferencesManager.preferences.collect { prefs ->
                _uiState.update { it.copy(syncPreferences = prefs) }
            }
        }
    }

    private fun observeSyncMode() {
        viewModelScope.launch {
            syncPreferencesManager.spotifySyncMode.collect { mode ->
                _uiState.update { it.copy(spotifySyncMode = mode) }
            }
        }
        viewModelScope.launch {
            syncPreferencesManager.youtubeSyncMode.collect { mode ->
                _uiState.update { it.copy(youtubeSyncMode = mode) }
            }
        }
        viewModelScope.launch {
            syncPreferencesManager.youtubeLikedStudioOnly.collect { enabled ->
                _uiState.update { it.copy(youtubeLikedStudioOnly = enabled) }
            }
        }
        viewModelScope.launch {
            syncPreferencesManager.discoverAutoMixes.collect { enabled ->
                _uiState.update { it.copy(discoverAutoMixes = enabled) }
            }
        }
        viewModelScope.launch {
            syncPreferencesManager.syncDays.collect { bitmask ->
                _uiState.update { it.copy(syncDays = bitmask) }
            }
        }
    }

    private fun observeAuthStates() {
        viewModelScope.launch {
            combine(
                tokenManager.spotifyAuthState,
                tokenManager.youTubeAuthState,
            ) { spotify, youtube ->
                Pair(spotify is AuthState.Connected, youtube is AuthState.Connected)
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
        viewModelScope.launch {
            syncHistoryDao.getRecentSyncs(limit = 10).collect { entities ->
                _uiState.update {
                    it.copy(recentSyncs = entities.map { e -> e.toInfo() })
                }
            }
        }
    }

    /**
     * Derives hero-card last-sync metadata from the most recent history entry.
     * Uses a limit-1 query so only the minimum data is loaded for this purpose.
     * Health is derived from [SyncDisplayStatus] — same source used by
     * [SyncHistoryRow] to render icons and badge colours.
     */
    private fun observeLastSync() {
        viewModelScope.launch {
            syncHistoryDao.getRecentSyncs(limit = 1).collect { entities ->
                val latest = entities.firstOrNull()?.toInfo()
                val relativeTime = latest?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it.startedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                } ?: ""
                val healthLabel = when (latest?.displayStatus) {
                    null -> ""
                    SyncDisplayStatus.Success -> "✓"
                    is SyncDisplayStatus.PartialSuccess -> "!"
                    is SyncDisplayStatus.Interrupted -> "!"
                    SyncDisplayStatus.Cancelled -> "⊘"
                    is SyncDisplayStatus.Failed -> "×"
                    else -> ""
                }
                val healthColor = when (latest?.displayStatus) {
                    SyncDisplayStatus.Success ->
                        androidx.compose.ui.graphics.Color(0xFF10B981)
                    is SyncDisplayStatus.PartialSuccess,
                    is SyncDisplayStatus.Interrupted ->
                        androidx.compose.ui.graphics.Color(0xFFF59E0B)
                    SyncDisplayStatus.Cancelled ->
                        androidx.compose.ui.graphics.Color(0xFF9CA3AF)
                    is SyncDisplayStatus.Failed ->
                        androidx.compose.ui.graphics.Color(0xFFEF4444)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
                _uiState.update { state ->
                    state.copy(
                        lastSyncRelativeTime = relativeTime,
                        // What the sync FOUND, not what it downloaded. In Online
                        // mode nothing downloads, so tracksDownloaded is always 0
                        // and the headline read "0 tracks" after a run that added
                        // 159 songs. newTracksFound is the honest answer in both
                        // modes — Offline downloads them, Online streams them, but
                        // either way they are new music in the library.
                        lastSyncTrackCount = latest?.newTracksFound,
                        lastSyncHealthLabel = healthLabel,
                        lastSyncHealthColor = healthColor,
                    )
                }
            }
        }
    }

    private fun observeSpotifyPlaylists() {
        viewModelScope.launch {
            playlistDao.getSpotifyPlaylistsForPreferences().collect { entities ->
                _uiState.update {
                    it.copy(
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
    }

    private fun observeYouTubePlaylists() {
        viewModelScope.launch {
            playlistDao.getYouTubePlaylistsForPreferences().collect { entities ->
                _uiState.update {
                    it.copy(
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

    /**
     * Observe the live count of tracks that failed YouTube matching.
     *
     * Drives the amber warning card on the Sync screen so the user always
     * knows when there are songs requiring manual review.
     */
    private fun observeUnmatchedCount() {
        viewModelScope.launch {
            musicRepository.getUnmatchedCount().collect { count ->
                _uiState.update { it.copy(unmatchedCount = count) }
            }
        }
    }

    /**
     * Observe the live count of user-flagged "wrong match" tracks so the
     * Sync-tab review card is reachable even when no sync-side matching
     * failures exist. Without this, tracks flagged from Now Playing had
     * no entry-point surface into Failed Matches and appeared lost.
     */
    private fun observeFlaggedCount() {
        viewModelScope.launch {
            musicRepository.getFlaggedCount().collect { count ->
                _uiState.update { it.copy(flaggedCount = count) }
            }
        }
    }

    /**
     * Mirrors HomeViewModel's `syncStatusFlow` + `musicDataFlow` +
     * `sourceCountsFlow` assembly that originally populated the
     * SyncStatusCard at the top of the Home screen. The card now
     * lives at the top of the Sync screen; this observer keeps it
     * fed with the same flow shape so the relocation introduces no
     * behavioural change.
     *
     * Inputs (all reactive):
     *  - `observeLatestSync()` for last-sync timestamp + display status
     *  - `getTrackCount()` for the "Tracks" stat
     *  - `getSpotifyDownloadedCount()` / `getYouTubeDownloadedCount()`
     *    for the per-source stats
     *  - `librarySizeHolder.size` for storage (disk truth — the Room
     *    `file_size_bytes` SUM is unreliable for legacy libraries)
     */
    private fun observeSyncStatusCard() {
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

    /** Map Room entity to lightweight presentation model. */
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
}
