package com.stash.core.common.platform

import java.io.File

/**
 * Abstraction over application filesystem directories, decoupling
 * business logic and storage layers from Android [android.content.Context].
 */
interface AppPaths {
    /** Directory for persistent internal files. */
    val filesDir: File

    /** Directory for temporary cache files that can be evicted by the system. */
    val cacheDir: File

    /** Directory for sensitive data that should never be included in system backups. */
    val noBackupFilesDir: File

    /** Resolves the absolute path for an application database with [name]. */
    fun getDatabasePath(name: String): File
}
