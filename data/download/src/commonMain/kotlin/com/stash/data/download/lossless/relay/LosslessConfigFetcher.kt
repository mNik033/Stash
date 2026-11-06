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

    /** Test seam: the network [refreshLoop] waits for while offline. */
    internal var network: ConfigNetworkSignal = createDefaultConfigNetworkSignal(appPaths)
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

    private val _refreshedAt = MutableStateFlow(0L)

    /**
     * Wall-clock time of this process's last [refresh] that applied a fresh, verified config; 0
     * before one. Never moved by the cache or by a failed refresh: Battery Saver's deferral keys
     * on it (StashApplication), so a failed fetch can't count as a recent one.
     */
    val refreshedAt: StateFlow<Long> = _refreshedAt.asStateFlow()

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
        _refreshedAt.value = System.currentTimeMillis()
        true
    }

    private fun apply(cfg: LosslessConfig) {
        _relays.value = cfg.relays
        _relayKey.value = cfg.relayKey
        _tidalRelay.value = cfg.tidalRelayUrl?.let { TidalRelay(it, cfg.tidalRelayToken?.takeIf { t -> t.isNotBlank() }) }
        _updatedAt.value = cfg.updatedAt
    }

    /** Load the cache, then keep the config fresh with [refreshLoop]. */
    fun start(scope: CoroutineScope) {
        if (!enabled) return
        scope.launch {
            loadCached()
            refreshLoop { refresh() }
        }
    }

    /**
     * Refresh now, then every [REFRESH_INTERVAL_MS]; after failures, back off ([nextDelayMs]). [refreshOnce] is passed
     * in so tests drive the loop on virtual time. Never returns.
     *
     * Offline, nothing runs on a timer: before each attempt the loop waits for a validated network ([network]) and
     * refreshes as soon as one is back. It used to retry every 15 min for as long as the process lived, each attempt a
     * request that could only fail. The failure count starts over only after a real wait (the earlier failures were on
     * another network); when the network can't be watched, [ConfigNetworkSignal.awaitOnline] returns false at once and
     * the count stands, so the back-off still grows.
     *
     * Every attempt is followed by a delay, so a network signal that says "online" while nothing works (or can't be
     * watched at all) can never make this spin: failures back off 15 min, 30 min, then hourly.
     */
    internal suspend fun refreshLoop(refreshOnce: suspend () -> Boolean): Nothing {
        var failures = 0
        while (true) {
            if (!network.isOnline() && network.awaitOnline()) failures = 0
            failures = if (refreshOnce()) 0 else failures + 1
            delay(nextDelayMs(failures))
        }
    }

    /**
     * The wait after an attempt, given the [failures] in a row so far: 6 h after a good refresh (0), and after a failure
     * (a captive portal, the host down) 15 min, then 30 min, then hourly.
     */
    internal fun nextDelayMs(failures: Int): Long {
        if (failures <= 0) return REFRESH_INTERVAL_MS
        val doublings = (failures - 1).coerceAtMost(MAX_DOUBLINGS)
        return (RETRY_INTERVAL_MS shl doublings).coerceAtMost(MAX_RETRY_INTERVAL_MS)
    }

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

        /** The first retry after a failure; each further failure in a row doubles it, up to [MAX_RETRY_INTERVAL_MS]. */
        const val RETRY_INTERVAL_MS = 15 * 60 * 1000L
        const val MAX_RETRY_INTERVAL_MS = 60 * 60 * 1000L

        /** Bounds the shift in [nextDelayMs] (the cap is reached after two doublings anyway). */
        const val MAX_DOUBLINGS = 8

        /** A hostile/misconfigured host must not OOM us — an `Error` would escape [ioCatching]. */
        const val MAX_CONFIG_BYTES = 64L * 1024
    }
}

