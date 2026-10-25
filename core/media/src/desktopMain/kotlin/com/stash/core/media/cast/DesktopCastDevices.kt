package com.stash.core.media.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DesktopCastDevices : CastDevices {
    override val connection: StateFlow<CastConnection> = MutableStateFlow<CastConnection>(CastConnection.Unavailable).asStateFlow()
    override val devices: StateFlow<List<CastDevice>> = MutableStateFlow(emptyList<CastDevice>()).asStateFlow()
    override val remote: StateFlow<CastRemote?> = MutableStateFlow<CastRemote?>(null).asStateFlow()
    override fun startScan() {}
    override fun stopScan() {}
    override fun connect(deviceId: String) {}
    override fun disconnect() {}
}
