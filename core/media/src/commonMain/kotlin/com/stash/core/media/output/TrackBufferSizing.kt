package com.stash.core.media.output

/**
 * How big each AudioTrack's buffer is, for the speed it plays at.
 *
 * Speed is AudioFlinger's time-stretch on the AudioTrack (Media3's Sonic sits
 * in the processor chain the float path skips), which reads `speed` frames of
 * the buffer for every frame it plays. Media3 1.9 therefore sizes such a track
 * for its top speed: the 1x size (4 × `AudioTrack.getMinBufferSize`, held to
 * 250–750 ms) times 8 (`DefaultAudioTrackBufferSizeProvider`). Stash's EQ,
 * bass, loudness and limiter run *before* that buffer, so a slider move, or
 * the next song's own loudness gain after a gapless change, is only heard once
 * the buffer has played out. The old cap at 2x still doubled the 1x buffer to
 * 500–1500 ms.
 *
 * So the buffer follows the speed in two steps:
 * - **Up to 2x: the 1x size, 250–750 ms**, as before speed moved to the
 *   AudioTrack. At 2x it still lasts 125–375 ms of real time, and its frames
 *   (at least 4 × the AudioTrack minimum, short of the 750 ms ceiling) are
 *   twice what AudioFlinger demands at 2x (≈ speed × the minimum). Below that
 *   demand `setPlaybackParams` is refused and the speed silently doesn't change.
 * - **Above 2x, up to Stash's 4x: twice that, 500–1500 ms**, the size the 2x
 *   cap gave every track, which is what was verified on a device at 4x. At 4x
 *   it lasts the same 125–375 ms of real time.
 *
 * A speed change across 2x rebuilds the track at the other size
 * ([StashAudioOutput.setPlaybackParameters]). Listen Together's ±3% drift
 * nudges and everyday 0.5–2x never do.
 */
internal object TrackBufferSizing {

    /** The fastest speed a track at the 1x size serves. */
    const val ONE_X_MAX_SPEED = 2f

    /** 1 (Media3's 1x size) up to 2x; 2 (double) above. */
    fun tierFor(speed: Float): Int = if (speed > ONE_X_MAX_SPEED) 2 else 1
}
