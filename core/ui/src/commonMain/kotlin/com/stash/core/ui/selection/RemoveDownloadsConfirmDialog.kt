package com.stash.core.ui.selection

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The one confirmation every multi-select list (Library Songs/Liked, album, artist, playlist and
 * Liked detail) shows before a batch "Remove download" (see [removeDownloadsNeedsConfirm]).
 * Shared so the copy, and the guard itself, can't drift between the five screens.
 *
 * @param count Songs whose downloads would be removed.
 * @param totalBytes Their combined file size; 0 when unknown (the size is then left out).
 */
@Composable
fun RemoveDownloadsConfirmDialog(
    count: Int,
    totalBytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(removeDownloadsTitle(count)) },
        text = { Text(removeDownloadsBody(totalBytes)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
