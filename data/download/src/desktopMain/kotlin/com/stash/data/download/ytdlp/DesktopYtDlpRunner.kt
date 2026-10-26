package com.stash.data.download.ytdlp

import com.stash.core.common.Log
import com.stash.core.common.platform.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Desktop implementation of [YtDlpRunner] that shells out directly to the
 * native `yt-dlp` executable on macOS, Windows, and Linux.
 */
@Singleton
class DesktopYtDlpRunner @Inject constructor(
    private val appPaths: AppPaths,
) : YtDlpRunner {

    override suspend fun execute(
        args: List<String>,
        onProgress: ((Float, Long, String) -> Unit)?,
    ): YtDlpResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val binary = resolveYtDlpBinary()

        val cmd = buildList {
            add(binary)
            if ("--ignore-config" !in args && "--no-config" !in args) {
                add("--ignore-config")
            }
            addAll(args)
        }

        val process = try {
            ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch $binary: ${e.message}")
            return@withContext YtDlpResponse(
                exitCode = -1,
                out = "",
                err = e.message ?: "Failed to start yt-dlp",
                elapsedTime = System.currentTimeMillis() - startTime,
            )
        }

        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val stdoutThread = Thread {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (stdout.length < MAX_OUTPUT_CHARS) {
                            stdout.appendLine(line)
                        }
                        if (onProgress != null && line.contains("[download]")) {
                            parseProgress(line)?.let { (pct, eta) ->
                                onProgress(pct, eta, line)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val stderrThread = Thread {
            try {
                process.errorStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (stderr.length < MAX_OUTPUT_CHARS) {
                            stderr.appendLine(line)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val exitCode = try {
            val finishedCode = withTimeoutOrNull(DEFAULT_TIMEOUT_MS) {
                runInterruptible {
                    process.waitFor()
                }
            }
            if (finishedCode != null) {
                finishedCode
            } else {
                process.destroyForcibly()
                -1
            }
        } catch (e: Throwable) {
            if (process.isAlive) {
                process.destroyForcibly()
            }
            throw e
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }

        stdoutThread.join(1000)
        stderrThread.join(1000)

        YtDlpResponse(
            exitCode = exitCode,
            out = stdout.toString(),
            err = stderr.toString(),
            elapsedTime = System.currentTimeMillis() - startTime,
        )
    }

    private fun resolveYtDlpBinary(): String {
        val os = System.getProperty("os.name").lowercase()
        val isWindows = os.contains("win")
        val exeName = if (isWindows) "yt-dlp.exe" else "yt-dlp"

        // 1. Check bundled app resources directory
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        if (!resourcesDir.isNullOrBlank()) {
            val bundled = File(resourcesDir, exeName)
            if (bundled.isFile && (isWindows || bundled.canExecute())) {
                return bundled.absolutePath
            }
        }

        // 2. Check app files bin directory
        val userBin = File(appPaths.filesDir, "bin/$exeName")
        if (userBin.isFile && (isWindows || userBin.canExecute())) {
            return userBin.absolutePath
        }

        // 3. On macOS/Linux, check standard system paths
        if (!isWindows) {
            val standardPaths = listOf(
                "/opt/homebrew/bin/$exeName",
                "/usr/local/bin/$exeName",
                "/usr/bin/$exeName",
            )
            for (path in standardPaths) {
                val f = File(path)
                if (f.isFile && f.canExecute()) {
                    return f.absolutePath
                }
            }
        }

        return exeName
    }

    private fun parseProgress(line: String): Pair<Float, Long>? {
        val pctMatch = Regex("""(\d+(?:\.\d+)?)%""").find(line) ?: return null
        val pct = pctMatch.groupValues[1].toFloatOrNull() ?: return null
        return pct to 0L
    }

    companion object {
        private const val TAG = "DesktopYtDlpRunner"
        private const val MAX_OUTPUT_CHARS = 4 * 1024 * 1024 // 4 MB
        private const val DEFAULT_TIMEOUT_MS = 120_000L // 2 minutes
    }
}
