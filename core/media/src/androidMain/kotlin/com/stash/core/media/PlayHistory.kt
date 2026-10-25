package com.stash.core.media

/**
 * The songs actually played, most recent last, so Back under shuffle goes to the song the user
 * heard before this one instead of the shuffle walk's previous slot, which may never have played.
 *
 * A song is an [Entry]: its media id AND its copy ordinal (the k-th time it appears in the
 * timeline, as [QueueDisplay.copyOrdinal]), so a song queued twice backs to the copy that played.
 *
 * There is no "going back" flag. A move to the most recent entry IS a step back, whoever made it:
 * our own Back seeks to [peek] and the move takes it off; the notification's, a headset's or Android
 * Auto's Previous (the session's own seekToPrevious, which the repository never sees as a Back)
 * does the same. Nothing can be left set to swallow a later song change.
 *
 * Main thread only, like the MediaController that feeds it.
 */
internal class PlayHistory(private val limit: Int = DEFAULT_LIMIT) {

    data class Entry(val mediaId: String, val copy: Int)

    private val back = ArrayDeque<Entry>()

    /** The song playing now, as last reported to [moveTo]; null right after [reset]. */
    var current: Entry? = null
        private set

    /** A new queue: nothing in it has been played yet. */
    fun reset() {
        back.clear()
        current = null
    }

    /**
     * The current song is now [entry]. [previousGone]: the song that was playing left the timeline
     * with this change (another controller replaced the queue), so its history no longer applies.
     * A move to the most recent entry is a step back and takes it off the history; any other move
     * records the song just left.
     */
    fun moveTo(entry: Entry, previousGone: Boolean) {
        val previous = current
        if (entry == previous) return
        current = entry
        when {
            previous == null -> Unit
            previousGone -> back.clear()
            back.lastOrNull() == entry -> back.removeLast()
            else -> {
                back.addLast(previous)
                if (back.size > limit) back.removeFirst()
            }
        }
    }

    /** The most recent entry, left in place: Back seeks to it and [moveTo] takes it off. */
    fun peek(): Entry? = back.lastOrNull()

    /** Drops the most recent entry (Back found it gone or unplayable). */
    fun pop(): Entry? = back.removeLastOrNull()

    /**
     * Replaces the most recent entry with [entry]: Back found its song under another copy ordinal
     * (an earlier copy was removed since), and the move there must still read as a step back.
     */
    fun replaceLast(entry: Entry) {
        if (back.isEmpty()) return
        back[back.lastIndex] = entry
    }

    internal val size: Int get() = back.size

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
