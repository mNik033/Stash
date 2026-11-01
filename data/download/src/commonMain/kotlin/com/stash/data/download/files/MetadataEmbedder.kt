package com.stash.data.download.files

import com.stash.core.common.AppVersionProvider
import com.stash.core.common.Log
import com.stash.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embeds metadata (TITLE, ARTIST, ALBUMARTIST, ALBUM, ISRC, ENCODER)
 * and optional cover art into audio files using ffmpeg. Container-
 * agnostic — `-c copy` muxes the new metadata + picture stream into
 * the original codec without re-encoding.
 */
@Singleton
class MetadataEmbedder @Inject constructor(
    private val ffmpegResolver: FfmpegBinaryResolver,
    private val appVersion: AppVersionProvider,
) {
    internal var renameFile: (File, File) -> Boolean = { source, target ->
        source.renameTo(target)
    }

    /** Test seam: how long one ffmpeg tag pass may run before it is killed. */
    internal var ffmpegTimeoutSec: Long = FFMPEG_TIMEOUT_SEC

    suspend fun embedMetadata(
        audioFile: File,
        track: Track,
        albumArtFile: File? = null,
    ): File = withContext(Dispatchers.IO) {
        val outputFile = File(
            audioFile.parent,
            "${audioFile.nameWithoutExtension}_tagged.${audioFile.extension}",
        )
        var backupFile: File? = null

        val opusPictureMetadataFile = prepareOpusPictureMetadataFile(audioFile, outputFile, albumArtFile)

        val args = buildFfmpegArgs(
            audioFile, outputFile, track, albumArtFile, appVersion,
            opusPictureMetadataFile = opusPictureMetadataFile,
        )

        try {
            val ffmpegPath = ffmpegResolver.resolveFfmpegBinary()
                ?: throw IOException("ffmpeg binary not found")

            val pb = ProcessBuilder(listOf(ffmpegPath.absolutePath) + args)
            ffmpegResolver.ldLibraryPath()?.let { pb.environment()["LD_LIBRARY_PATH"] = it }
            val process = pb.start()

            val stdoutDrain = StreamDrain(process.inputStream)
            val stderrDrain = StreamDrain(process.errorStream)
            val exit = try {
                val finished = runInterruptible {
                    process.waitFor(ffmpegTimeoutSec, TimeUnit.SECONDS)
                }
                if (!finished) {
                    throw IOException("ffmpeg timed out after ${ffmpegTimeoutSec}s for ${audioFile.absolutePath}")
                }
                process.exitValue()
            } finally {
                process.destroyForcibly()
            }
            val stdout = stdoutDrain.text()
            val stderr = stderrDrain.text()

            if (exit != 0) {
                Log.w(TAG, "ffmpeg exited $exit for ${audioFile.absolutePath} (args: ${args.take(8)}...)")
                if (stderr.isNotBlank()) {
                    Log.w(TAG, "ffmpeg stderr: ${stderr.take(500)}")
                }
                if (stdout.isNotBlank()) {
                    Log.d(TAG, "ffmpeg stdout: ${stdout.take(200)}")
                }
                throw IOException("ffmpeg exited $exit for ${audioFile.absolutePath}")
            }

            if (!outputFile.exists() || outputFile.length() <= 0) {
                throw IOException("ffmpeg produced no output for ${audioFile.absolutePath}")
            }

            val backup = File.createTempFile(
                "stash_${audioFile.nameWithoutExtension}_untagged_",
                ".${audioFile.extension}",
                audioFile.parentFile,
            ).apply { delete() }
            backupFile = backup
            if (!renameFile(audioFile, backup)) {
                throw IOException("could not preserve original file ${audioFile.absolutePath}")
            }
            if (!renameFile(outputFile, audioFile)) {
                throw IOException("could not install tagged file ${audioFile.absolutePath}")
            }
            if (!backup.delete()) {
                Log.w(TAG, "could not delete backup file ${backup.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ffmpeg embed failed for ${audioFile.absolutePath}: ${e.message}", e)
            outputFile.delete()
            backupFile?.let { backup ->
                if (backup.exists() && !audioFile.exists()) {
                    if (!renameFile(backup, audioFile)) {
                        backup.copyTo(audioFile, overwrite = true)
                        backup.delete()
                    }
                }
            }
            throw e
        } finally {
            opusPictureMetadataFile?.delete()
        }

        audioFile
    }

    private fun prepareOpusPictureMetadataFile(
        audioFile: File,
        outputFile: File,
        albumArtFile: File?,
    ): File? {
        val outputExt = outputFile.extension.lowercase()
        if (outputExt !in OPUS_OGG_EXTENSIONS) return null
        if (albumArtFile == null || !albumArtFile.exists() || albumArtFile.length() <= 0) return null

        return try {
            val pictureBlock = FlacPictureBlock.build(albumArtFile.readBytes())
            val base64Block = Base64.getEncoder().encodeToString(pictureBlock)
            if (base64Block.length <= MAX_INLINE_PICTURE_BASE64_BYTES) return null

            val metaFile = File(audioFile.parent, "${audioFile.nameWithoutExtension}_picmeta.ffmetadata")
            metaFile.writeText(";FFMETADATA1\nMETADATA_BLOCK_PICTURE=$base64Block\n")
            metaFile
        } catch (e: Exception) {
            Log.w(TAG, "ffmetadata picture-file build failed for ${audioFile.absolutePath}: ${e.message}")
            null
        }
    }

    private class StreamDrain(stream: InputStream) {
        private val captured = StringBuilder()
        private val thread = Thread {
            try {
                stream.bufferedReader().use { reader ->
                    val buf = CharArray(4096)
                    while (true) {
                        val n = reader.read(buf)
                        if (n < 0) break
                        synchronized(captured) {
                            val room = MAX_CAPTURE_CHARS - captured.length
                            if (room > 0) captured.append(buf, 0, minOf(n, room))
                        }
                    }
                }
            } catch (_: IOException) {
            }
        }.apply { isDaemon = true; start() }

        fun text(): String {
            thread.join(DRAIN_JOIN_MS)
            return synchronized(captured) { captured.toString() }
        }
    }

    companion object {
        private const val TAG = "MetadataEmbedder"

        private const val FFMPEG_TIMEOUT_SEC = 120L
        private const val MAX_CAPTURE_CHARS = 8 * 1024
        private const val DRAIN_JOIN_MS = 2_000L

        private val OPUS_OGG_EXTENSIONS = setOf("opus", "ogg")

        internal const val MAX_INLINE_PICTURE_BASE64_BYTES = 64 * 1024

        private fun sanitize(value: String): String =
            value.replace(Regex("[\\x00-\\x1f]"), "")

        internal fun buildFfmpegArgs(
            audioFile: File,
            outputFile: File,
            track: Track,
            albumArtFile: File?,
            appVersion: AppVersionProvider,
            opusPictureMetadataFile: File? = null,
        ): List<String> = buildList {
            add("-i"); add(audioFile.absolutePath)

            val outputExt = outputFile.extension.lowercase()
            val supportsAttachedPic = outputExt !in OPUS_OGG_EXTENSIONS
            val hasArt = albumArtFile != null && albumArtFile.exists() && albumArtFile.length() > 0
            val useMetadataFileFallback = !supportsAttachedPic && opusPictureMetadataFile != null

            if (supportsAttachedPic && hasArt) {
                add("-i"); add(albumArtFile.absolutePath)
                add("-map"); add("0:a")
                add("-map"); add("1:0")
                add("-disposition:v:0"); add("attached_pic")
            } else if (useMetadataFileFallback) {
                add("-i"); add(opusPictureMetadataFile.absolutePath)
                add("-map_metadata"); add("1")
            }

            add("-metadata"); add("title=${sanitize(track.title)}")
            add("-metadata"); add("artist=${sanitize(track.artist)}")

            if (track.album.isNotEmpty()) {
                add("-metadata"); add("album=${sanitize(track.album)}")
            }

            val effectiveAlbumArtist = track.albumArtist.ifBlank { track.artist }
            if (effectiveAlbumArtist.isNotEmpty()) {
                add("-metadata"); add("ALBUMARTIST=${sanitize(effectiveAlbumArtist)}")
                add("-metadata"); add("album_artist=${sanitize(effectiveAlbumArtist)}")
            }

            track.isrc?.takeIf { it.isNotBlank() }?.let {
                add("-metadata"); add("ISRC=${sanitize(it)}")
            }

            add("-metadata"); add("ENCODER=Stash ${appVersion.versionName}")

            if (!supportsAttachedPic && hasArt && !useMetadataFileFallback) {
                try {
                    val pictureBlock = FlacPictureBlock.build(albumArtFile.readBytes())
                    val base64Block = Base64.getEncoder().encodeToString(pictureBlock)
                    add("-metadata"); add("METADATA_BLOCK_PICTURE=$base64Block")
                } catch (e: Exception) {
                    Log.w(TAG, "FlacPictureBlock build failed for ${audioFile.absolutePath}: ${e.message}")
                }
            }

            add("-c"); add("copy")
            add("-y")
            add(outputFile.absolutePath)
        }
    }
}

