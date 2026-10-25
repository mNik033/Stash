package com.stash.data.lyrics.sidecar

import com.stash.core.data.db.entity.LyricsEntity
import java.io.IOException

/**
 * Sidecar writer contract for exporting lyrics files (.lrc / .ttml) alongside audio downloads.
 */
interface LyricsSidecarWriter {

    /** The track has no audio file to sit beside (it streams): [write] has nothing to do. */
    class NoDownloadedFileException(trackId: Long) : IOException("Track $trackId has no downloaded file")

    /**
     * Writes the sidecar(s) for [trackId] using [lyrics].
     */
    suspend fun write(trackId: Long, lyrics: LyricsEntity)

    /**
     * Explicit "Save with song file" action: always writes the plain `.lrc`, regardless of whether
     * `.ttml` also exists or whether this is a new/existing track.
     */
    suspend fun writeLrcSidecar(trackId: Long, lyrics: LyricsEntity)

    /** Deletes just the `.ttml` sidecar — used when the user switches to LRC-only. Best-effort/silent. */
    suspend fun deleteTtmlSidecar(trackId: Long)
}
