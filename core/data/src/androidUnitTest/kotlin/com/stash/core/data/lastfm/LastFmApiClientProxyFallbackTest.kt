package com.stash.core.data.lastfm

import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.dao.LastFmCacheDao
import io.mockk.coEvery
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
 * When the read proxy fails, a cacheable read falls back to a direct read key.
 * Last.fm's own "not found" is not a failure of the proxy, though: retrying it
 * directly only doubled the traffic on the shared key the proxy shields.
 */
class LastFmApiClientProxyFallbackTest {

    private val proxyHost = "proxy.example"

    /** Answers [proxyCode]/[proxyBody] for the proxy and a clean tag list directly. */
    private class Canned(
        private val proxyHost: String,
        private val proxyCode: Int,
        private val proxyBody: String,
    ) : Interceptor {
        var directCalls = 0

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val (code, body) = if (req.url.host == proxyHost) {
                proxyCode to proxyBody
            } else {
                directCalls++
                200 to """{"toptags":{"tag":[{"name":"indie","count":100}]}}"""
            }
            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("canned")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun client(canned: Canned): LastFmApiClient {
        val cacheDao = mockk<LastFmCacheDao>(relaxed = true)
        coEvery { cacheDao.get(any()) } returns null
        return LastFmApiClient(
            okHttpClient = OkHttpClient.Builder().addInterceptor(canned).build(),
            credentials = LastFmCredentials(
                apiKey = "key",
                apiSecret = "secret",
                proxyUrl = "https://$proxyHost/lastfm",
            ),
            rateLimitGate = LastFmRateLimitGate(),
            cacheDao = cacheDao,
        )
    }

    @Test
    fun `a not-found answer from the proxy is final`() = runTest {
        val canned = Canned(proxyHost, 200, """{"error":6,"message":"Track not found"}""")

        val result = client(canned).getTrackTopTags(artist = "a", track = "deep cut")

        assertThat(result.isFailure).isTrue()
        assertThat(canned.directCalls).isEqualTo(0)
    }

    @Test
    fun `a failing proxy still falls back to the direct key`() = runTest {
        val canned = Canned(proxyHost, 502, """{"error":"upstream_unreachable"}""")

        val result = client(canned).getTrackTopTags(artist = "a", track = "t")

        assertThat(result.getOrNull()?.map { it.name }).containsExactly("indie")
        assertThat(canned.directCalls).isEqualTo(1)
    }
}
