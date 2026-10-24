package com.stash.core.auth.crypto

/**
 * Abstraction for authenticated symmetric encryption of sensitive credentials,
 * implemented by Tink backed by Android Keystore on Android, and
 * Keychain / Credential Manager on Desktop.
 */
interface EncryptionManager {
    /** Encrypts [plaintext] returning the authenticated ciphertext. */
    fun encrypt(plaintext: ByteArray): ByteArray

    /** Decrypts [ciphertext] previously produced by [encrypt]. */
    fun decrypt(ciphertext: ByteArray): ByteArray

    /** Returns true once if the underlying keyset was reset due to permanent corruption or key loss. */
    fun consumeKeysetReset(): Boolean = false
}
