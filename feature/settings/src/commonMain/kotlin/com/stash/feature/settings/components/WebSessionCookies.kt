package com.stash.feature.settings.components

/**
 * Pure rules for expiring one service's cookies in the shared WebView cookie
 * jar, without touching any other service's (the sign-in WebViews, the squid
 * captcha and Google's `_GRECAPTCHA` reputation cookie all share one jar).
 *
 * Kept free of `android.webkit` so the rules can be unit-tested; the sign-in
 * WebViews feed the strings to `CookieManager.setCookie`.
 */
internal object WebSessionCookies {

    private const val EXPIRED = "Expires=Thu, 01 Jan 1970 00:00:00 GMT"

    /** The cookie names in a `CookieManager.getCookie` header (`"a=1; b=2"`). */
    fun names(cookieHeader: String?): List<String> =
        cookieHeader.orEmpty()
            .split(';')
            .map { it.substringBefore('=').trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /**
     * The `Set-Cookie` values that expire [name] however it was set. A cookie is
     * keyed by (name, domain, path), so an expiry without a `Domain` only
     * removes a HOST-ONLY cookie of that name — never the `Domain=[domain]`
     * cookie the services actually set (Spotify's `sp_dc` is
     * `Domain=.spotify.com`). Clearing with the host-only form alone is why a
     * disconnected Spotify account kept signing itself straight back in.
     *
     * `Secure` is required to touch `__Secure-` / `__Host-` names and harmless
     * on the rest (these are set from https URLs); a `__Host-` cookie cannot
     * carry a `Domain`, so it gets the host-only form only.
     */
    fun expiryHeaders(name: String, domain: String): List<String> {
        val hostOnly = "$name=; $EXPIRED; Path=/; Secure"
        if (name.startsWith("__Host-")) return listOf(hostOnly)
        return listOf(hostOnly, "$name=; Domain=$domain; $EXPIRED; Path=/; Secure")
    }
}
