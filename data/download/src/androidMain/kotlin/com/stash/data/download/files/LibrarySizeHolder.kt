package com.stash.data.download.files

import com.stash.core.data.repository.MusicRepository
import javax.inject.Inject
import javax.inject.Singleton
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

/**
 * Single source of truth for the music library's on-disk size + lossless
 * breakdown. Replaces per-screen StateFlow + collector glue with a shared
 * @Singleton observed by [com.stash.feature.home.HomeViewModel] and
 * [com.stash.feature.settings.SettingsViewModel].
 *
 * The legacy DAO `SUM(file_size_bytes)` is unreliable on libraries that
 * predate the v0.8.x downloader fix — many rows have the column stuck at
 * 0 and the recovery backfill fails on orphaned rows + content:// path
 * mismatches. [FileOrganizer.computeMusicLibrarySize] walks the actual
 * filesystem (storage-mode-aware: internal `filesDir/music` for default,
 * one SAF children query per folder for users on external storage) and
 * returns disk truth.
 *
 * Lifecycle:
 *  - The walker only runs when at least one consumer observes [size]
 *    (`SharingStarted.WhileSubscribed(5_000)`); a 5-second grace period
 *    covers brief navigation transitions.
 *  - Walks happen on [Dispatchers.IO] via `flowOn`.
 *  - On walk failure, `scan` returns the previous value — the StateFlow
 *    never flashes to 0 mid-recompute.
 *  - A track-count change walks again at most every [MIN_WALK_GAP_MS] after
 *    the last walk ended. A sync inserts or downloads hundreds of tracks,
 *    each a count change, and on an SD card a walk is a binder query per
 *    folder, so Home re-walked the tree back to back for the whole sync,
 *    competing with the downloads' own writes. [refresh] still walks at once.
 *
 * Cold-start: the StateFlow starts at `LibrarySizeBreakdown(0, 0, 0)`, and
 * the UI shows 0 GB until the first walk ends. On SAF storage that used to
 * take 1-2 minutes per ~3000 files (DocumentFile queried the provider about
 * four times per file); it is now one query per folder. Persisting the last
 * value across app restarts is out of scope (see spec).
 */
@Singleton
class LibrarySizeHolder @Inject constructor(
    private val fileOrganizer: FileOrganizer,
    private val musicRepository: MusicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshVersion = MutableStateFlow(0L)

    // The one collector's (stateIn) bookkeeping for the walk gap.
    private var lastVersion = 0L
    private var lastWalkEndedAtMs = 0L

    val size: StateFlow<LibrarySizeBreakdown> = combine(
        musicRepository.getTrackCount().distinctUntilChanged(),
        refreshVersion,
    ) { _, version -> version }
        .scan(NOT_WALKED) { prev, version ->
            // A collection's first walk (prev is still the seed) and a refresh run at once.
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

    /** Requests a fresh filesystem walk even when the track count is unchanged. */
    fun refresh() {
        refreshVersion.update { it + 1 }
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000

    internal companion object {
        /** Least idle time between two walks that a track-count change starts. */
        const val MIN_WALK_GAP_MS = 60_000L

        /** [scan]'s seed: shown until the first walk of a collection ends. */
        private val NOT_WALKED = LibrarySizeBreakdown(0L, 0L, 0)
    }
}
