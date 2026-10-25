package com.stash.feature.sync

/**
 * Progress of the approved Failed Matches downloads, for the "Downloading 12
 * approved songs" banner. Approving hides a row at once (the download then
 * runs in the background, two at a time), so without this the list just
 * emptied and nothing said the songs were on their way.
 *
 * One "run" lasts while any approval is still waiting or downloading; the
 * first approval after a run has finished starts a new one, so the counts
 * describe what the listener just did rather than everything since launch.
 *
 * @property waiting  Approved, not finished yet (downloading or waiting for a slot).
 * @property done     Finished in this run and downloaded.
 * @property failed   Finished in this run without a file (back in the list).
 */
data class ApprovedDownloads(
    val waiting: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
) {
    val total: Int get() = waiting + done + failed
    val active: Boolean get() = waiting > 0

    /** Finished share of this run, for a determinate progress bar. */
    val fraction: Float get() = if (total == 0) 0f else (done + failed).toFloat() / total

    /** One more approval joins the run; one after a finished run starts a new run. */
    fun started(): ApprovedDownloads =
        if (active) copy(waiting = waiting + 1) else ApprovedDownloads(waiting = 1)

    /** An approval finished, [downloaded] or not. */
    fun finished(downloaded: Boolean): ApprovedDownloads = copy(
        waiting = (waiting - 1).coerceAtLeast(0),
        done = done + if (downloaded) 1 else 0,
        failed = failed + if (downloaded) 0 else 1,
    )
}

private fun songs(n: Int) = if (n == 1) "song" else "songs"

/** "Downloading 12 approved songs" while a run is going; null otherwise. */
internal fun approvedDownloadsTitle(d: ApprovedDownloads): String? =
    if (d.active) "Downloading ${d.total} approved ${songs(d.total)}" else null

/** "3 of 12 done", plus how many didn't download (they are back in the list). */
internal fun approvedDownloadsDetail(d: ApprovedDownloads): String {
    val finished = d.done + d.failed
    val base = "$finished of ${d.total} done"
    return if (d.failed > 0) "$base · ${d.failed} didn't download" else base
}

/**
 * The snackbar when a run ends with songs downloaded ("Downloaded 12 approved
 * songs."); null when none did, since each failure already said so.
 */
internal fun approvedDownloadsFinishedMessage(d: ApprovedDownloads): String? =
    if (!d.active && d.done > 0) "Downloaded ${d.done} approved ${songs(d.done)}." else null
