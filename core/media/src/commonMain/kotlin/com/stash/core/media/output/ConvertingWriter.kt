package com.stash.core.media.output

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Moves PCM from the audio sink's buffers to the platform output, converting
 * [inFormat] → float → [outFormat] and running a float transform (the DSP
 * chain, a software fade) on the way.
 *
 * The one subtle job is accounting. Media3's `DefaultAudioSink` counts what
 * it has written by how far each buffer's position advanced, and uses that
 * count for the playback position and for "has everything played out" at the
 * end of a track. Formats here can differ in size (2-byte int16 in, 4-byte
 * float out), and the platform output accepts partial writes. So the input
 * position only ever moves by whole frames the output has *actually*
 * accepted — never by what was merely converted and queued. Frames are 1:1
 * (no stage here changes duration), so the count stays exact.
 *
 * Not thread-safe; owned by one output, used on the playback thread.
 */
class ConvertingWriter(
    val inFormat: SampleFormat,
    val outFormat: SampleFormat,
    val channels: Int,
    private val maxChunkFrames: Int = DEFAULT_CHUNK_FRAMES,
) {
    private val inFrameSize = inFormat.frameSize(channels)
    private val outFrameSize = outFormat.frameSize(channels)
    private val scratch = FloatArray(maxChunkFrames * channels)
    private val pending: ByteBuffer = ByteBuffer
        .allocateDirect(maxChunkFrames * outFrameSize)
        .order(ByteOrder.nativeOrder())
        .apply { limit(0) }
    private var pendingFramesCommitted = 0

    /** Requantizer for integer outputs; null for float. */
    val quantizer: Quantizer? = if (outFormat.isInteger) Quantizer(outFormat) else null

    /** True while a converted chunk is only partly written to the output. */
    val hasPending: Boolean get() = pending.hasRemaining()

    /**
     * Writes as much of [input] as the output accepts.
     *
     * @param process runs on each chunk's float samples (interleaved, `frames`
     *   frames) before encoding; must not change the frame count.
     * @param sink writes as much of the buffer it is given as it can, advancing
     *   that buffer's position (exactly `AudioOutput.write`'s contract).
     * @return true once [input] is fully consumed. On false the caller must pass
     *   the same [input] again (or [flush]).
     */
    fun write(
        input: ByteBuffer,
        process: (samples: FloatArray, frames: Int) -> Unit,
        sink: (ByteBuffer) -> Unit,
    ): Boolean {
        while (true) {
            if (pending.hasRemaining()) {
                sink(pending)
                commitWritten(input)
                if (pending.hasRemaining()) return false
            }
            val frames = minOf(input.remaining() / inFrameSize, maxChunkFrames)
            if (frames == 0) {
                // A trailing partial frame can't be played; swallow it rather
                // than spin. (DefaultAudioSink only ever hands over whole frames.)
                input.position(input.limit())
                return true
            }
            PcmCodec.decode(input, inFormat, frames, channels, scratch)
            process(scratch, frames)
            pending.clear()
            PcmCodec.encode(scratch, frames * channels, outFormat, pending, quantizer)
            pending.flip()
            pendingFramesCommitted = 0
        }
    }

    /** Drops any half-written chunk and resets the requantizer (seek / flush). */
    fun flush() {
        pending.clear().limit(0)
        pendingFramesCommitted = 0
        quantizer?.reset()
    }

    /** Advances [input] by the whole frames of the pending chunk the output has taken so far. */
    private fun commitWritten(input: ByteBuffer) {
        val framesDone = pending.position() / outFrameSize
        val delta = framesDone - pendingFramesCommitted
        if (delta > 0) {
            input.position(input.position() + delta * inFrameSize)
            pendingFramesCommitted = framesDone
        }
    }

    companion object {
        /** ~46 ms at 44.1 kHz: small enough for L2, big enough to amortize the loop. */
        const val DEFAULT_CHUNK_FRAMES = 2048
    }
}
