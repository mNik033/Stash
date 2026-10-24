package com.stash.core.model.weblink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which clock samples a device keeps (audit A5): a clock set since the last ones starts them again; network noise doesn't. */
class ClockOffsetTest {
    private val hour = 3_600_000L

    /** An exchange with a 200 ms round trip when this device's clock is [skew] behind the server's. */
    private fun sample(serverTime: Long, skew: Long) = ClockOffset.Sample(serverTime - skew - 100, serverTime - skew + 100, serverTime)

    @Test fun `a clock set forward starts the samples again, so the offset follows at once`() {
        var kept = emptyList<ClockOffset.Sample>()
        for (i in 0 until 5) kept = ClockOffset.keep(kept, sample(10 * hour + i * 60_000, skew = 2 * hour))
        assertThat(ClockOffset.of(kept)).isEqualTo(2 * hour)
        // Automatic time turned on: the clock jumps 2 h forward. The lower median of all six would still say +2 h.
        val next = sample(11 * hour, skew = 0)
        assertThat(ClockOffset.of(kept + next)).isEqualTo(2 * hour)
        kept = ClockOffset.keep(kept, next)
        assertThat(kept).containsExactly(next)
        assertThat(ClockOffset.of(kept)).isEqualTo(0)
    }

    @Test fun `network noise is kept, and only the newest five count`() {
        var kept = emptyList<ClockOffset.Sample>()
        for (i in 0 until 7) kept = ClockOffset.keep(kept, sample(10 * hour + i * 60_000, skew = 1_000L * (i % 3)))
        assertThat(kept).hasSize(ClockOffset.SAMPLES)
        assertThat(kept.last().serverTime).isEqualTo(10 * hour + 6 * 60_000)
    }
}
