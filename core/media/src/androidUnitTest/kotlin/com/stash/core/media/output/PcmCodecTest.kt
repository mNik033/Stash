package com.stash.core.media.output

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Test

class PcmCodecTest {

    private fun buffer(bytes: Int) = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())

    @Test fun `int16 to float to int16 is bit exact across the whole range`() {
        val n = 65536
        val src = buffer(n * 2)
        for (v in Short.MIN_VALUE..Short.MAX_VALUE) src.putShort(v.toShort())
        src.flip()
        val floats = FloatArray(n)
        PcmCodec.decode(src, SampleFormat.INT16, n, 1, floats)
        assertThat(src.position()).isEqualTo(0) // decode never consumes
        val out = buffer(n * 2)
        val q = Quantizer(SampleFormat.INT16)
        PcmCodec.encode(floats, n, SampleFormat.INT16, out, q)
        out.flip()
        src.rewind()
        assertThat(out).isEqualTo(src)
        assertThat(q.isDithering).isFalse()
    }

    @Test fun `int24 packed round trip is bit exact including sign extension`() {
        val values = intArrayOf(0, 1, -1, 8_388_607, -8_388_608, 123_456, -654_321, 255, -256)
        val src = buffer(values.size * 3)
        for (v in values) { src.put(v.toByte()); src.put((v shr 8).toByte()); src.put((v shr 16).toByte()) }
        src.flip()
        val floats = FloatArray(values.size)
        PcmCodec.decode(src, SampleFormat.INT24, values.size, 1, floats)
        assertThat(floats[3]).isEqualTo(8_388_607 / 8_388_608f)
        assertThat(floats[4]).isEqualTo(-1f)
        val out = buffer(values.size * 3)
        PcmCodec.encode(floats, values.size, SampleFormat.INT24, out, Quantizer(SampleFormat.INT24))
        out.flip()
        src.rewind()
        assertThat(out).isEqualTo(src)
    }

    @Test fun `16-bit samples written as 24 and 32 bit are zero padded, not altered`() {
        val floats = floatArrayOf(1234 / 32768f, -32768 / 32768f, 32767 / 32768f)
        val o24 = buffer(9)
        PcmCodec.encode(floats, 3, SampleFormat.INT24, o24, Quantizer(SampleFormat.INT24))
        o24.flip()
        val back24 = FloatArray(3)
        PcmCodec.decode(o24, SampleFormat.INT24, 3, 1, back24)
        assertThat(back24).isEqualTo(floats)
        val o32 = buffer(12)
        PcmCodec.encode(floats, 3, SampleFormat.INT32, o32, Quantizer(SampleFormat.INT32))
        o32.flip()
        assertThat(o32.getInt(0)).isEqualTo(1234 shl 16)
        assertThat(o32.getInt(4)).isEqualTo(Int.MIN_VALUE)
    }

    @Test fun `float output is copied verbatim, overs included`() {
        val floats = floatArrayOf(0.25f, -1.5f, 1e-9f)
        val out = buffer(12)
        PcmCodec.encode(floats, 3, SampleFormat.FLOAT, out, null)
        assertThat(out.position()).isEqualTo(12)
        out.flip()
        assertThat(out.getFloat(0)).isEqualTo(0.25f)
        assertThat(out.getFloat(4)).isEqualTo(-1.5f)
        assertThat(out.getFloat(8)).isEqualTo(1e-9f)
    }

    @Test fun `integer outputs clamp instead of wrapping`() {
        val q = Quantizer(SampleFormat.INT16)
        assertThat(q.quantize(1.0f)).isEqualTo(32767)
        assertThat(q.quantize(7.0f)).isEqualTo(32767)
        assertThat(q.quantize(-7.0f)).isEqualTo(-32768)
        val q24 = Quantizer(SampleFormat.INT24)
        assertThat(q24.quantize(2f)).isEqualTo(8_388_607)
    }

    @Test fun `off-grid samples switch on TPDF dither within plus or minus one LSB`() {
        val q = Quantizer(SampleFormat.INT16)
        // 24-bit content on a 16-bit output: a quarter of an LSB off the grid.
        val x = (1000 + 0.25f) / 32768f
        val seen = HashSet<Int>()
        repeat(10_000) { seen += q.quantize(x) }
        assertThat(q.isDithering).isTrue()
        assertThat(seen).containsAnyOf(999, 1000, 1001)
        assertThat(seen.all { it in 999..1001 }).isTrue()
    }

    @Test fun `dither averages to the true value instead of truncating toward zero`() {
        // Truncation (the old chain's toInt()) maps 1000.75 LSB to 1000 every time: a
        // signal-correlated error. TPDF dither keeps the long-run mean at 1000.75.
        val q = Quantizer(SampleFormat.INT16)
        val x = 1000.75f / 32768f
        var sum = 0.0
        val n = 200_000
        repeat(n) { sum += q.quantize(x) }
        assertThat(abs(sum / n - 1000.75)).isLessThan(0.02)
    }

    @Test fun `dither relocks off after a run of on-grid samples`() {
        val q = Quantizer(SampleFormat.INT16)
        q.quantize(0.3f / 32768f) // off grid
        assertThat(q.isDithering).isTrue()
        repeat(Quantizer.RELOCK_SAMPLES) { q.quantize(0f) }
        assertThat(q.isDithering).isFalse()
        // Back on the grid, silence and exact samples are untouched again.
        assertThat(q.quantize(0f)).isEqualTo(0)
        assertThat(q.quantize(5 / 32768f)).isEqualTo(5)
    }

    @Test fun `reset clears dithering`() {
        val q = Quantizer(SampleFormat.INT24)
        q.quantize(0.1234567f)
        assertThat(q.isDithering).isTrue()
        q.reset()
        assertThat(q.isDithering).isFalse()
    }

    @Test fun `32-bit output is never dithered`() {
        val q = Quantizer(SampleFormat.INT32)
        val x = sin(1.0).toFloat() * 0.5f
        assertThat(q.quantize(x)).isEqualTo(q.quantize(x))
        assertThat(q.isDithering).isFalse()
    }
}
