package com.stash.core.auth.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException

class KeysetRecoveryTest {

    /**
     * The Keystore lost its master key while the encrypted keyset stayed: opening
     * threw on every call, for ever, until the user cleared the app's data. Now
     * the dead keyset is replaced and the store opens again.
     */
    @Test
    fun `an unrecoverable keyset is replaced and opened afresh`() {
        var resets = 0
        val opened = KeysetRecovery.open(
            open = { if (resets == 0) throw GeneralSecurityException("keyset is encrypted") else "aead" },
            isUnrecoverable = { true },
            reset = { resets++ },
        )

        assertEquals("aead", opened.value)
        assertTrue(opened.wasReset)
        assertEquals(1, resets)
    }

    @Test
    fun `a transient failure is retried without touching the keyset`() {
        var attempts = 0
        var resets = 0
        val opened = KeysetRecovery.open(
            open = { if (attempts++ == 0) throw GeneralSecurityException("binder hiccup") else "aead" },
            isUnrecoverable = { fail("must not be asked when the retry worked"); true },
            reset = { resets++ },
        )

        assertEquals("aead", opened.value)
        assertFalse(opened.wasReset)
        assertEquals(0, resets)
    }

    /** Wiping sign-ins over an error that may pass would be worse than failing this once. */
    @Test
    fun `a persistent failure that is not confirmed unrecoverable is rethrown, keyset kept`() {
        val failure = GeneralSecurityException("keystore unavailable")
        var resets = 0
        try {
            KeysetRecovery.open<String>(
                open = { throw failure },
                isUnrecoverable = { false },
                reset = { resets++ },
            )
            fail("expected the failure to propagate")
        } catch (e: GeneralSecurityException) {
            assertSame(failure, e)
        }
        assertEquals(0, resets)
    }
}
