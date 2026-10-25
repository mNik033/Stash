package com.stash.feature.sync

import androidx.compose.ui.graphics.Color
import com.stash.core.data.sync.DayOfWeekSet
import com.stash.core.data.sync.SyncPhase
import com.stash.core.data.sync.SyncPreferences
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import com.stash.core.model.SyncDisplayStatus
import com.stash.core.model.SyncMode
import com.stash.core.model.SyncTrigger

/**
 * Presentation-layer model for a single sync history row.
 *
 * Decoupled from the Room entity so the UI has no transitive Room dependency.
 */
data class SyncHistoryInfo(
    val id: Long,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: String,
    val tracksDownloaded: Int = 0,
    val tracksFailed: Int = 0,
    val newTracksFound: Int = 0,
    val playlistsChecked: Int = 0,
    val bytesDownloaded: Long = 0,
    val trigger: SyncTrigger = SyncTrigger.MANUAL,
    /** Online (true) / Offline (false) / unknown-legacy (null) at the time of this sync. */
    val streamingMode: Boolean? = null,
    val errorMessage: String? = null,
    val diagnostics: String? = null,
    /**
     * Richer display summary derived from the raw sync record. Prefer this
     * over [status] for rendering — it distinguishes partial success,
     * interruption (process kill), and genuine failure.
     */
    val displayStatus: SyncDisplayStatus = SyncDisplayStatus.Idle,
)

/**
 * Lightweight model for a Spotify playlist's sync preference toggle.
 */
data class SpotifySyncPlaylist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val type: PlaylistType,
    val syncEnabled: Boolean,
    val artUrl: String? = null,
    val hideFromHome: Boolean = false,
    val pinnedToHomeAt: Long? = null,
)

/**
 * Lightweight model for a YouTube Music playlist's sync preference toggle.
 * Mirrors [SpotifySyncPlaylist] so the UI can use a shared toggle row
 * composable; kept as a separate type so future YouTube-only fields don't
 * pollute the Spotify struct.
 */
data class YouTubeSyncPlaylist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val type: PlaylistType,
    val syncEnabled: Boolean,
    val artUrl: String? = null,
    val hideFromHome: Boolean = false,
    val pinnedToHomeAt: Long? = null,
)

/**
 * Full UI state for the Sync screen.
 */
data class SyncUiState(
    val syncPhase: SyncPhase = SyncPhase.Idle,
    val overallProgress: Float = 0f,
    val syncPreferences: SyncPreferences = SyncPreferences(),
    val spotifyConnected: Boolean = false,
    val youTubeConnected: Boolean = false,
    val recentSyncs: List<SyncHistoryInfo> = emptyList(),
    val isSyncing: Boolean = false,
    /** Spotify playlists available for sync preference toggles. */
    val spotifyPlaylists: List<SpotifySyncPlaylist> = emptyList(),
    /** YouTube Music playlists available for sync preference toggles. */
    val youTubePlaylists: List<YouTubeSyncPlaylist> = emptyList(),
    /**
     * Per-source sync modes. Each service's Sync Preferences card
     * renders its own Refresh/Accumulate chip row bound to one of
     * these. Defaults to ACCUMULATE for both.
     */
    val spotifySyncMode: SyncMode = SyncMode.ACCUMULATE,
    val youtubeSyncMode: SyncMode = SyncMode.ACCUMULATE,
    /**
     * Non-null while the Refresh-confirm dialog is shown for that source.
     * Set when the user taps Refresh on a source currently in ACCUMULATE
     * (Refresh deletes rotated-out downloads, so we ask first); cleared on
     * confirm/cancel. null = no dialog.
     */
    val pendingRefreshSource: MusicSource? = null,
    /**
     * When true, the YT Music Liked Songs sync filters out UGC, cover,
     * live, and podcast tracks. Other YT content is unaffected. Default false.
     */
    val youtubeLikedStudioOnly: Boolean = false,
    /**
     * Whether sync discovers the service's own algorithmic mixes. Default true
     * (opt-out) — see SyncPreferencesManager.discoverAutoMixes.
     */
    val discoverAutoMixes: Boolean = true,
    /**
     * Days-of-week bitmask for the auto-sync schedule. Bit 0 = Mon … bit 6 = Sun.
     * Default 127 = every day.
     */
    val syncDays: Int = 0b1111111,
    /** Number of tracks that could not be matched to a YouTube video. */
    val unmatchedCount: Int = 0,
    /**
     * Number of tracks the user flagged from Now Playing as "wrong
     * match." Drives the Sync-tab review card alongside [unmatchedCount]
     * so flagged tracks are reachable even when no sync failures exist.
     */
    val flaggedCount: Int = 0,

    // -- Hero card: last-sync metadata ----------------------------------------
    /** Relative time string for the most recent sync, e.g. "2 hours ago". Empty if never synced. */
    val lastSyncRelativeTime: String = "",
    /** New tracks the most recent sync added, or null if never synced. */
    val lastSyncTrackCount: Int? = null,
    /** Short health label: "✓ healthy", "! partial", "× failed", or "". */
    val lastSyncHealthLabel: String = "",
    /** Tint colour for [lastSyncHealthLabel]. */
    val lastSyncHealthColor: Color = Color.Transparent,

    // -- SyncStatusCard inputs (relocated from HomeUiState) -------------------
    /**
     * Aggregated stats + display status driving the
     * [com.stash.feature.sync.components.SyncStatusCard] at the top of
     * this screen.
     */
    val syncStatus: SyncStatusInfo = SyncStatusInfo(),
    /**
     * True after at least one sync has completed. Drives the
     * "Tap Sync Now" prompt vs. the stats grid in [SyncStatusCard].
     */
    val hasEverSynced: Boolean = false,
)

/**
 * What the "Undo last sync" card shows: the restore point taken before the
 * last sync, and how many playlists that sync changed.
 */
data class UndoCard(
    val syncId: Long,
    val createdAtEpochMs: Long,
    val changedPlaylists: Int,
)

/** A sync is under way, waiting for its network included: not idle, done or failed. */
internal fun SyncPhase.isActive(): Boolean =
    this !is SyncPhase.Idle && this !is SyncPhase.Completed && this !is SyncPhase.Error
