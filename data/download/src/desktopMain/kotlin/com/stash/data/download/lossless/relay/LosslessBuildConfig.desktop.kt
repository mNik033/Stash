package com.stash.data.download.lossless.relay

actual object LosslessBuildConfig {
    actual val configUrl: String = System.getenv("LOSSLESS_CONFIG_URL").orEmpty()
    actual val configPubKey: String = System.getenv("LOSSLESS_CONFIG_PUBKEY").orEmpty()
}

