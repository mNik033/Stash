package com.stash.feature.sync

import android.text.format.DateUtils

internal actual fun syncedAgoText(finishedAt: Long?, now: Long, justNow: String): String = when {
    finishedAt == null -> ""
    now - finishedAt < 60_000L -> justNow
    else -> DateUtils.getRelativeTimeSpanString(finishedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

