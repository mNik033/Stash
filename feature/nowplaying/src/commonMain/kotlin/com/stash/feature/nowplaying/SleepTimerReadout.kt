package com.stash.feature.nowplaying

import java.util.Locale

/*
 * The sleep timer's live readout: the short caption under the moon ("23m", "0:45"), the sheet's
 * clock ("22:41"), what TalkBack says, and when each next changes, so the screen redraws exactly
 * then instead of polling. Pure, so it is pinned by JVM tests. Every value rounds UP, like a
 * countdown: with 22:01 left the caption says 23m, and it never reads 0 while music still plays.
 */

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60_000L

/** Whole units of [unitMs] in [ms], rounded up (0 for nothing left). */
private fun ceilDiv(ms: Long, unitMs: Long): Long = if (ms <= 0L) 0L else (ms + unitMs - 1) / unitMs

/**
 * The caption under the moon icon for [remainingMs] left: minutes ("23m", "1h 5m", "3h") until the
 * final minute, then a seconds clock ("1:00", "0:45").
 */
internal fun sleepTimerBadge(remainingMs: Long): String {
    if (remainingMs <= MINUTE_MS) {
        val seconds = ceilDiv(remainingMs, SECOND_MS)
        // A full minute reads "1:00", not "0:60".
        return if (seconds == 60L) "1:00" else String.format(Locale.US, "0:%02d", seconds)
    }
    val minutes = ceilDiv(remainingMs, MINUTE_MS)
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "${minutes}m"
        rest == 0L -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}

/** The sheet's live clock for [remainingMs] left: "22:41", "1:02:03", "0:45". */
internal fun sleepTimerClock(remainingMs: Long): String {
    val seconds = ceilDiv(remainingMs, SECOND_MS)
    val h = seconds / 3_600
    val m = (seconds % 3_600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

private fun plural(n: Long, unit: String): String = if (n == 1L) "1 $unit" else "$n ${unit}s"

/**
 * What TalkBack says for [remainingMs] left. Like the caption, minutes until the final minute
 * ("23 minutes", "1 hour 5 minutes"), then seconds ("45 seconds"); [withSeconds] (the sheet's
 * clock) says every part ("22 minutes 41 seconds"). Never the "22:41" a screen reader would read
 * as a time of day.
 */
internal fun sleepTimerSpoken(remainingMs: Long, withSeconds: Boolean = false): String {
    if (!withSeconds && remainingMs > MINUTE_MS) {
        val minutes = ceilDiv(remainingMs, MINUTE_MS)
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0L -> plural(minutes, "minute")
            rest == 0L -> plural(hours, "hour")
            else -> "${plural(hours, "hour")} ${plural(rest, "minute")}"
        }
    }
    val seconds = ceilDiv(remainingMs, SECOND_MS)
    val parts = listOfNotNull(
        (seconds / 3_600).takeIf { it > 0 }?.let { plural(it, "hour") },
        ((seconds % 3_600) / 60).takeIf { it > 0 }?.let { plural(it, "minute") },
        (seconds % 60).takeIf { it > 0 || seconds == 0L }?.let { plural(it, "second") },
    )
    return parts.joinToString(" ")
}

/**
 * How long until the readout for [remainingMs] changes: the next whole minute boundary for the
 * caption above the final minute, the next second inside it, or always the next second with
 * [everySecond] (the sheet's clock). Null once nothing is left: the timer is fading out and is
 * about to report Off, so there is nothing more to tick.
 */
internal fun msUntilSleepReadoutChanges(remainingMs: Long, everySecond: Boolean = false): Long? {
    if (remainingMs <= 0L) return null
    val unit = if (everySecond || remainingMs <= MINUTE_MS) SECOND_MS else MINUTE_MS
    // The readout shows ceil(remaining / unit); it next changes when remaining reaches the
    // multiple of unit below that.
    return remainingMs - (ceilDiv(remainingMs, unit) - 1) * unit
}
