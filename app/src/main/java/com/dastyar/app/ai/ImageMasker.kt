package com.dastyar.app.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/**
 * Builds the image + mask pair a real inpainting model needs.
 *
 * The Cloudflare inpainting model (Stable Diffusion v1.5) only reliably keeps
 * the source picture when the image and the mask are the SAME square size, so
 * a photo is centre-cropped to a square and scaled to 512x512 first. In the
 * mask, white pixels are the region the model may change and black pixels must
 * be reproduced exactly.
 *
 * The region comes from what the user asked for: "only change the sky" must not
 * repaint the ground, and a generic request is allowed to change most of the
 * frame while a thin border still anchors the model to the real photo.
 *
 * Everything here runs on the device; only the single resulting request leaves
 * the phone.
 */
object ImageMasker {

    /** The model was trained/released at 512x512 and behaves best at that size. */
    private const val SIZE = 512

    data class Masked(val image: ByteArray, val mask: ByteArray)

    /**
     * Decodes [source] and produces a square (image, mask) PNG pair for [region].
     * Returns null when the bytes are not a decodable image.
     */
    fun build(source: ByteArray, region: CloudflareClient.EditRegion): Masked? {
        val decoded = try {
            BitmapFactory.decodeByteArray(source, 0, source.size)
        } catch (_: Exception) {
            null
        } ?: return null

        val square = centerCropSquare(decoded)
        val image = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)

        val mask = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        // Start fully white: the whole frame may be changed.
        canvas.drawColor(Color.WHITE)
        val black = Paint().apply { color = Color.BLACK }
        val white = Paint().apply { color = Color.WHITE }

        when (region) {
            // Only the band above the horizon may change; the ground/subject is
            // kept exactly. A landscape subject usually sits at ~55% height, so
            // the sky band is the top 40% -- verified to preserve the houses.
            CloudflareClient.EditRegion.SKY ->
                canvas.drawRect(0f, SIZE * 0.40f, SIZE.toFloat(), SIZE.toFloat(), black)

            // Change the outer frame (background) and keep a generous centre
            // block, so a "change the background" request does not touch the
            // subject in the middle of the shot.
            CloudflareClient.EditRegion.BACKGROUND -> {
                canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), white)
                val m = SIZE * 0.16f
                canvas.drawRect(m, m, SIZE - m, SIZE - m, black)
            }

            // A generic request may change the whole picture. A thin black
            // border is kept so the model still anchors to the real photo
            // instead of drifting into a brand-new scene.
            CloudflareClient.EditRegion.WHOLE -> {
                val b = SIZE * 0.03f
                canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), black)
                canvas.drawRect(b, b, SIZE - b, SIZE - b, white)
            }
        }

        val imgOut = ByteArrayOutputStream()
        val maskOut = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.PNG, 100, imgOut)
        mask.compress(Bitmap.CompressFormat.PNG, 100, maskOut)
        return Masked(imgOut.toByteArray(), maskOut.toByteArray())
    }

    /** Crops to the largest centred square so the model keeps the main subject. */
    private fun centerCropSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        if (src.width == src.height) return src
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        return Bitmap.createBitmap(src, x, y, side, side)
    }
}
