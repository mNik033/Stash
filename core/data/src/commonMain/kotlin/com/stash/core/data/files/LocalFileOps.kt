package com.stash.core.data.files

/** Health of a downloaded row's backing local file. */
enum class LocalFileState {
    OK,
    TOO_SMALL,
    MISSING,
    INCONCLUSIVE,
}

/**
 * Common platform operations for inspecting and managing local downloaded audio files.
 */
interface LocalFileOps {
    fun sizeBytes(path: String?): Long
    fun delete(path: String?): Boolean
    fun exists(path: String?): Boolean
    fun acceptDownloadOrDelete(path: String?): Boolean
    fun classify(path: String?, minBytes: Long): LocalFileState
    fun isSameFile(a: String?, b: String?): Boolean
}
