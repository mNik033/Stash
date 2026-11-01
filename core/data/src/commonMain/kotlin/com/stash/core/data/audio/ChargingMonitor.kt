package com.stash.core.data.audio

/**
 * Answers whether the device is currently connected to external power.
 */
interface ChargingMonitor {
    fun isCharging(): Boolean
}

