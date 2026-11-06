package com.stash.core.common.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager

/** Production entry point: the [PowerManager] of [context]'s application. */
fun SystemBatterySaver(context: Context): SystemBatterySaver {
    val appContext = context.applicationContext
    return SystemBatterySaver(
        read = powerSaveReader(appContext),
        subscribe = { onChange -> registerPowerSaveReceiver(appContext, onChange) },
    )
}

private fun powerSaveReader(context: Context): () -> Boolean {
    val power = context.getSystemService(PowerManager::class.java)
    // No PowerManager at all (never on a real phone): always off.
    return { power?.isPowerSaveMode ?: false }
}

private fun registerPowerSaveReceiver(context: Context, onChange: () -> Unit) {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onChange()
    }
    val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        context.registerReceiver(receiver, filter)
    }
}

