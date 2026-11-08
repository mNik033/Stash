package com.stash.core.data.listening

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

@Singleton
class RecapPreference @Inject constructor(
    appPaths: AppPaths,
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { File(appPaths.filesDir, "datastore/recap_preference.preferences_pb") },
    )

    private val mutedKey = booleanPreferencesKey("story_muted")

    val muted: Flow<Boolean> = dataStore.data
        .map { it[mutedKey] ?: false }
        .distinctUntilChanged()
        .catch { emit(false) }

    suspend fun setMuted(muted: Boolean) {
        dataStore.edit { it[mutedKey] = muted }
    }
}

