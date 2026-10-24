package com.stash.core.auth.crypto

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides AES-256-GCM authenticated encryption backed by Android Keystore.
 *
 * The master key is stored in Android Keystore and never leaves the secure hardware.
 * The Tink keyset (encrypted by the master key) is persisted in SharedPreferences so
 * it survives app restarts.
 *
 * If the master key is lost while the encrypted keyset remains (see
 * [KeysetRecovery]), the keyset is replaced with a new one. Everything sealed
 * under the old keyset is unreadable either way; the user signs in again
 * instead of being stuck, and [consumeKeysetReset] lets the token store drop
 * the dead blobs.
 */
@Singleton
class TinkEncryptionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : EncryptionManager {
    @Volatile
    private var keysetResetPending = false

    private val aead: Aead by lazy {
        AeadConfig.register()
        val opened = KeysetRecovery.open(
            open = ::openKeyset,
            isUnrecoverable = ::keysetIsUnrecoverable,
            reset = ::resetKeyset,
        )
        if (opened.wasReset) keysetResetPending = true
        opened.value
    }

    /**
     * Encrypts [plaintext] using AES-256-GCM.
     *
     * @return The ciphertext including the authentication tag and IV.
     */
    override fun encrypt(plaintext: ByteArray): ByteArray =
        aead.encrypt(plaintext, EMPTY_ASSOCIATED_DATA)

    /**
     * Decrypts [ciphertext] previously produced by [encrypt].
     *
     * @throws java.security.GeneralSecurityException if the ciphertext is tampered or the key is wrong.
     */
    override fun decrypt(ciphertext: ByteArray): ByteArray =
        aead.decrypt(ciphertext, EMPTY_ASSOCIATED_DATA)

    /**
     * True exactly once after the keyset was replaced because the old one could
     * no longer be opened: every ciphertext stored before is now permanently
     * unreadable, and the caller that consumes this should delete its copies.
     */
    @Synchronized
    override fun consumeKeysetReset(): Boolean {
        val pending = keysetResetPending
        keysetResetPending = false
        return pending
    }

    private fun openKeyset(): Aead =
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)

    /**
     * Whether the stored keyset can never be opened again, as opposed to the
     * Keystore failing for the moment. True when the master key is gone, when
     * Keystore reports it permanently invalidated, or when the key works but
     * still does not open the keyset. Any doubt (Keystore itself unreachable)
     * answers false: wiping sign-ins over a transient error would be worse.
     */
    private fun keysetIsUnrecoverable(): Boolean = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            true
        } else {
            try {
                val probe = AndroidKeystoreKmsClient().getAead(MASTER_KEY_URI)
                probe.decrypt(probe.encrypt(PROBE, EMPTY_ASSOCIATED_DATA), EMPTY_ASSOCIATED_DATA)
                true // The key works, yet it does not open the keyset.
            } catch (e: Exception) {
                generateSequence<Throwable>(e) { it.cause }.any {
                    it is KeyPermanentlyInvalidatedException || it is UnrecoverableKeyException
                }
            }
        }
    } catch (_: Exception) {
        false
    }

    /** Deletes the unreadable keyset and its master key so Tink generates both anew. */
    private fun resetKeyset() {
        Log.w(TAG, "credential keyset can no longer be opened (Keystore key lost); starting a new one — saved sign-ins must be redone")
        context.getSharedPreferences(KEYSET_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEYSET_NAME)
            .commit()
        runCatching { AndroidKeystoreKmsClient().deleteKey(MASTER_KEY_URI) }
            .onFailure { Log.w(TAG, "could not delete the old master key", it) }
    }

    private companion object {
        const val TAG = "TinkEncryption"
        const val KEYSET_NAME = "stash_auth_keyset"
        const val KEYSET_PREFS = "stash_auth_keyset_prefs"
        const val MASTER_KEY_ALIAS = "stash_master_key"
        const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        val EMPTY_ASSOCIATED_DATA = ByteArray(0)
        val PROBE = byteArrayOf(0x53, 0x74, 0x61, 0x73, 0x68)
    }
}
