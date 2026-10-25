package com.stash.feature.library.mixbuilder

/**
 * Multiplatform scheduler for mix materialization and discovery refresh background work.
 */
interface MixWorkerScheduler {
    suspend fun enqueueRefresh(mixId: Long)
    suspend fun enqueueDiscovery()
}
