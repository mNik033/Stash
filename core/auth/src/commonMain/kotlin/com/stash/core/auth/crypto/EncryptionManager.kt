package com.stash.core.auth.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

/** Prefix applied to string values that have been encrypted and encoded with Base64. */
const val ENCRYPTED_PREFIX = "enc:v1:"

/**
 * Checks whether [value] begins with [ENCRYPTED_PREFIX].
 */
fun isEncryptedPayload(value: String): Boolean = value.startsWith(ENCRYPTED_PREFIX)

/**
 * Encrypts [plaintext] using authenticated AES-GCM and encodes the ciphertext as a Base64 string
 * with the [ENCRYPTED_PREFIX] header.
 *
 * If [plaintext] is blank or empty, returns [plaintext] as-is.
 */
@OptIn(ExperimentalEncodingApi::class)
fun EncryptionManager.encryptString(plaintext: String): String {
    if (plaintext.isEmpty()) return ""
    val ciphertext = encrypt(plaintext.encodeToByteArray())
    return ENCRYPTED_PREFIX + Base64.Default.encode(ciphertext)
}

/**
 * Decrypts a string previously encrypted via [encryptString].
 *
 * Transparent migration: If [stored] does NOT start with [ENCRYPTED_PREFIX], it is assumed
 * to be legacy unencrypted plaintext and is returned as-is.
 *
 * If decryption fails (e.g. keyset was reset), returns empty string if [fallbackToEmpty] is true,
 * or null otherwise.
 */
@OptIn(ExperimentalEncodingApi::class)
fun EncryptionManager.decryptString(stored: String, fallbackToEmpty: Boolean = false): String? {
    if (stored.isEmpty()) return ""
    if (!stored.startsWith(ENCRYPTED_PREFIX)) {
        // Transparent legacy plaintext pass-through
        return stored
    }
    val rawBase64 = stored.removePrefix(ENCRYPTED_PREFIX)
    return try {
        val ciphertext = Base64.Default.decode(rawBase64)
        val decrypted = decrypt(ciphertext)
        decrypted.decodeToString()
    } catch (_: Throwable) {
        if (fallbackToEmpty) "" else null
    }
}
