package com.stash.core.data.audio

import android.content.Context
import com.stash.core.common.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discards everything written to it. A desugaring-safe stand-in for
 * [OutputStream.nullOutputStream] (added in Java 11 / Android API 33), which
 * throws [NoSuchMethodError] on this app's minSdk-26 range — every device
 * below Android 13 crashed the ffmpeg/loudness path with it. Overriding the
 * bulk `write` keeps `InputStream.copyTo` (which writes whole buffers) cheap.
 */
internal object NullOutputStream : OutputStream() {
    override fun write(b: Int) = Unit
    override fun write(b: ByteArray, off: Int, len: Int) = Unit
}

/**
 * Production [FFmpegBridge] backed by the youtubedl-android ffmpeg native
 * binary at `nativeLibraryDir/libffmpeg.so`.
 *
 * Required `LD_LIBRARY_PATH` paths are reconstructed from `Context` using the
 * same layout that `YoutubeDL.init` sets up: ffmpeg's shared libraries (and
 * Python's, which ffmpeg can transitively need for some codecs) live under
 * `noBackupFilesDir/youtubedl-android/packages/{python,ffmpeg,aria2c}/usr/lib`.
 * Mirroring the library's env-var convention guarantees ffmpeg resolves its
 * sibling .so files at runtime; without it `dlopen` of libavcodec etc. fails
 * with "library not found."
 */
@Singleton
class FFmpegBridgeImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : FFmpegBridge {

    override suspend fun runWithStderrCapture(args: List<String>): String =
        withContext(Dispatchers.IO) {
            val binary = ffmpegBinary()
            if (!binary.exists() || !binary.canExecute()) {
                throw IOException(
                    "ffmpeg binary missing or not executable at ${binary.absolutePath}; " +
                        "ensure FFmpeg.getInstance().init(context) has been called",
                )
            }

            val cmd = ArrayList<String>(args.size + 1).apply {
                add(binary.absolutePath)
                addAll(args)
            }

            val process = ProcessBuilder(cmd)
                .apply {
                    val env = environment()
                    env["LD_LIBRARY_PATH"] = ldLibraryPath()
                    env["TMPDIR"] = context.cacheDir.absolutePath
                }
                .redirectErrorStream(false)
                .start()

            val (exit, stderr) = awaitCapturingStderr(process)

            if (exit != 0) {
                Log.w(TAG, "ffmpeg exited with code $exit; stderr length=${stderr.length}")
                throw IOException(
                    buildString {
                        append("ffmpeg exited with code "); append(exit)
                        if (stderr.isNotBlank()) {
                            append(":\n"); append(stderr)
                        }
                    },
                )
            }
            stderr
        }

    private fun ffmpegBinary(): File =
        File(context.applicationInfo.nativeLibraryDir, FFMPEG_LIB_NAME)

    /**
     * Reconstructs the `LD_LIBRARY_PATH` that the youtubedl-android library
     * sets internally. Order matches the library so behavior is identical:
     * python's lib dir first, then ffmpeg's, then aria2c's.
     *
     * If a given package dir doesn't exist (e.g. user only ever ran ffmpeg
     * direct), that entry still gets added — `dlopen` ignores non-existent
     * directories, and emitting the same string the library does avoids
     * subtle drift if the youtubedl-android internals later check it.
     */
    private fun ldLibraryPath(): String {
        val base = File(context.noBackupFilesDir, "youtubedl-android/packages")
        return buildString {
            append(File(base, "python/usr/lib").absolutePath); append(':')
            append(File(base, "ffmpeg/usr/lib").absolutePath); append(':')
            append(File(base, "aria2c/usr/lib").absolutePath)
        }
    }

    companion object {
        private const val TAG = "FFmpegBridge"

        /**
         * Reads [process]'s stderr to the end, waits for it to exit and returns
         * (exit code, stderr). stdout is drained and discarded.
         *
         * Kills the process if the calling coroutine is cancelled first. The
         * reads block a thread, and cancellation cannot interrupt a blocked
         * read: a cancelled LoudnessBackfillWorker (the charger unplugged) left
         * ffmpeg decoding the whole file, 25-50 s for a FLAC, on battery, which
         * is what deferring to charging exists to avoid. A watcher waiting on
         * another thread destroys the process on cancellation, which ends the
         * reads, and the cancellation is then rethrown rather than reported
         * as an ffmpeg failure.
         */
        internal suspend fun awaitCapturingStderr(process: Process): Pair<Int, String> = coroutineScope {
            // UNDISPATCHED so the watcher is armed before the first blocking
            // read, even if this coroutine is already cancelled.
            val killer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    process.destroy()
                }
            }
            try {
                // Drain stdout on a side thread so the pipe buffer can't fill up
                // and block ffmpeg's writes. Content is discarded — ebur128's
                // Summary lives on stderr.
                val stdoutDrainer = Thread {
                    try {
                        process.inputStream.use { it.copyTo(NullOutputStream) }
                    } catch (_: Exception) {
                        // Stream closed while draining — fine.
                    }
                }.also { it.isDaemon = true; it.start() }

                val stderr = try {
                    process.errorStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    Log.w(TAG, "stderr read failed: ${e.message}")
                    ""
                }

                val exit = try {
                    process.waitFor()
                } catch (ie: InterruptedException) {
                    process.destroy()
                    Thread.currentThread().interrupt()
                    throw ie
                }
                stdoutDrainer.join(500)
                ensureActive()
                exit to stderr
            } finally {
                // Normal exit: the process has already ended, so the watcher's
                // destroy() is a no-op.
                killer.cancel()
            }
        }

        // youtubedl-android extracts the ffmpeg binary into nativeLibraryDir
        // under this name. Confirmed by disassembling the library AAR:
        // `YoutubeDL.init` sets `ffmpegPath = File(binDir, "libffmpeg.so")`.
        // The .so suffix is cosmetic — Android's PackageManager unpacks
        // anything matching `lib*.so` from the APK and grants it the
        // apk_data_file SELinux context that permits execution.
        private const val FFMPEG_LIB_NAME = "libffmpeg.so"
    }
}
