package com.stash.core.data.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

    private val cycleAlbumArtKey = booleanPreferencesKey("cycle_album_art")

    /**
     * Settings > Appearance > Cycle album art: Now Playing's art turns slowly through the album's
     * back and inside pages, when it has them. On unless turned off.
     */
    override val cycleAlbumArt = context.nowPlayingDataStore.data.map { it[cycleAlbumArtKey] ?: true }

    override suspend fun setCycleAlbumArt(enabled: Boolean) {
        context.nowPlayingDataStore.edit { it[cycleAlbumArtKey] = enabled }
    }

    private val fullArtKey = booleanPreferencesKey("full_art")

    /**
     * Now Playing's full art layout: the art across the whole width and down behind the controls,
     * the album's pages taking turns as the song plays (the toggle in its top row). Kept, so Now
     * Playing opens as it was left. On unless turned off: the layout the owner set Now Playing up
     * with (2026-10-26).
     */
    override val fullArt = context.nowPlayingDataStore.data.map { it[fullArtKey] ?: true }

    override suspend fun setFullArt(on: Boolean) {
        context.nowPlayingDataStore.edit { it[fullArtKey] = on }
    }

    private val titleAlignKey = stringPreferencesKey("title_align")

    /** Unset or unknown reads as [NpTitleAlign.DEFAULT]. */
    override val titleAlign = context.nowPlayingDataStore.data.map { prefs ->
        prefs[titleAlignKey]?.let { name -> NpTitleAlign.entries.firstOrNull { it.name == name } } ?: NpTitleAlign.DEFAULT
    }

    override suspend fun setTitleAlign(align: NpTitleAlign) {
        context.nowPlayingDataStore.edit { it[titleAlignKey] = align.name }
    }

    private val titleButtonKey = stringPreferencesKey("title_button")

    /** Unset or unknown reads as [TitleButton.SLEEP_TIMER], the button Now Playing always had. */
    override val titleButton = context.nowPlayingDataStore.data.map { prefs ->
        prefs[titleButtonKey]?.let { name -> TitleButton.entries.firstOrNull { it.name == name } } ?: TitleButton.SLEEP_TIMER
    }

    override suspend fun setTitleButton(button: TitleButton) {
        context.nowPlayingDataStore.edit { it[titleButtonKey] = button.name }
    }

    private val fullArtBlendKey = stringPreferencesKey("full_art_blend")

    /** Unset or unknown reads as [FullArtBlend.DEFAULT]. */
    override val fullArtBlend = context.nowPlayingDataStore.data.map { prefs ->
        prefs[fullArtBlendKey]?.let { name -> FullArtBlend.entries.firstOrNull { it.name == name } } ?: FullArtBlend.DEFAULT
    }

    override suspend fun setFullArtBlend(blend: FullArtBlend) {
        context.nowPlayingDataStore.edit { it[fullArtBlendKey] = blend.name }
    }

    private val discogsArtKey = booleanPreferencesKey("discogs_art")
    private val discogsArtOnSinceKey = longPreferencesKey("discogs_art_on_since")

    /**
     * Settings > Appearance > Discogs art: Discogs fills in the back covers and inside pages the
     * Cover Art Archive lacks. Its images are its users' photos and scans, some of them poor, so
     * it can be turned off for the archive's alone. On unless turned off.
     */
    override val discogsArt = context.nowPlayingDataStore.data.map { it[discogsArtKey] ?: true }

    /** When Discogs art was last turned on (0: never turned off): answers from before are asked again. */
    override val discogsArtOnSince = context.nowPlayingDataStore.data.map { it[discogsArtOnSinceKey] ?: 0L }

    override suspend fun setDiscogsArt(on: Boolean) {
        context.nowPlayingDataStore.edit {
            it[discogsArtKey] = on
            if (on) it[discogsArtOnSinceKey] = System.currentTimeMillis()
        }
    }

    private val fullArtZoomKey = floatPreferencesKey("full_art_zoom")

    /**
     * How close full art shows the album (Settings > Appearance > Full art zoom): a square cover is
     * this many screen widths across. 1.0 fits the whole cover across the screen; more crops it,
     * and the pan shows what the crop hides.
     */
    override val fullArtZoom = context.nowPlayingDataStore.data.map {
        (it[fullArtZoomKey] ?: FULL_ART_ZOOM_DEFAULT).coerceIn(FULL_ART_ZOOM_MIN, FULL_ART_ZOOM_MAX)
    }

    override suspend fun setFullArtZoom(zoom: Float) {
        context.nowPlayingDataStore.edit { it[fullArtZoomKey] = zoom.coerceIn(FULL_ART_ZOOM_MIN, FULL_ART_ZOOM_MAX) }
    }

    private val fullArtPanKey = stringPreferencesKey("full_art_pan")

    /** Unset or unknown reads as [FullArtPan.DEFAULT]. */
    override val fullArtPan = context.nowPlayingDataStore.data.map { prefs ->
        prefs[fullArtPanKey]?.let { name -> FullArtPan.entries.firstOrNull { it.name == name } } ?: FullArtPan.DEFAULT
    }

    override suspend fun setFullArtPan(pan: FullArtPan) {
        context.nowPlayingDataStore.edit { it[fullArtPanKey] = pan.name }
    }

    companion object {
        const val FULL_ART_ZOOM_MIN = 1f
        const val FULL_ART_ZOOM_MAX = 2f

        /** The whole cover, no crop: the owner's pick after living with 1.1 (2026-10-31). */
        const val FULL_ART_ZOOM_DEFAULT = 1f
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
