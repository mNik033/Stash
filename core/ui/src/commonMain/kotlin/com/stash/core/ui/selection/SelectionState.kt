package com.stash.core.ui.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Multi-select state for one track-list screen. `isActive` is derived from a non-empty selection. */
class SelectionState(initial: Set<Long> = emptySet()) {
    var selectedIds by mutableStateOf(initial)
        private set
    val isActive: Boolean get() = selectedIds.isNotEmpty()
    val count: Int get() = selectedIds.size
    fun isSelected(id: Long): Boolean = id in selectedIds
    fun enter(id: Long) { selectedIds = selectedIds + id }
    fun toggle(id: Long) { selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id }
    fun selectAll(ids: Collection<Long>) { selectedIds = ids.toSet() }
    fun clear() { selectedIds = emptySet() }
}

/**
 * Survives rotation / process death by persisting the id set, up to [MAX_SAVED_SELECTION] ids.
 * A bigger selection (a "Select all" of a large library) is not saved and comes back empty:
 * written whole, 30-50K boxed ids went into the activity's saved state (hundreds of KB), past
 * the Binder transaction budget, which crashes the app in onStop with TransactionTooLargeException.
 */
val SelectionStateSaver: Saver<SelectionState, List<Long>> = Saver(
    save = { if (it.selectedIds.size <= MAX_SAVED_SELECTION) it.selectedIds.toList() else emptyList() },
    restore = { SelectionState(it.toSet()) },
)

/** The largest selection [SelectionStateSaver] persists (a few KB of saved state). */
const val MAX_SAVED_SELECTION = 500

@Composable
fun rememberSelectionState(): SelectionState =
    rememberSaveable(saver = SelectionStateSaver) { SelectionState() }
