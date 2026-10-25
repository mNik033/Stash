package com.stash.desktop.platform

import com.stash.core.auth.crypto.EncryptionManager
import com.stash.core.common.platform.AppPaths
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopEncryptionManager @Inject constructor(
    appPaths: AppPaths,
) : EncryptionManager {

    private val keyFile = File(appPaths.noBackupFilesDir, "stash_desktop.key")
    private val secretKey: SecretKeySpec
    private val secureRandom = SecureRandom()

    init {
        val keyBytes = if (keyFile.exists() && keyFile.length() == 32L) {
            keyFile.readBytes()
        } else {
            val generated = ByteArray(32)
            secureRandom.nextBytes(generated)
            keyFile.parentFile?.mkdirs()
            keyFile.writeBytes(generated)
            generated
        }
        secretKey = SecretKeySpec(keyBytes, "AES")
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext)

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return combined
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= GCM_IV_LENGTH_BYTES) { "Ciphertext is too short for GCM IV" }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(ciphertext, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val encryptedPayload = ByteArray(ciphertext.size - GCM_IV_LENGTH_BYTES)
        System.arraycopy(ciphertext, GCM_IV_LENGTH_BYTES, encryptedPayload, 0, encryptedPayload.size)
        return cipher.doFinal(encryptedPayload)
    }

    override fun consumeKeysetReset(): Boolean = false

    companion object {
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
