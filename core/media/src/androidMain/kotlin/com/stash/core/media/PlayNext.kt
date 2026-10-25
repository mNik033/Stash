package com.stash.core.media

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.stash.core.media.service.StashPlaybackService

/**
 * "Play next", shared by the repository (shuffle off: the controller inserts) and the playback
 * service (shuffle on: only the session's own player knows the real play order, see
 * [StashPlaybackService.COMMAND_PLAY_NEXT_SHUFFLED]). Both run [plan] against a [Player], so the
 * placement can't drift apart between the two, and tests drive the same code with a fake player.
 *
 * Songs queued with Play next carry [StashPlaybackService.EXTRA_PLAY_NEXT] in their metadata
 * extras, which ride the session round trip like the track id does. A new one goes after the run
 * of tagged songs right behind the current one ([QueueDisplay.playNextSlot]), so several play in
 * the order they were added.
 */
internal object PlayNext {

    /** Where the song goes: timeline slot [at], the shuffle walk with it placed (null with shuffle
     *  off), and the song just before slot [at] as (track id, copy ordinal), which locates the
     *  same spot in the logical queue (see [logicalIndexAfter]). */
    data class Plan(val at: Int, val walk: List<Int>?, val afterTrackId: Long, val afterCopy: Int)

    /** [item] with the Play next tag. Its other extras (the track id first) are kept. */
    fun tag(item: MediaItem): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
            putBoolean(StashPlaybackService.EXTRA_PLAY_NEXT, true)
        }
        return item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
    }

    fun isTagged(item: MediaItem): Boolean =
        item.mediaMetadata.extras?.getBoolean(StashPlaybackService.EXTRA_PLAY_NEXT, false) == true

    /** The extras' track id, else the media id; -1 for neither (as the repository's timeline ids). */
    fun trackIdOf(item: MediaItem): Long =
        item.mediaMetadata.extras?.getLong(StashPlaybackService.EXTRA_TRACK_ID, -1L)?.takeIf { it != -1L }
            ?: item.mediaId.toLongOrNull() ?: -1L

    /** [player]'s shuffle walk (timeline indexes in play order), or null with shuffle off. */
    fun walkOf(player: Player): List<Int>? {
        val count = player.mediaItemCount
        if (!player.shuffleModeEnabled || count == 0) return null
        val timeline = player.currentTimeline
        return QueueDisplay.walkShuffleOrder(count, timeline.getFirstWindowIndex(true)) {
            timeline.getNextWindowIndex(it, Player.REPEAT_MODE_OFF, true)
        }.takeIf { it.size == count }
    }

    /** Where a Play next goes on [player], given its play order [walk]; null for an empty player. */
    fun plan(player: Player, walk: List<Int>?): Plan? {
        val count = player.mediaItemCount
        val current = player.currentMediaItemIndex
        if (count == 0 || current !in 0 until count) return null
        val slot = QueueDisplay.playNextSlot(count, current, walk) { isTagged(player.getMediaItemAt(it)) }
        // slot.at >= current + 1, so there is always a song before it.
        val idsBefore = List(slot.at) { trackIdOf(player.getMediaItemAt(it)) }
        val after = slot.at - 1
        return Plan(slot.at, slot.walk, idsBefore[after], QueueDisplay.copyOrdinalOfId(idsBefore, after))
    }

    /**
     * The service's half: inserts [item] on [player] (the session's own ExoPlayer) where [plan]
     * says, and hands the new play order to [setShuffleOrder]. Returns the plan, for the result.
     */
    fun insert(player: Player, item: MediaItem, setShuffleOrder: (IntArray) -> Unit): Plan? {
        val plan = plan(player, walkOf(player)) ?: return null
        player.addMediaItem(plan.at, item)
        plan.walk?.let { setShuffleOrder(it.toIntArray()) }
        return plan
    }

    /** [plan] for the command's SessionResult extras. */
    fun toResultExtras(plan: Plan): Bundle = Bundle().apply {
        putInt(StashPlaybackService.EXTRA_PLAY_NEXT_INDEX, plan.at)
        putLong(StashPlaybackService.EXTRA_PLAY_NEXT_AFTER_ID, plan.afterTrackId)
        putInt(StashPlaybackService.EXTRA_PLAY_NEXT_AFTER_COPY, plan.afterCopy)
    }

    /** The (track id, copy) the service inserted after, or null when the result doesn't carry it. */
    fun afterFromResult(extras: Bundle): Pair<Long, Int>? {
        if (!extras.containsKey(StashPlaybackService.EXTRA_PLAY_NEXT_AFTER_ID)) return null
        return extras.getLong(StashPlaybackService.EXTRA_PLAY_NEXT_AFTER_ID) to
            extras.getInt(StashPlaybackService.EXTRA_PLAY_NEXT_AFTER_COPY, 0)
    }

    /**
     * Where the song goes in the logical queue: right after the [afterCopy]-th copy of
     * [afterTrackId], the song it follows in the timeline. The timeline holds the logical queue's
     * songs in the same order, so this keeps it so ([QueueDisplay.compute],
     * [QueueDisplay.moveTimelineTarget]). -1 when that song isn't in [logical].
     */
    fun logicalIndexAfter(logical: List<com.stash.core.model.Track>, afterTrackId: Long, afterCopy: Int): Int {
        val idx = QueueDisplay.indexOfCopy(logical, afterTrackId, afterCopy)
        return if (idx < 0) -1 else idx + 1
    }
}
