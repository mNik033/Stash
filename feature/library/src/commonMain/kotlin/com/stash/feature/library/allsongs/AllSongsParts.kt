package com.stash.feature.library.allsongs

import com.stash.core.model.Track
import com.stash.core.ui.components.fastScrollSectionLetter

/** Most files one share carries: every uri rides in the intent, which the system caps at about 1 MB. */
internal const val MAX_SHARE = 500

/** The scrollbar bubble's letter for the row at [index]: the first letter of that song's title. */
internal fun titleSectionLabel(songs: List<Track>): (Int) -> String? = { index ->
    songs.getOrNull(index)?.let { fastScrollSectionLetter(it.title) }
}