package com.stash.core.media.output

import android.media.AudioDeviceInfo
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * [OutputPlan.key] describes what the hardware does, nothing more: a change
 * of reason alone (STANDARD → MATCH while on the speaker or Bluetooth, or the
 * stored mode loading after a cold start) must not rebuild the stream.
 */
class OutputPlanKeyTest {

    private fun device(id: Int): AudioDeviceInfo = mockk { every { this@mockk.id } returns id }

    private fun shared(reason: MixerDecision.Reason, format: SampleFormat = SampleFormat.FLOAT, id: Int = 1) =
        OutputPlan(MixerDecision.Shared(reason), format, device(id), null)

    private fun configured(): OutputPlan {
        val option = MixerOption(SampleFormat.INT24, 96_000, 2, bitPerfect = true, index = 2)
        return OutputPlan(MixerDecision.Configure(option, SampleFormat.INT24), SampleFormat.INT24, device(7), Any())
    }

    @Test fun `shared plans with the same track format share a key whatever the reason`() {
        val keys = MixerDecision.Reason.entries.map { shared(it).key }.toSet()
        assertThat(keys).hasSize(1)
    }

    @Test fun `switching STANDARD to MATCH off USB does not rebuild`() {
        val live = shared(MixerDecision.Reason.MODE_STANDARD)
        val fresh = shared(MixerDecision.Reason.NOT_USB)
        assertThat(live.needsRebuildFor(fresh)).isFalse()
    }

    @Test fun `moving between non-USB outputs does not rebuild`() {
        // Speaker → Bluetooth: Android reroutes the shared mixer itself.
        assertThat(shared(MixerDecision.Reason.NOT_USB, id = 1).needsRebuildFor(shared(MixerDecision.Reason.NOT_USB, id = 2)))
            .isFalse()
    }

    @Test fun `the reason stays on the plan for the signal path`() {
        val plan = shared(MixerDecision.Reason.RATE_UNSUPPORTED_BY_DAC)
        assertThat((plan.decision as MixerDecision.Shared).reason)
            .isEqualTo(MixerDecision.Reason.RATE_UNSUPPORTED_BY_DAC)
    }

    @Test fun `on the shared mixer the track format is not part of the set-up`() {
        // A shared stream opens in the source's own format (Android converts it),
        // so the planner's format and the live track's can differ: keying on it
        // made every recheck rebuild the stream.
        assertThat(shared(MixerDecision.Reason.MODE_STANDARD).key)
            .isEqualTo(shared(MixerDecision.Reason.MODE_STANDARD, SampleFormat.INT16).key)
    }

    @Test fun `a configured mixer in a different format is a different set-up`() {
        val option = MixerOption(SampleFormat.INT16, 96_000, 2, bitPerfect = false, index = 1)
        val pcm16 = OutputPlan(MixerDecision.Configure(option, SampleFormat.INT16), SampleFormat.INT16, device(7), Any())
        assertThat(configured().key).isNotEqualTo(pcm16.key)
    }

    @Test fun `going to or from a configured USB mixer rebuilds`() {
        val configured = configured()
        val shared = shared(MixerDecision.Reason.NOT_USB)
        assertThat(shared.needsRebuildFor(configured)).isTrue()
        assertThat(configured.needsRebuildFor(shared)).isTrue()
    }

    @Test fun `a rejected stream already on the shared mixer is not rebuilt for its format`() {
        // Refused, the stream fell back to the shared mixer in the refused format;
        // a plain shared plan changes nothing the hardware does, so no re-seek.
        val live = configured().rejected()
        assertThat(live.needsRebuildFor(shared(MixerDecision.Reason.MODE_STANDARD))).isFalse()
    }

    @Test fun `a rejected stream still follows a configured mixer that is offered again`() {
        val live = configured().rejected()
        assertThat(live.needsRebuildFor(configured())).isTrue()
    }
}
