package com.stash.core.data.mix

import kotlinx.coroutines.flow.Flow

/**
 * Common interface for treating Last.fm as a sync source.
 */
interface LastFmRecommendationSource {
    companion object {
        const val RECIPE_NAME = "Recommended by Last.fm"
        const val DISCOVERY_RATIO = 1.0f
        const val TARGET_LENGTH = 40
        const val SEED_STRATEGY = "TRACK_SIMILAR"
    }

    fun observeState(): Flow<LastFmRecommendationState>

    suspend fun setRecommendationsEnabled(enabled: Boolean)
    suspend fun reconcile(kickRefresh: Boolean = false)
}

