package com.stash.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.stash.core.common.platform.AppPaths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User opt-out for the Qobuz discovery sections on Home (New Releases,
 * Qobuz Playlists, Top Albums, and the genre chips that steer them).
 * When `false`, Home keeps only the Stash mix hero/rails and whatever the
 * user imports from Spotify / YouTube — and the catalog fetches behind the
 * discovery rows are not made at all. Default `true` preserves current
 * behavior.
 */
@Singleton
class HomeDiscoveryPreference @Inject constructor(
    appPaths: AppPaths,
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { File(appPaths.filesDir, "datastore/home_discovery_preference.preferences_pb") },
    )

    private val enabledKey = booleanPreferencesKey("qobuz_discovery_enabled")

    val enabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[enabledKey] ?: true
    }.distinctUntilChanged().catch { emit(true) }

    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { it[enabledKey] = value }
    }
}
