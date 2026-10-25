package com.stash.core.data.repository

import com.stash.core.data.db.dao.DownloadedFileRef
import com.stash.core.data.files.LocalFileState

/**
 * Outcome of scanning the `is_downloaded = 1` rows against what's actually on
 * disk.
 *
 * @property resetIds rows to un-mark — their local file is reliably missing or
 *   too small to be real audio. They become not-downloaded (and therefore
 *   streamable / re-downloadable).
 * @property junkPaths the subset whose file is PRESENT but too small (a failed
 *   download's garbage body) — these are additionally deleted from disk.
 *   Missing rows contribute no path (nothing to delete).
 * @property nullPath count of rows whose stored path was null/blank (telemetry).
 * @property checkedPaths for each reset row that had a path, the path found
 *   unusable. The reset is guarded on it (`resetMissingFiles`): classifying a
 *   large SAF library takes a while, and a FLAC upgrade or swap can record a
 *   new file for the row meanwhile — resetting by id alone would wipe that.
 */
internal data class DownloadReconcileResult(
    val resetIds: List<Long>,
    val junkPaths: List<String>,
    val nullPath: Int,
    val checkedPaths: Map<Long, String> = emptyMap(),
)

/**
 * Pure classification for the download-integrity sweep. Acts ONLY on
 * unambiguous evidence:
 *  - [LocalFileState.MISSING] (reliably absent) -> un-mark the row.
 *  - [LocalFileState.TOO_SMALL] (present but junk) -> un-mark AND delete the file.
 *  - [LocalFileState.OK] / [LocalFileState.INCONCLUSIVE] -> leave untouched.
 *
 * INCONCLUSIVE is the safety valve: a SAF document whose size couldn't be read
 * at cold start must never be un-marked or deleted, or a real external-storage
 * library could be damaged on a flaky boot.
 *
 * Kept pure (state lookup injected) so it's exhaustively testable without a
 * filesystem or Room.
 */
internal fun classifyDownloadedRefs(
    refs: List<DownloadedFileRef>,
    stateOf: (String?) -> LocalFileState,
): DownloadReconcileResult {
    val resetIds = mutableListOf<Long>()
    val junkPaths = mutableListOf<String>()
    val checkedPaths = LinkedHashMap<Long, String>()
    var nullPath = 0
    for (ref in refs) {
        val path = ref.filePath
        if (path.isNullOrBlank()) nullPath++
        when (stateOf(path)) {
            LocalFileState.MISSING -> {
                resetIds += ref.id
                if (!path.isNullOrBlank()) checkedPaths[ref.id] = path
            }
            LocalFileState.TOO_SMALL -> {
                resetIds += ref.id
                if (!path.isNullOrBlank()) {
                    junkPaths += path
                    checkedPaths[ref.id] = path
                }
            }
            LocalFileState.OK, LocalFileState.INCONCLUSIVE -> Unit // leave as-is
        }
    }
    return DownloadReconcileResult(resetIds, junkPaths, nullPath, checkedPaths)
}
