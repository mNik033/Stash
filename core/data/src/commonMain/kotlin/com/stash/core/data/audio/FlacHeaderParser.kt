package com.stash.core.data.audio

import com.stash.core.common.Log
import java.io.RandomAccessFile

/**
 * Parses ground-truth metadata directly from a FLAC STREAMINFO block (RFC 9580 / Xiph).
 */
object FlacHeaderParser {
    private const val TAG = "FlacHeaderParser"

    data class FlacInfo(
        val durationMs: Long,
        val sampleRateHz: Int,
        val bitsPerSample: Int,
        val channels: Int,
        val totalSamples: Long,
    )

    fun parse(filePath: String): FlacInfo? {
        return try {
            RandomAccessFile(filePath, "r").use { raf ->
                val header = ByteArray(26)
                if (raf.read(header) < 26) return null
                if (header[0] != 'f'.code.toByte() ||
                    header[1] != 'L'.code.toByte() ||
                    header[2] != 'a'.code.toByte() ||
                    header[3] != 'c'.code.toByte()
                ) return null

                // Bytes 18, 19, 20 (high 4 bits): sample rate (20 bits)
                val b18 = header[18].toInt() and 0xFF
                val b19 = header[19].toInt() and 0xFF
                val b20 = header[20].toInt() and 0xFF
                val sampleRate = (b18 shl 12) or (b19 shl 4) or (b20 ushr 4)

                // Byte 20 (bits 3..1): channels - 1 (3 bits)
                val channels = ((b20 ushr 1) and 0x07) + 1

                // Byte 20 bit 0 + byte 21 bits 7..4: bits per sample - 1 (5 bits)
                val b21 = header[21].toInt() and 0xFF
                val bps = (((b20 and 0x01) shl 4) or (b21 ushr 4)) + 1

                // Byte 21 (bits 3..0) + bytes 22..25: total samples in stream (36 bits)
                val b22 = header[22].toLong() and 0xFF
                val b23 = header[23].toLong() and 0xFF
                val b24 = header[24].toLong() and 0xFF
                val b25 = header[25].toLong() and 0xFF
                val totalSamples = ((b21.toLong() and 0x0F) shl 32) or (b22 shl 24) or (b23 shl 16) or (b24 shl 8) or b25

                val durationMs = if (sampleRate > 0) (totalSamples * 1000L) / sampleRate else 0L

                FlacInfo(
                    durationMs = durationMs,
                    sampleRateHz = sampleRate,
                    bitsPerSample = bps,
                    channels = channels,
                    totalSamples = totalSamples,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse FLAC header for $filePath: ${e.message}")
            null
        }
    }
}

