package com.stash.core.data.prefs

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DesktopLyricsPreference @Inject constructor() : LyricsPreference {
    private val _liveBarEnabled = MutableStateFlow(false)
    override val liveBarEnabled: Flow<Boolean> = _liveBarEnabled.asStateFlow()
    override suspend fun setLiveBarEnabled(value: Boolean) { _liveBarEnabled.value = value }

    private val _sourcePreference = MutableStateFlow(LyricsSourcePreference.APPLE_MUSIC)
    override val sourcePreference: Flow<LyricsSourcePreference> = _sourcePreference.asStateFlow()
    override suspend fun setSourcePreference(value: LyricsSourcePreference) { _sourcePreference.value = value }

    private val outputOffsets = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<Long>>()
    override fun outputOffsetMs(outputKey: String): Flow<Long> =
        outputOffsets.computeIfAbsent(outputKey) { MutableStateFlow(0L) }.asStateFlow()
    override suspend fun setOutputOffsetMs(outputKey: String, offsetMs: Long) {
        outputOffsets.computeIfAbsent(outputKey) { MutableStateFlow(0L) }.value = offsetMs
    }

    private val _bulkFetchCursor = MutableStateFlow(0L)
    override val bulkFetchCursor: Flow<Long> = _bulkFetchCursor.asStateFlow()
    override suspend fun setBulkFetchCursor(trackId: Long) { _bulkFetchCursor.value = trackId }

    private val _ttmlUpgradeCursor = MutableStateFlow(0L)
    override val ttmlUpgradeCursor: Flow<Long> = _ttmlUpgradeCursor.asStateFlow()
    override suspend fun setTtmlUpgradeCursor(trackId: Long) { _ttmlUpgradeCursor.value = trackId }

    private val _recheckCursor = MutableStateFlow(0L)
    override val recheckCursor: Flow<Long> = _recheckCursor.asStateFlow()
    override suspend fun setRecheckCursor(trackId: Long) { _recheckCursor.value = trackId }

    private val _recheckFinishedAt = MutableStateFlow<Long?>(null)
    override val recheckFinishedAt: Flow<Long?> = _recheckFinishedAt.asStateFlow()
    override suspend fun finishRecheck(atMs: Long) {
        _recheckCursor.value = 0L
        _recheckFinishedAt.value = atMs
    }
}
