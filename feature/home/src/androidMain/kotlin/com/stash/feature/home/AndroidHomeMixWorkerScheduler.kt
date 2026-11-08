package com.stash.feature.home

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.stash.core.data.prefs.DownloadNetworkPreference
import com.stash.core.data.sync.workers.StashDiscoveryWorker
import com.stash.core.data.sync.workers.StashMixRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidHomeMixWorkerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadNetworkPreference: DownloadNetworkPreference,
) : HomeMixWorkerScheduler {

    override suspend fun refreshMix(recipeId: Long, recipeName: String, onMessage: (String) -> Unit) {
        val request = OneTimeWorkRequestBuilder<StashMixRefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(StashMixRefreshWorker.KEY_RECIPE_ID to recipeId))
            .build()
        val uniqueName = "${StashMixRefreshWorker.ONE_SHOT_WORK_NAME}_$recipeId"
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)

        val mode = downloadNetworkPreference.current()
        StashDiscoveryWorker.enqueueOneTime(context, mode)

        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(uniqueName)
            .firstOrNull { infos ->
                val ours = infos.firstOrNull { it.id == request.id } ?: return@firstOrNull false
                when (ours.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        onMessage("Refreshed $recipeName")
                        true
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        onMessage("Refresh failed — try again later")
                        true
                    }
                    else -> false
                }
            }
    }
}

