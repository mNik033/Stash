package com.stash.desktop.platform

import com.stash.core.auth.crypto.EncryptionManager
import com.stash.core.common.platform.AppPaths
import com.stash.core.common.platform.FileSecurity
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
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
        // 1. macOS Keychain retrieval
        if (Platform.isMac()) {
            val fromKeychain = loadFromMacKeychain()
            if (fromKeychain != null && fromKeychain.size == 32) {
                // If legacy key file exists on disk, wipe and delete it now that Keychain has the key
                if (keyFile.exists()) {
                    runCatching {
                        keyFile.writeBytes(ByteArray(keyFile.length().toInt()))
                        keyFile.delete()
                    }
                }
                return fromKeychain
            }

            // Check if legacy key file exists on macOS for migration into Keychain
            if (keyFile.exists()) {
                val legacyBytes = runCatching { keyFile.readBytes() }.getOrNull()
                if (legacyBytes != null && legacyBytes.size == 32) {
                    if (saveToMacKeychain(legacyBytes)) {
                        runCatching {
                            keyFile.writeBytes(ByteArray(32))
                            keyFile.delete()
                        }
                    }
                    return legacyBytes
                }
            }
        }

        // 2. Windows DPAPI or POSIX file retrieval
        if (keyFile.exists()) {
            var wasUnencryptedWindowsKey = false
            val raw = runCatching {
                val bytes = keyFile.readBytes()
                if (Platform.isWindows()) {
                    if (bytes.size > 32) {
                        Crypt32Util.cryptUnprotectData(bytes)
                    } else if (bytes.size == 32) {
                        wasUnencryptedWindowsKey = true
                        bytes
                    } else {
                        null
                    }
                } else {
                    bytes
                }
            }.getOrNull()

            if (raw != null && raw.size == 32) {
                // Auto-upgrade legacy unencrypted 32-byte key on Windows to DPAPI
                if (Platform.isWindows() && wasUnencryptedWindowsKey) {
                    runCatching {
                        val protectedBytes = Crypt32Util.cryptProtectData(raw)
                        keyFile.writeBytes(protectedBytes)
                        FileSecurity.restrictToOwner(keyFile)
                    }
                }
                return raw
            }
            keysetWasReset = true
        }

        // 3. Generate a new 32-byte AES key
        val generated = ByteArray(32)
        secureRandom.nextBytes(generated)

        if (Platform.isMac()) {
            // Store directly in macOS Keychain
            if (saveToMacKeychain(generated)) {
                return generated
            }
            // Fall back to local file if Keychain command failed (e.g. headless CI)
        }

        keyFile.parentFile?.mkdirs()
        val toStore = if (Platform.isWindows()) {
            try {
                Crypt32Util.cryptProtectData(generated)
            } catch (e: Throwable) {
                throw GeneralSecurityException("Failed to secure encryption key with Windows DPAPI", e)
            }
        } else {
            generated
        }
        keyFile.writeBytes(toStore)
        FileSecurity.restrictToOwner(keyFile)
        return generated
    }

    private fun loadFromMacKeychain(): ByteArray? = runCatching {
        val pb = ProcessBuilder(
            "/usr/bin/security",
            "find-generic-password",
            "-a", KEYCHAIN_ACCOUNT,
            "-s", KEYCHAIN_SERVICE,
            "-w",
        )
        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        if (exitCode == 0 && output.isNotEmpty()) {
            Base64.getDecoder().decode(output)
        } else {
            null
        }
    }.getOrNull()

    private fun saveToMacKeychain(keyBytes: ByteArray): Boolean = runCatching {
        val base64Key = Base64.getEncoder().encodeToString(keyBytes)
        val pb = ProcessBuilder(
            "/usr/bin/security",
            "add-generic-password",
            "-a", KEYCHAIN_ACCOUNT,
            "-s", KEYCHAIN_SERVICE,
            "-w", base64Key,
            "-U",
        )
        val process = pb.start()
        process.waitFor() == 0
    }.getOrDefault(false)

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
        private const val KEYCHAIN_SERVICE = "com.stash.desktop"
        private const val KEYCHAIN_ACCOUNT = "master_aes_key"
    }
}
