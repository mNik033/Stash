package com.stash.data.download.files

import android.database.Cursor
import android.database.MatrixCursor
import android.provider.DocumentsContract.Document
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The SD-card / SAF library size walk ([FileOrganizer.sumDocumentTree]): one children query per
 * folder, never a query per file, and the same totals DocumentFile gave.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SafLibrarySizeWalkTest {

    private data class Child(val id: String, val mime: String?, val size: Long?, val name: String?)

    private fun dir(id: String) = Child(id, Document.MIME_TYPE_DIR, null, id)

    /** A provider over [tree] (folder id → its children) that records every query it answers. */
    private class FakeProvider(private val tree: Map<String, List<Child>>, private val columns: Array<String>) {
        val queried = mutableListOf<String>()

        fun children(folderId: String): Cursor? {
            queried += folderId
            val kids = tree[folderId] ?: return null
            return MatrixCursor(columns).apply {
                for (k in kids) {
                    addRow(columns.map { col ->
                        when (col) {
                            Document.COLUMN_DOCUMENT_ID -> k.id
                            Document.COLUMN_MIME_TYPE -> k.mime
                            Document.COLUMN_SIZE -> k.size
                            Document.COLUMN_DISPLAY_NAME -> k.name
                            else -> null
                        }
                    })
                }
            }
        }
    }

    private val projection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_DISPLAY_NAME,
    )

    /** Artist/Album, like a real library: two artists, three albums. */
    private val library = mapOf(
        "root" to listOf(dir("A"), dir("B"), Child("cover.jpg", "image/jpeg", 10, "cover.jpg")),
        "A" to listOf(dir("A/One"), dir("A/Two")),
        "A/One" to listOf(
            Child("a1", "audio/flac", 300, "01 Song.FLAC"),
            Child("a2", "audio/mpeg", 50, "02 Song.mp3"),
        ),
        "A/Two" to listOf(Child("a3", "audio/ogg", 40, "01 Song.opus")),
        "B" to listOf(dir("B/Three")),
        "B/Three" to listOf(
            Child("b1", "audio/wav", 1_000, "01 Song.wav"),
            // A provider that doesn't know a file's size: counted, adds nothing.
            Child("b2", "audio/flac", null, "02 Song.flac"),
            // No MIME type: DocumentFile called this neither a file nor a folder.
            Child("b3", null, 999, "mystery.flac"),
        ),
    )

    @Test fun `sums every file the way DocumentFile did`() {
        val provider = FakeProvider(library, projection)

        val size = FileOrganizer.sumDocumentTree("root", provider::children)

        // 10 + 300 + 50 + 40 + 1000 + 0; mystery.flac (no MIME type) is not a file.
        assertThat(size.totalBytes).isEqualTo(1_400L)
        // FLAC (any case) and WAV: 300 + 1000 + 0.
        assertThat(size.losslessBytes).isEqualTo(1_300L)
        assertThat(size.losslessFileCount).isEqualTo(3)
    }

    @Test fun `asks the provider once per folder, never per file`() {
        val provider = FakeProvider(library, projection)

        FileOrganizer.sumDocumentTree("root", provider::children)

        // Six folders, nine files: six queries.
        assertThat(provider.queried).containsExactly("root", "A", "A/One", "A/Two", "B", "B/Three")
    }

    @Test fun `reads columns by name, in whatever order the provider returns them`() {
        val provider = FakeProvider(library, projection.reversedArray())

        assertThat(FileOrganizer.sumDocumentTree("root", provider::children).totalBytes).isEqualTo(1_400L)
    }

    @Test fun `a folder the provider can't list counts as empty`() {
        // "B" answers null (the card went away mid-walk): A's files still count.
        val provider = FakeProvider(library - "B", projection)

        val size = FileOrganizer.sumDocumentTree("root", provider::children)

        assertThat(size.totalBytes).isEqualTo(400L)
    }

    @Test fun `a folder listed under its own child is walked once`() {
        val looped = mapOf(
            "root" to listOf(dir("A")),
            "A" to listOf(dir("root"), Child("a1", "audio/flac", 7, "a.flac")),
        )
        val provider = FakeProvider(looped, projection)

        val size = FileOrganizer.sumDocumentTree("root", provider::children)

        assertThat(size.totalBytes).isEqualTo(7L)
        assertThat(provider.queried).containsExactly("root", "A")
    }
}
