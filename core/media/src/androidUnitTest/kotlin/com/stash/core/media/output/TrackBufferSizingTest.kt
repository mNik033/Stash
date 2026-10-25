package com.stash.core.media.output

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioOutputProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The AudioTrack's buffer is Media3's 1x size up to 2x speed (EQ changes and a
 * gapless song's loudness gain are heard within 250–750 ms again) and doubles
 * above, where AudioFlinger's time-stretch drains it faster.
 */
class TrackBufferSizingTest {

    /** What DefaultAudioSink hands the provider: float stereo at 48 kHz, 250 ms (12,000 frames). */
    private fun sinkConfig(bufferSize: Int = 12_000 * 8) = AudioOutputProvider.OutputConfig.Builder()
        .setEncoding(C.ENCODING_PCM_FLOAT)
        .setSampleRate(48_000)
        .setChannelMask(STEREO)
        .setBufferSize(bufferSize)
        .build()

    @Test fun `everyday speeds and Listen Together's nudges keep the 1x size`() {
        listOf(0.1f, 0.5f, 0.97f, 1f, 1.03f, 1.5f, 2f).forEach {
            assertThat(TrackBufferSizing.tierFor(it)).isEqualTo(1)
        }
    }

    @Test fun `above 2x, up to Stash's 4x, the buffer doubles`() {
        listOf(2.01f, 3f, 4f).forEach { assertThat(TrackBufferSizing.tierFor(it)).isEqualTo(2) }
    }

    @Test fun `a float track at 1x opens with the sink's own config`() {
        val config = sinkConfig()
        assertThat(trackConfig(config, SampleFormat.FLOAT, SampleFormat.FLOAT, 2, tier = 1)).isSameInstanceAs(config)
    }

    @Test fun `the doubled tier holds twice the frames`() {
        val track = trackConfig(sinkConfig(), SampleFormat.FLOAT, SampleFormat.FLOAT, 2, tier = 2)
        assertThat(track.bufferSize).isEqualTo(24_000 * 8)
        assertThat(track.encoding).isEqualTo(C.ENCODING_PCM_FLOAT)
        assertThat(track.sampleRate).isEqualTo(48_000)
    }

    @Test fun `a bit-perfect track keeps the frame count in its own encoding`() {
        // 12,000 float frames → 12,000 packed 24-bit frames (6 bytes each in stereo).
        val track = trackConfig(sinkConfig(), SampleFormat.FLOAT, SampleFormat.INT24, 2, tier = 1)
        assertThat(track.bufferSize).isEqualTo(12_000 * 6)
        assertThat(track.encoding).isEqualTo(C.ENCODING_PCM_24BIT)
    }

    @Test fun `a 16-bit decoder's buffer converts by frames, tier included`() {
        // 12,000 16-bit stereo frames (4 bytes each) → float, doubled: 24,000 frames of 8 bytes.
        val config = sinkConfig(bufferSize = 12_000 * 4)
        val track = trackConfig(config, SampleFormat.INT16, SampleFormat.FLOAT, 2, tier = 2)
        assertThat(track.bufferSize).isEqualTo(24_000 * 8)
    }

    private companion object {
        /** AudioFormat.CHANNEL_OUT_STEREO (FRONT_LEFT | FRONT_RIGHT). */
        const val STEREO = 0x4 or 0x8
    }
}
