package com.stash.feature.nowplaying

import androidx.compose.runtime.Composable

internal class StringRes(val value: String) {
    fun format(vararg args: Any): String = runCatching { value.format(*args) }.getOrDefault(value)
}

internal class PluralRes(val one: String, val other: String) {
    fun format(count: Int, vararg args: Any): String {
        val pattern = if (count == 1) one else other
        return runCatching { pattern.format(*args) }.getOrDefault(pattern)
    }
}

@Composable
internal fun stringResource(id: StringRes): String = id.value

@Composable
internal fun stringResource(id: StringRes, vararg args: Any): String = id.format(*args)

@Composable
internal fun pluralStringResource(id: PluralRes, count: Int, vararg args: Any): String = id.format(count, *args)

internal object NowPlayingR {
    object string {
        val play_on_title = StringRes("Play on")
        val play_on_button = StringRes("Play on")
        val play_on_button_playing = StringRes("Playing on %s")
        val play_on_this_phone = StringRes("This phone")
        val play_on_browsers = StringRes("Browsers")
        val play_on_browser_unavailable = StringRes("Browser unavailable")
        val play_on_browser_volume = StringRes("Browser volume")
        val play_on_browser_note = StringRes("Volume is controlled in this browser")
        val play_on_speakers = StringRes("Speakers")
        val play_on_browser_ready = StringRes("Ready")
        val play_on_browser_playing = StringRes("Playing %s")
        val play_on_browser_needs_click = StringRes("Click the Stash tab once first")
        val play_on_browser_reload = StringRes("Reload the Stash tab to play there")
        val play_on_browser_not_open = StringRes("Open play.stashfm.app to play there")
        val play_on_starting_on = StringRes("Starting on %s…")
        val play_on_click_tab = StringRes("Click the Stash tab once in %s")
        val play_on_reconnecting = StringRes("Reconnecting to %s…")
        val play_on_playing_on = StringRes("Playing on %s")
        val play_on_connecting_to = StringRes("Connecting to %s…")
        val lyrics_credit_uploaded_by = StringRes("Uploaded by %s")
        val lyrics_credit_made_by = StringRes("Made by %s")
        val play_on_queue_phone_only = StringRes("Plays on this phone only")
        val play_on_queue_play_here = StringRes("Play here")
        val play_on_queue_phone_only_tap = StringRes("'%s' is only on this phone")
        val np_hidden_casting = StringRes("Not available while casting")
        val np_hidden_download_mode = StringRes("Only in Online mode")
        val np_hidden_preview = StringRes("Not available for preview tracks")
        val np_hidden_soundcloud = StringRes("Not available for SoundCloud tracks")
        val np_hidden_together = StringRes("Not available in Listen Together")
        val np_hidden_scanned = StringRes("Not available for local files")
        val np_hidden_cloud = StringRes("Not available for cloud files")
        val np_hidden_no_cast = StringRes("No cast devices available")
        val np_hidden_other = StringRes("Not available")
        val np_hidden_overflow = StringRes("More")
    }

    object plurals {
        val play_on_notice_skipped = PluralRes("Skipped %d song only on this phone", "Skipped %d songs only on this phone")
    }
}

