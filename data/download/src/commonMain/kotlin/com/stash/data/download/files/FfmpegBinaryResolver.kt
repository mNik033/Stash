package com.stash.data.download.files

import java.io.File

/**
 * Resolves the platform-specific ffmpeg binary and any required dynamic linker environment.
 */
interface FfmpegBinaryResolver {
    fun resolveFfmpegBinary(): File?
    fun ldLibraryPath(): String? = null
}

