package com.stash.core.data.prefs

import kotlinx.coroutines.flow.Flow

/** How Now Playing's ambient moves (Settings > Appearance > Ambient style). */
enum class AmbientStyle {
    /** The default: the web player's soft orbs. */
    SOFT_GLOW,

    /** The app's original ambient. */
    CLASSIC,
}

interface NowPlayingPreference {
    val ambientAnimationEnabled: Flow<Boolean>
    suspend fun setAmbientAnimationEnabled(enabled: Boolean)

    val ambientStyle: Flow<AmbientStyle>
    suspend fun setAmbientStyle(style: AmbientStyle)

    val pinnedActions: Flow<Set<NowPlayingAction>>
    suspend fun setPinnedActions(pins: Set<NowPlayingAction>)
    suspend fun togglePin(action: NowPlayingAction): Boolean
    suspend fun resetPinnedActions()
}
