package com.stash.core.data.audio

import com.stash.core.common.Log
import com.stash.core.common.platform.AppPaths
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Desktop implementation of [AudioDurationExtractor].
 * Uses [FlacHeaderParser] for FLAC files, and ffprobe (if available) for other audio formats.
 */
@Singleton
class DesktopAudioDurationExtractor @Inject constructor(
    private val appPaths: AppPaths,
) : AudioDurationExtractor {

    override fun extractMs(filePath: String): Long? {
        val ext = filePath.substringAfterLast('.').lowercase()
        if (ext == "flac") {
            FlacHeaderParser.parse(filePath)?.let { info ->
                if (info.durationMs > 0) return info.durationMs
            }
        }
        return extract(filePath)?.durationMs?.takeIf { it > 0 }
    }

    override fun extract(filePath: String): AudioMetadata? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return null
        val ext = file.extension.lowercase()

        if (ext == "flac") {
            FlacHeaderParser.parse(filePath)?.let { info ->
                val bitrate = if (info.durationMs > 0) {
                    ((file.length() * 8L * 1000L) / info.durationMs / 1000L).toInt()
                } else 0
                return AudioMetadata(
                    durationMs = info.durationMs,
                    bitrateKbps = bitrate,
                    format = "flac",
                    bitsPerSample = info.bitsPerSample.takeIf { it in 8..32 },
                    sampleRateHz = info.sampleRateHz.takeIf { it > 0 },
                )
            }
        }

        // Try ffprobe
        val ffprobe = resolveFfprobeBinary()
        if (ffprobe != null) {
            val probed = probeWithFfprobe(ffprobe, filePath, file.length())
            if (probed != null) return probed
        }

        // Basic extension fallback
        return AudioMetadata(
            durationMs = 0L,
            bitrateKbps = 0,
            format = ext.ifBlank { "unknown" },
        )
    }

    override fun extractAlbum(filePath: String): String? {
        val ffprobe = resolveFfprobeBinary() ?: return null
        return try {
            val cmd = listOf(
                ffprobe.absolutePath,
                "-v", "error",
                "-show_entries", "format_tags=album",
                "-of", "default=noprint_wrappers=1:nokey=1",
                filePath,
            )
            val process = ProcessBuilder(cmd).start()
            val text = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            text.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "extractAlbum failed for $filePath: ${e.message}")
            null
        }
    }

    private fun probeWithFfprobe(ffprobe: File, filePath: String, fileSizeBytes: Long): AudioMetadata? {
        return try {
            val cmd = listOf(
                ffprobe.absolutePath,
                "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "stream=codec_name,sample_rate,bits_per_raw_sample,bit_rate:format=duration,bit_rate",
                "-of", "default=noprint_wrappers=1",
                filePath,
            )
            val process = ProcessBuilder(cmd).start()
            val lines = process.inputStream.bufferedReader().readLines()
            process.waitFor()

            var codec: String? = null
            var sampleRate: Int? = null
            var bitDepth: Int? = null
            var streamBitrate: Int? = null
            var formatBitrate: Int? = null
            var durationSec: Double? = null

            for (line in lines) {
                val idx = line.indexOf('=')
                if (idx == -1) continue
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                when (key) {
                    "codec_name" -> codec = value
                    "sample_rate" -> sampleRate = value.toIntOrNull()
                    "bits_per_raw_sample" -> bitDepth = value.toIntOrNull()
                    "bit_rate" -> {
                        val br = value.toIntOrNull()
                        if (streamBitrate == null) streamBitrate = br else formatBitrate = br
                    }
                    "duration" -> durationSec = value.toDoubleOrNull()
                }
            }

            val durationMs = durationSec?.let { (it * 1000).toLong() } ?: 0L
            val bitrateBps = streamBitrate ?: formatBitrate ?: 0
            val bitrateKbps = if (bitrateBps > 0) {
                bitrateBps / 1000
            } else if (durationMs > 0 && fileSizeBytes > 0) {
                ((fileSizeBytes * 8L * 1000L) / durationMs / 1000L).toInt()
            } else 0

            val ext = filePath.substringAfterLast('.').lowercase()
            AudioMetadata(
                durationMs = durationMs,
                bitrateKbps = bitrateKbps,
                format = codec ?: ext.ifBlank { "unknown" },
                bitsPerSample = bitDepth?.takeIf { it in 8..32 },
                sampleRateHz = sampleRate?.takeIf { it > 0 },
            )
        } catch (e: Exception) {
            Log.w(TAG, "ffprobe failed for $filePath: ${e.message}")
            null
        }
    }

    private fun resolveFfprobeBinary(): File? {
        val exeName = if (System.getProperty("os.name").lowercase().contains("windows")) "ffprobe.exe" else "ffprobe"
        // 1. Files in bin/
        val binDir = File(appPaths.filesDir, "bin")
        val inBin = File(binDir, exeName)
        if (inBin.exists() && inBin.canExecute()) return inBin

        // 2. Resource dir
        System.getProperty("compose.application.resources.dir")?.let { resDir ->
            val inRes = File(resDir, exeName)
            if (inRes.exists() && inRes.canExecute()) return inRes
        }

        // 3. Search PATH
        val pathEnv = System.getenv("PATH") ?: ""
        val separator = File.pathSeparator
        for (dir in pathEnv.split(separator)) {
            val candidate = File(dir, exeName)
            if (candidate.exists() && candidate.canExecute()) return candidate
        }

        return null
    }

    companion object {
        private const val TAG = "DesktopAudioDurationExtractor"
    }
}

