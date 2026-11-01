package com.stash.data.download.files

/**
 * Result of a successful [FileOrganizer.commitDownload].
 *
 * @property filePath Absolute file path or content URI where the track was saved.
 * @property sizeBytes Size of the committed file in bytes.
 */
data class CommittedTrack(
    val filePath: String,
    val sizeBytes: Long,
)

