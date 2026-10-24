package com.stash.core.common.platform

import kotlin.time.Duration

/**
 * Platform abstraction for background job execution and periodic sync tasks,
 * decoupling domain sync logic from Android WorkManager.
 */
interface BackgroundScheduler {

    enum class ExistingWorkPolicy {
        REPLACE,
        KEEP,
        APPEND,
    }

    sealed class TaskResult {
        data object Success : TaskResult()
        data object Retry : TaskResult()
        data class Failure(val cause: Throwable? = null) : TaskResult()
    }

    /**
     * Enqueue a one-off task with a unique work name.
     */
    fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
        taskName: String,
        params: Map<String, String> = emptyMap(),
    )

    /**
     * Enqueue a periodic recurring task.
     */
    fun enqueuePeriodicWork(
        uniqueWorkName: String,
        repeatInterval: Duration,
        taskName: String,
        params: Map<String, String> = emptyMap(),
    )

    /**
     * Cancel an active or scheduled unique background task.
     */
    fun cancelUniqueWork(uniqueWorkName: String)
}
