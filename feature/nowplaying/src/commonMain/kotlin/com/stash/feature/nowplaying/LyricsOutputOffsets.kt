package com.stash.feature.nowplaying

import com.stash.feature.nowplaying.ui.LyricsOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The audio output lyrics are heard through, and the lyrics offset the listener set for it in
 * the sync dialog ("Apply to all songs on <device>"). Bluetooth headphones play noticeably later
 * than wired ones, so lyrics timed right on one are early on the other; the per-song offset can't
 * fix both, a per-output one can.
 */
interface LyricsOutputOffsets {
    /** The output playing now, or null while it isn't known (nothing has played yet). */
    val output: Flow<LyricsOutput?>

    /** [output]'s offset (signed ms; positive delays lyrics), 0 when none is set. */
    fun offsetMs(output: LyricsOutput): Flow<Long>

    suspend fun setOffsetMs(output: LyricsOutput, offsetMs: Long)

    /** No output known: the ViewModel's default, so unit tests see per-song offsets only. */
    object None : LyricsOutputOffsets {
        override val output: Flow<LyricsOutput?> = flowOf(null)
        override fun offsetMs(output: LyricsOutput): Flow<Long> = flowOf(0L)
        override suspend fun setOffsetMs(output: LyricsOutput, offsetMs: Long) = Unit
    }
}
