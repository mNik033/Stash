package com.stash.feature.home

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopHomeMixWorkerScheduler @Inject constructor() : HomeMixWorkerScheduler {
    override suspend fun refreshMix(recipeId: Long, recipeName: String, onMessage: (String) -> Unit) {
        onMessage("Refreshed $recipeName")
    }
}

