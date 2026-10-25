package com.stash.feature.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The sleep timer's live readout: the caption under the moon, the sheet's clock, what TalkBack
 * says, and when each next changes (so the screen wakes once a minute, then once a second in the
 * final minute, and never between).
 */
class SleepTimerReadoutTest {

    private fun min(m: Long) = m * 60_000L
    private fun sec(s: Long) = s * 1_000L

    @Test fun `the caption counts whole minutes, rounded up`() {
        assertEquals("23m", sleepTimerBadge(min(22) + sec(1)))
        assertEquals("23m", sleepTimerBadge(min(23)))
        assertEquals("2m", sleepTimerBadge(min(1) + 1))
    }

    @Test fun `the caption shows hours past an hour`() {
        assertEquals("1h 5m", sleepTimerBadge(min(65)))
        assertEquals("3h", sleepTimerBadge(min(180)))
        assertEquals("2h 59m", sleepTimerBadge(min(179)))
    }

    @Test fun `the final minute is a seconds clock that never reads zero early`() {
        assertEquals("1:00", sleepTimerBadge(min(1)))
        assertEquals("0:45", sleepTimerBadge(sec(44) + 1))
        assertEquals("0:01", sleepTimerBadge(1))
        assertEquals("0:00", sleepTimerBadge(0))
    }

    @Test fun `the sheet's clock shows minutes and seconds, and hours when there are any`() {
        assertEquals("22:41", sleepTimerClock(min(22) + sec(41)))
        assertEquals("1:02:03", sleepTimerClock(min(62) + sec(3)))
        assertEquals("0:45", sleepTimerClock(sec(45)))
        assertEquals("0:01", sleepTimerClock(10))
    }

    @Test fun `TalkBack hears words, not a time of day`() {
        assertEquals("23 minutes", sleepTimerSpoken(min(22) + sec(1)))
        assertEquals("1 hour 5 minutes", sleepTimerSpoken(min(65)))
        assertEquals("2 hours", sleepTimerSpoken(min(120)))
        assertEquals("45 seconds", sleepTimerSpoken(sec(45)))
        assertEquals("1 minute", sleepTimerSpoken(min(1), withSeconds = true))
        assertEquals("22 minutes 41 seconds", sleepTimerSpoken(min(22) + sec(41), withSeconds = true))
        assertEquals("1 hour 1 second", sleepTimerSpoken(min(60) + sec(1), withSeconds = true))
        assertEquals("0 seconds", sleepTimerSpoken(0, withSeconds = true))
    }

    @Test fun `the caption wakes at the next minute boundary, then every second in the final minute`() {
        // 22:01 shows 23m until 22:00 is left.
        assertEquals(sec(1), msUntilSleepReadoutChanges(min(22) + sec(1)))
        // At a whole minute the label just changed: the next change is a full minute away.
        assertEquals(min(1), msUntilSleepReadoutChanges(min(23)))
        // 1:00.5 shows 2m until 1:00 is left, where the seconds clock takes over.
        assertEquals(500L, msUntilSleepReadoutChanges(min(1) + 500))
        assertEquals(sec(1), msUntilSleepReadoutChanges(min(1)))
        assertEquals(250L, msUntilSleepReadoutChanges(sec(44) + 250))
    }

    @Test fun `the sheet's clock wakes every second`() {
        assertEquals(400L, msUntilSleepReadoutChanges(min(22) + 400, everySecond = true))
        assertEquals(sec(1), msUntilSleepReadoutChanges(min(22), everySecond = true))
    }

    @Test fun `nothing left, nothing to wake for`() {
        assertNull(msUntilSleepReadoutChanges(0))
        assertNull(msUntilSleepReadoutChanges(-5, everySecond = true))
    }
}
