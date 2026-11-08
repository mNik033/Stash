package com.stash.data.download.backfill

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stash.core.common.platform.AppPaths
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal object BackfillDataStoreProvider {
    private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

    fun getStore(file: File): DataStore<Preferences> =
        stores.computeIfAbsent(file.canonicalPath) {
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { file },
            )
        }
}

/**
 * Persists the observable state of the metadata-backfill worker so the
 * Home re-tagging banner can render progress without polling
 * the worker directly.
 *
 *  - [State.IDLE] — no backfill in flight, banner hidden.
 *  - [State.RUNNING] — worker is iterating; banner shows progress.
 *  - [State.FINISHED] — worker drained the queue; banner shows the
 *    completion summary until the user dismisses it
 *    ([markFinishedAcknowledged]).
 *
 * [BackfillSnapshot.safSkipped] counts content:// rows the worker
 * couldn't operate on in place (typically external-storage downloads on
 * Android 11+); it's surfaced in the completion summary so users know
 * why the "done" count is lower than the "total" they saw mid-run.
 */
@Singleton
class MetadataBackfillState @Inject constructor(
    appPaths: AppPaths,
) {
    private val store: DataStore<Preferences> = BackfillDataStoreProvider.getStore(
        File(appPaths.filesDir, "datastore/metadata_backfill_state.preferences_pb"),
    )

    val snapshot: Flow<BackfillSnapshot> = store.data.map { prefs ->
        BackfillSnapshot(
            state = State.valueOf(prefs[KEY_STATE] ?: State.IDLE.name),
            processed = prefs[KEY_PROCESSED] ?: 0,
            total = prefs[KEY_TOTAL] ?: 0,
            safSkipped = prefs[KEY_SAF_SKIPPED] ?: 0,
            finishedAt = prefs[KEY_FINISHED_AT],
        )
    }

    suspend fun markStarted(total: Int) {
        store.edit {
            it[KEY_STATE] = State.RUNNING.name
            it[KEY_TOTAL] = total
            it[KEY_PROCESSED] = 0
            it[KEY_SAF_SKIPPED] = 0
            it.remove(KEY_FINISHED_AT)
        }
    }

    suspend fun publishProgress(processed: Int, total: Int) {
        store.edit {
            it[KEY_PROCESSED] = processed
            it[KEY_TOTAL] = total
        }
    }

    suspend fun incrementSafSkipped() {
        store.edit {
            it[KEY_SAF_SKIPPED] = (it[KEY_SAF_SKIPPED] ?: 0) + 1
        }
    }

    suspend fun markFinished() {
        store.edit {
            it[KEY_STATE] = State.FINISHED.name
            it[KEY_FINISHED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun markFinishedAcknowledged() {
        store.edit {
            it[KEY_STATE] = State.IDLE.name
        }
    }

    enum class State { IDLE, RUNNING, FINISHED }

    data class BackfillSnapshot(
        val state: State,
        val processed: Int,
        val total: Int,
        val safSkipped: Int,
        val finishedAt: Long?,
    )

    companion object {
        private val KEY_STATE = stringPreferencesKey("state")
        private val KEY_PROCESSED = intPreferencesKey("processed")
        private val KEY_TOTAL = intPreferencesKey("total")
        private val KEY_SAF_SKIPPED = intPreferencesKey("saf_skipped")
        private val KEY_FINISHED_AT = longPreferencesKey("finished_at")
    }
}

