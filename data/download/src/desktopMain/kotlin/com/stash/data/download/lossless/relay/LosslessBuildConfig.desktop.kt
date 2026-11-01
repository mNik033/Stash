package com.stash.data.download.lossless.relay

import java.io.File
import java.util.Properties

actual object LosslessBuildConfig {
    actual val configUrl: String
        get() = GeneratedLosslessConfig.CONFIG_URL.ifBlank {
            System.getProperty("lossless.configUrl")?.takeIf { it.isNotBlank() }
                ?: System.getenv("LOSSLESS_CONFIG_URL")?.takeIf { it.isNotBlank() }
                ?: loadFromLocalProperties("lossless.configUrl")
                ?: ""
        }

    actual val configPubKey: String
        get() = GeneratedLosslessConfig.CONFIG_PUBKEY.ifBlank {
            System.getProperty("lossless.configPubKey")?.takeIf { it.isNotBlank() }
                ?: System.getenv("LOSSLESS_CONFIG_PUBKEY")?.takeIf { it.isNotBlank() }
                ?: loadFromLocalProperties("lossless.configPubKey")
                ?: ""
        }

    private fun loadFromLocalProperties(key: String): String? {
        return runCatching {
            val userDir = System.getProperty("user.dir", ".")
            val candidates = listOfNotNull(
                File("local.properties"),
                File("../local.properties"),
                File(userDir, "local.properties"),
                File(userDir).parentFile?.let { File(it, "local.properties") },
            )
            for (f in candidates) {
                if (f.exists() && f.isFile) {
                    val p = Properties().apply { f.inputStream().use { load(it) } }
                    val v = p.getProperty(key)?.trim()
                    if (!v.isNullOrBlank()) return@runCatching v
                }
            }
            null
        }.getOrNull()
    }
}

