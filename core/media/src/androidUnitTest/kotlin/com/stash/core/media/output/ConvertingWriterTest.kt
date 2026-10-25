package com.stash.core.media.output

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test

/**
 * The writer's contract with Media3: the input position advances only by
 * whole frames the output has really accepted, so DefaultAudioSink's
 * written-frame count (and with it the playback position and end-of-track
 * detection) stays exact even when the output takes partial writes.
 */
class ConvertingWriterTest {

    /** An output that accepts at most [capacityBytes] per call, like a full AudioTrack. */
    private class FakeOutput(var capacityBytes: Int) {
        val received = ByteBuffer.allocate(1 shl 20).order(ByteOrder.nativeOrder())
        fun write(buf: ByteBuffer) {
            val n = minOf(capacityBytes, buf.remaining())
            repeat(n) { received.put(buf.get()) }
        }
    }

    private fun int16Stereo(frames: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) { b.putShort(i.toShort()); b.putShort((-i).toShort()) }
        b.flip()
        return b
    }

    private val identity: (FloatArray, Int) -> Unit = { _, _ -> }

    @Test fun `int16 to float with unlimited output consumes everything in one call`() {
        val w = ConvertingWriter(SampleFormat.INT16, SampleFormat.FLOAT, 2, maxChunkFrames = 64)
        val out = FakeOutput(Int.MAX_VALUE)
        val input = int16Stereo(1000)
        assertThat(w.write(input, identity, out::write)).isTrue()
        assertThat(input.hasRemaining()).isFalse()
        assertThat(out.received.position()).isEqualTo(1000 * 8)
        out.received.flip()
        assertThat(out.received.getFloat(8 * 999)).isEqualTo(999 / 32768f)
        assertThat(out.received.getFloat(8 * 999 + 4)).isEqualTo(-999 / 32768f)
    }

    @Test fun `input advances only by whole frames the output accepted`() {
        val w = ConvertingWriter(SampleFormat.INT16, SampleFormat.FLOAT, 2, maxChunkFrames = 64)
        // 8-byte float frames; accept 20 bytes per call = 2.5 frames.
        val out = FakeOutput(20)
        val input = int16Stereo(10)
        assertThat(w.write(input, identity, out::write)).isFalse()
        // 2 whole frames reached the output, so exactly 2 input frames (8 bytes) are consumed.
        assertThat(input.position()).isEqualTo(2 * 4)
        assertThat(w.write(input, identity, out::write)).isFalse()
        assertThat(input.position()).isEqualTo(5 * 4) // 40 bytes = 5 frames
        while (!w.write(input, identity, out::write)) Unit
        assertThat(input.position()).isEqualTo(10 * 4)
        assertThat(out.received.position()).isEqualTo(10 * 8)
    }

    @Test fun `a full output consumes nothing and keeps the chunk for the retry`() {
        val w = ConvertingWriter(SampleFormat.FLOAT, SampleFormat.INT24, 2, maxChunkFrames = 16)
        val out = FakeOutput(0)
        val input = ByteBuffer.allocateDirect(32 * 8).order(ByteOrder.nativeOrder())
        repeat(64) { input.putFloat(0.5f) }
        input.flip()
        var calls = 0
        val process: (FloatArray, Int) -> Unit = { _, _ -> calls++ }
        assertThat(w.write(input, process, out::write)).isFalse()
        assertThat(w.write(input, process, out::write)).isFalse()
        assertThat(input.position()).isEqualTo(0)
        assertThat(w.hasPending).isTrue()
        assertThat(calls).isEqualTo(1) // the retry did not re-process the chunk
        out.capacityBytes = Int.MAX_VALUE
        assertThat(w.write(input, process, out::write)).isTrue()
        assertThat(out.received.position()).isEqualTo(32 * 6)
        assertThat(calls).isEqualTo(2)
    }

    @Test fun `process sees each frame exactly once, in order`() {
        val w = ConvertingWriter(SampleFormat.INT16, SampleFormat.INT16, 2, maxChunkFrames = 7)
        val out = FakeOutput(13)
        val input = int16Stereo(50)
        val seen = ArrayList<Float>()
        val process: (FloatArray, Int) -> Unit = { s, f -> for (i in 0 until f) seen += s[i * 2] }
        while (!w.write(input, process, out::write)) Unit
        assertThat(seen.map { Math.round(it * 32768) }).isEqualTo((0 until 50).toList())
        out.received.flip()
        input.rewind()
        assertThat(out.received).isEqualTo(input) // identity process, same format: bit exact
    }

    @Test fun `flush drops the half-written chunk`() {
        val w = ConvertingWriter(SampleFormat.INT16, SampleFormat.FLOAT, 2)
        val out = FakeOutput(8)
        val input = int16Stereo(100)
        assertThat(w.write(input, identity, out::write)).isFalse()
        assertThat(w.hasPending).isTrue()
        w.flush()
        assertThat(w.hasPending).isFalse()
        val fresh = int16Stereo(4)
        out.capacityBytes = Int.MAX_VALUE
        assertThat(w.write(fresh, identity, out::write)).isTrue()
    }

    @Test fun `transform output reaches the device`() {
        val w = ConvertingWriter(SampleFormat.FLOAT, SampleFormat.FLOAT, 1)
        val out = FakeOutput(Int.MAX_VALUE)
        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).putFloat(0.5f).putFloat(-0.25f).apply { flip() }
        w.write(input, { s, f -> for (i in 0 until f) s[i] *= 2f }, out::write)
        out.received.flip()
        assertThat(out.received.getFloat(0)).isEqualTo(1f)
        assertThat(out.received.getFloat(4)).isEqualTo(-0.5f)
    }
}
