package com.stash.core.auth.crypto

/**
 * The decision of when an unreadable credential keyset is given up on, kept
 * free of Android/Tink types so it can be unit-tested. [TinkEncryptionManager]
 * supplies the Keystore-backed pieces.
 *
 * Why it exists: the Tink keyset is stored encrypted under an Android Keystore
 * master key. Some devices lose that key (lock-screen changes, Keystore
 * corruption) while the encrypted keyset stays in SharedPreferences; from then
 * on opening the keyset threw on EVERY call, forever. Every saved sign-in read
 * back as "not connected" and every save threw — the user could not sign in
 * again until they cleared the app's data.
 */
internal object KeysetRecovery {

    /** What [open] produced, and whether the keyset had to be replaced for it. */
    data class Opened<T>(val value: T, val wasReset: Boolean)

    /**
     * Opens the keyset with [open]. A failure is retried once; if it persists
     * AND [isUnrecoverable] confirms the keyset can never be opened again (its
     * master key is gone or permanently invalidated, or a working key no
     * longer opens it), the keyset is replaced via [reset] and opened afresh.
     * Anything else is rethrown untouched, so a transient Keystore hiccup never
     * costs the user their sign-ins.
     */
    fun <T> open(open: () -> T, isUnrecoverable: () -> Boolean, reset: () -> Unit): Opened<T> {
        try {
            return Opened(open(), wasReset = false)
        } catch (_: Exception) {
            // Retried below: the Keystore is known to fail transiently.
        }
        val failure = try {
            return Opened(open(), wasReset = false)
        } catch (e: Exception) {
            e
        }
        if (!isUnrecoverable()) throw failure
        reset()
        return Opened(open(), wasReset = true)
    }
}
