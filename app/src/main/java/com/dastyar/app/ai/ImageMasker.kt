package com.dastyar.app.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Builds the mask a real inpainting model needs: white pixels are the region
 * the model is allowed to change, black pixels must be kept exactly as they
 * are. The region is chosen from what the user asked for, so a request like
 * "only change the sky" does not repaint the whole picture.
 *
 * Everything here is local (Android's own codecs); no image ever leaves the
 * phone except the single inpainting request.
 */
object ImageMasker {

    /** Sensible working size: inpainting is 512-based and small images upload fast. */
    private const val MAX_SIDE = 512

    data class Masked(val image: ByteArray, val mask: ByteArray)

    /**
     * Decodes [source] and produces a (image, mask) PNG pair for [region].
     * Returns null when the bytes are not a decodable image.
     */
    fun build(source: ByteArray, region: CloudflareClient.EditRegion): Masked? {
        val decoded = try {
            BitmapFactory.decodeByteArray(source, 0, source.size)
        } catch (_: Exception) {
            null
        } ?: return null

        val scaled = scale(decoded)
        val w = scaled.width
        val h = scaled.height

        // The mask starts fully white (change allowed). For a partial region we
        // paint black over the parts that must stay untouched, with a soft edge
        // so the seam is not a hard line.
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(mask)
        canvas.drawColor(android.graphics.Color.WHITE)
        val black = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }

        val keepTop = when (region) {
            // A landscape's subject usually sits near the horizon, so only the
            // band above it is allowed to change. Anything lower would repaint
            // the houses/ground the user asked to keep.
            CloudflareClient.EditRegion.SKY -> (h * 0.42f).toInt()
            CloudflareClient.EditRegion.BACKGROUND -> h             // keep the middle; change borders
            CloudflareClient.EditRegion.WHOLE -> 0
        }
        if (keepTop > 0) canvas.drawRect(0f, keepTop.toFloat(), w.toFloat(), h.toFloat(), black)
        if (region == CloudflareClient.EditRegion.BACKGROUND) {
            // Change the outer frame (background), keep a generous centre block.
            val bx = (w * 0.18f).toInt()
            val by = (h * 0.18f).toInt()
            canvas.drawColor(android.graphics.Color.BLACK)
            val white = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
            canvas.drawRect(
                (w * 0.06f), (h * 0.06f), (w * 0.94f), (h * 0.94f), white
            )
            // The centre block is repainted black so the subject is preserved.
            canvas.drawRect(bx.toFloat(), by.toFloat(), (w - bx).toFloat(), (h - by).toFloat(), black)
        }

        val imgOut = ByteArrayOutputStream()
        val maskOut = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, imgOut)
        mask.compress(Bitmap.CompressFormat.PNG, 100, maskOut)
        return Masked(imgOut.toByteArray(), maskOut.toByteArray())
    }

    private fun scale(src: Bitmap): Bitmap {
        val max = maxOf(src.width, src.height)
        if (max <= MAX_SIDE) return src
        val ratio = MAX_SIDE.toFloat() / max
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
