package com.stash.desktop.platform

import com.stash.core.common.platform.Connectivity
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopConnectivity @Inject constructor() : Connectivity {

    override fun isConnected(): Boolean = try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return true
        interfaces.asSequence().any { iface ->
            try {
                iface.isUp && !iface.isLoopback && !iface.isPointToPoint && iface.hardwareAddress != null
            } catch (_: Exception) {
                false
            }
        }
    } catch (_: Exception) {
        true
    }

    override fun isCellular(): Boolean = false
}
