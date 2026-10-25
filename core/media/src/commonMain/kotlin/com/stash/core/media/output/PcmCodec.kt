package com.stash.core.media.output

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.round

/**
 * Lossless-where-possible conversion between interleaved PCM bytes and the
 * float working format the output stage processes in.
 *
 * Scaling is by powers of two — int16 `/ 2^15`, int24 `/ 2^23`, int32
 * `/ 2^31` — the same convention as Android's FLAC decoder
 * (`float_from_i32(sample << (32 - bps))`) and Media3's
 * `ToFloatPcmAudioProcessor`. That makes every 16- and 24-bit integer sample
 * EXACTLY representable as a float, so int → float → int is a bit-exact round
 * trip. Bit-perfect output depends on that.
 */
object PcmCodec {

    /**
     * Reads [frames] × [channels] samples in [format] starting at [src]'s
     * position into [dst] (from index 0). Does NOT move [src]'s position: the
     * caller decides how much input was consumed only once the output has
     * actually been written.
     */
    fun decode(
        src: ByteBuffer,
        format: SampleFormat,
        frames: Int,
        channels: Int,
        dst: FloatArray,
    ) {
        val count = frames * channels
        val view = src.duplicate().order(ByteOrder.nativeOrder())
        when (format) {
            SampleFormat.FLOAT -> view.asFloatBuffer().get(dst, 0, count)
            SampleFormat.INT16 -> {
                val shorts = view.asShortBuffer()
                for (i in 0 until count) dst[i] = shorts.get(i) * INV_2_15
            }
            SampleFormat.INT24 -> {
                var p = view.position()
                for (i in 0 until count) {
                    val v = (view.get(p).toInt() and 0xFF) or
                        ((view.get(p + 1).toInt() and 0xFF) shl 8) or
                        (view.get(p + 2).toInt() shl 16) // sign-extends the top byte
                    dst[i] = v * INV_2_23
                    p += 3
                }
            }
            SampleFormat.INT32 -> {
                val ints = view.asIntBuffer()
                for (i in 0 until count) dst[i] = (ints.get(i) * INV_2_31_D).toFloat()
            }
        }
    }

    /**
     * Writes [count] samples from [src] into [dst] (relative puts, so [dst]'s
     * position advances) in [format]. Integer formats go through [quantizer];
     * float is copied verbatim — no clamp, the mixer has headroom.
     */
    fun encode(
        src: FloatArray,
        count: Int,
        format: SampleFormat,
        dst: ByteBuffer,
        quantizer: Quantizer?,
    ) {
        when (format) {
            SampleFormat.FLOAT -> {
                val floats = dst.asFloatBuffer()
                floats.put(src, 0, count)
                dst.position(dst.position() + count * 4)
            }
            SampleFormat.INT16 -> {
                val q = requireNotNull(quantizer)
                for (i in 0 until count) dst.putShort(q.quantize(src[i]).toShort())
            }
            SampleFormat.INT24 -> {
                val q = requireNotNull(quantizer)
                for (i in 0 until count) {
                    val v = q.quantize(src[i])
                    dst.put(v.toByte())
                    dst.put((v shr 8).toByte())
                    dst.put((v shr 16).toByte())
                }
            }
            SampleFormat.INT32 -> {
                val q = requireNotNull(quantizer)
                for (i in 0 until count) dst.putInt(q.quantize(src[i]))
            }
        }
    }

    internal const val INV_2_15 = 1f / 32768f
    internal const val INV_2_23 = 1f / 8388608f
    internal const val INV_2_31_D = 1.0 / 2147483648.0
}

/**
 * Float → integer requantizer with "dither only when it is needed".
 *
 * A sample that lands exactly on the target grid (every sample of a 16-bit
 * source written to 16 bits, or of a 24-bit source written to 24) is written
 * as-is: no noise, bit-perfect. The first sample that does NOT fit — DSP
 * output, a software fade, 24-bit content on a 16-bit DAC — switches on TPDF
 * dither (triangular, ±1 LSB): the textbook way to turn requantization
 * distortion into a benign, signal-independent noise floor. Truncation (what
 * the old 16-bit chain did, five times over) instead correlates the error
 * with the music, which is audible as grit on quiet fades.
 *
 * Dither relocks off after [RELOCK_SAMPLES] consecutive on-grid samples
 * (digital silence between tracks, or the end of a fade), so a brief volume
 * dip doesn't leave a bit-perfect stream dithered until the next seek.
 *
 * 32-bit output is never dithered: a float carries 24 bits of mantissa, so
 * rounding to 32-bit integers loses nothing audible (< −180 dBFS).
 *
 * Not thread-safe: one instance per output stream, used on the playback thread.
 */
class Quantizer(val format: SampleFormat, seed: Long = 0x2545F4914F6CDD1DL) {

    init {
        require(format.isInteger) { "Quantizer is for integer formats, not $format" }
    }

    private val scale: Double = when (format) {
        SampleFormat.INT16 -> 32768.0
        SampleFormat.INT24 -> 8388608.0
        else -> 2147483648.0
    }
    private val max: Double = scale - 1.0
    private val min: Double = -scale

    private var state: Long = if (seed == 0L) 1L else seed
    private var dithering = false
    private var exactRun = 0

    /** True while dither is being applied (for diagnostics and tests). */
    val isDithering: Boolean get() = dithering

    fun reset() {
        dithering = false
        exactRun = 0
    }

    fun quantize(x: Float): Int {
        val v = x.toDouble() * scale
        if (format == SampleFormat.INT32) return clamp(round(v))
        val onGrid = v == floor(v)
        if (onGrid) {
            if (!dithering) return clamp(v)
            if (++exactRun >= RELOCK_SAMPLES) {
                dithering = false
                exactRun = 0
                return clamp(v)
            }
        } else {
            dithering = true
            exactRun = 0
        }
        return clamp(round(v + tpdf()))
    }

    private fun clamp(v: Double): Int = when {
        v >= max -> max.toInt()
        v <= min -> min.toInt()
        else -> v.toInt()
    }

    /** Triangular PDF noise in (−1, 1) LSB: the sum of two uniform variables. */
    private fun tpdf(): Double = uniform() + uniform() - 1.0

    /** xorshift64* → [0, 1). Deterministic per seed, allocation-free. */
    private fun uniform(): Double {
        var x = state
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        state = x
        return ((x * 0x2545F4914F6CDD1DL) ushr 11) * (1.0 / (1L shl 53))
    }

    companion object {
        /** ~46 ms of stereo at 44.1 kHz. */
        const val RELOCK_SAMPLES = 4096
    }
}
