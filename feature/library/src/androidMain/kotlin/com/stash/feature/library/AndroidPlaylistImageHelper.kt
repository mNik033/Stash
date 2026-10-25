package com.stash.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPlaylistImageHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaylistImageHelper {

    override fun savePlaylistCoverImage(playlistId: Long, imageUri: String): String? {
        val uri = runCatching { Uri.parse(imageUri) }.getOrNull() ?: return null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return null

            val size = minOf(original.width, original.height)
            val x = (original.width - size) / 2
            val y = (original.height - size) / 2
            val cropped = Bitmap.createBitmap(original, x, y, size, size)

            val scaled = Bitmap.createScaledBitmap(cropped, 512, 512, true)
            if (cropped !== original) original.recycle()
            if (scaled !== cropped) cropped.recycle()

            val dir = File(context.filesDir, "playlist_covers")
            dir.mkdirs()
            val file = File(dir, "$playlistId.jpg")
            file.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            scaled.recycle()

            "file://${file.absolutePath}?v=${System.currentTimeMillis()}"
        } catch (e: Exception) {
            null
        }
    }

    override fun deletePlaylistCoverFile(playlistId: Long) {
        val file = File(context.filesDir, "playlist_covers/$playlistId.jpg")
        file.delete()
    }
}
