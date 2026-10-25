package com.stash.core.data.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.nowPlayingDataStore by preferencesDataStore(
    name = "now_playing_preference",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class AndroidNowPlayingPreference @Inject constructor(@ApplicationContext private val context: Context) : NowPlayingPreference {
    private val ambientAnimationKey = booleanPreferencesKey("ambient_animation_enabled")
    override val ambientAnimationEnabled = context.nowPlayingDataStore.data.map { it[ambientAnimationKey] ?: true }

    override suspend fun setAmbientAnimationEnabled(enabled: Boolean) {
        context.nowPlayingDataStore.edit { it[ambientAnimationKey] = enabled }
    }

    private val ambientStyleKey = stringPreferencesKey("ambient_style")

    /** Unset or unknown reads as [AmbientStyle.SOFT_GLOW], so existing installs get the default. */
    override val ambientStyle = context.nowPlayingDataStore.data.map { prefs ->
        prefs[ambientStyleKey]?.let { name -> AmbientStyle.entries.firstOrNull { it.name == name } }
            ?: AmbientStyle.SOFT_GLOW
    }

    override suspend fun setAmbientStyle(style: AmbientStyle) {
        context.nowPlayingDataStore.edit { it[ambientStyleKey] = style.name }
    }

    private val pinnedActionsKey = stringSetPreferencesKey("pinned_actions")

    /**
     * The actions pinned to Now Playing's top row (edited from the ⋮ menu). Unset reads as
     * [NowPlayingAction.DefaultPins]; an empty set is kept as empty (the user unpinned all).
     * Unknown names are ignored, see [NowPlayingAction.parsePins].
     */
    override val pinnedActions = context.nowPlayingDataStore.data.map { prefs ->
        NowPlayingAction.parsePins(prefs[pinnedActionsKey])
    }

    override suspend fun setPinnedActions(pins: Set<NowPlayingAction>) {
        context.nowPlayingDataStore.edit { it[pinnedActionsKey] = pins.mapTo(HashSet()) { a -> a.name } }
    }

    /**
     * Pins or unpins [action] in one DataStore transaction: the read, the
     * [NowPlayingAction.togglePin] rule (with its [NowPlayingAction.MAX_PINS] cap) and the
     * write happen inside a single edit, which DataStore serialises. Two quick taps therefore
     * both land, rather than the second overwriting the first from a stale read, and the cap
     * holds in storage whatever the UI believed. Returns false when the row was full.
     */
    override suspend fun togglePin(action: NowPlayingAction): Boolean {
        var changed = false
        context.nowPlayingDataStore.edit { prefs ->
            val next = NowPlayingAction.togglePin(NowPlayingAction.parsePins(prefs[pinnedActionsKey]), action)
            if (next != null) {
                prefs[pinnedActionsKey] = next.mapTo(HashSet()) { a -> a.name }
                changed = true
            }
        }
        return changed
    }

    /** Back to Radio · ⋮ · Queue (and to whatever a later release makes the default). */
    override suspend fun resetPinnedActions() {
        context.nowPlayingDataStore.edit { it.remove(pinnedActionsKey) }
    }
}
