package com.stash.core.data.prefs

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.stash.core.common.platform.AppPaths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopLibraryScopePreference @Inject constructor(
    appPaths: AppPaths,
) : LibraryScopePreference {

    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { File(appPaths.filesDir, "datastore/library_scope_preference.preferences_pb") },
    )

    private val inLibraryOnlyKey = booleanPreferencesKey("artists_albums_in_library_only")

    override val inLibraryOnly: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[inLibraryOnlyKey] ?: false
    }

    override suspend fun current(): Boolean = inLibraryOnly.first()

    override suspend fun setInLibraryOnly(value: Boolean) {
        dataStore.edit { it[inLibraryOnlyKey] = value }
    }
}
