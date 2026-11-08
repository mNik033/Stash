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

/**
 * Settings › Library & Storage › Listening history: whether this phone keeps a history at all.
 *
 * While [paused], [com.stash.core.media.listening.ListeningRecorder] records nothing: no listen, no
 * skip, and no "now playing" to Last.fm or ListenBrainz (owner decision 6, 2026-10-18). With no listen
 * written, nothing is scrobbled either, and Stash Mixes, play counts and auto-save see nothing. Plays
 * that reach the phone from another device (the web mirror, a library file) are not this phone
 * listening and still arrive.
 *
 * Reads that fail (an IOException from the store) fall back to "not paused": history on is the
 * default, and a broken file must not silently stop it.
 */
@Singleton
class ListeningHistoryPreference @Inject constructor(
    appPaths: AppPaths,
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { File(appPaths.filesDir, "datastore/listening_history_preference.preferences_pb") },
    )

    private val pausedKey = booleanPreferencesKey("history_paused")

    val paused: Flow<Boolean> = dataStore.data
        .map { it[pausedKey] ?: false }
        .distinctUntilChanged()
        .catch { emit(false) }

    suspend fun setPaused(paused: Boolean) {
        dataStore.edit { it[pausedKey] = paused }
    }
}

