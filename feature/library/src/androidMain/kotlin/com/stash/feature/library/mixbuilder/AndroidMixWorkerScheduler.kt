package com.stash.feature.library.mixbuilder

import android.content.Context
import com.stash.core.data.prefs.DownloadNetworkPreference
import com.stash.core.data.sync.workers.StashDiscoveryWorker
import com.stash.core.data.sync.workers.StashMixRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMixWorkerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadNetworkPreference: DownloadNetworkPreference,
) : MixWorkerScheduler {

    override suspend fun enqueueRefresh(mixId: Long) {
        StashMixRefreshWorker.enqueueOneTime(context, mixId)
    }

    override suspend fun enqueueDiscovery() {
        StashDiscoveryWorker.enqueueOneTime(context, downloadNetworkPreference.current())
    }
}
