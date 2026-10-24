package com.stash.core.common.platform

/**
 * Runtime platform and operating system information.
 */
interface PlatformInfo {
    val isAndroid: Boolean
    val isDesktop: Boolean
    val isMac: Boolean
    val isWindows: Boolean
    val isLinux: Boolean
    val osName: String
}
