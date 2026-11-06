package com.stash.feature.search

import com.stash.data.ytmusic.model.MoodTile
import kotlinx.coroutines.flow.StateFlow

/**
 * What Search shows before anything is typed: YouTube Music's [moods] and [genres] tiles, each with the [covers]
 * saved or read for it (by params). [online] is null until the Online/Offline switch is read; Offline there are no
 * tiles. [switchedOff] is Settings > Appearance > Moods & genres in Search turned off: no tiles either, as Offline.
 * [loaded] turns true once Search knows whether it has tiles: at once when some were saved, else when today's
 * read is over (answered, failed, or no network to try), so a fresh install waits for its first tiles rather than
 * deciding there are none.
 */
data class MoodBrowseState(
    val online: Boolean? = null,
    val switchedOff: Boolean = false,
    val loaded: Boolean = false,
    val moods: List<MoodTile> = emptyList(),
    val genres: List<MoodTile> = emptyList(),
    val covers: Map<String, List<String>> = emptyMap(),
) {
    /** The grid has something to show: Online with the switch on, with tiles in either section. */
    val hasTiles: Boolean get() = online == true && !switchedOff && (moods.isNotEmpty() || genres.isNotEmpty())

    /**
     * Whether Search shows the grid before anything is typed: true with tiles, false Offline, with the switch off, or
     * once it's known there are none (a fresh install without network, YouTube not answering), null while that isn't
     * known yet. False is Search as it was before the grid: the recent searches, the field focused on the first open.
     */
    val showsGrid: Boolean? get() = when {
        online == false || switchedOff -> false
        hasTiles -> true
        online == true && loaded -> false
        else -> null
    }
}

/**
 * Interface for Search's "Moods & moments" and "Genres" view model.
 */
interface MoodBrowseViewModel {
    val state: StateFlow<MoodBrowseState>
    fun onResume()
    fun onTilesVisible(tiles: List<MoodTile>)
}
