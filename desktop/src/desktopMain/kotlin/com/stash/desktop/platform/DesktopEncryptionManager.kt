package com.stash.desktop.platform

import com.stash.core.auth.crypto.EncryptionManager
import com.stash.core.common.platform.AppPaths
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
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
    @Volatile private var keysetWasReset = false

    init {
        val keyBytes = loadOrGenerateKey()
        secretKey = SecretKeySpec(keyBytes, "AES")
    }

    private fun loadOrGenerateKey(): ByteArray {
        if (keyFile.exists()) {
            val raw = runCatching {
                val bytes = keyFile.readBytes()
                if (Platform.isWindows() && bytes.size > 32) {
                    Crypt32Util.cryptUnprotectData(bytes)
                } else {
                    bytes
                }
            }.getOrNull()
            if (raw != null && raw.size == 32) {
                return raw
            }
            keysetWasReset = true
        }

        val generated = ByteArray(32)
        secureRandom.nextBytes(generated)
        keyFile.parentFile?.mkdirs()
        val toStore = if (Platform.isWindows()) {
            runCatching { Crypt32Util.cryptProtectData(generated) }.getOrDefault(generated)
        } else {
            generated
        }
        keyFile.writeBytes(toStore)
        restrictPermissions(keyFile)
        return generated
    }

    private fun restrictPermissions(file: File) {
        if (!Platform.isWindows()) {
            runCatching {
                Files.setPosixFilePermissions(
                    file.toPath(),
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                )
            }
        }
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

    override fun consumeKeysetReset(): Boolean {
        val reset = keysetWasReset
        keysetWasReset = false
        return reset
    }

    companion object {
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
