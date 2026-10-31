package com.stash.core.auth.youtube

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

/**
 * Persists user-provided Google Cloud OAuth credentials (Client ID and Client Secret)
 * for the YouTube device-code authorization flow.
 *
 * Client Secret is encrypted at rest using [EncryptionManager].
 */
@Singleton
class YouTubeCredentialsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: EncryptionManager,
) {
    /** Secondary constructor for testing */
    constructor(context: Context) : this(context, NoOpEncryptionManager)

    companion object {
        private val KEY_CLIENT_ID = stringPreferencesKey("yt_client_id")
        private val KEY_CLIENT_SECRET = stringPreferencesKey("yt_client_secret")
    }

    /** Reactive flow of the stored Client ID, empty string if not set. */
    val clientId: Flow<String> = context.youTubeCredentials.data.map { prefs ->
        prefs[KEY_CLIENT_ID] ?: ""
    }

    /** Reactive flow of the stored Client Secret (decrypted), empty string if not set. */
    val clientSecret: Flow<String> = context.youTubeCredentials.data.map { prefs ->
        prefs[KEY_CLIENT_SECRET]?.let { encryption.decryptString(it, fallbackToEmpty = true) } ?: ""
    }

    /**
     * Returns true if both Client ID and Client Secret have been saved.
     */
    suspend fun hasCredentials(): Boolean {
        val id = getClientId()
        val secret = getClientSecret()
        return id.isNotBlank() && secret.isNotBlank()
    }

    /**
     * Returns the stored Client ID, or empty string if not set.
     */
    suspend fun getClientId(): String {
        return context.youTubeCredentials.data.first()[KEY_CLIENT_ID] ?: ""
    }

    /**
     * Returns the stored Client Secret (decrypted), or empty string if not set.
     */
    suspend fun getClientSecret(): String {
        return context.youTubeCredentials.data.first()[KEY_CLIENT_SECRET]?.let {
            encryption.decryptString(it, fallbackToEmpty = true)
        } ?: ""
    }

    /**
     * Saves the user's Google Cloud OAuth credentials. Client Secret is encrypted at rest.
     *
     * @param clientId     The OAuth Client ID (TV / limited-input device type).
     * @param clientSecret The OAuth Client Secret paired with [clientId].
     */
    suspend fun saveCredentials(clientId: String, clientSecret: String) {
        val encryptedSecret = encryption.encryptString(clientSecret.trim())
        context.youTubeCredentials.edit { prefs ->
            prefs[KEY_CLIENT_ID] = clientId.trim()
            prefs[KEY_CLIENT_SECRET] = encryptedSecret
        }
    }

    /**
     * Clears the stored credentials.
     */
    suspend fun clearCredentials() {
        context.youTubeCredentials.edit { it.clear() }
    }
}

/** Singleton DataStore instance for YouTube credentials. */
private val Context.youTubeCredentials by preferencesDataStore(
    name = "youtube_credentials",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
