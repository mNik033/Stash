package com.stash.core.data.library

import androidx.room.withTransaction
import com.stash.core.common.TrackCanonical
import com.stash.core.common.strictCanonical
import com.stash.core.data.blocklist.BlocklistKey
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.dao.CanonicalWordsRow
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The one-time re-key behind schema 55. Until then, songs saved from Search, a stream, a like, a
 * hand-off or a tap stored their canonical words in the strict form ([strictCanonical]: "don t stop
 * feat x" / "a b") while everything else stored the loose rule ([TrackCanonical]: "don't stop" /
 * "a, b"). Every lookup now computes the loose rule, so those rows must hold it too or they are
 * never found by their words. The migration can't do it (SQL can't run the rule), so this runs
 * after it, from [com.stash.core.data.sync.workers.CanonicalRecomputeWorker].
 *
 * What it changes, and nothing else:
 *  - only rows whose stored words are exactly the strict form of their own title and artist, and
 *    differ from the loose form. Rows the sync, discovery or the YouTube canonicalizer wrote are
 *    already loose and are never touched; rows with no canonical words at all (local imports)
 *    stay blank;
 *  - only `canonical_title` / `canonical_artist`, by id, compare-and-set: a row another writer
 *    changed after it was read keeps that writer's words.
 *
 * It never merges or deletes a row: rows that now share a key (the same song saved twice, from
 * Search and from a sync) stay two rows with their own playlists, likes and plays. The startup
 * dedup that merged such rows once wiped playlist memberships (removed in #585); preventing new
 * duplicates is the lookups' job, now that they find these rows.
 *
 * A row whose new key is on the blocklist keeps its old words. The library lists hide, and the
 * blocklist integrity sweep deletes, every row whose stored key is blocked; the blocklist is keyed
 * by the loose words, which don't tell versions apart, so re-keying a downloaded "Song (Live)"
 * whose studio "Song" the user blocked would make the live file vanish.
 *
 * Batched and resumable: rows are read in id order, [BATCH_SIZE] at a time, each batch written in
 * one short transaction (none when nothing in it changes), and the caller is handed the last id
 * after each batch so a cancelled or killed run resumes there. Running it again over rows it
 * already re-keyed changes nothing.
 */
class CanonicalRecompute @Inject constructor(
    private val database: StashDatabase,
) {

    /** What a run did: rows read, rows re-keyed, rows left alone because the new key is blocked, the last id read. */
    data class Outcome(val scanned: Int, val rekeyed: Int, val keptBlocked: Int, val lastId: Long)

    /**
     * Re-keys every strict-written row with an id above [afterId].
     *
     * @param afterId resume point: the last id a previous run finished (0 for the first run).
     * @param batchSize rows per read and per transaction.
     * @param onBatchDone called after each batch is committed, with its last id; persist it to resume.
     */
    suspend fun run(
        afterId: Long = 0L,
        batchSize: Int = BATCH_SIZE,
        onBatchDone: suspend (lastId: Long) -> Unit = {},
    ): Outcome {
        require(batchSize > 0) { "batchSize must be positive" }
        val trackDao = database.trackDao()
        val blockedKeys = database.trackBlocklistDao().getAllKeys().toHashSet()
        var cursor = afterId
        var scanned = 0
        var rekeyed = 0
        var keptBlocked = 0
        while (true) {
            coroutineContext.ensureActive()
            val rows = trackDao.canonicalWordsAfter(cursor, batchSize)
            if (rows.isEmpty()) break
            val changes = rows.mapNotNull { row -> looseWordsFor(row)?.let { row to it } }
            val (blocked, allowed) = changes.partition { (_, words) ->
                BlocklistKey.fromStoredCanonicals(words.second, words.first) in blockedKeys
            }
            keptBlocked += blocked.size
            if (allowed.isNotEmpty()) {
                rekeyed += database.withTransaction {
                    allowed.sumOf { (row, words) ->
                        trackDao.recomputeCanonicalIfUnchanged(
                            id = row.id,
                            oldTitle = row.canonicalTitle,
                            oldArtist = row.canonicalArtist,
                            newTitle = words.first,
                            newArtist = words.second,
                        )
                    }
                }
            }
            scanned += rows.size
            cursor = rows.last().id
            onBatchDone(cursor)
            if (rows.size < batchSize) break
        }
        return Outcome(scanned = scanned, rekeyed = rekeyed, keptBlocked = keptBlocked, lastId = cursor)
    }

    companion object {
        /** Rows per read and per write transaction: ~40 short transactions for a 19k-row library. */
        const val BATCH_SIZE = 500

        /**
         * The loose (title, artist) words [row] should hold, or null when it must be left alone:
         * its stored words are not exactly the strict form of its own title and artist (the sync,
         * discovery, the canonicalizer or nobody wrote them), they already equal the loose form,
         * or the loose title would be blank ("(Intro)", "[Untitled]", "Ft. Lauderdale"): its strict
         * words still name the song, and a blank one would only join the local imports' blank key.
         * Pure, so the rule is testable without a database.
         */
        fun looseWordsFor(row: CanonicalWordsRow): Pair<String, String>? {
            if (row.canonicalTitle.isEmpty() && row.canonicalArtist.isEmpty()) return null
            if (row.canonicalTitle != strictCanonical(row.title)) return null
            if (row.canonicalArtist != strictCanonical(row.artist)) return null
            val title = TrackCanonical.title(row.title)
            val artist = TrackCanonical.artist(row.artist)
            if (title.isBlank()) return null
            if (title == row.canonicalTitle && artist == row.canonicalArtist) return null
            return title to artist
        }
    }
}
