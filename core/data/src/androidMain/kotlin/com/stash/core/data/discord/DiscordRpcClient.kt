package com.stash.core.data.discord

import com.stash.core.common.Log
import com.stash.core.auth.discord.DiscordHeaders
import com.stash.core.auth.discord.DiscordRateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Posts/clears Discord Rich Presence via the headless-sessions endpoint,
 * authenticated with the connected account's own scraped user token (see
 * DiscordLoginWebView) â€” not a bot. Ported from echo-discord's RPC.kt +
 * TokenManager.kt with two upstream issues fixed:
 *
 *  - #16 (unsafe API calls): every endpoint moved from v10 to v9 (what the
 *    real client uses â€” v10 is bot/public-API-only), and every request now
 *    goes through [DiscordHeaders] instead of a bare Authorization header.
 *  - #15 (rate limits): [requestActivity] debounces â€” a burst of calls (fast
 *    track skipping) collapses to one network call for the LATEST activity â€”
 *    and every call routes through [DiscordRateLimiter], which also reads
 *    Discord's own Retry-After / X-RateLimit-* signals to back off instead of
 *    repeatedly 429ing.
 *
 * @param onUnauthorized invoked if the stored token stops working (revoked
 *   Discord session, password change, etc.) so the caller can flip the
 *   connection back to NotConnected.
 */
