package com.stash.feature.sync

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

internal object R {
    object string {
        val sync_title = StringRes("Sync")
        val sync_cancel = StringRes("Cancel")
        val sync_stop = StringRes("Stop")
        val sync_ok = StringRes("OK")
        val sync_failed_downloads_title = StringRes("Failed Downloads")
        val sync_auto_sync = StringRes("Auto-sync")
        val sync_auto_sync_off = StringRes("Off")
        val sync_auto_sync_at = StringRes("At %1\$s")
        val sync_auto_sync_at_wifi = StringRes("At %1\$s · Wi-Fi")
        val sync_history_row = StringRes("History, blocked songs, undo")
        val sync_review_sub_both = StringRes("%1\$d unmatched · %2\$d flagged — tap to fix")
        val sync_review_sub_flagged = StringRes("Tap to pick a replacement")
        val sync_review_sub_unmatched = StringRes("Tap to review")
        val sync_button = StringRes("Sync")
        val sync_firstrun_title = StringRes("Bring your music in")
        val sync_firstrun_body = StringRes("Connect an account and sync. Every playlist, mix and liked song shows up on Home and in Library.")
        val sync_connect_spotify = StringRes("Connect Spotify")
        val sync_connect_youtube = StringRes("Connect YouTube Music")
        val sync_connect_soundcloud = StringRes("Connect SoundCloud")
        val sync_connect_lastfm = StringRes("Also use Last.fm for scrobbles")
        val sync_ready_body = StringRes("Tap Sync and every playlist, mix and liked song shows up on Home and in Library.")
        val sync_arrival_sync_now = StringRes("Sync now")
        val sync_home_title = StringRes("Your music is here")
        val sync_home_synced_ago = StringRes("synced %1\$s")
        val sync_play_new = StringRes("Play something new")
        val sync_download_title = StringRes("On this device")
        val sync_download_none = StringRes("Nothing saved yet.")
        val sync_saved_cd = StringRes("Saved")
        val sync_download_choose = StringRes("Choose what to save")
        val sync_download_note = StringRes("Download mode plays your downloads and hides anything that's not downloaded.")
        val sync_syncing_title = StringRes("Bringing in your music")
        val sync_syncing_body = StringRes("Play anything that's landed. You can leave this screen.")
        val sync_downloads_title = StringRes("Downloads")
        val sync_downloads_subtitle = StringRes("View current downloads, queue, and history")
        val sync_downloads_cd = StringRes("Open downloads")
        val sync_blocked_songs_title = StringRes("Blocked Songs")
        val sync_blocked_songs_none = StringRes("No blocked songs")
        val sync_log_title = StringRes("SYNC LOG")
        val sync_log_copy = StringRes("Copy")
        val sync_auto_sync_paused_note = StringRes("Auto-sync paused after Undo. Tap Sync to resume.")
        val sync_time_just_now = StringRes("just now")
        val sync_time_mins_ago = StringRes("%d min ago")
        val sync_time_hours_ago = StringRes("%d hr ago")
        val sync_undo_last_sync_title = StringRes("Undo last sync")
        val sync_undo_last_sync_subtitle = StringRes("Puts back the %1\$s it changed, as they were %2\$s")
        val sync_undo = StringRes("Undo")
        val sync_undoing = StringRes("Undoing…")
        val sync_mix_mode_title = StringRes("When a song leaves a playlist")
        val sync_mode_refresh = StringRes("Refresh")
        val sync_mode_accumulate = StringRes("Accumulate")
        val sync_mode_refresh_desc = StringRes("Removed here too, so your playlists match your account")
        val sync_mode_accumulate_desc = StringRes("Kept here, so nothing you synced disappears")
        val sync_recommended_tracks_title = StringRes("Recommended tracks")
        val sync_lastfm_recommended_enabled_desc = StringRes("A rotating playlist of Last.fm recommendations is kept up to date")
        val sync_lastfm_recommended_disabled_desc = StringRes("Off — no recommendations are generated")
        val sync_studio_only_title = StringRes("Studio recordings only")
        val sync_studio_only_desc = StringRes("Excludes covers, live recordings, and UGC uploads from your Liked Songs. Other YouTube playlists are unaffected.")
        val sync_arrival_title = StringRes("How should your music arrive?")
        val sync_arrival_stream_title = StringRes("Stream everything")
        val sync_arrival_recommended = StringRes("Recommended")
        val sync_arrival_stream_body = StringRes("Surface your library for streaming.")
        val sync_arrival_save_title = StringRes("Save to this device")
        val sync_arrival_save_body = StringRes("Pick the playlists you want to download.")
        val sync_arrival_change_later = StringRes("Change it any time from the top of Sync.")
        val sync_manage_in_library_count = StringRes("%1\$d of %2\$d in your library")
        val sync_home_summary = StringRes("%1\$s · %2\$s · %3\$s")
        val sync_account_summary = StringRes("%1\$s · %2\$s")
    }

    object plurals {
        val sync_tracks_count = PluralRes("%d track", "%d tracks")
        val sync_songs_count = PluralRes("%d song", "%d songs")
        val sync_mixes_count = PluralRes("%d mix", "%d mixes")
        val sync_time_days_ago = PluralRes("%d day ago", "%d days ago")
        val sync_playlists_count = PluralRes("%d playlist", "%d playlists")
        val sync_failed_downloads_count = PluralRes("%d track needs attention", "%d tracks need attention")
        val sync_review_need_review = PluralRes("%d song needs review", "%d songs need review")
        val sync_review_flagged_wrong = PluralRes("%d song flagged as wrong match", "%d songs flagged as wrong match")
        val sync_review_unmatched = PluralRes("%d song couldn't be matched", "%d songs couldn't be matched")
        val sync_blocked_songs_count = PluralRes("%d song will never re-download", "%d songs will never re-download")
    }
}

