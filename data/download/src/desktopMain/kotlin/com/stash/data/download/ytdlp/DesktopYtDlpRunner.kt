package com.stash.data.download.ytdlp

import com.stash.core.common.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Desktop implementation of [YtDlpRunner] that shells out directly to the
 * native `yt-dlp` executable on macOS and Windows.
 */
@Singleton
class DesktopYtDlpRunner @Inject constructor() : YtDlpRunner {

    override suspend fun execute(
        args: List<String>,
        onProgress: ((Float, Long, String) -> Unit)?,
    ): YtDlpResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val binary = resolveYtDlpBinary()

        val cmd = ArrayList<String>(args.size + 1).apply {
            add(binary)
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
                        stdout.appendLine(line)
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
                        stderr.appendLine(line)
                    }
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val exitCode = try {
            process.waitFor()
        } catch (e: InterruptedException) {
            process.destroy()
            throw e
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
        val binaryName = if (os.contains("win")) "yt-dlp.exe" else "yt-dlp"

        // Check well-known macOS/Linux paths first
        val standardPaths = listOf(
            "/opt/homebrew/bin/$binaryName",
            "/usr/local/bin/$binaryName",
            "/usr/bin/$binaryName",
        )
        for (path in standardPaths) {
            val f = File(path)
            if (f.exists() && f.canExecute()) {
                return f.absolutePath
            }
        }
        return binaryName
    }

    private fun parseProgress(line: String): Pair<Float, Long>? {
        val pctMatch = Regex("""(\d+(?:\.\d+)?)%""").find(line) ?: return null
        val pct = pctMatch.groupValues[1].toFloatOrNull() ?: return null
        return pct to 0L
    }

    companion object {
        private const val TAG = "DesktopYtDlpRunner"
    }
}
