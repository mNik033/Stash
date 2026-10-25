package com.stash.core.media.output

import java.util.concurrent.ConcurrentHashMap

/**
 * Mixer configurations Android or the DAC refused, so no stream is planned
 * onto one again until something has changed.
 *
 * A refusal degrades the stream to the shared mixer ([StashAudioOutput.onMixerRejected],
 * or the AudioTrack fallback in [StashAudioOutputProvider]). Without this
 * memory the next recheck — a routing callback, a device event, a speed
 * change — planned the refused configuration afresh, saw it differ from the
 * degraded stream and rebuilt it, only to be refused again: an audible
 * re-seek, over and over, that never became an error the player would stop on.
 *
 * Kept per device and configuration (a configured [OutputPlan.key] names
 * both) and forgotten when that device is unplugged or re-attached, or the
 * mode changes, so a replug or a new setting tries again. Thread-safe: plans
 * are made on the players' playback threads, device events arrive on main.
 */
internal class RefusedConfigurations {
    /** Refused [OutputPlan.key] → the id of the device that refused it. */
    private val refused = ConcurrentHashMap<String, Int>()

    /** [plan] was refused: plans for its device and configuration fall back from now on. */
    fun remember(plan: OutputPlan) {
        val device = plan.device ?: return
        if (plan.decision is MixerDecision.Configure) refused[plan.key] = device.id
    }

    /** [plan], or the shared fallback when its device already refused its configuration. */
    fun screen(plan: OutputPlan): OutputPlan =
        if (plan.decision is MixerDecision.Configure && refused.containsKey(plan.key)) {
            plan.rejected(SampleFormat.FLOAT)
        } else {
            plan
        }

    /** The device with [deviceId] went away or came back: let it be asked again. */
    fun forgetDevice(deviceId: Int) {
        refused.values.removeAll { it == deviceId }
    }

    /** Forget every refusal (the mode changed). */
    fun clear() = refused.clear()
}
