package com.stash.core.data.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Its own file, so a corrupt write can only reset this state, and a reset only means the
 * recompute runs once more (it changes nothing the second time).
 */
private val Context.canonicalRecomputeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "canonical_recompute",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Whether the one-time [CanonicalRecompute] has finished on this install, and where a stopped run
 * resumes. The gate that keeps it to once: startup enqueues the worker only while [isDone] is
 * false, and a run ([CanonicalRecomputeRunner]) returns at once when it is true.
 *
 * Re-armed ([rearm]) when a backup is restored or merged: a backup made before schema 55 brings
 * back rows in the old strict words. Each re-arm starts a new generation, and a run marks done only
 * the generation it started under ([markDone]): a re-arm that lands after a run's last read, before
 * it marks done, would otherwise be swallowed, leaving the merged rows with their old words.
 */
@Singleton
class CanonicalRecomputeState internal constructor(
    private val store: DataStore<Preferences>,
) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.canonicalRecomputeDataStore)

    /**
     * One run over the library: the generation it serves and the row id it resumes after (0 for
     * the first row).
     */
    data class Pass(val generation: Int, val cursor: Long)

    /** True once a run reached the last row under the current [RULE_VERSION], with no re-arm since. */
    suspend fun isDone(): Boolean = isDone(store.data.first())

    /** The run still to do, from where a stopped one left off; null when [isDone]. */
    suspend fun pendingPass(): Pass? {
        val prefs = store.data.first()
        if (isDone(prefs)) return null
        return Pass(generation = prefs[GENERATION] ?: 0, cursor = prefs[CURSOR] ?: 0L)
    }

    /**
     * Records that [pass] is done with every row up to [lastId], so a stopped run resumes after it.
     * Skipped when a re-arm came since [pass] began: the re-arm's restart from the first row stands.
     */
    suspend fun saveCursor(pass: Pass, lastId: Long) {
        store.edit { if ((it[GENERATION] ?: 0) == pass.generation) it[CURSOR] = lastId }
    }

    /**
     * Marks the recompute finished for [RULE_VERSION], unless a re-arm came since [pass] began; the
     * cursor is no longer needed. In one edit, so a re-arm is either before it (and this refuses) or
     * after it (and [isDone] turns false again).
     *
     * @return false when re-armed meanwhile: the recompute must run again from the first row.
     */
    suspend fun markDone(pass: Pass): Boolean {
        var marked = false
        store.edit {
            if ((it[GENERATION] ?: 0) == pass.generation) {
                it[DONE_VERSION] = RULE_VERSION
                it[DONE_GENERATION] = pass.generation
                it.remove(CURSOR)
                marked = true
            }
        }
        return marked
    }

    /**
     * Asks for a fresh run from the first row (a restored or merged backup may hold old rows): a new
     * generation, which no run that began before it can mark done.
     */
    suspend fun rearm() {
        store.edit {
            it[GENERATION] = (it[GENERATION] ?: 0) + 1
            it.remove(CURSOR)
        }
    }

    private fun isDone(prefs: Preferences): Boolean =
        (prefs[DONE_VERSION] ?: 0) >= RULE_VERSION && (prefs[DONE_GENERATION] ?: 0) == (prefs[GENERATION] ?: 0)

    companion object {
        /**
         * Bump to run the recompute again on every install, e.g. if [CanonicalRecompute] learns a
         * new legacy form to recognise. Never needed for rows written by the current rule.
         */
        const val RULE_VERSION = 1

        private val DONE_VERSION = intPreferencesKey("done_rule_version")
        private val CURSOR = longPreferencesKey("cursor_after_id")

        /** Bumped by every [rearm]. Absent (0) on an install that was never re-armed. */
        private val GENERATION = intPreferencesKey("generation")

        /** The [GENERATION] the last finished run served. */
        private val DONE_GENERATION = intPreferencesKey("done_generation")

        /** The state over the process-wide DataStore, for code that has a Context but no injection. */
        fun forContext(context: Context): CanonicalRecomputeState =
            CanonicalRecomputeState(context.applicationContext.canonicalRecomputeDataStore)
    }
}