class DiscordRpcClient(
    private val userToken: String,
    private val onUnauthorized: (() -> Unit)? = null,
    private val onNeedsConsent: (() -> Unit)? = null,
    /** Overridable so tests can answer Discord's endpoints without the network. */
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val rateLimiter = DiscordRateLimiter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var accessToken: String? = null
    @Volatile private var activityToken: String? = null

    private data class PendingUpdate(val activity: DiscordActivity?, val nonce: Long)
    private var nonceCounter = 0L
    private val pending = MutableStateFlow<PendingUpdate?>(null)

    /** One OAuth exchange at a time; see [getAccessToken]. */
    private val tokenLock = Mutex()

    // collectLatest cancels the in-flight delay for any update superseded
    // before it fires â€” this is the actual fix for #15's repro (fast
    // skips), not just a retry-after-the-fact patch.
    private val collector: Job = scope.launch {
        pending.collectLatest { update ->
            if (update == null) return@collectLatest
            delay(DEBOUNCE_MS)
            runCatching { sendNow(update.activity) }
        }
    }

    /** Queue a presence update (or null to clear). Safe to call on every track change. */
    fun requestActivity(activity: DiscordActivity?) {
        pending.value = PendingUpdate(activity, nonceCounter++)
    }

    /**
     * Clears the status, then stops this client for good (disconnect, logout,
     * revoked token). The clear runs first: cancelling straight away would
     * leave "Listening toâ€¦" on the profile.
     *
     * The debounced collector is stopped before the clear, and a post it has
     * in flight is waited for (an HTTP call in flight is not abandoned): a
     * disconnect within the debounce of a track change otherwise let that
     * post land after the clear and create a session nothing ever deleted,
     * leaving "Listening toâ€¦" on the profile after disconnecting.
     */
    fun close() {
        scope.launch {
            collector.cancelAndJoin()
            runCatching { sendNow(null) }
            scope.cancel()
        }
    }

    /**
    * Warms up the OAuth connection immediately after the user links their
    * Discord account, so the one-time consent prompt (and Rich Presence itself)
    * doesn't wait for the first track change to trigger it.
    */
    fun connect() {
        scope.launch { runCatching { getAccessToken() } }
    }

    // Internal, not private, so tests can drive one send without the debounce.
    internal suspend fun sendNow(activity: DiscordActivity?) {
        if (activity == null) {
            deleteSession()
        } else {
            runCatching { post(DiscordSession(listOf(activity), activityToken)) }
                .onFailure { Log.w("DiscordRpc", "sendNow failed", it) }
        }
    }

    /**
     * The cached access token, minting one when there is none. Serialised by
     * [tokenLock]: [connect] and the first presence update both arrive here
     * right after linking, and without the lock each ran its own authorize
     * and token exchange.
     */
    private suspend fun getAccessToken(): String = tokenLock.withLock {
        accessToken ?: mintAccessToken()
    }

    private suspend fun mintAccessToken(): String {
        val verifier = randomVerifier()
        val challenge = codeChallenge(verifier)

        val authorizeReq = Request.Builder().url(
            "https://discord.com/api/v9/oauth2/authorize" +
                "?client_id=$CLIENT_ID&response_type=code&redirect_uri=$REDIRECT_URI" +
                "&code_challenge=$challenge&code_challenge_method=S256" +
                "&scope=${SCOPES.replace(" ", "%20")}&state=undefined",
        )
        DiscordHeaders.apply(authorizeReq, token = userToken, bearer = false)
        authorizeReq.post(
            JsonObject(mapOf("authorize" to JsonPrimitive(true)))
                .toString().toRequestBody("application/json".toMediaType()),
        )
        rateLimiter.awaitReady()
        // Every response is closed, error paths included, or OkHttp can't reuse the connection.
        val location = http.newCall(authorizeReq.build()).await().use { res ->
            rateLimiter.observe(res)
            if (res.code == 401) { onUnauthorized?.invoke(); throw IllegalStateException("Discord token revoked") }
            if (!res.isSuccessful) throw IllegalStateException("authorize failed: ${res.code}")
            Json.decodeFromString<JsonObject>(res.body?.string() ?: throw IllegalStateException("Empty response body"))["location"]!!.jsonPrimitive.content
        }
        val code = location.substringAfter("code=").substringBefore("&")

        val tokenReq = Request.Builder().url("https://discord.com/api/v9/oauth2/token")
        DiscordHeaders.apply(tokenReq)
        tokenReq.post(
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("code_verifier", verifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .build(),
        )
        rateLimiter.awaitReady()
        val newAccess = http.newCall(tokenReq.build()).await().use { tokenRes ->
            rateLimiter.observe(tokenRes)
            if (!tokenRes.isSuccessful) {
                val body = runCatching { tokenRes.body?.string() }.getOrNull()
                if (body?.contains("invalid_grant") == true) onNeedsConsent?.invoke()
                throw IllegalStateException("token exchange failed: ${tokenRes.code} â€” body: $body")
            }
            Json.decodeFromString<JsonObject>(tokenRes.body?.string() ?: throw IllegalStateException("Empty response body"))["access_token"]!!.jsonPrimitive.content
        }
        accessToken = newAccess
        return newAccess
    }

    private suspend fun post(session: DiscordSession) {
        val payload = Json.encodeToString(DiscordSession.serializer(), session)
        sendAuthorized { auth ->
            val req = Request.Builder().url("https://discord.com/api/v9/users/@me/headless-sessions")
            DiscordHeaders.apply(req, token = auth, bearer = true)
            req.post(payload.toRequestBody("application/json".toMediaType())).build()
        }.use { res ->
            if (!res.isSuccessful) {
                val body = runCatching { res.body?.string() }.getOrNull()
                Log.w("DiscordRpc", "headless-sessions POST failed: ${res.code} ${res.message} â€” body: $body")
                return // presence is best-effort â€” never crash playback over it
            }
            activityToken = Json.decodeFromString<JsonObject>(res.body?.string() ?: throw IllegalStateException("Empty response body"))["token"]!!.jsonPrimitive.content
        }
        Log.i("DiscordRpc", "Presence posted successfully")
    }

    private suspend fun deleteSession() {
        val token = activityToken ?: return
        val payload = JsonObject(mapOf("token" to JsonPrimitive(token))).toString()
        // Runs on every pause, so an unclosed body here leaked a connection per pause.
        sendAuthorized { auth ->
            val req = Request.Builder().url("https://discord.com/api/v9/users/@me/headless-sessions/delete")
            DiscordHeaders.apply(req, token = auth, bearer = true)
            req.post(payload.toRequestBody("application/json".toMediaType())).build()
        }.use { res ->
            if (res.isSuccessful) activityToken = null
        }
    }

    /**
     * Sends a request made with the OAuth access token, and on a 401 mints a new
     * access token and sends it once more.
     *
     * A 401 here only says the *access token* is no longer accepted. Discord's
     * access tokens expire (after 7 days by default) while the user token that
     * mints them stays valid, and a media app's process can outlive one. This
     * 401 used to be reported through [onUnauthorized], which deletes the stored
     * user token, so the user was silently disconnected and had to log in to
     * Discord again. Only `oauth2/authorize`, which presents the user token, can
     * prove that token was revoked, and [getAccessToken] reports that 401 itself.
     * A second 401 reaches the caller as an ordinary failed response.
     *
     * @param build makes the request for the given access token. It runs again
     *   for the retry, with the new token.
     */
    private suspend fun sendAuthorized(build: (accessToken: String) -> Request): Response {
        val auth = getAccessToken()
        val res = execute(build(auth))
        if (res.code != 401) return res
        res.close()
        Log.w("DiscordRpc", "${res.request.url.encodedPath} got 401 â€” minting a new access token")
        if (accessToken == auth) accessToken = null
        return execute(build(getAccessToken()))
    }

    /** Sends one request through [rateLimiter]. The caller closes the response. */
    private suspend fun execute(request: Request): Response {
        rateLimiter.awaitReady()
        val res = http.newCall(request).await()
        rateLimiter.observe(res)
        return res
    }

    companion object {
        private val CLIENT_ID = DiscordRpcConfig.CLIENT_ID
        private val REDIRECT_URI = DiscordRpcConfig.REDIRECT_URI
        private val SCOPES = DiscordRpcConfig.SCOPES
        private const val DEBOUNCE_MS = 1200L
        private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val SECURE_RANDOM = java.security.SecureRandom()
        private fun randomVerifier() = (1..128).map { CHARS[SECURE_RANDOM.nextInt(CHARS.length)] }.joinToString("")
        private fun codeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        private suspend fun Call.await(): Response = suspendCoroutine { cont ->
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
                override fun onResponse(call: Call, response: Response) = cont.resume(response)
            })
        }
    }
}