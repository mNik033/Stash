package com.stash.core.ui.ambient

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberLiveReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var removeAnimations by remember(resolver) { mutableStateOf(readRemoveAnimations(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                removeAnimations = readRemoveAnimations(resolver)
            }
        }
        val registered = try {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
            )
            true
        } catch (_: Exception) {
            false
        }
        removeAnimations = readRemoveAnimations(resolver)
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    return motionStill(removeAnimations, LocalBatterySaverOn.current)
}

private fun readRemoveAnimations(resolver: ContentResolver): Boolean = try {
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (_: Exception) {
    false
}

