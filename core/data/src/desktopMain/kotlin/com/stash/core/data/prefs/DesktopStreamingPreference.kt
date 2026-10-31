package com.stash.core.data.prefs

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stash.core.common.platform.AppPaths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopStreamingPreference @Inject constructor(
    appPaths: AppPaths,
) : StreamingPreference {

    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { File(appPaths.filesDir, "datastore/streaming_preference.preferences_pb") },
    )

    private val enabledKey = booleanPreferencesKey("streaming_enabled")
    private val cellularKey = booleanPreferencesKey("streaming_on_cellular")
    private val qualityKey = stringPreferencesKey("streaming_quality_tier")
    private val forceYouTubeFallbackKey = booleanPreferencesKey("force_youtube_fallback")
    private val forceQbdlxOnlyKey = booleanPreferencesKey("force_qbdlx_only")
    private val skipQobuzKey = booleanPreferencesKey("test_skip_qobuz")

    override val enabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[enabledKey] ?: true
    }

    override val streamOnCellular: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[cellularKey] ?: false
    }

    override val streamQuality: Flow<StreamQualityTier> = dataStore.data.map { prefs ->
        runCatching { StreamQualityTier.valueOf(prefs[qualityKey] ?: "") }
            .getOrDefault(StreamQualityTier.LOSSLESS)
    }

    override val forceYouTubeFallback: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[forceYouTubeFallbackKey] ?: false
    }

    override val forceQbdlxOnly: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[forceQbdlxOnlyKey] ?: false
    }

    override val skipQobuz: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[skipQobuzKey] ?: false
    }

    override suspend fun current(): Boolean = enabled.first()
    override suspend fun isForceYouTubeFallback(): Boolean = forceYouTubeFallback.first()
    override suspend fun isForceQbdlxOnly(): Boolean = forceQbdlxOnly.first()
    override suspend fun isSkipQobuz(): Boolean = skipQobuz.first()

    override suspend fun setEnabled(value: Boolean) {
        dataStore.edit { it[enabledKey] = value }
    }

    override suspend fun setStreamOnCellular(value: Boolean) {
        dataStore.edit { it[cellularKey] = value }
    }

    override suspend fun setStreamQuality(tier: StreamQualityTier) {
        dataStore.edit { it[qualityKey] = tier.name }
    }

    override suspend fun setForceYouTubeFallback(value: Boolean) {
        dataStore.edit { it[forceYouTubeFallbackKey] = value }
    }

    override suspend fun setForceQbdlxOnly(value: Boolean) {
        dataStore.edit { it[forceQbdlxOnlyKey] = value }
    }

    override suspend fun setSkipQobuz(value: Boolean) {
        dataStore.edit { it[skipQobuzKey] = value }
    }
}
