package com.dastyar.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Small helper for the two places an image can live:
 *
 *  - the app's own storage, under `filesDir/images` — this is what "تصاویر من"
 *    shows and what makes the app read a picture back instead of regenerating
 *    it (so no API request is ever spent just to display an old image);
 *  - the shared gallery, via MediaStore — this is what "ذخیره در گالری" writes.
 *
 * Nothing here talks to a provider; it only moves bytes around on the device.
 */
object GallerySaver {

    private fun imagesDir(ctx: Context): File =
        File(ctx.filesDir, "images").apply { if (!exists()) mkdirs() }

    /** Writes bytes into the app's private image store and returns the name. */
    fun saveToApp(ctx: Context, bytes: ByteArray, name: String = "img_${System.currentTimeMillis()}.png"): String {
        val f = File(imagesDir(ctx), name)
        f.writeBytes(bytes)
        return f.name
    }

    /** Reads a previously saved app image, or null when the file is gone. */
    fun readFromApp(ctx: Context, name: String): ByteArray? {
        val f = File(imagesDir(ctx), name)
        return if (f.exists()) f.readBytes() else null
    }

    /** Deletes an app image; the gallery copy, if any, is left untouched. */
    fun deleteFromApp(ctx: Context, name: String) {
        val f = File(imagesDir(ctx), name)
        if (f.exists()) f.delete()
    }

    /**
     * Writes bytes to the shared gallery. Returns null on success, or a
     * human-readable Persian error to show the user.
     *
     * On Android 10+ this needs no permission; the caller is responsible for
     * obtaining WRITE_EXTERNAL_STORAGE first on Android 9 and below.
     */
    fun saveToGallery(ctx: Context, bytes: ByteArray, displayName: String): String? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Dastyar"
                    )
                }
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return "ذخیره در گالری ناموفق بود."
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: return "ذخیره در گالری ناموفق بود."
            null
        } catch (e: Exception) {
            "ذخیره در گالری ناموفق بود: ${e.message ?: "خطای ناشناخته"}"
        }
    }
}
