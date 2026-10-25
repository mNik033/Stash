package com.stash.desktop.platform

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stash.core.auth.crypto.EncryptionManager
import com.stash.core.auth.model.ServiceToken
import com.stash.core.auth.model.UserInfo
import com.stash.core.auth.store.TokenStore
import com.stash.core.common.platform.AppPaths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class StoredToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpoch: Long,
    val scope: String = "",
    val userId: String = "",
    val displayName: String = "",
    val imageUrl: String = "",
)

@Singleton
class DesktopTokenStore @Inject constructor(
    appPaths: AppPaths,
    private val encryption: EncryptionManager,
) : TokenStore {

    private val json = Json { ignoreUnknownKeys = true }

    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { File(appPaths.noBackupFilesDir, "desktop_tokens.preferences_pb") },
    )

    private companion object {
        const val CLOUD_PREFIX = "cloud_token_"
        val CLOUD_ACCOUNT_KEY = Regex("[a-z]+_[0-9]+")
    }

    private object Keys {
        val SPOTIFY = stringPreferencesKey("spotify_token")
        val YOUTUBE = stringPreferencesKey("youtube_token")
        val DISCORD = stringPreferencesKey("discord_token")
    }

    // ── Spotify ──────────────────────────────────────────────────────────

    override val spotifyToken: Flow<ServiceToken?> = dataStore.data.map { prefs ->
        prefs[Keys.SPOTIFY]?.let { decryptToken(it) }
    }

    override val spotifyUser: Flow<UserInfo?> = dataStore.data.map { prefs ->
        prefs[Keys.SPOTIFY]?.let { decryptUser(it) }
    }

    override suspend fun saveSpotifyToken(token: ServiceToken, user: UserInfo?) {
        val stored = token.toStoredToken(user)
        saveBlob(Keys.SPOTIFY, encryptStored(stored))
    }

    override suspend fun clearSpotify() {
        dataStore.edit { it.remove(Keys.SPOTIFY) }
    }

    // ── YouTube Music ────────────────────────────────────────────────────

    override val youTubeToken: Flow<ServiceToken?> = dataStore.data.map { prefs ->
        prefs[Keys.YOUTUBE]?.let { decryptToken(it) }
    }

    override val youTubeUser: Flow<UserInfo?> = dataStore.data.map { prefs ->
        prefs[Keys.YOUTUBE]?.let { decryptUser(it) }
    }

    override suspend fun saveYouTubeToken(token: ServiceToken, user: UserInfo?) {
        val stored = token.toStoredToken(user)
        saveBlob(Keys.YOUTUBE, encryptStored(stored))
    }

    override suspend fun clearYouTube() {
        dataStore.edit { it.remove(Keys.YOUTUBE) }
    }

    // ── Discord ──────────────────────────────────────────────────────────

    override val discordToken: Flow<ServiceToken?> = dataStore.data.map { prefs ->
        prefs[Keys.DISCORD]?.let { decryptToken(it) }
    }

    override val discordUser: Flow<UserInfo?> = dataStore.data.map { prefs ->
        prefs[Keys.DISCORD]?.let { decryptUser(it) }
    }

    override suspend fun saveDiscordToken(token: ServiceToken, user: UserInfo?) {
        val stored = token.toStoredToken(user)
        saveBlob(Keys.DISCORD, encryptStored(stored))
    }

    override suspend fun clearDiscord() {
        dataStore.edit { it.remove(Keys.DISCORD) }
    }

    // ── Cloud Accounts ───────────────────────────────────────────────────

    override suspend fun getCloudToken(accountKey: String): ServiceToken? =
        dataStore.data.first()[cloudKey(accountKey)]?.let { decryptToken(it) }

    override suspend fun saveCloudToken(accountKey: String, token: ServiceToken) {
        saveBlob(cloudKey(accountKey), encryptStored(token.toStoredToken(null)))
    }

    override suspend fun clearCloudToken(accountKey: String) {
        dataStore.edit { it.remove(cloudKey(accountKey)) }
    }

    private fun cloudKey(accountKey: String): Preferences.Key<String> {
        require(accountKey.matches(CLOUD_ACCOUNT_KEY)) { "bad cloud account key" }
        return stringPreferencesKey(CLOUD_PREFIX + accountKey)
    }

    private suspend fun saveBlob(key: Preferences.Key<String>, blob: String) {
        dataStore.edit { prefs ->
            if (encryption.consumeKeysetReset()) {
                prefs.remove(Keys.SPOTIFY)
                prefs.remove(Keys.YOUTUBE)
                prefs.remove(Keys.DISCORD)
                prefs.asMap().keys.map { it.name }.filter { it.startsWith(CLOUD_PREFIX) }
                    .forEach { prefs.remove(stringPreferencesKey(it)) }
            }
            prefs[key] = blob
        }
    }

    // ── Encryption helpers ───────────────────────────────────────────────

    private fun encryptStored(stored: StoredToken): String {
        val plaintext = json.encodeToString(StoredToken.serializer(), stored).toByteArray(Charsets.UTF_8)
        val encrypted = encryption.encrypt(plaintext)
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun decryptToken(encrypted: String): ServiceToken? = try {
        val stored = decryptStored(encrypted)
        if (stored.refreshToken.isEmpty()) null
        else ServiceToken(stored.accessToken, stored.refreshToken, stored.expiresAtEpoch, stored.scope)
    } catch (_: Exception) {
        null
    }

    private fun decryptUser(encrypted: String): UserInfo? = try {
        val stored = decryptStored(encrypted)
        if (stored.userId.isEmpty()) null
        else UserInfo(stored.userId, stored.displayName, stored.imageUrl.ifEmpty { null })
    } catch (_: Exception) {
        null
    }

    private fun decryptStored(encrypted: String): StoredToken {
        val bytes = Base64.getDecoder().decode(encrypted)
        val plaintext = encryption.decrypt(bytes)
        return json.decodeFromString(StoredToken.serializer(), String(plaintext, Charsets.UTF_8))
    }

    private fun ServiceToken.toStoredToken(user: UserInfo?): StoredToken = StoredToken(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtEpoch = expiresAtEpoch,
        scope = scope,
        userId = user?.id ?: "",
        displayName = user?.displayName ?: "",
        imageUrl = user?.imageUrl ?: "",
    )
}
