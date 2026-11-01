package com.stash.data.download.files

import com.stash.core.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopLibrarySizeHolder @Inject constructor(
    private val fileOrganizer: DesktopFileOrganizer,
    private val musicRepository: MusicRepository,
) : LibrarySizeHolder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshVersion = MutableStateFlow(0L)

    private var lastVersion = 0L
    private var lastWalkEndedAtMs = 0L

    override val size: StateFlow<LibrarySizeBreakdown> = combine(
        musicRepository.getTrackCount().distinctUntilChanged(),
        refreshVersion,
    ) { _, version -> version }
        .scan(NOT_WALKED) { prev, version ->
            val asked = prev === NOT_WALKED || version != lastVersion
            lastVersion = version
            if (!asked) {
                val wait = lastWalkEndedAtMs + MIN_WALK_GAP_MS - nowMs()
                if (wait > 0) delay(wait)
            }
            try {
                fileOrganizer.computeMusicLibrarySize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                prev
            } finally {
                lastWalkEndedAtMs = nowMs()
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibrarySizeBreakdown(0L, 0L, 0),
        )

    override fun refresh() {
        refreshVersion.update { it + 1 }
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000

    internal companion object {
        const val MIN_WALK_GAP_MS = 60_000L
        private val NOT_WALKED = LibrarySizeBreakdown(0L, 0L, 0)
    }
}
