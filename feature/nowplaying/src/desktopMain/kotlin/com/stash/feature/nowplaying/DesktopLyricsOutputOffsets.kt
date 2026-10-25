package com.stash.feature.nowplaying

import com.stash.feature.nowplaying.ui.LyricsOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopLyricsOutputOffsets @Inject constructor() : LyricsOutputOffsets {
    override val output: Flow<LyricsOutput?> = flowOf(null)
    override fun offsetMs(output: LyricsOutput): Flow<Long> = flowOf(0L)
    override suspend fun setOffsetMs(output: LyricsOutput, offsetMs: Long) = Unit
}
