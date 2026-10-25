package com.stash.feature.nowplaying.ui

import com.stash.data.lyrics.parser.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBarModeTest {

    private val lines = listOf(LrcLine(timestampMs = 1_000L, text = "hi"))

    @Test fun `synced maps to Live when the live bar is enabled`() {
        val mode = liveBarModeFor(LyricsViewState.Synced(lines, plainFallback = "hi"), liveEnabled = true)
        assertEquals(LiveBarMode.Live(lines), mode)
    }

    @Test fun `the live bar follows the same lines as the lyrics view, breaks included`() {
        val withBreaks = listOf(LrcLine(0L, "one"), LrcLine(2_000L, ""), LrcLine(3_000L, "two"), LrcLine(5_000L, ""))
        val mode = liveBarModeFor(LyricsViewState.Synced(withBreaks, plainFallback = ""), liveEnabled = true)
        val body = lyricsBodyFor(LyricsViewState.Synced(withBreaks, plainFallback = ""))
        assertEquals(LiveBarMode.Live(listOf(LrcLine(0L, "one"), LrcLine(3_000L, "two"), LrcLine(5_000L, ""))), mode)
        assertEquals((body as LyricsBody.Lines).lines, (mode as LiveBarMode.Live).lines)
    }

    @Test fun `synced maps to Static when the live bar is off (the default)`() {
        val mode = liveBarModeFor(LyricsViewState.Synced(lines, plainFallback = "hi"), liveEnabled = false)
        assertEquals(LiveBarMode.Static, mode)
    }

    @Test fun `plain maps to Static regardless of the toggle`() {
        assertEquals(LiveBarMode.Static, liveBarModeFor(LyricsViewState.Plain("text"), liveEnabled = true))
        assertEquals(LiveBarMode.Static, liveBarModeFor(LyricsViewState.Plain("text"), liveEnabled = false))
    }

    @Test fun `everything else maps to Hidden`() {
        assertEquals(LiveBarMode.Hidden, liveBarModeFor(LyricsViewState.Loading, liveEnabled = true))
        assertEquals(LiveBarMode.Hidden, liveBarModeFor(LyricsViewState.None, liveEnabled = true))
        assertEquals(LiveBarMode.Hidden, liveBarModeFor(LyricsViewState.Instrumental, liveEnabled = true))
        assertEquals(LiveBarMode.Hidden, liveBarModeFor(LyricsViewState.Error(retryable = true), liveEnabled = true))
    }
}
