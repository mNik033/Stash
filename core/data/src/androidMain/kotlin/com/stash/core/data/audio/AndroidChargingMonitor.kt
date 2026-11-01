package com.stash.core.data.audio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AndroidChargingMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChargingMonitor {
    override fun isCharging(): Boolean = runCatching {
        val battery = context.registerReceiver(
            /* receiver = */ null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        plugged != 0
    }.getOrDefault(false)
}

