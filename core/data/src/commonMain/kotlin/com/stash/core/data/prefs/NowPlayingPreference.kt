package com.stash.core.data.prefs

import kotlinx.coroutines.flow.Flow

/** How Now Playing's ambient moves (Settings > Appearance > Ambient style). */
enum class AmbientStyle {
    /** The default: the web player's soft orbs. */
    SOFT_GLOW,

    /** The app's original ambient. */
    CLASSIC,
}

/** Where Now Playing's title and artist sit (Settings > Appearance > Title alignment). */
enum class NpTitleAlign {
    CENTER,
    START,
    ;

    companion object {
        /** At the leading edge, as the owner set Now Playing up (2026-10-26). */
        val DEFAULT = START
    }
}

/**
 * What sits beside Now Playing's title, opposite the like heart (Settings > Appearance > Button
 * beside the title): the sleep timer (the default), nothing, or one of Now Playing's actions.
 */
enum class TitleButton(val action: NowPlayingAction?) {
    SLEEP_TIMER(null),
    NONE(null),
    LYRICS(NowPlayingAction.LYRICS),
    QUEUE(NowPlayingAction.QUEUE),
    RADIO(NowPlayingAction.RADIO),
    PLAYBACK_SPEED(NowPlayingAction.PLAYBACK_SPEED),
    SAVE_TO_PLAYLIST(NowPlayingAction.SAVE_TO_PLAYLIST),
    SHARE(NowPlayingAction.SHARE),
    DOWNLOAD(NowPlayingAction.DOWNLOAD),
    VIEW_ALBUM(NowPlayingAction.VIEW_ALBUM),
}

/** What full art melts into under the controls (Settings > Appearance > Full art blend). */
enum class FullArtBlend {
    /** The page itself, blurred. */
    BLURRED,

    /** A color from the page's bottom edge, darkened. */
    SOLID,
    ;

    companion object {
        /** A solid color, as the owner set Now Playing up (2026-10-26). */
        val DEFAULT = SOLID
    }
}

/** How fast full art's pages pan across what their crop hides (Settings > Appearance > Full art pan). */
enum class FullArtPan(val dpPerSecond: Float) {
    OFF(0f),
    SLOW(8f),

    /** As approved on the mockup (2026-10-26). */
    NORMAL(16f),
    FAST(28f),
    ;

    companion object {
        /** Slow, as the owner set Now Playing up (2026-10-26). */
        val DEFAULT = SLOW
    }
}

interface NowPlayingPreference {
    val ambientAnimationEnabled: Flow<Boolean>
    suspend fun setAmbientAnimationEnabled(enabled: Boolean)

    val cycleAlbumArt: Flow<Boolean>
    suspend fun setCycleAlbumArt(enabled: Boolean)

    val fullArt: Flow<Boolean>
    suspend fun setFullArt(on: Boolean)

    val titleAlign: Flow<NpTitleAlign>
    suspend fun setTitleAlign(align: NpTitleAlign)

    val titleButton: Flow<TitleButton>
    suspend fun setTitleButton(button: TitleButton)

    val fullArtBlend: Flow<FullArtBlend>
    suspend fun setFullArtBlend(blend: FullArtBlend)

    val discogsArt: Flow<Boolean>
    val discogsArtOnSince: Flow<Long>
    suspend fun setDiscogsArt(on: Boolean)

    val fullArtZoom: Flow<Float>
    suspend fun setFullArtZoom(zoom: Float)

    val fullArtPan: Flow<FullArtPan>
    suspend fun setFullArtPan(pan: FullArtPan)

    val ambientStyle: Flow<AmbientStyle>
    suspend fun setAmbientStyle(style: AmbientStyle)

    val pinnedActions: Flow<Set<NowPlayingAction>>
    suspend fun setPinnedActions(pins: Set<NowPlayingAction>)
    suspend fun togglePin(action: NowPlayingAction): Boolean
    suspend fun resetPinnedActions()

    companion object {
        const val FULL_ART_ZOOM_MIN = 1f
        const val FULL_ART_ZOOM_MAX = 2f

        /** Close to the whole cover, as the owner set Now Playing up (2026-10-26). */
        const val FULL_ART_ZOOM_DEFAULT = 1.1f
    }
}
