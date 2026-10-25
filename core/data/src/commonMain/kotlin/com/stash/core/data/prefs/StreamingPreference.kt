package com.stash.core.data.prefs

import kotlinx.coroutines.flow.Flow

/** Stream-quality tier. Persisted as the enum's `name` string. */
enum class StreamQualityTier { LOSSLESS, HIGH_QUALITY_LOSSY }

/**
 * User-facing streaming preferences interface.
 */
interface StreamingPreference {
    val enabled: Flow<Boolean>
    val streamOnCellular: Flow<Boolean>
    val streamQuality: Flow<StreamQualityTier>
    val forceYouTubeFallback: Flow<Boolean>
    val forceQbdlxOnly: Flow<Boolean>
    val skipQobuz: Flow<Boolean>

    suspend fun current(): Boolean
    suspend fun isForceYouTubeFallback(): Boolean
    suspend fun isForceQbdlxOnly(): Boolean
    suspend fun isSkipQobuz(): Boolean

    suspend fun setEnabled(value: Boolean)
    suspend fun setStreamOnCellular(value: Boolean)
    suspend fun setStreamQuality(tier: StreamQualityTier)
    suspend fun setForceYouTubeFallback(value: Boolean)
    suspend fun setForceQbdlxOnly(value: Boolean)
    suspend fun setSkipQobuz(value: Boolean)
    suspend fun purgeRetiredKeys() {}
}
