package com.stash.data.download.files

import com.stash.core.common.platform.AppPaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopFfmpegBinaryResolver @Inject constructor(
    private val appPaths: AppPaths,
) : FfmpegBinaryResolver {

    override fun resolveFfmpegBinary(): File? {
        val exeName = if (System.getProperty("os.name").lowercase().contains("windows")) "ffmpeg.exe" else "ffmpeg"
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
            val candidate = File(dir, exeName)
            if (candidate.exists() && candidate.canExecute()) return candidate
        }
        return null
    }

    override fun ldLibraryPath(): String? = null
}

