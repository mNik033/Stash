package com.stash.data.download.lossless

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stash.core.auth.crypto.EncryptionManager
import com.stash.core.auth.crypto.NoOpEncryptionManager
import com.stash.core.auth.crypto.decryptString
import com.stash.core.auth.crypto.encryptString
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.model.DownloadMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull



/**
 * Persists user preferences for the lossless-source resolver chain:
 *
 *  - Priority order (which source the registry tries first, second, etc.)
 *  - Minimum quality threshold (skip sources whose stated format is lower)
 *
 * Per-source enable/disable lives on the [LosslessSource] implementation
 * itself (typically gated on whether the user has provided required
 * credentials or a configured URL template). This preference store is
 * only for the cross-source ordering and threshold.
 *
 * Order is stored as a comma-joined string of source ids — survives across
 * app versions, tolerates unknown ids gracefully (registry filters them
 * out before resolving), and keeps the schema trivially debuggable.
 */
@Singleton
class LosslessSourcePreferences @Inject constructor(
    appPaths: com.stash.core.common.platform.AppPaths,
    private val downloadQueueDao: DownloadQueueDao,
    private val encryption: EncryptionManager,
) {
    private val dataStore: DataStore<Preferences> = getDataStore(
        java.io.File(appPaths.filesDir, "datastore/lossless_source_preferences.preferences_pb")
    )

    /** Secondary constructor for testing */
    constructor(
        appPaths: com.stash.core.common.platform.AppPaths,
        downloadQueueDao: DownloadQueueDao,
    ) : this(appPaths, downloadQueueDao, NoOpEncryptionManager)

    private val priorityKey = stringPreferencesKey("priority_order")
    private val minQualityKey = stringPreferencesKey("min_quality")
    private val enabledKey = booleanPreferencesKey("enabled")
    private val customLosslessEndpointKey = stringPreferencesKey("custom_lossless_endpoint")
    private val bitchordEndpointKey = stringPreferencesKey("bitchord_endpoint")
    private val bitchordAccessTokenKey = stringPreferencesKey("bitchord_access_token")
    private val captchaCookieKey = stringPreferencesKey("squid_wtf_captcha_verified_at")
    private val captchaCookieSetAtKey = longPreferencesKey("squid_wtf_captcha_set_at_ms")
    private val bannerDismissedKey = booleanPreferencesKey("home_banner_dismissed")
    private val losslessOfflineDismissedKey = booleanPreferencesKey("lossless_offline_dismissed")
    private val qualityTierKey = stringPreferencesKey("lossless_quality_tier")
    private val youtubeFallbackKey = booleanPreferencesKey("youtube_fallback_enabled")
    private val tidalAacFallbackKey = booleanPreferencesKey("tidal_aac_fallback")
    private val downloadModeKey = stringPreferencesKey("download_mode")
    // Retained only so [purgeRetiredSourceKeys] can delete them from existing
    // installs; the antra and arcod sources were removed.
    private val antraSessionKey = stringPreferencesKey("antra_session_cookie")
    private val antraCfClearanceKey = stringPreferencesKey("antra_cf_clearance_cookie")
    private val antraUsernameKey = stringPreferencesKey("antra_username")
    private val arcodRescueDismissedKey = booleanPreferencesKey("arcod_rescue_dismissed")

    /**
     * Master switch for the lossless-source pipeline. When false, the
     * download path skips the registry entirely and goes straight to
     * yt-dlp — same behaviour as before the lossless feature shipped.
     *
     * Defaults to true (v0.9.8+): fresh installs land lossless-ready;
     * existing v0.9.7 users who explicitly toggled it off keep their
     * saved value (DataStore preserves explicit writes). Users who
     * never opened the toggle pick up the new default — functionally
     * identical to v0.9.7 behaviour because the captcha is unverified
     * (silent yt-dlp/MP3 fallback via SquidWtfCaptchaInterceptor).
     *
     * The toggle lives on this preferences class (rather than its own
     * DataStore) so all lossless-related settings stay in one place
     * and the schema can evolve together.
     *
     * Deduped, and caught so a read error cannot terminate the chain: this feeds
     * two of Home's banner combines, and a DataStore IOException that killed the
     * flow would leave Home stuck in `isLoading` forever.
     *
     * The catch fails OPEN — `emit(true)`, the same default the `map` documents
     * above. Deliberate: failing to `false` would switch lossless off for a
     * paying user on one transient IOException. (Contrast [customLosslessEndpoint]
     * and the dismissal flags below, whose defaults happen to be the closed ones.)
     * Note `catch {}` COMPLETES the flow: after an error these emit their fallback
     * once and never re-emit for the process lifetime.
     */
    val enabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[enabledKey] ?: true
    }.distinctUntilChanged().catch { emit(true) }

    suspend fun enabledNow(): Boolean = enabled.first()

    /**
     * A user-supplied lossless relay base URL (Settings › Audio › Advanced), or
     * null. Outranks the public relay from runtime config when set — an explicit
     * choice beats an implicit one. Stored normalised: https only, no trailing
     * slash, no query. The APK ships no default. Deduped (the store re-emits on
     * every unrelated write) and fail-closed (a read error must not terminate a
     * `combine`).
     */
    val customLosslessEndpoint: Flow<String?> = dataStore.data.map { prefs ->
        prefs[customLosslessEndpointKey]?.takeIf { it.isNotBlank() }
    }.distinctUntilChanged().catch { emit(null) }

    suspend fun customLosslessEndpointNow(): String? = customLosslessEndpoint.first()

    suspend fun setCustomLosslessEndpoint(raw: String?) {
        val v = normaliseEndpoint(raw)
        dataStore.edit { prefs ->
            if (v == null) prefs.remove(customLosslessEndpointKey) else prefs[customLosslessEndpointKey] = v
        }
    }

    /**
     * The user's own self-hosted BitChord Lossless Relay (Tidal), or null = the Tidal
     * rung is off. Same normalisation as [customLosslessEndpoint] (https only, no
     * query, no trailing slash), so a path prefix survives: BitChord's own
     * `https://host/<ACCESS_TOKEN>` form works as-is. The APK ships no default —
     * a BitChord server spends ONE person's Tidal account, so it is never shared
     * through the signed runtime config. Deduped and fail-closed.
     */
    val bitchordEndpoint: Flow<String?> = dataStore.data.map { prefs ->
        prefs[bitchordEndpointKey]?.takeIf { it.isNotBlank() }
    }.distinctUntilChanged().catch { emit(null) }

    suspend fun bitchordEndpointNow(): String? = bitchordEndpoint.first()

    suspend fun setBitchordEndpoint(raw: String?) {
        val v = normaliseEndpoint(raw)
        dataStore.edit { prefs ->
            if (v == null) prefs.remove(bitchordEndpointKey) else prefs[bitchordEndpointKey] = v
        }
    }

    /**
     * The server's `ACCESS_TOKEN`, sent as `X-Access-Token` — or null when the
     * server runs open, or the token is already the endpoint's path prefix. A
     * secret: encrypted at rest with [EncryptionManager]. Never logged, never in the diagnostics bundle.
     */
    val bitchordAccessToken: Flow<String?> = dataStore.data.map { prefs ->
        prefs[bitchordAccessTokenKey]?.takeIf { it.isNotBlank() }?.let { encryption.decryptString(it) }
    }.distinctUntilChanged().catch { emit(null) }

    suspend fun bitchordAccessTokenNow(): String? = bitchordAccessToken.first()

    suspend fun setBitchordAccessToken(raw: String?) {
        val v = raw?.trim()?.takeIf { it.isNotEmpty() }
        val encrypted = v?.let { encryption.encryptString(it) }
        dataStore.edit { prefs ->
            if (encrypted == null) prefs.remove(bitchordAccessTokenKey) else prefs[bitchordAccessTokenKey] = encrypted
        }
    }

    /**
     * The Lossless switch, which now governs STREAMING only: downloads follow [downloadMode].
     * The download mode is pinned first, in the same edit, so turning lossless streaming
     * off or on never changes what a download saves (a user who never had a stored mode
     * would otherwise have it follow this switch through [legacyDownloadMode]). It no longer
     * releases waiting downloads: they wait under the download mode, which this doesn't touch.
     */
    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { prefs ->
            if (prefs[downloadModeKey] == null) prefs[downloadModeKey] = legacyDownloadMode(prefs).name
            prefs[enabledKey] = value
        }
    }

    /**
     * What a download saves: Lossless, AAC 320 or Opus ([DownloadMode]). One setting for every
     * download path; streaming keeps [enabled].
     *
     * Before this setting, the Lossless switch decided downloads too: on = FLAC first, off = the
     * lossy chain. Until a mode is stored it reads from that switch ([legacyDownloadMode]), and
     * [migrateDownloadMode] stores it once at app start, so an existing user's downloads carry
     * on as they were.
     */
    val downloadMode: Flow<DownloadMode> = dataStore.data.map { prefs ->
        DownloadMode.fromName(prefs[downloadModeKey]) ?: legacyDownloadMode(prefs)
    }.distinctUntilChanged().catch { emit(DownloadMode.LOSSLESS) }

    suspend fun downloadModeNow(): DownloadMode = downloadMode.first()

    suspend fun setDownloadMode(mode: DownloadMode) {
        dataStore.edit { prefs -> prefs[downloadModeKey] = mode.name }
    }

    /**
     * Stores the download mode the old Lossless switch implied (on = [DownloadMode.LOSSLESS],
     * off = [DownloadMode.OPUS]) when none is stored yet. Once, silently; a no-op afterwards.
     * Returns the stored mode.
     */
    suspend fun migrateDownloadMode(): DownloadMode {
        var mode = DownloadMode.LOSSLESS
        dataStore.edit { prefs ->
            val stored = DownloadMode.fromName(prefs[downloadModeKey])
            mode = stored ?: legacyDownloadMode(prefs).also { prefs[downloadModeKey] = it.name }
        }
        return mode
    }

    /** The mode the old Lossless switch meant for downloads: on (the default) = Lossless, off = Opus. */
    private fun legacyDownloadMode(prefs: Preferences): DownloadMode =
        if (prefs[enabledKey] ?: true) DownloadMode.LOSSLESS else DownloadMode.OPUS

    /** Tests: write keys as an older build stored them, bypassing today's setters. */
    internal suspend fun writeRawForTest(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    /**
     * Tidal's 320 kbps AAC when a song has no lossless version, for streaming and
     * downloads. Default ON; OFF keeps Tidal FLAC-only.
     */
    val tidalAacFallback: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[tidalAacFallbackKey] ?: true
    }

    suspend fun tidalAacFallbackNow(): Boolean = tidalAacFallback.first()

    suspend fun setTidalAacFallback(value: Boolean) {
        dataStore.edit { prefs -> prefs[tidalAacFallbackKey] = value }
    }

    val youtubeFallbackEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[youtubeFallbackKey] ?: false
    }

    suspend fun youtubeFallbackEnabledNow(): Boolean = youtubeFallbackEnabled.first()

    suspend fun setYoutubeFallbackEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[youtubeFallbackKey] = value }
        // Enabling YouTube fallback means deferred rows can now be
        // satisfied via the legacy yt-dlp pipeline — release them back
        // to PENDING. Disabling fallback is a no-op for the queue.
        if (value) downloadQueueDao.requeueWaitingForLossless()
    }

    /**
     * Value of the `captcha_verified_at` cookie that qobuz.squid.wtf sets
     * after a successful ALTCHA solve. The download-music endpoint reads
     * this cookie (HttpOnly, ~30-min sliding window) to gate access; our
     * OkHttp client attaches it to every squid.wtf request via
     * [com.stash.data.download.lossless.squid.SquidWtfCaptchaInterceptor].
     */
    val captchaCookieValue: Flow<String?> = dataStore.data.map { prefs ->
        prefs[captchaCookieKey]?.takeIf { it.isNotBlank() }
    }

    /**
     * Wall-clock epoch-millis when the current captcha cookie was set.
     * Emits 0L when no cookie has ever been stored or the most recent
     * value was cleared. Used by SquidCookieAutoRefresher to derive
     * cookie age and schedule the next refresh ~25 min after set.
     */
    val captchaCookieSetAtMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[captchaCookieSetAtKey] ?: 0L
    }

    suspend fun captchaCookieValueNow(): String? = captchaCookieValue.first()

    suspend fun setCaptchaCookieValue(value: String?) {
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmed == null) {
                prefs.remove(captchaCookieKey)
                prefs.remove(captchaCookieSetAtKey)
            } else {
                prefs[captchaCookieKey] = trimmed
                prefs[captchaCookieSetAtKey] = now
            }
        }
    }

    /**
     * One-shot cleanup for removed sources: deletes the harvested antra.hoshi.cfd
     * session cookie, cf_clearance cookie and username, and arcod's retired banner
     * flag, so no stale login state lingers on disk. Called once at startup (see
     * StashApplication). No-op when the keys are absent.
     */
    suspend fun purgeRetiredSourceKeys() {
        dataStore.edit { prefs ->
            prefs.remove(antraSessionKey)
            prefs.remove(antraCfClearanceKey)
            prefs.remove(antraUsernameKey)
            prefs.remove(arcodRescueDismissedKey)
        }
    }

    /**
     * Whether the user has dismissed the "Try lossless audio" Home
     * banner. Once dismissed, the banner never shows again — same
     * forever-dismissed semantics as `LastFmSessionPreference.bannerDismissed`.
     */
    val bannerDismissed: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[bannerDismissedKey] ?: false
    }.distinctUntilChanged().catch { emit(false) }

    suspend fun setBannerDismissed(dismissed: Boolean) {
        dataStore.edit { prefs -> prefs[bannerDismissedKey] = dismissed }
    }

    /**
     * Whether the user has dismissed the "No lossless right now" Home banner.
     * Forever-dismissed. Deduped and fail-closed for the same reason as [enabled].
     */
    val losslessOfflineDismissed: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[losslessOfflineDismissedKey] ?: false
    }.distinctUntilChanged().catch { emit(false) }

    suspend fun setLosslessOfflineDismissed(dismissed: Boolean) {
        dataStore.edit { prefs -> prefs[losslessOfflineDismissedKey] = dismissed }
    }

    /**
     * User-selected lossless quality tier.
     */
    val qualityTier: Flow<LosslessQualityTier> = dataStore.data.map { prefs ->
        prefs[qualityTierKey]
            ?.let { runCatching { LosslessQualityTier.valueOf(it) }.getOrNull() }
            ?: defaultQualityTier(prefs)
    }

    suspend fun qualityTierNow(): LosslessQualityTier = qualityTier.first()

    suspend fun setQualityTier(tier: LosslessQualityTier) {
        dataStore.edit { prefs -> prefs[qualityTierKey] = tier.name }
    }

    private fun defaultQualityTier(prefs: Preferences): LosslessQualityTier {
        val enabledExplicitlyTrue = prefs[enabledKey] == true
        return if (enabledExplicitlyTrue) LosslessQualityTier.MAX else LosslessQualityTier.HI_RES
    }

    /**
     * Emits the user's source-id priority order. Falls back to
     * [DEFAULT_PRIORITY] when no order has been explicitly saved.
     */
    val priorityOrder: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[priorityKey]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.ifEmpty { DEFAULT_PRIORITY }
            ?: DEFAULT_PRIORITY
    }

    /** One-shot read for callers that want the current value without subscribing. */
    suspend fun priorityOrderNow(): List<String> = priorityOrder.first()

    suspend fun setPriorityOrder(order: List<String>) {
        dataStore.edit { prefs ->
            prefs[priorityKey] = order.joinToString(",")
        }
    }

    /**
     * Minimum acceptable quality. Sources whose [SourceResult.format]
     * doesn't meet this threshold are skipped during resolve.
     */
    val minQuality: Flow<MinQuality> = dataStore.data.map { prefs ->
        val raw = prefs[minQualityKey]
        raw?.let { runCatching { MinQuality.valueOf(it) }.getOrNull() }
            ?: MinQuality.LOSSLESS
    }

    suspend fun minQualityNow(): MinQuality = minQuality.first()

    suspend fun setMinQuality(value: MinQuality) {
        dataStore.edit { prefs -> prefs[minQualityKey] = value.name }
    }

    /**
     * Threshold an [AudioFormat] must meet to be accepted by the chain.
     */
    enum class MinQuality {
        LOSSLESS, HIGH_LOSSY, ANY;

        fun accepts(format: AudioFormat): Boolean = when (this) {
            LOSSLESS -> format.isLossless
            HIGH_LOSSY -> format.isLossless || format.bitrateKbps >= 256
            ANY -> true
        }
    }

    companion object {
        private val dataStores = java.util.concurrent.ConcurrentHashMap<String, DataStore<Preferences>>()

        internal fun getDataStore(file: java.io.File): DataStore<Preferences> =
            dataStores.computeIfAbsent(file.canonicalPath) {
                androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    produceFile = { file },
                )
            }

        val DEFAULT_PRIORITY: List<String> = listOf(
            "qbdlx_qobuz",
            "tidal_bitchord",
            "squid_qobuz",
            "kennyy_qobuz",
        )

        fun normaliseEndpoint(raw: String?): String? {
            val u = raw?.trim()?.toHttpUrlOrNull()?.takeIf { it.scheme == "https" } ?: return null
            return u.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
        }
    }
}
