package com.stash.data.download.lossless.relay

import com.stash.core.common.platform.AppPaths

internal actual fun createDefaultConfigNetworkSignal(appPaths: AppPaths): ConfigNetworkSignal =
    AlwaysOnlineConfigNetworkSignal

