package com.stash.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stash.core.common.platform.AppPaths
import com.stash.core.model.DownloadNetworkMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's chosen [DownloadNetworkMode] for background
 * downloads. Both `StashDiscoveryWorker` and `TagEnrichmentWorker` read
 * this at schedule time to decide what `Constraints` to apply.
 *
 * When the user changes the mode, the Settings layer is responsible for
 * re-scheduling the affected workers so the new constraints take effect —
 * WorkManager snapshots constraints at enqueue time, it doesn't observe
 * this flow itself.
 *
 * Stored by enum name so it survives ordinal shifts across app versions.
 */
@Singleton
class DownloadNetworkPreference @Inject constructor(
    appPaths: AppPaths,
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { File(appPaths.filesDir, "datastore/download_network_preference.preferences_pb") },
    )

    private val modeKey = stringPreferencesKey("download_network_mode")

    /** Reactive stream of the currently-selected mode, defaulting to [DownloadNetworkMode.WIFI_AND_CHARGING]. */
    val mode: Flow<DownloadNetworkMode> = dataStore.data.map { prefs ->
        val name = prefs[modeKey]
        name?.let { runCatching { DownloadNetworkMode.valueOf(it) }.getOrNull() }
            ?: DownloadNetworkMode.WIFI_AND_CHARGING
    }

    /** Snapshot of the current mode — used at worker schedule time. */
    suspend fun current(): DownloadNetworkMode = mode.first()

    /** Persist [value]. Caller is responsible for re-scheduling workers. */
    suspend fun setMode(value: DownloadNetworkMode) {
        dataStore.edit { prefs ->
            prefs[modeKey] = value.name
        }
    }
}

