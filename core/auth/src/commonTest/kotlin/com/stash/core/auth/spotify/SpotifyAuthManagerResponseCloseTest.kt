package com.stash.core.auth.spotify

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spotify rejecting the token request (e.g. a stale TOTP version: HTTP 400 on
 * EVERY refresh while the token is expired) returned without closing the
 * response, leaking a pooled connection per call. So did the server-time
 * probe whenever its answer had no Date header.
 */
class SpotifyAuthManagerResponseCloseTest {

    private class TrackedBody : ResponseBody() {
        var closed = false
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = 0
        override fun source(): BufferedSource = Buffer()
        override fun close() {
            closed = true
        }
    }

    @Test fun `a rejected token request closes every response it opened`() {
        val bodies = mutableListOf<TrackedBody>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = TrackedBody().also { bodies += it }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    // The HEAD server-time probe answers without a Date header;
                    // the token endpoint rejects the request.
                    .code(if (chain.request().method == "HEAD") 200 else 400)
                    .message("test")
                    .body(body)
                    .build()
            }
            .build()

        val token = runBlocking { SpotifyAuthManager(client).getAccessToken("cookie") }

        assertNull(token)
        assertEquals(2, bodies.size)
        assertTrue("every response closed", bodies.all { it.closed })
    }
}
