package com.stash.core.data.platform

import android.content.Context
import com.stash.core.common.platform.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppPaths @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppPaths {
    override val filesDir: File
        get() = context.filesDir

    override val cacheDir: File
        get() = context.cacheDir

    override val noBackupFilesDir: File
        get() = context.noBackupFilesDir

    override fun getDatabasePath(name: String): File {
        return context.getDatabasePath(name)
    }
}
