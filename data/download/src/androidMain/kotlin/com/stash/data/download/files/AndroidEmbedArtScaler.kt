package com.stash.data.download.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
open class AndroidEmbedArtScaler @Inject constructor() : EmbedArtScaler {

    private val decodeLock = Mutex()

    override suspend fun fit(bytes: ByteArray, maxPx: Int): EmbedArtScaler.Fitted? {
        val (w, h) = readBounds(bytes)
        if (w <= 0 || h <= 0) return EmbedArtScaler.Fitted(bytes, longSidePx = 0)
        val longSide = maxOf(w, h)
        if (longSide <= maxPx) return EmbedArtScaler.Fitted(bytes, longSide)

        return decodeLock.withLock {
            var decoded: Bitmap? = null
            var scaled: Bitmap? = null
            try {
                decoded = decodeSampled(bytes, sampleSizeFor(longSide, maxPx))
                    ?: return@withLock EmbedArtScaler.Fitted(bytes, longSidePx = 0)
                val scale = maxPx.toDouble() / maxOf(decoded.width, decoded.height)
                scaled = if (scale < 1.0) {
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * scale).roundToInt().coerceAtLeast(1),
                        (decoded.height * scale).roundToInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    decoded
                }
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                EmbedArtScaler.Fitted(out.toByteArray(), maxOf(scaled.width, scaled.height))
            } catch (oom: OutOfMemoryError) {
                null
            } finally {
                if (scaled != null && scaled !== decoded) scaled.recycle()
                decoded?.recycle()
            }
        }
    }

    protected open fun readBounds(bytes: ByteArray): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    protected open fun decodeSampled(bytes: ByteArray, sampleSize: Int): Bitmap? =
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )

    companion object {
        private const val JPEG_QUALITY = 90

        internal fun sampleSizeFor(longSide: Int, maxPx: Int): Int {
            var sample = 1
            while (longSide / (sample * 2) >= maxPx) sample *= 2
            return sample
        }
    }
}

