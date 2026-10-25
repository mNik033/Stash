package com.stash.core.data.platform

import com.stash.core.common.platform.PlatformInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPlatformInfo @Inject constructor() : PlatformInfo {
    override val isAndroid: Boolean = true
    override val isDesktop: Boolean = false
    override val isMac: Boolean = false
    override val isWindows: Boolean = false
    override val isLinux: Boolean = false
    override val osName: String = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
}
