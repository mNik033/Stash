package com.stash.core.data.files

import com.stash.core.common.constants.StashConstants
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Desktop implementation of [LocalFileOps] using standard java.io.File.
 */
@Singleton
class DesktopLocalFileOps @Inject constructor() : LocalFileOps {

    override fun sizeBytes(path: String?): Long =
        if (path.isNullOrBlank()) 0L else runCatching { File(path).length() }.getOrDefault(0L)

    override fun delete(path: String?): Boolean =
        if (path.isNullOrBlank()) false else runCatching { File(path).delete() }.getOrDefault(false)

    override fun exists(path: String?): Boolean =
        if (path.isNullOrBlank()) false else runCatching { File(path).exists() }.getOrDefault(false)

    override fun acceptDownloadOrDelete(path: String?): Boolean {
        if (sizeBytes(path) >= StashConstants.MIN_PLAYABLE_LOCAL_BYTES) return true
        delete(path)
        return false
    }

    override fun classify(path: String?, minBytes: Long): LocalFileState {
        if (path.isNullOrBlank()) return LocalFileState.MISSING
        val f = File(path)
        if (!f.exists()) return LocalFileState.MISSING
        val len = f.length()
        return if (len >= minBytes) LocalFileState.OK else LocalFileState.TOO_SMALL
    }

    override fun isSameFile(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a == b) return true
        return runCatching {
            File(a).canonicalFile == File(b).canonicalFile
        }.getOrDefault(false)
    }
}
