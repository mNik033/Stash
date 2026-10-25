package com.stash.desktop.platform

import com.stash.core.common.Log
import com.stash.core.common.platform.BackgroundScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

@Singleton
class DesktopScheduler @Inject constructor() : BackgroundScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: BackgroundScheduler.ExistingWorkPolicy,
        taskName: String,
        params: Map<String, String>,
    ) {
        val existing = activeJobs[uniqueWorkName]
        if (existing?.isActive == true && policy == BackgroundScheduler.ExistingWorkPolicy.KEEP) {
            return
        }
        existing?.cancel()

        val job = scope.launch {
            Log.d(TAG, "Executing unique work '$uniqueWorkName' (task: $taskName, params: $params)")
        }
        activeJobs[uniqueWorkName] = job
    }

    override fun enqueuePeriodicWork(
        uniqueWorkName: String,
        repeatInterval: Duration,
        taskName: String,
        params: Map<String, String>,
    ) {
        val existing = activeJobs[uniqueWorkName]
        existing?.cancel()

        val job = scope.launch {
            while (isActive) {
                delay(repeatInterval)
                Log.d(TAG, "Executing periodic work '$uniqueWorkName' (task: $taskName, interval: $repeatInterval)")
            }
        }
        activeJobs[uniqueWorkName] = job
    }

    override fun cancelUniqueWork(uniqueWorkName: String) {
        activeJobs.remove(uniqueWorkName)?.cancel()
    }

    companion object {
        private const val TAG = "DesktopScheduler"
    }
}
