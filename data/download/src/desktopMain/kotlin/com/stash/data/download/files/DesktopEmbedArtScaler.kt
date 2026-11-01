package com.stash.data.download.files

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Desktop implementation of [EmbedArtScaler] using standard JVM ImageIO.
 */
@Singleton
class DesktopEmbedArtScaler @Inject constructor() : EmbedArtScaler {

    override suspend fun fit(bytes: ByteArray, maxPx: Int): EmbedArtScaler.Fitted? {
        return try {
            val img = ImageIO.read(ByteArrayInputStream(bytes))
                ?: return EmbedArtScaler.Fitted(bytes, longSidePx = 0)
            val w = img.width
            val h = img.height
            val longSide = maxOf(w, h)
            if (longSide <= maxPx) {
                return EmbedArtScaler.Fitted(bytes, longSide)
            }
            val scale = maxPx.toDouble() / longSide
            val targetW = (w * scale).roundToInt().coerceAtLeast(1)
            val targetH = (h * scale).roundToInt().coerceAtLeast(1)

            val scaled = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
            val g2d = scaled.createGraphics()
            g2d.drawImage(img.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH), 0, 0, null)
            g2d.dispose()

            val bos = ByteArrayOutputStream()
            ImageIO.write(scaled, "jpg", bos)
            EmbedArtScaler.Fitted(bos.toByteArray(), maxOf(targetW, targetH))
        } catch (e: Exception) {
            EmbedArtScaler.Fitted(bytes, longSidePx = 0)
        }
    }
}

