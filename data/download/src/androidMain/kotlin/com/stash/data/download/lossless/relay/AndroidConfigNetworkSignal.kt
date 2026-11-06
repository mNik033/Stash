package com.stash.data.download.lossless.relay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.stash.core.common.platform.AppPaths
import com.stash.core.data.platform.AndroidAppPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

/**
 * [ConfigNetworkSignal] over [ConnectivityManager], with the same "validated internet" rule as the player's
 * `ConnectivityMonitor` (`:core:media`, which this module can't depend on).
 */
internal class AndroidConfigNetworkSignal(private val context: Context) : ConfigNetworkSignal {

    private val cm: ConnectivityManager?
        get() = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val cm = cm ?: return true // can't tell: behave as before (try)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.reachesInternet()
    }

    override suspend fun awaitOnline(): Boolean {
        val cm = cm ?: return false
        try {
            return callbackFlow {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        if (networkCapabilities.reachesInternet()) {
                            trySend(true)
                        }
                    }

                    override fun onLost(network: Network) {
                        // Default network gone; waiting continues until another validates.
                    }
                }
                try {
                    cm.registerDefaultNetworkCallback(callback)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Log.w(TAG, "couldn't register network callback (${t.javaClass.simpleName})")
                    trySend(false)
                    close()
                    return@callbackFlow
                }
                awaitClose {
                    runCatching { cm.unregisterNetworkCallback(callback) }
                }
            }.first()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "awaitOnline failed (${t.javaClass.simpleName})")
            return false
        }
    }

    private companion object {
        const val TAG = "LosslessConfigNet"

        fun NetworkCapabilities.reachesInternet(): Boolean =
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

internal actual fun createDefaultConfigNetworkSignal(appPaths: AppPaths): ConfigNetworkSignal =
    (appPaths as? AndroidAppPaths)?.context?.let { AndroidConfigNetworkSignal(it) } ?: AlwaysOnlineConfigNetworkSignal

