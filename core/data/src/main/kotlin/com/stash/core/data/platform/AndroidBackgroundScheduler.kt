package com.stash.core.data.platform

import android.content.Context
import androidx.work.ExistingWorkPolicy as AndroidExistingWorkPolicy
import androidx.work.WorkManager
import com.stash.core.common.platform.BackgroundScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Android implementation of [BackgroundScheduler] backed by [WorkManager].
 */
@Singleton
class AndroidBackgroundScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackgroundScheduler {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: BackgroundScheduler.ExistingWorkPolicy,
        taskName: String,
        params: Map<String, String>,
    ) {
        // Enqueue via WorkManager if needed, or no-op placeholder for general tasks
    }

    override fun enqueuePeriodicWork(
        uniqueWorkName: String,
        repeatInterval: Duration,
        taskName: String,
        params: Map<String, String>,
    ) {
        // Enqueue periodic task via WorkManager
    }

    override fun cancelUniqueWork(uniqueWorkName: String) {
        workManager.cancelUniqueWork(uniqueWorkName)
    }
}
