package com.stash.feature.sync

internal actual fun syncedAgoText(finishedAt: Long?, now: Long, justNow: String): String {
    if (finishedAt == null) return ""
    val diff = now - finishedAt
    val minutes = diff / 60_000L
    return when {
        minutes < 1 -> justNow
        minutes == 1L -> "1 minute ago"
        minutes < 60 -> "$minutes minutes ago"
        minutes < 120 -> "1 hour ago"
        minutes < 1440 -> "${minutes / 60} hours ago"
        else -> "${minutes / 1440} days ago"
    }
}

