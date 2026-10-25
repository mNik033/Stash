package com.stash.feature.library

import com.stash.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Library's single-song "Remove download", made undoable.
 *
 * Removing a download deletes the file, and the Songs tab lists downloads only, so the row
 * just vanished: one mis-tap in the ⋮ sheet and the song was gone with no way back. Now the
 * removal is STAGED first. The song leaves the downloads lists at once ([hiddenIds]), the screen
 * offers Undo for as long as its snackbar shows, and the file is deleted only on [commit]: the
 * snackbar went away without Undo, the listener left the screen, the ViewModel was cleared, or
 * another song was staged (one Undo at a time, like a mail app's).
 *
 * Driving the delete from the snackbar's own lifetime, rather than a timer in here, keeps the
 * Undo honest: TalkBack lengthens a snackbar that has an action, and a timer could delete the
 * file while the button still offered to keep it.
 *
 * After [remove] succeeds the song stays hidden for [settleMillis]: the database write lands
 * before Room re-emits the downloads list, and without the grace the row would blink back for
 * that beat. A failed removal shows the song again at once (its file is still there).
 *
 * Pure (no Android) so the JVM tests can drive it with virtual time. All calls come from the
 * main thread; the StateFlow compare-and-sets keep a commit and an Undo racing from ever both
 * winning.
 *
 * @param scope  Where the delete runs. Must outlive the screen AND the ViewModel: [commit] is
 *   called from onCleared, when viewModelScope is already cancelled, and a delete cut off
 *   between the file and its database row would leave the row pointing at a missing file.
 * @param remove Deletes the song's download; true on success. Reports its own failures.
 */
internal class UndoableDownloadRemoval(
    private val scope: CoroutineScope,
    private val remove: suspend (Track) -> Boolean,
    private val settleMillis: Long = SETTLE_MS,
) {
    private val _pending = MutableStateFlow<Track?>(null)

    /** The staged removal the listener can still undo, or null. */
    val pending: StateFlow<Track?> = _pending.asStateFlow()

    private val _hiddenIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Songs to leave out of the downloads lists: the staged one, and any whose removal is landing. */
    val hiddenIds: StateFlow<Set<Long>> = _hiddenIds.asStateFlow()

    /**
     * Stage [track]'s removal. Any song already staged is committed first. A song that's
     * already staged or on its way out is left alone, so a double tap can't delete twice.
     */
    fun stage(track: Track) {
        if (track.id in _hiddenIds.value) return
        commit()
        _hiddenIds.update { it + track.id }
        _pending.value = track
    }

    /** Undo [trackId]'s staged removal: nothing is deleted. False when it's too late (or wasn't staged). */
    fun undo(trackId: Long): Boolean {
        val staged = _pending.value ?: return false
        if (staged.id != trackId || !_pending.compareAndSet(staged, null)) return false
        _hiddenIds.update { it - trackId }
        return true
    }

    /**
     * Delete the staged song's download now. With a [trackId], only if that's the song staged:
     * a snackbar that outlived its removal (replaced by a newer one) can't commit the newer.
     */
    fun commit(trackId: Long? = null) {
        val staged = _pending.value ?: return
        if (trackId != null && staged.id != trackId) return
        if (!_pending.compareAndSet(staged, null)) return
        scope.launch {
            try {
                if (remove(staged)) delay(settleMillis)
            } finally {
                _hiddenIds.update { it - staged.id }
            }
        }
    }
}

/** How long a removed song stays hidden after its delete, while Room re-emits the lists. */
private const val SETTLE_MS = 3_000L
