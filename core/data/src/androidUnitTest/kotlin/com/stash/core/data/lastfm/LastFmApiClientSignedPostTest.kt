package com.stash.core.data.lastfm

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

/**
 * How a signed write ([LastFmApiClient.scrobble]) reports Last.fm's answer.
 * The scrobbler decides between "disconnect", "skip this play" and "retry
 * later" from [LastFmApiException.errorCode], so the code must survive both a
 * 4xx and a 200 that carries an error, and an error must never pass as success.
 */
class LastFmApiClientSignedPostTest {

    private fun client(code: Int, body: String): LastFmApiClient {
        val canned = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("canned")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return LastFmApiClient(
            okHttpClient = OkHttpClient.Builder().addInterceptor(canned).build(),
            credentials = LastFmCredentials(apiKey = "key", apiSecret = "secret"),
            rateLimitGate = LastFmRateLimitGate(),
            cacheDao = mockk(relaxed = true),
        )
    }

    private suspend fun LastFmApiClient.scrobbleOne() =
        scrobble(sessionKey = "sk", artist = "a", track = "t", timestampEpochSeconds = 1L)

    @Test
    fun `a 4xx keeps the Last-fm error code`() = runTest {
        val result = client(403, """{"error":9,"message":"Invalid session key"}""").scrobbleOne()

        assertThat((result.exceptionOrNull() as LastFmApiException).errorCode).isEqualTo(9)
    }

    @Test
    fun `a 200 carrying an error is a failure and not a scrobble`() = runTest {
        val result = client(200, """{"error":6,"message":"Invalid parameters"}""").scrobbleOne()

        assertThat((result.exceptionOrNull() as LastFmApiException).errorCode).isEqualTo(6)
    }

    @Test
    fun `a 5xx without an error body is a plain failure`() = runTest {
        val result = client(503, "<html>Service Unavailable</html>").scrobbleOne()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isNotInstanceOf(LastFmApiException::class.java)
    }

    @Test
    fun `an accepted scrobble succeeds`() = runTest {
        val result = client(200, """{"scrobbles":{"@attr":{"accepted":1,"ignored":0}}}""").scrobbleOne()

        assertThat(result.isSuccess).isTrue()
    }
}
