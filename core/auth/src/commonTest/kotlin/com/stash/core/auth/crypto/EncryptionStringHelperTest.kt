package com.stash.core.auth.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptionStringHelperTest {

    private val fakeEncryptionManager = object : EncryptionManager {
        override fun encrypt(plaintext: ByteArray): ByteArray =
            plaintext.map { (it + 1).toByte() }.toByteArray()

        override fun decrypt(ciphertext: ByteArray): ByteArray =
            ciphertext.map { (it - 1).toByte() }.toByteArray()
    }

    @Test
    fun encryptString_wrapsInEncryptedPrefixAndDecrypts() {
        val original = "secret_qobuz_token_12345"
        val encrypted = fakeEncryptionManager.encryptString(original)

        assertTrue(isEncryptedPayload(encrypted))
        assertTrue(encrypted.startsWith("enc:v1:"))

        val decrypted = fakeEncryptionManager.decryptString(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun decryptString_transparentLegacyPlaintextPassThrough() {
        val legacyPlaintext = "raw_unencrypted_legacy_token"
        assertFalse(isEncryptedPayload(legacyPlaintext))

        // When reading legacy unencrypted values, returns raw plaintext without failing
        val result = fakeEncryptionManager.decryptString(legacyPlaintext)
        assertEquals(legacyPlaintext, result)
    }

    @Test
    fun encryptString_emptyStringReturnsEmpty() {
        assertEquals("", fakeEncryptionManager.encryptString(""))
        assertEquals("", fakeEncryptionManager.decryptString(""))
    }
}

