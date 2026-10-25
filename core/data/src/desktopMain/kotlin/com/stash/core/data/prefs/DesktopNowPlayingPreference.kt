package com.stash.core.data.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DesktopNowPlayingPreference : NowPlayingPreference {
    private val _ambientAnimationEnabled = MutableStateFlow(true)
    override val ambientAnimationEnabled: Flow<Boolean> = _ambientAnimationEnabled.asStateFlow()

    override suspend fun setAmbientAnimationEnabled(enabled: Boolean) {
        _ambientAnimationEnabled.value = enabled
    }

    private val _ambientStyle = MutableStateFlow(AmbientStyle.SOFT_GLOW)
    override val ambientStyle: Flow<AmbientStyle> = _ambientStyle.asStateFlow()

    override suspend fun setAmbientStyle(style: AmbientStyle) {
        _ambientStyle.value = style
    }

    private val _pinnedActions = MutableStateFlow<Set<NowPlayingAction>>(NowPlayingAction.DefaultPins)
    override val pinnedActions: Flow<Set<NowPlayingAction>> = _pinnedActions.asStateFlow()

    override suspend fun setPinnedActions(pins: Set<NowPlayingAction>) {
        _pinnedActions.value = pins
    }

    override suspend fun togglePin(action: NowPlayingAction): Boolean {
        var changed = false
        _pinnedActions.update { current ->
            val next = NowPlayingAction.togglePin(current, action)
            if (next != null) {
                changed = true
                next
            } else current
        }
        return changed
    }

    override suspend fun resetPinnedActions() {
        _pinnedActions.value = NowPlayingAction.DefaultPins
    }
}
