package com.stash.core.data.listenbrainz

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stash.core.auth.crypto.EncryptionManager
import com.stash.core.auth.crypto.NoOpEncryptionManager
import com.stash.core.auth.crypto.decryptString
import com.stash.core.auth.crypto.encryptString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Dedicated DataStore for the ListenBrainz connection — mirrors `lastfm_session`. */
private val Context.listenBrainzDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "listenbrainz",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Stores the user's ListenBrainz token and, just as importantly, **when they
 * connected**.
 *
 * User token is encrypted at rest using [EncryptionManager].
 */
@Singleton
class ListenBrainzPreference @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: EncryptionManager,
) {
    /** Secondary constructor for testing */
    constructor(context: Context) : this(context, NoOpEncryptionManager)

    private val tokenKey = stringPreferencesKey("user_token")
    private val connectedAtKey = longPreferencesKey("connected_at_ms")
    private val nowPlayingKey = stringPreferencesKey("now_playing_enabled")

    val token: Flow<String?> = context.listenBrainzDataStore.data.map { prefs ->
        prefs[tokenKey]?.takeIf { it.isNotBlank() }?.let { encryption.decryptString(it) }
    }

    /** Null when never connected. */
    val connectedAtMs: Flow<Long?> = context.listenBrainzDataStore.data.map { prefs ->
        prefs[connectedAtKey]
    }

    /** Users who want scrobbles but not a live "listening now" status. */
    val nowPlayingEnabled: Flow<Boolean> = context.listenBrainzDataStore.data.map { prefs ->
        prefs[nowPlayingKey] != "false"
    }

    suspend fun tokenNow(): String? = runCatching { token.first() }.getOrNull()

    suspend fun connectedAtNow(): Long? = runCatching { connectedAtMs.first() }.getOrNull()

    suspend fun nowPlayingEnabledNow(): Boolean =
        runCatching { nowPlayingEnabled.first() }.getOrDefault(true)

    /**
     * Saves a validated token. [nowMs] becomes the cutoff, so only listens from
     * this moment forward are ever submitted.
     *
     * Reconnecting with the same token does NOT move the cutoff forward — that
     * would silently drop anything queued during an outage the user was
     * reconnecting to fix.
     */
    suspend fun connect(token: String, nowMs: Long) {
        val trimmed = token.trim()
        val encrypted = encryption.encryptString(trimmed)
        context.listenBrainzDataStore.edit { prefs ->
            val existing = prefs[tokenKey]?.let { encryption.decryptString(it) }
            prefs[tokenKey] = encrypted
            if (existing != trimmed || prefs[connectedAtKey] == null) {
                prefs[connectedAtKey] = nowMs
            }
        }
    }

    suspend fun setNowPlayingEnabled(enabled: Boolean) {
        context.listenBrainzDataStore.edit { prefs ->
            prefs[nowPlayingKey] = enabled.toString()
        }
    }

    /** Disconnects. The caller should also clear this target's submission rows. */
    suspend fun clear() {
        context.listenBrainzDataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(connectedAtKey)
        }
    }
}
