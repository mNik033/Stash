package com.stash.core.ui.selection

/**
 * Whether a batch "Remove download" of [count] songs must be confirmed first. One song is the
 * same one-tap action the track sheet offers; more than one (up to a whole "Select all") deletes
 * files that can take hours to fetch again, and sat one mis-tap away from Delete, which was
 * already confirmed.
 */
fun removeDownloadsNeedsConfirm(count: Int): Boolean = count > 1

/** Title of the shared batch "Remove download" confirmation, e.g. "Remove 12 downloads?". */
fun removeDownloadsTitle(count: Int): String =
    if (count == 1) "Remove 1 download?" else "Remove $count downloads?"

/**
 * Body of the batch "Remove download" confirmation. Names the space freed when the rows know
 * their file sizes ([totalBytes] > 0), and says what the user keeps: the songs stay in their
 * playlists and can still stream, they just stop playing offline.
 */
fun removeDownloadsBody(totalBytes: Long): String {
    val files = if (totalBytes > 0) "Their audio files (${formatDownloadBytes(totalBytes)})" else "Their audio files"
    return "$files will be deleted from this device. The songs stay in your playlists and can " +
        "still stream, but won't play offline until you download them again."
}

/** "850.0 MB", "1.2 GB": one decimal over B/KB/MB/GB/TB by powers of 1024. */
internal fun formatDownloadBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}
