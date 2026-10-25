package com.stash.desktop.platform

import com.stash.core.common.platform.PlatformInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopPlatformInfo @Inject constructor() : PlatformInfo {
    override val isAndroid: Boolean = false
    override val isDesktop: Boolean = true

    override val osName: String = System.getProperty("os.name") ?: "Desktop"

    private val lowerOs = osName.lowercase()

    override val isMac: Boolean = lowerOs.contains("mac")
    override val isWindows: Boolean = lowerOs.contains("win")
    override val isLinux: Boolean = lowerOs.contains("nix") || lowerOs.contains("nux") || lowerOs.contains("aix")
}
