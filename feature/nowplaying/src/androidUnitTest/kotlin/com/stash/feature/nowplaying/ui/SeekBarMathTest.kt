package com.stash.feature.nowplaying.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seek bar's pure rules: where a fraction lands, when a held seek target lets go, the
 * accessibility ±10 s steps, and where the scrub bubble sits.
 */
class SeekBarMathTest {

    @Test fun `a fraction lands inside the track`() {
        assertEquals(90_000L, seekTargetMs(0.5f, 180_000L))
        assertEquals(0L, seekTargetMs(-0.2f, 180_000L))
        assertEquals(180_000L, seekTargetMs(1.4f, 180_000L))
        assertEquals(0L, seekTargetMs(0.5f, 0L))
    }

    @Test fun `a released seek holds until the reported progress reaches it`() {
        // Still the old position a frame after release: keep showing the target (no snap back).
        assertFalse(seekArrived(progress = 0.20f, target = 0.75f))
        assertTrue(seekArrived(progress = 0.745f, target = 0.75f))
    }

    @Test fun `the ten second steps stay inside the track`() {
        assertEquals(20_000L, seekStepMs(30_000L, 180_000L, -SEEK_STEP_MS))
        assertEquals(0L, seekStepMs(4_000L, 180_000L, -SEEK_STEP_MS))
        assertEquals(180_000L, seekStepMs(175_000L, 180_000L, SEEK_STEP_MS))
    }

    @Test fun `the scrub bubble centres on the thumb`() {
        assertEquals(450f, scrubBubbleLeft(thumbX = 500f, bubbleWidth = 100f, barWidth = 1_000f), 0.001f)
    }

    @Test fun `the scrub bubble stays over the bar at either end`() {
        assertEquals(0f, scrubBubbleLeft(thumbX = 10f, bubbleWidth = 100f, barWidth = 1_000f), 0.001f)
        assertEquals(900f, scrubBubbleLeft(thumbX = 990f, bubbleWidth = 100f, barWidth = 1_000f), 0.001f)
        assertEquals(900f, scrubBubbleLeft(thumbX = 1_000f, bubbleWidth = 100f, barWidth = 1_000f), 0.001f)
    }

    @Test fun `a bubble wider than the bar centres on it`() {
        assertEquals(-20f, scrubBubbleLeft(thumbX = 0f, bubbleWidth = 140f, barWidth = 100f), 0.001f)
    }
}
