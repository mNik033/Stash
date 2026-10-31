package com.stash.data.download.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stash.core.common.platform.AppPaths
import com.stash.core.data.prefs.QualityPreference
import com.stash.core.model.QualityTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's preferred audio quality tier via DataStore across platforms.
 *
 * Implements [QualityPreference] so feature modules can depend on the
 * abstraction in `:core:data` without pulling in `:data:download`.
 * The tier is stored by its enum name so it survives across app versions
 * even if the ordinal changes.
 */
@Singleton
class QualityPreferencesManager @Inject constructor(
    appPaths: AppPaths,
) : QualityPreference {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { File(appPaths.filesDir, "datastore/quality_preferences.preferences_pb") },
    )

    private val qualityKey = stringPreferencesKey("quality_tier")

    /**
     * Emits the current [QualityTier], defaulting to [QualityTier.MAX] (v0.9.8+).
     */
    override val qualityTier: Flow<QualityTier> = dataStore.data.map { prefs ->
        val name = prefs[qualityKey]
        name?.let { runCatching { QualityTier.valueOf(it) }.getOrNull() } ?: QualityTier.MAX
    }

    /** Persists the selected [tier]. */
    override suspend fun setQualityTier(tier: QualityTier) {
        dataStore.edit { prefs ->
            prefs[qualityKey] = tier.name
        }
    }
}

/**
 * Maps a [QualityTier] to the corresponding yt-dlp command-line arguments.
 */
fun QualityTier.toYtDlpArgs(): List<String> = when (this) {
    QualityTier.MAX -> listOf(
        "-f", "141/251/140/250/bestaudio",
        "--embed-metadata",
    )
    QualityTier.BEST -> listOf(
        "-f", "140/bestaudio[ext=m4a]/251/bestaudio",
        "--embed-metadata",
    )
    QualityTier.HIGH -> listOf(
        "-f", "251/250/bestaudio[ext=webm]/bestaudio",
        "--embed-metadata",
    )
    QualityTier.NORMAL -> listOf(
        "-f", "250/251/bestaudio",
        "--embed-metadata",
    )
    QualityTier.LOW -> listOf(
        "-f", "250/249/bestaudio",
        "--embed-metadata",
    )
}

/**
 * yt-dlp args for Download quality "Opus": Opus whenever YouTube has it, whatever the tier.
 */
fun QualityTier.toOpusYtDlpArgs(): List<String> = listOf(
    "-f",
    when (this) {
        QualityTier.MAX, QualityTier.BEST, QualityTier.HIGH -> "251/250/249/bestaudio[acodec=opus]/140/bestaudio"
        QualityTier.NORMAL -> "250/251/249/bestaudio[acodec=opus]/140/bestaudio"
        QualityTier.LOW -> "249/250/251/bestaudio[acodec=opus]/139/140/bestaudio"
    },
    "--embed-metadata",
)

