package com.stash.data.download.lossless.relay

import com.stash.data.download.BuildConfig

actual object LosslessBuildConfig {
    actual val configUrl: String = BuildConfig.LOSSLESS_CONFIG_URL
    actual val configPubKey: String = BuildConfig.LOSSLESS_CONFIG_PUBKEY
}

