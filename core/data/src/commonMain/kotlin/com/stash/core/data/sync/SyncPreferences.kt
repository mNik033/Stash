package com.stash.core.data.sync

import com.stash.core.model.SyncMode

/**
 * User-configurable sync scheduling preferences backed by DataStore.
 *
 * @property syncHour   Hour of day (0-23) for the scheduled daily sync.
 * @property syncMinute Minute of hour (0-59) for the scheduled daily sync.
 * @property autoSyncEnabled Whether the daily auto-sync is active.
 * @property wifiOnly   Whether sync should only run on unmetered (Wi-Fi) connections.
 * @property spotifySyncMode Refresh/accumulate mode applied to Spotify-sourced playlists.
 * @property youtubeSyncMode Refresh/accumulate mode applied to YouTube-sourced playlists.
 * @property youtubeLikedStudioOnly When true, filters UGC, cover, live, and podcast tracks from YT Music Liked Songs sync.
 * @property syncDays 7-bit bitmask of days the auto-sync should run (bit 0 = Monday … bit 6 = Sunday).
 * @property autoSyncPausedAfterUndo Auto-sync skips its runs until the user next taps Sync.
 */
data class SyncPreferences(
    val syncHour: Int = 6,
    val syncMinute: Int = 0,
    val autoSyncEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val spotifySyncMode: SyncMode = SyncMode.ACCUMULATE,
    val youtubeSyncMode: SyncMode = SyncMode.ACCUMULATE,
    /**
     * When true, the YouTube Music Liked Songs sync filters out UGC, cover,
     * live, and podcast tracks (anything that isn't ATV / OMV /
     * OFFICIAL_SOURCE_MUSIC). Other YT Music content (Home Mixes, custom
     * user playlists) is unaffected. Defaults to false — everything syncs.
     */
    val youtubeLikedStudioOnly: Boolean = false,
    /**
     * 7-bit bitmask of days the auto-sync should run. Bit 0 = Monday … bit 6 = Sunday.
     * Default 0b1111111 (127) = every day, matching the prior daily behavior.
     * 0 means no day is enabled — UI surfaces a "pick at least one day" hint and
     * the scheduler does not enqueue work.
     */
    val syncDays: Int = 0b1111111,
    /**
     * Set by "Undo last sync", cleared by the next "Sync now". While set, the
     * daily Auto-sync skips its runs: otherwise it would redo the sync the user
     * just undid, hours later and unasked. A manual Sync is the user saying the
     * library is right again, so it resumes the schedule.
     */
    val autoSyncPausedAfterUndo: Boolean = false,
)
