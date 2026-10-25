package com.stash.feature.settings.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebSessionCookiesTest {

    /**
     * Spotify sets `sp_dc` with `Domain=.spotify.com`. The old clear sent only
     * the host-only form, which is a different cookie key and left the real
     * `sp_dc` in place, so Connect after Disconnect re-submitted it.
     */
    @Test
    fun `expiry covers the domain cookie as well as a host-only one`() {
        val headers = WebSessionCookies.expiryHeaders("sp_dc", ".spotify.com")

        assertThat(headers).hasSize(2)
        assertThat(headers.any { "Domain=.spotify.com" in it }).isTrue()
        assertThat(headers.any { "Domain=" !in it }).isTrue()
        headers.forEach {
            assertThat(it).startsWith("sp_dc=;")
            assertThat(it).contains("Expires=Thu, 01 Jan 1970 00:00:00 GMT")
            assertThat(it).contains("Path=/")
        }
    }

    @Test
    fun `a __Host- cookie is expired without a Domain, which it may not carry`() {
        val headers = WebSessionCookies.expiryHeaders("__Host-GAPS", ".google.com")

        assertThat(headers).hasSize(1)
        assertThat(headers.single()).doesNotContain("Domain=")
        assertThat(headers.single()).contains("Secure")
    }

    @Test
    fun `names are read from a cookie header, values ignored`() {
        assertThat(WebSessionCookies.names("SID=a=b; HSID=x;  LOGIN_INFO=y ; SID=z"))
            .containsExactly("SID", "HSID", "LOGIN_INFO").inOrder()
        assertThat(WebSessionCookies.names(null)).isEmpty()
        assertThat(WebSessionCookies.names("")).isEmpty()
    }
}
