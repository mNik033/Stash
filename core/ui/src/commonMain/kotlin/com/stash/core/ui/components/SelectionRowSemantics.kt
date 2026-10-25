package com.stash.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState

/**
 * While multi-select is on, a track row reads to TalkBack as a checkbox with its checked state.
 * The visible Checkbox has no click of its own (the row toggles it) and so contributed no state,
 * and the row's click carried none either: a TalkBack user couldn't tell which songs were
 * selected before a batch Delete. Applied after the row's clickable, so the two merge.
 */
internal fun Modifier.selectionRowSemantics(selectionActive: Boolean, selected: Boolean): Modifier =
    if (!selectionActive) this else semantics {
        role = Role.Checkbox
        toggleableState = ToggleableState(selected)
    }

/** The long-press action's TalkBack label on a track row: it enters multi-select. */
internal fun selectLongClickLabel(selectionActive: Boolean, onLongPress: (() -> Unit)?): String? =
    if (onLongPress != null && !selectionActive) "Select" else null
