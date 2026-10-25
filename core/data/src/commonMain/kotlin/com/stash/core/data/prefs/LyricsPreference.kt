package com.stash.core.data.prefs

import kotlinx.coroutines.flow.Flow

/** Which source family the lyrics chain is allowed to use. See [LyricsPreference.sourcePreference]. */
enum class LyricsSourcePreference {
    /** Default. Apple Music (word-synced TTML) is consulted first, LRC sources fall back. */
    APPLE_MUSIC,
    /** Apple Music is never consulted — LRCLIB/KuGou/YT only. */
    LRC_ONLY,
}

/**
 * Lyrics display and sync preferences contract.
 */
interface LyricsPreference {
    val liveBarEnabled: Flow<Boolean>
    suspend fun setLiveBarEnabled(value: Boolean)

    val sourcePreference: Flow<LyricsSourcePreference>
    suspend fun setSourcePreference(value: LyricsSourcePreference)

    fun outputOffsetMs(outputKey: String): Flow<Long>
    suspend fun setOutputOffsetMs(outputKey: String, offsetMs: Long)

    val bulkFetchCursor: Flow<Long>
    suspend fun setBulkFetchCursor(trackId: Long)

    val ttmlUpgradeCursor: Flow<Long>
    suspend fun setTtmlUpgradeCursor(trackId: Long)

    val recheckCursor: Flow<Long>
    suspend fun setRecheckCursor(trackId: Long)

    val recheckFinishedAt: Flow<Long?>
    suspend fun finishRecheck(atMs: Long)
}
