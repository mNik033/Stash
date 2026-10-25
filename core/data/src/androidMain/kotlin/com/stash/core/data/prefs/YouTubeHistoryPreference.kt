package com.stash.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Dedicated DataStore for YT history sync opt-in. Internal so a test can write a pre-cutoff install's state. */
internal val Context.youtubeHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "youtube_history_preference",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Persists the user's opt-in for scrobbling local plays to YouTube
 * Music's Watch History (thereby feeding YT Music's recommender graph).
 *
 * Default is false — the feature is off on every fresh install. Flipping
 * on shows a one-time confirmation dialog in the Settings layer; this
 * class itself is unaware of the dialog, it just stores the bool.
 *
 * ## The opt-in instant
 *
 * Alongside the bool it stores **when** the toggle was turned on, and the
 * scrobbler sends only plays that started at or after that instant: the same
 * history-flood guard as the connect timestamp in
 * [com.stash.core.data.listenbrainz.ListenBrainzPreference]. Every play Stash
 * has ever recorded sits in `listening_events` with `yt_scrobbled = 0`, so
 * without a cutoff, turning the toggle on sent the user's entire back
 * catalogue to their Watch History, each play stamped "now" (the ping carries
 * no timestamp), while the dialog promised only the tracks they finish from
 * then on.
 *
 * Turning the toggle off forgets the instant, so turning it on again starts
 * from that new moment: plays made while the user had opted out are never sent.
 */
@Singleton
class YouTubeHistoryPreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val enabledAtKey = longPreferencesKey("enabled_at_ms")

    val enabled: Flow<Boolean> = context.youtubeHistoryDataStore.data.map { prefs ->
        prefs[enabledKey] ?: false
    }

    /**
     * The cutoff: plays that started before this instant are never sent. Null
     * while the toggle is off, and for an install that turned it on before the
     * instant was stored until [sendPlaysSinceMs] stamps one.
     */
    val sendPlaysSince: Flow<Long?> = context.youtubeHistoryDataStore.data.map { prefs ->
        if (prefs[enabledKey] == true) prefs[enabledAtKey] else null
    }

    suspend fun current(): Boolean = enabled.first()

    /**
     * Turning on stamps [nowMs] as the cutoff. Turning on while already on does
     * NOT move it: that would silently drop plays still queued from an outage
     * (the rule [com.stash.core.data.listenbrainz.ListenBrainzPreference.connect]
     * follows for a reconnect).
     */
    suspend fun setEnabled(value: Boolean, nowMs: Long = System.currentTimeMillis()) {
        context.youtubeHistoryDataStore.edit { prefs ->
            if (value) {
                if (prefs[enabledKey] != true || prefs[enabledAtKey] == null) prefs[enabledAtKey] = nowMs
            } else {
                prefs.remove(enabledAtKey)
            }
            prefs[enabledKey] = value
        }
    }

    /**
     * The cutoff for a drain, or null when the toggle is off.
     *
     * An install that turned the toggle on before the cutoff existed has no
     * stored instant. It is stamped with [nowMs] here, once, instead of being
     * read as "no cutoff": that would give those users the same whole-history
     * backfill (or the rest of one already under way), which they never asked
     * for either. The cost is that plays still queued at the upgrade are not sent.
     */
    suspend fun sendPlaysSinceMs(nowMs: Long = System.currentTimeMillis()): Long? {
        val prefs = context.youtubeHistoryDataStore.edit { p ->
            if (p[enabledKey] == true && p[enabledAtKey] == null) p[enabledAtKey] = nowMs
        }
        return if (prefs[enabledKey] == true) prefs[enabledAtKey] else null
    }
}
