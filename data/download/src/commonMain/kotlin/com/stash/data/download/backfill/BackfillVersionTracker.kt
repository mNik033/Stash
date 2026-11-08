package com.stash.data.download.backfill

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.stash.core.common.AppVersionProvider
import com.stash.core.common.platform.AppPaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Once-per-version enqueue gate for backfill workers, keyed by caller.
 *
 * Persists the highest `versionCode` for which a backfill identified by
 * [key] has been enqueued. [shouldRunForCurrentVersion] returns `true`
 * exactly once per new `versionCode` per key:
 *  - First install / first launch of any version → returns `true`.
 *  - After [markEnqueuedForCurrentVersion] at the same version →
 *    returns `false`, so re-installing the same binary doesn't re-fire
 *    the worker.
 *  - After a version bump (new `versionCode` > stored value) → returns
 *    `true` again, giving us a clean upgrade lever for any future
 *    re-tagging pass.
 *
 * The [key] parameter lets multiple independent backfills share the
 * single `metadata_backfill_state.preferences_pb` DataStore file under
 * disjoint preference keys.
 */
@Singleton
class BackfillVersionTracker @Inject constructor(
    appPaths: AppPaths,
    private val appVersion: AppVersionProvider,
) {
    private val store: DataStore<Preferences> = BackfillDataStoreProvider.getStore(
        File(appPaths.filesDir, "datastore/metadata_backfill_state.preferences_pb"),
    )

    suspend fun shouldRunForCurrentVersion(key: String): Boolean {
        val stored = store.data.first()[intPreferencesKey(key)] ?: -1
        return stored < appVersion.versionCode
    }

    suspend fun markEnqueuedForCurrentVersion(key: String) {
        store.edit { it[intPreferencesKey(key)] = appVersion.versionCode }
    }
}

