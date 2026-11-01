package com.stash.core.data.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.stash.core.common.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [AudioDurationExtractor] using [MediaMetadataRetriever]
 * and [MediaExtractor], with FLAC STREAMINFO header fallback.
 */
@Singleton
class AndroidAudioDurationExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioDurationExtractor {

    override fun extractMs(filePath: String): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (filePath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(filePath))
            } else {
                retriever.setDataSource(filePath)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
        } catch (e: Exception) {
            Log.w(TAG, "extractMs failed for $filePath: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun extract(filePath: String): AudioMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (filePath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(filePath))
            } else {
                retriever.setDataSource(filePath)
            }
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val bitrateBps = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull() ?: 0
            val mime = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val (trackMime, extractorSampleRate, extractorBitDepth) = probeExtractor(context, filePath)
            val format = normalizeFormat(trackMime?.takeUnless { it == MediaFormat.MIMETYPE_AUDIO_RAW } ?: mime)
            val resolvedBitDepth = extractorBitDepth
                ?: if (format == "flac") parseFlacStreamInfoBitDepth(filePath) else null
            AudioMetadata(
                durationMs = durationMs,
                bitrateKbps = if (bitrateBps > 0) bitrateBps / 1000 else 0,
                format = format,
                bitsPerSample = resolvedBitDepth?.takeIf { it in 8..32 },
                sampleRateHz = extractorSampleRate?.takeIf { it > 0 },
            )
        } catch (e: Exception) {
            Log.w(TAG, "extract failed for $filePath: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun extractAlbum(filePath: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (filePath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(filePath))
            } else {
                retriever.setDataSource(filePath)
            }
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "extractAlbum failed for $filePath: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val TAG = "AudioDurationExtractor"

        internal fun normalizeFormat(mime: String?): String {
            if (mime == null) return "unknown"
            val lower = mime.lowercase()
            return when {
                "mp4a" in lower || "aac" in lower -> "aac"
                "opus" in lower -> "opus"
                "vorbis" in lower -> "vorbis"
                "flac" in lower -> "flac"
                "mpeg" in lower || "mp3" in lower -> "mp3"
                else -> lower.substringAfter("audio/", "").ifBlank { "unknown" }
            }
        }

        internal fun probeExtractor(context: Context, filePath: String): Triple<String?, Int?, Int?> {
            val extractor = MediaExtractor()
            return try {
                if (filePath.startsWith("content://")) {
                    extractor.setDataSource(context, Uri.parse(filePath), null)
                } else {
                    extractor.setDataSource(filePath)
                }
                val format = (0 until extractor.trackCount).asSequence().map(extractor::getTrackFormat)
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    ?: return Triple(null, null, null)
                val sampleRate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }
                    .getOrNull()
                    ?.takeIf { it > 0 }
                val bitDepth = if (Build.VERSION.SDK_INT >= 30) {
                    runCatching {
                        format.getInteger("bits-per-sample")
                    }.getOrNull()?.takeIf { it > 0 }
                } else null
                Triple(format.getString(MediaFormat.KEY_MIME), sampleRate, bitDepth)
            } catch (e: Exception) {
                Log.w(TAG, "probeExtractor failed for $filePath: ${e.javaClass.simpleName}: ${e.message}")
                Triple(null, null, null)
            } finally {
                runCatching { extractor.release() }
            }
        }

        internal fun parseFlacStreamInfoBitDepth(filePath: String): Int? {
            return FlacHeaderParser.parse(filePath)?.bitsPerSample
        }
    }
}

