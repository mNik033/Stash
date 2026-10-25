package com.stash.feature.nowplaying.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LyricsOffsetsTest {

    // ── effectiveLyricsOffsetMs ──────────────────────────────────────────────────────────────

    @Test fun `the song's offset and the output's add up`() {
        assertEquals(1_300L, effectiveLyricsOffsetMs(songOffsetMs = 1_000L, outputOffsetMs = 300L))
        assertEquals(-200L, effectiveLyricsOffsetMs(songOffsetMs = -500L, outputOffsetMs = 300L))
    }

    @Test fun `a missing offset counts as none`() {
        assertEquals(300L, effectiveLyricsOffsetMs(songOffsetMs = null, outputOffsetMs = 300L)) // a stream
        assertEquals(1_000L, effectiveLyricsOffsetMs(songOffsetMs = 1_000L, outputOffsetMs = null)) // output unknown
        assertEquals(0L, effectiveLyricsOffsetMs(null, null))
    }

    @Test fun `the sum stays within a song offset's cap`() {
        assertEquals(LYRICS_OFFSET_CAP_MS, effectiveLyricsOffsetMs(59_000L, 5_000L))
        assertEquals(-LYRICS_OFFSET_CAP_MS, effectiveLyricsOffsetMs(-58_000L, -OUTPUT_OFFSET_CAP_MS))
    }

    // ── clampOutputOffsetMs ──────────────────────────────────────────────────────────────────

    @Test fun `an output offset is held within ten seconds either way`() {
        assertEquals(350L, clampOutputOffsetMs(350L))
        assertEquals(OUTPUT_OFFSET_CAP_MS, clampOutputOffsetMs(30_000L))
        assertEquals(-OUTPUT_OFFSET_CAP_MS, clampOutputOffsetMs(-30_000L))
    }

    // ── lyricsPositionMs ─────────────────────────────────────────────────────────────────────

    @Test fun `a positive offset holds the lyrics back`() {
        assertEquals(9_700L, lyricsPositionMs(playerPositionMs = 10_000L, offsetMs = 300L))
    }

    @Test fun `a negative offset brings them forward`() {
        assertEquals(10_500L, lyricsPositionMs(playerPositionMs = 10_000L, offsetMs = -500L))
    }

    @Test fun `the lyrics position never goes below the start`() {
        assertEquals(0L, lyricsPositionMs(playerPositionMs = 100L, offsetMs = 300L))
    }

    // ── lyricsSeekTargetMs ───────────────────────────────────────────────────────────────────

    @Test fun `a tapped line seeks to where it is heard`() {
        assertEquals(20_300L, lyricsSeekTargetMs(lineMs = 20_000L, offsetMs = 300L))
        assertEquals(19_500L, lyricsSeekTargetMs(lineMs = 20_000L, offsetMs = -500L))
    }

    @Test fun `seeking to a tapped line makes it the line the lyrics are on`() {
        for (offset in listOf(-2_000L, -300L, 0L, 300L, 1_300L)) {
            val target = lyricsSeekTargetMs(lineMs = 20_000L, offsetMs = offset)
            assertEquals(20_000L, lyricsPositionMs(playerPositionMs = target, offsetMs = offset))
        }
    }

    @Test fun `a seek never goes before the start`() {
        assertEquals(0L, lyricsSeekTargetMs(lineMs = 200L, offsetMs = -500L))
    }

    // ── lyricsOutputOf ───────────────────────────────────────────────────────────────────────

    @Test fun `an output is keyed by its kind and name and shows its name`() {
        val headphones = lyricsOutputOf("BLUETOOTH", "WH-1000XM4")
        assertEquals("BLUETOOTH:WH-1000XM4", headphones.key)
        assertEquals("WH-1000XM4", headphones.name)
    }

    @Test fun `the same device keys the same every time, stray spaces aside`() {
        assertEquals(lyricsOutputOf("BLUETOOTH", "Pixel Buds"), lyricsOutputOf("BLUETOOTH", " Pixel Buds "))
    }

    @Test fun `a different kind is a different output even under the same name`() {
        assertNotEquals(lyricsOutputOf("BLUETOOTH", "Studio").key, lyricsOutputOf("USB", "Studio").key)
    }
}
