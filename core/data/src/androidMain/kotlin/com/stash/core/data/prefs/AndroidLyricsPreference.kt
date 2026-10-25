package com.stash.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Dedicated DataStore for lyrics display preferences. */
private val Context.lyricsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lyrics_preference",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class AndroidLyricsPreference @Inject constructor(
    @ApplicationContext private val context: Context,
) : LyricsPreference {
    private val liveBarKey = booleanPreferencesKey("live_bar_enabled")
    private val sourceKey = stringPreferencesKey("lyrics_source_preference")
    private val bulkFetchCursorKey = longPreferencesKey("bulk_fetch_cursor")
    private val ttmlUpgradeCursorKey = longPreferencesKey("ttml_upgrade_cursor")
    private val recheckCursorKey = longPreferencesKey("library_recheck_cursor")
    private val recheckFinishedAtKey = longPreferencesKey("library_recheck_finished_at")

    override val liveBarEnabled: Flow<Boolean> = context.lyricsDataStore.data.map { prefs ->
        prefs[liveBarKey] ?: false
    }

    override suspend fun setLiveBarEnabled(value: Boolean) {
        context.lyricsDataStore.edit { it[liveBarKey] = value }
    }

    override val sourcePreference: Flow<LyricsSourcePreference> = context.lyricsDataStore.data.map { prefs ->
        prefs[sourceKey]?.let { stored ->
            runCatching { LyricsSourcePreference.valueOf(stored) }.getOrNull()
        } ?: LyricsSourcePreference.APPLE_MUSIC
    }

    override suspend fun setSourcePreference(value: LyricsSourcePreference) {
        context.lyricsDataStore.edit { it[sourceKey] = value.name }
    }

    override fun outputOffsetMs(outputKey: String): Flow<Long> {
        val key = outputOffsetKey(outputKey)
        return context.lyricsDataStore.data.map { it[key] ?: 0L }.distinctUntilChanged()
    }

    override suspend fun setOutputOffsetMs(outputKey: String, offsetMs: Long) {
        val key = outputOffsetKey(outputKey)
        context.lyricsDataStore.edit { prefs ->
            if (offsetMs == 0L) {
                prefs.remove(key)
            } else {
                prefs[key] = offsetMs
            }
        }
    }

    private fun outputOffsetKey(outputKey: String) = longPreferencesKey("output_offset_ms:$outputKey")

    override val bulkFetchCursor: Flow<Long> = context.lyricsDataStore.data.map { it[bulkFetchCursorKey] ?: 0L }

    override suspend fun setBulkFetchCursor(trackId: Long) {
        context.lyricsDataStore.edit { it[bulkFetchCursorKey] = trackId }
    }

    override val ttmlUpgradeCursor: Flow<Long> = context.lyricsDataStore.data.map { it[ttmlUpgradeCursorKey] ?: 0L }

    override suspend fun setTtmlUpgradeCursor(trackId: Long) {
        context.lyricsDataStore.edit { it[ttmlUpgradeCursorKey] = trackId }
    }

    override val recheckCursor: Flow<Long> = context.lyricsDataStore.data.map { it[recheckCursorKey] ?: 0L }

    override suspend fun setRecheckCursor(trackId: Long) {
        context.lyricsDataStore.edit { it[recheckCursorKey] = trackId }
    }

    override val recheckFinishedAt: Flow<Long?> = context.lyricsDataStore.data.map { it[recheckFinishedAtKey] }

    override suspend fun finishRecheck(atMs: Long) {
        context.lyricsDataStore.edit {
            it.remove(recheckCursorKey)
            it[recheckFinishedAtKey] = atMs
        }
    }
}
