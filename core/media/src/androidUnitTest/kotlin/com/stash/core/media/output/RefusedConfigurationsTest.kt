package com.stash.core.media.output

import android.media.AudioDeviceInfo
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * A configuration Android or the DAC refused is not planned again until the
 * DAC is replugged or the mode changes — the recheck that follows a refusal
 * used to rebuild the stream into the same refusal forever. Mirrors
 * HiResOutputController.recheck: a stream is rebuilt only when
 * `live.needsRebuildFor(screen(fresh))`.
 */
class RefusedConfigurationsTest {

    private fun device(id: Int): AudioDeviceInfo = mockk { every { this@mockk.id } returns id }

    private val dac = device(7)

    private fun bitPerfect(device: AudioDeviceInfo = dac, rate: Int = 96_000, index: Int = 2): OutputPlan {
        val option = MixerOption(SampleFormat.INT24, rate, 2, bitPerfect = true, index = index)
        return OutputPlan(MixerDecision.Configure(option, SampleFormat.INT24), SampleFormat.INT24, device, Any())
    }

    private fun rateMatched(device: AudioDeviceInfo = dac): OutputPlan {
        val option = MixerOption(SampleFormat.INT32, 96_000, 2, bitPerfect = false, index = 4)
        return OutputPlan(MixerDecision.Configure(option, SampleFormat.FLOAT), SampleFormat.FLOAT, device, Any())
    }

    private val refusals = RefusedConfigurations()

    @Test fun `a refused configuration is planned as the shared fallback`() {
        val plan = bitPerfect()
        refusals.remember(plan)
        val screened = refusals.screen(bitPerfect())
        assertThat(screened.decision).isEqualTo(MixerDecision.Shared(MixerDecision.Reason.REJECTED))
        assertThat(screened.trackFormat).isEqualTo(SampleFormat.FLOAT)
        assertThat(screened.device).isSameInstanceAs(dac)
    }

    @Test fun `after a refused mixer preference a recheck does not rebuild`() {
        // activate(): the track is already open in INT24 when Android says no.
        val plan = bitPerfect()
        refusals.remember(plan)
        val live = plan.rejected()
        assertThat(live.needsRebuildFor(refusals.screen(bitPerfect()))).isFalse()
    }

    @Test fun `after a refused rate-matched preference a recheck does not rebuild`() {
        val plan = rateMatched()
        refusals.remember(plan)
        val live = plan.rejected()
        val fresh = refusals.screen(rateMatched())
        assertThat(fresh.key).isEqualTo(live.key)
        assertThat(live.needsRebuildFor(fresh)).isFalse()
    }

    @Test fun `after a refused AudioTrack format a recheck does not rebuild`() {
        // StashAudioOutputProvider: the INT24 track wouldn't open, float did.
        val plan = bitPerfect()
        refusals.remember(plan)
        val live = plan.rejected(SampleFormat.FLOAT)
        val fresh = refusals.screen(bitPerfect())
        assertThat(fresh.key).isEqualTo(live.key)
        assertThat(live.needsRebuildFor(fresh)).isFalse()
    }

    @Test fun `without the memory the refused plan would be rebuilt`() {
        // The loop this class exists to stop.
        val live = bitPerfect().rejected(SampleFormat.FLOAT)
        assertThat(live.needsRebuildFor(bitPerfect())).isTrue()
    }

    @Test fun `replugging the DAC retries the configuration`() {
        val plan = bitPerfect()
        refusals.remember(plan)
        val live = plan.rejected(SampleFormat.FLOAT)
        refusals.forgetDevice(7)
        val fresh = refusals.screen(bitPerfect())
        assertThat(fresh.decision).isInstanceOf(MixerDecision.Configure::class.java)
        assertThat(live.needsRebuildFor(fresh)).isTrue()
    }

    @Test fun `another device coming or going keeps the refusal`() {
        refusals.remember(bitPerfect())
        refusals.forgetDevice(3)
        assertThat(refusals.screen(bitPerfect()).decision)
            .isEqualTo(MixerDecision.Shared(MixerDecision.Reason.REJECTED))
    }

    @Test fun `a mode change retries the configuration`() {
        refusals.remember(bitPerfect())
        refusals.clear()
        assertThat(refusals.screen(bitPerfect()).decision).isInstanceOf(MixerDecision.Configure::class.java)
    }

    @Test fun `a refusal covers only that device and configuration`() {
        refusals.remember(bitPerfect())
        // Another song's rate on the same DAC, and the same configuration on another DAC.
        assertThat(refusals.screen(bitPerfect(rate = 44_100, index = 0)).decision)
            .isInstanceOf(MixerDecision.Configure::class.java)
        assertThat(refusals.screen(bitPerfect(device = device(9))).decision)
            .isInstanceOf(MixerDecision.Configure::class.java)
    }

    @Test fun `shared plans are never screened`() {
        val shared = OutputPlan(MixerDecision.Shared(MixerDecision.Reason.NOT_USB), SampleFormat.FLOAT, dac, null)
        refusals.remember(shared)
        assertThat(refusals.screen(shared)).isSameInstanceAs(shared)
    }
}
