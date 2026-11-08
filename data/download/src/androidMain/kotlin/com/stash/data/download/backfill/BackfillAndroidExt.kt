package com.stash.data.download.backfill

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.stash.core.common.AppVersionProvider
import com.stash.core.common.platform.AppPaths
import java.io.File

internal val Context.backfillDataStore: DataStore<Preferences>
    get() = BackfillDataStoreProvider.getStore(
        File(filesDir, "datastore/metadata_backfill_state.preferences_pb"),
    )

private class ContextAppPaths(private val context: Context) : AppPaths {
    override val filesDir: File get() = context.filesDir
    override val cacheDir: File get() = context.cacheDir
    override val noBackupFilesDir: File get() = context.noBackupFilesDir
    override fun getDatabasePath(name: String): File = context.getDatabasePath(name)
}

fun MetadataBackfillState(context: Context): MetadataBackfillState =
    MetadataBackfillState(ContextAppPaths(context))

fun BackfillVersionTracker(context: Context, appVersion: AppVersionProvider): BackfillVersionTracker =
    BackfillVersionTracker(ContextAppPaths(context), appVersion)
