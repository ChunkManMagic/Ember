package com.ember.companion.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream

/**
 * Video thumbnails are extracted once at import time and cached under the app's
 * cache directory. Images are handed to the image loader straight from their
 * content URI, so nothing is copied for them.
 */
object Thumbs {

    private const val DIR = "thumbs"
    private const val MAX_EDGE = 480

    fun fileFor(context: Context, key: String): File =
        File(File(context.cacheDir, DIR), "$key.jpg")

    fun exists(context: Context, key: String): Boolean = fileFor(context, key).exists()

    /** Returns the cache file on success, or null if no frame could be read. */
    fun generateVideoThumb(context: Context, uri: Uri, key: String): File? {
        if (key.isBlank()) return null
        val target = fileFor(context, key)
        if (target.exists() && target.length() > 0) return target
        val dir = target.parentFile ?: return null
        if (!dir.exists()) dir.mkdirs()

        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        return try {
            retriever.setDataSource(context, uri)
            frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (frame == null) {
                null
            } else {
                val scaled = scale(frame)
                FileOutputStream(target).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                if (scaled !== frame) scaled.recycle()
                target
            }
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
            runCatching { frame?.recycle() }
        }
    }

    fun imageUriFor(item: com.ember.companion.data.db.MediaItem): Uri? =
        runCatching { Uri.parse(item.uri) }.getOrNull()

    fun extensionOf(mime: String): String =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"

    private fun scale(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_EDGE) return source
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
