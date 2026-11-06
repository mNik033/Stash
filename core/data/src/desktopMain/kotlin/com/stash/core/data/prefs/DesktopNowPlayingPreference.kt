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

    private val _cycleAlbumArt = MutableStateFlow(true)
    override val cycleAlbumArt: Flow<Boolean> = _cycleAlbumArt.asStateFlow()

    override suspend fun setCycleAlbumArt(enabled: Boolean) {
        _cycleAlbumArt.value = enabled
    }

    private val _fullArt = MutableStateFlow(true)
    override val fullArt: Flow<Boolean> = _fullArt.asStateFlow()

    override suspend fun setFullArt(on: Boolean) {
        _fullArt.value = on
    }

    private val _titleAlign = MutableStateFlow(NpTitleAlign.DEFAULT)
    override val titleAlign: Flow<NpTitleAlign> = _titleAlign.asStateFlow()

    override suspend fun setTitleAlign(align: NpTitleAlign) {
        _titleAlign.value = align
    }

    private val _titleButton = MutableStateFlow(TitleButton.SLEEP_TIMER)
    override val titleButton: Flow<TitleButton> = _titleButton.asStateFlow()

    override suspend fun setTitleButton(button: TitleButton) {
        _titleButton.value = button
    }

    private val _fullArtBlend = MutableStateFlow(FullArtBlend.DEFAULT)
    override val fullArtBlend: Flow<FullArtBlend> = _fullArtBlend.asStateFlow()

    override suspend fun setFullArtBlend(blend: FullArtBlend) {
        _fullArtBlend.value = blend
    }

    private val _discogsArt = MutableStateFlow(true)
    override val discogsArt: Flow<Boolean> = _discogsArt.asStateFlow()

    private val _discogsArtOnSince = MutableStateFlow(0L)
    override val discogsArtOnSince: Flow<Long> = _discogsArtOnSince.asStateFlow()

    override suspend fun setDiscogsArt(on: Boolean) {
        _discogsArt.value = on
        if (on) _discogsArtOnSince.value = System.currentTimeMillis()
    }

    private val _fullArtZoom = MutableStateFlow(NowPlayingPreference.FULL_ART_ZOOM_DEFAULT)
    override val fullArtZoom: Flow<Float> = _fullArtZoom.asStateFlow()

    override suspend fun setFullArtZoom(zoom: Float) {
        _fullArtZoom.value = zoom.coerceIn(NowPlayingPreference.FULL_ART_ZOOM_MIN, NowPlayingPreference.FULL_ART_ZOOM_MAX)
    }

    private val _fullArtPan = MutableStateFlow(FullArtPan.DEFAULT)
    override val fullArtPan: Flow<FullArtPan> = _fullArtPan.asStateFlow()

    override suspend fun setFullArtPan(pan: FullArtPan) {
        _fullArtPan.value = pan
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
