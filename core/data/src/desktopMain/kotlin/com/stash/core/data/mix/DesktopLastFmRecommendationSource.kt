package com.stash.core.data.mix

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopLastFmRecommendationSource @Inject constructor() : LastFmRecommendationSource {
    override fun observeState(): Flow<LastFmRecommendationState> =
        flowOf(LastFmRecommendationState())

    override suspend fun setRecommendationsEnabled(enabled: Boolean) {}

    override suspend fun reconcile(kickRefresh: Boolean) {}
}
