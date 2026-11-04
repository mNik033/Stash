package com.stash.core.data.audio

import com.stash.core.common.Log
import com.stash.core.common.platform.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopFFmpegBridge @Inject constructor(
    private val appPaths: AppPaths,
) : FFmpegBridge {

    override suspend fun runWithStderrCapture(args: List<String>): String = withContext(Dispatchers.IO) {
        val ffmpeg = resolveFfmpegBinary() ?: throw IOException("ffmpeg binary not found on Desktop")
        val pb = ProcessBuilder(listOf(ffmpeg.absolutePath) + args)
        val process = pb.start()
        val stderr = process.errorStream.bufferedReader().readText()
        process.inputStream.bufferedReader().readText() // drain stdout
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            Log.w(TAG, "ffmpeg exited with $exitCode: ${stderr.take(200)}")
        }
        stderr
    }

    private fun resolveFfmpegBinary(): File? {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val exeName = if (isWindows) "ffmpeg.exe" else "ffmpeg"
        val binDir = File(appPaths.filesDir, "bin")
        val inBin = File(binDir, exeName)
        if (inBin.exists() && inBin.canExecute()) return inBin

        System.getProperty("compose.application.resources.dir")?.let { resDir ->
            val inRes = File(resDir, exeName)
            if (inRes.exists() && inRes.canExecute()) return inRes
        }

        val pathEnv = System.getenv("PATH") ?: ""
        val separator = File.pathSeparator
        for (dir in pathEnv.split(separator)) {
            if (dir.isBlank()) continue
            val candidate = File(dir, exeName)
            if (candidate.exists() && candidate.canExecute()) return candidate
        }

        if (!isWindows) {
            for (dir in listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin")) {
                val candidate = File(dir, exeName)
                if (candidate.exists() && candidate.canExecute()) return candidate
            }
        }
        return null
    }

    companion object {
        private const val TAG = "DesktopFFmpegBridge"
    }
}

