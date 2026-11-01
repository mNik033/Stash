package com.stash.core.data.audio

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopChargingMonitor @Inject constructor() : ChargingMonitor {
    override fun isCharging(): Boolean = true
}

