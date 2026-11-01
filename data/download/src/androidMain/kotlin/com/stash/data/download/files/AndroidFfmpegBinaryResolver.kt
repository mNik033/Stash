package com.stash.data.download.files

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFfmpegBinaryResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : FfmpegBinaryResolver {

    override fun resolveFfmpegBinary(): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val candidates = listOf("libffmpeg.so", "libffmpeg.zip.so")
        return candidates.map { File(nativeDir, it) }.firstOrNull { it.exists() }
    }

    override fun ldLibraryPath(): String {
        val base = File(context.noBackupFilesDir, "youtubedl-android/packages")
        return buildString {
            append(File(base, "python/usr/lib").absolutePath); append(':')
            append(File(base, "ffmpeg/usr/lib").absolutePath); append(':')
            append(File(base, "aria2c/usr/lib").absolutePath)
        }
    }
}

