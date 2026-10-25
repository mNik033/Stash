package com.stash.data.lyrics.sidecar

import com.stash.core.data.db.entity.LyricsEntity
import com.stash.core.data.db.entity.TrackEntity

object LyricsSidecarFormatting {
    fun buildLrcBody(track: TrackEntity, lyrics: LyricsEntity): String = buildString {
        appendLine("[ti:${track.title}]")
        appendLine("[ar:${track.albumArtist.ifBlank { track.artist }}]")
        if (track.album.isNotBlank()) appendLine("[al:${track.album}]")
        if (track.durationMs > 0) {
            val sec = (track.durationMs / 1000).toInt()
            appendLine("[length:${sec / 60}:%02d]".format(sec % 60))
        }
        appendLine("[by:Stash]")
        append(lyrics.syncedLrc?.takeUnless(String::isBlank) ?: lyrics.plainText.orEmpty())
    }
}
