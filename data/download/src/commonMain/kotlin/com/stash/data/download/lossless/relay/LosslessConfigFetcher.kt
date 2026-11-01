package com.stash.data.download.lossless.relay

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stash.core.common.Log
import com.stash.core.common.platform.AppPaths
import com.stash.data.download.lossless.LosslessSourcePreferences
import java.io.File
import java.io.IOException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** One relay base from the signed config; lower [priority] is tried first. */
@Serializable
data class RelayEntry(val base: String, val priority: Int = 1)

/**
 * A Tidal (BitChord) server: an https base and its optional token.
 */
data class TidalRelay(val base: String, val token: String?, val userOwned: Boolean = false)

@Serializable
data class LosslessConfig(
    val v: Int = 1,
    val relays: List<RelayEntry> = emptyList(),
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("relay_key") val relayKey: String? = null,
    @SerialName("tidal_relay_url") val tidalRelayUrl: String? = null,
    @SerialName("tidal_relay_token") val tidalRelayToken: String? = null,
)

/**
 * The runtime relay list.
 */
@Singleton
class LosslessConfigFetcher @Inject constructor(
    appPaths: AppPaths,
    sharedClient: OkHttpClient,
) {
    private val dataStore: DataStore<Preferences> = getDataStore(
        File(appPaths.filesDir, "datastore/lossless_relay_config.preferences_pb")
    )

    /** Test seam. */
    internal var configUrl: String = LosslessBuildConfig.configUrl

    /** Test seam. */
    internal var publicKeyB64: String = LosslessBuildConfig.configPubKey

    /** Test seam. */
    internal var httpClient: OkHttpClient = sharedClient
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonKey = stringPreferencesKey("config_json")

    private val _relays = MutableStateFlow<List<RelayEntry>>(emptyList())
    val relays: StateFlow<List<RelayEntry>> = _relays.asStateFlow()

    private val _relayKey = MutableStateFlow<String?>(null)
    /** The current relay access key from the applied config; null when unsigned. */
    val relayKey: StateFlow<String?> = _relayKey.asStateFlow()

    private val _tidalRelay = MutableStateFlow<TidalRelay?>(null)

    /** The Tidal (BitChord) server from the signed config, or null. See [LosslessConfig.tidalRelayUrl]. */
    val tidalRelay: StateFlow<TidalRelay?> = _tidalRelay.asStateFlow()

    private val _updatedAt = MutableStateFlow(0L)
    /** `updated_at` (unix seconds) of the applied config; 0 until one is loaded. For the diagnostics bundle. */
    val updatedAt: StateFlow<Long> = _updatedAt.asStateFlow()

    private val installIdKey = stringPreferencesKey("install_id")

    suspend fun installId(): String =
        dataStore.edit { p ->
            if (p[installIdKey] == null) p[installIdKey] = UUID.randomUUID().toString()
        }[installIdKey]!!

    val enabled: Boolean get() = configUrl.isNotBlank() && publicKeyB64.isNotBlank()

    suspend fun loadCached() {
        val cached = readCache() ?: return
        parse(cached)?.let { apply(it) }
    }

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext false
        val body = ioCatching("config") { getBytes(configUrl) } ?: return@withContext false
        val sig = ioCatching("sig") { String(getBytes("$configUrl.sig")).trim() } ?: return@withContext false
        if (!verify(body, sig)) {
            Log.w(TAG, "lossless.json signature invalid — keeping the cached copy")
            return@withContext false
        }
        val text = String(body)
        val fresh = parse(text) ?: return@withContext false
        val cachedUpdatedAt = readCache()?.let { parse(it)?.updatedAt } ?: 0L
        if (fresh.updatedAt < cachedUpdatedAt) {
            Log.w(TAG, "lossless config older than the cached copy (${fresh.updatedAt} < $cachedUpdatedAt) — rejected")
            return@withContext false
        }
        apply(fresh)
        ioCatching("cache write") { dataStore.edit { it[jsonKey] = text } }
        Log.i(TAG, "lossless config applied: ${fresh.relays.size} relay(s)")
        true
    }

    private fun apply(cfg: LosslessConfig) {
        _relays.value = cfg.relays
        _relayKey.value = cfg.relayKey
        _tidalRelay.value = cfg.tidalRelayUrl?.let { TidalRelay(it, cfg.tidalRelayToken?.takeIf { t -> t.isNotBlank() }) }
        _updatedAt.value = cfg.updatedAt
    }

    fun start(scope: CoroutineScope) {
        if (!enabled) return
        scope.launch {
            loadCached()
            while (true) {
                val ok = refresh()
                delay(nextDelayMs(ok))
            }
        }
    }

    internal fun nextDelayMs(refreshed: Boolean): Long = if (refreshed) REFRESH_INTERVAL_MS else RETRY_INTERVAL_MS

    internal fun verify(bytes: ByteArray, sigB64: String): Boolean = runCatching {
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)))
        Signature.getInstance("SHA256withECDSA").run { initVerify(pub); update(bytes); verify(Base64.getDecoder().decode(sigB64)) }
    }.getOrDefault(false)

    private suspend fun readCache(): String? =
        ioCatching("cache read") { dataStore.data.first()[jsonKey] }

    private fun parse(text: String): LosslessConfig? = runCatching {
        val cfg = json.decodeFromString<LosslessConfig>(text)
        cfg.copy(
            relays = cfg.relays
                .mapNotNull { e -> LosslessSourcePreferences.normaliseEndpoint(e.base)?.let { RelayEntry(it, e.priority) } }
                .sortedBy { it.priority },
            tidalRelayUrl = LosslessSourcePreferences.normaliseEndpoint(cfg.tidalRelayUrl),
        )
    }.getOrNull()

    private fun getBytes(url: String): ByteArray {
        val req = Request.Builder().url(url).header("Accept", "*/*").get().build()
        httpClient.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val src = r.body?.source() ?: throw IOException("empty body")
            if (src.request(MAX_CONFIG_BYTES + 1)) throw IOException("body over $MAX_CONFIG_BYTES bytes")
            return src.readByteArray()
        }
    }

    private inline fun <T> ioCatching(label: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val detail = e.message?.takeIf {
            it.startsWith("HTTP ") || it.startsWith("body over") || it == "empty body"
        } ?: e.javaClass.simpleName
        Log.w(TAG, "lossless config $label failed: $detail")
        null
    }

    internal suspend fun clearForTest() {
        _relays.value = emptyList()
        _relayKey.value = null
        _tidalRelay.value = null
        dataStore.edit { it.clear() }
    }

    private companion object {
        const val TAG = "LosslessConfig"
        private val dataStores = java.util.concurrent.ConcurrentHashMap<String, DataStore<Preferences>>()

        internal fun getDataStore(file: File): DataStore<Preferences> =
            dataStores.computeIfAbsent(file.canonicalPath) {
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    produceFile = { file },
                )
            }
        const val REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val RETRY_INTERVAL_MS = 15 * 60 * 1000L
        const val MAX_CONFIG_BYTES = 64L * 1024
    }
}

