package io.github.drhydr0.layers.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.Document
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Small previews of a whole document through the shared [DocumentRenderer]. Keeps its
 * own small image cache, so repeated thumbnails of one project don't re-decode photos.
 * Use from a single background thread.
 */
class ThumbnailRenderer(private val fileFor: (AssetId) -> File, private val maxSide: Int = DEFAULT_MAX_SIDE) {

    private val renderer = DocumentRenderer(TextLayouts(maxEntries = 64))
    private val images = object : LruCache<AssetId, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: AssetId, value: Bitmap) = value.allocationByteCount
    }

    fun writePng(document: Document, out: OutputStream) {
        val bitmap = render(document)
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        } finally {
            bitmap.recycle()
        }
    }

    private fun render(document: Document): Bitmap {
        val c = document.canvas
        val scale = maxSide.toDouble() / max(c.width, c.height)
        val w = (c.width * scale).roundToInt().coerceAtLeast(1)
        val h = (c.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(w.toFloat() / c.width, h.toFloat() / c.height)
        renderer.draw(canvas, document, ::imageFor, clipToCanvas = true)
        return bitmap
    }

    private fun imageFor(id: AssetId): Bitmap? = images.get(id) ?: runCatching {
        // Software bitmaps: hardware ones can't be drawn into a Bitmap-backed canvas.
        PreviewImages.decodeBounded(fileFor(id), maxSide, software = true)
    }.getOrNull()?.also { images.put(id, it) }

    companion object {
        const val DEFAULT_MAX_SIDE = 384
        private const val CACHE_BYTES = 16 * 1024 * 1024
        private const val PNG_QUALITY = 100
    }
}
