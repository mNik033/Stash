package com.stash.feature.home

/**
 * Multiplatform scheduler for mix materialization and refresh work.
 */
interface HomeMixWorkerScheduler {
    suspend fun refreshMix(recipeId: Long, recipeName: String, onMessage: (String) -> Unit)
}

