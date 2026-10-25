package com.stash.feature.settings

import android.content.Context
import android.net.Uri

/**
 * Carries a library folder the user picked during a restore's folder re-grant
 * across the process restart that ends the restore.
 *
 * Why not [com.stash.core.data.prefs.StoragePreference]: a full restore swaps
 * `datastore/storage_preferences.preferences_pb` underneath the LIVE DataStore
 * singleton, which keeps serving — and on every `edit`, re-writing — its
 * pre-restore in-memory copy. Writing the picked folder through it before the
 * restart would replace the restored file with pre-restore values plus the new
 * URI, silently reverting the restored library settings. This tiny
 * SharedPreferences file is not part of any backup, so nothing swaps it; it is
 * written synchronously right before the kill and applied by `StashApplication`
 * through the freshly-loaded DataStore on the next start.
 */
object RestoredFolderHandoff {
    private const val PREFS_NAME = "stash_restore_handoff"
    private const val KEY_TREE_URI = "pending_tree_uri"

    /** Records [uri] for the next start. Synchronous: the process is about to be killed. */
    fun record(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, uri.toString())
            .commit()
    }

    /** Returns the recorded folder, if any, and forgets it so it is applied once. */
    fun consume(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TREE_URI, null) ?: return null
        prefs.edit().remove(KEY_TREE_URI).apply()
        return Uri.parse(raw)
    }
}
