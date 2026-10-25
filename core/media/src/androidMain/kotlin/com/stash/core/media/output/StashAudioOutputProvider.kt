package com.stash.core.media.output

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import com.stash.core.media.equalizer.dsp.FloatDspChain

/**
 * Wraps Media3's AudioTrack provider so every PCM stream goes through
 * [StashAudioOutput], opened in the format [HiResOutputController] planned.
 *
 * `DefaultAudioSink` keeps believing the AudioTrack takes the format *it*
 * produced (float, or 16-bit when a decoder ignores the float request): the
 * [OutputConfig] it holds is untouched, and only the config used to open the
 * real AudioTrack carries the planned encoding and the buffer for the player's
 * speed ([trackConfig]). Offload, passthrough and tunneling are not PCM and
 * pass straight through.
 */
@OptIn(UnstableApi::class)
class StashAudioOutputProvider(
    delegate: AudioOutputProvider,
    private val controller: HiResOutputController,
    private val session: OutputSession,
    private val newDspChain: () -> FloatDspChain,
) : ForwardingAudioOutputProvider(delegate) {

    override fun getAudioOutput(config: OutputConfig): AudioOutput {
        val inFormat = when (config.encoding) {
            C.ENCODING_PCM_FLOAT -> SampleFormat.FLOAT
            C.ENCODING_PCM_16BIT -> SampleFormat.INT16
            else -> null
        }
        val channels = Integer.bitCount(config.channelMask)
        if (inFormat == null || config.isOffload || config.isTunneling || channels <= 0) {
            return super.getAudioOutput(config)
        }
        val attributes = config.audioAttributes.platformAudioAttributes
        // The plan and the buffer both follow the player's speed right now.
        val tier = TrackBufferSizing.tierFor(session.speed)
        var plan = controller.prepare(config.sampleRate, channels, session, attributes)
        // Shared mixer: open the track in the source's own format. Android converts it,
        // and no conversion runs in our stage.
        if (plan.decision is MixerDecision.Shared && plan.trackFormat != inFormat) {
            plan = plan.withTrackFormat(inFormat)
        }
        val inner = try {
            super.getAudioOutput(trackConfig(config, inFormat, plan.trackFormat, channels, tier))
        } catch (e: AudioOutputProvider.InitializationException) {
            if (plan.trackFormat == inFormat) throw e
            // A device that lists a bit-perfect format but won't open an
            // AudioTrack in it: play, on the shared mixer, rather than not at all.
            // Remembered, or the next recheck would plan the same format again
            // and rebuild the stream into the same refusal, over and over.
            Log.w(TAG, "AudioTrack in ${plan.trackFormat} refused; falling back to float", e)
            controller.onTrackRefused(plan)
            plan = plan.rejected(inFormat)
            super.getAudioOutput(trackConfig(config, inFormat, inFormat, channels, tier))
        }
        return StashAudioOutput(
            inner = inner,
            inFormat = inFormat,
            outFormat = plan.trackFormat,
            channelCount = channels,
            streamSampleRate = config.sampleRate,
            plan = plan,
            dsp = newDspChain(),
            controller = controller,
            session = session,
            platformAttributes = attributes,
            bufferTier = tier,
        )
    }

    private companion object {
        const val TAG = "StashAudioOutput"
    }
}

/**
 * The config the AudioTrack is really opened with: [trackFormat]'s encoding,
 * and [tier] times the frames `DefaultAudioSink` sized at 1x (see
 * [TrackBufferSizing]). The sink keeps [config] as it is: it compares configs,
 * buffer size included, to decide whether the next song can keep the track,
 * so a size that moved with the speed would open a new track at a gapless
 * seam.
 */
@OptIn(UnstableApi::class)
internal fun trackConfig(
    config: OutputConfig,
    inFormat: SampleFormat,
    trackFormat: SampleFormat,
    channels: Int,
    tier: Int,
): OutputConfig {
    if (trackFormat == inFormat && tier == 1) return config
    // Same number of frames (times the tier) in the track's encoding.
    val frames = config.bufferSize / inFormat.frameSize(channels)
    return config.buildUpon()
        .setEncoding(trackFormat.toMedia3Encoding())
        .setBufferSize(frames * tier * trackFormat.frameSize(channels))
        .build()
}

private fun SampleFormat.toMedia3Encoding(): Int = when (this) {
    SampleFormat.INT16 -> C.ENCODING_PCM_16BIT
    SampleFormat.INT24 -> C.ENCODING_PCM_24BIT
    SampleFormat.INT32 -> C.ENCODING_PCM_32BIT
    SampleFormat.FLOAT -> C.ENCODING_PCM_FLOAT
}
