package com.stash.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Singleton DataStore for the hi-res output mode, separate from other preference stores. */
private val Context.hiResOutputDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hires_output_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Persists [HiResOutputMode] via DataStore; an unknown stored value reads as STANDARD. */
@Singleton
class HiResOutputPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : HiResOutputPreference {
    private val modeKey = stringPreferencesKey("mode")

    override val mode: Flow<HiResOutputMode> = context.hiResOutputDataStore.data
        .map { prefs ->
            prefs[modeKey]
                ?.let { stored -> HiResOutputMode.entries.firstOrNull { it.name == stored } }
                ?: HiResOutputMode.STANDARD
        }
        .distinctUntilChanged()

    override suspend fun setMode(mode: HiResOutputMode) {
        context.hiResOutputDataStore.edit { prefs -> prefs[modeKey] = mode.name }
    }
}
