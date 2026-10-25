package com.stash.core.data.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopStreamingPreference @Inject constructor() : StreamingPreference {
    private val _enabled = MutableStateFlow(true)
    private val _streamOnCellular = MutableStateFlow(false)
    private val _streamQuality = MutableStateFlow(StreamQualityTier.LOSSLESS)
    private val _forceYouTubeFallback = MutableStateFlow(false)
    private val _forceQbdlxOnly = MutableStateFlow(false)
    private val _skipQobuz = MutableStateFlow(false)

    override val enabled: Flow<Boolean> = _enabled.asStateFlow()
    override val streamOnCellular: Flow<Boolean> = _streamOnCellular.asStateFlow()
    override val streamQuality: Flow<StreamQualityTier> = _streamQuality.asStateFlow()
    override val forceYouTubeFallback: Flow<Boolean> = _forceYouTubeFallback.asStateFlow()
    override val forceQbdlxOnly: Flow<Boolean> = _forceQbdlxOnly.asStateFlow()
    override val skipQobuz: Flow<Boolean> = _skipQobuz.asStateFlow()

    override suspend fun current(): Boolean = _enabled.value
    override suspend fun isForceYouTubeFallback(): Boolean = _forceYouTubeFallback.value
    override suspend fun isForceQbdlxOnly(): Boolean = _forceQbdlxOnly.value
    override suspend fun isSkipQobuz(): Boolean = _skipQobuz.value

    override suspend fun setEnabled(value: Boolean) { _enabled.value = value }
    override suspend fun setStreamOnCellular(value: Boolean) { _streamOnCellular.value = value }
    override suspend fun setStreamQuality(tier: StreamQualityTier) { _streamQuality.value = tier }
    override suspend fun setForceYouTubeFallback(value: Boolean) { _forceYouTubeFallback.value = value }
    override suspend fun setForceQbdlxOnly(value: Boolean) { _forceQbdlxOnly.value = value }
    override suspend fun setSkipQobuz(value: Boolean) { _skipQobuz.value = value }
}
