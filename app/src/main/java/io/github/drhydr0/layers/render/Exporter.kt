package io.github.drhydr0.layers.render

import android.graphics.Bitmap
import android.graphics.Canvas
import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.Color
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.ImageLayer
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CancellationException

/**
 * Renders a document snapshot to an image file through the shared [DocumentRenderer],
 * so exports match the editor. Blocking: run on a background thread, one export per instance.
 */
class Exporter(private val fileFor: (AssetId) -> File) {

    @Volatile
    var cancelled = false

    fun export(document: Document, options: ExportOptions, out: OutputStream) {
        val bitmap = render(document, options)
        try {
            checkCancelled()
            val format = when (options.format) {
                ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
            }
            if (!bitmap.compress(format, options.jpegQuality, out)) throw java.io.IOException("Encoding failed")
        } finally {
            bitmap.recycle()
        }
    }

    private fun render(document: Document, options: ExportOptions): Bitmap {
        val doc = if (options.format == ExportFormat.PNG && options.transparentBackground) {
            document.copy(canvas = document.canvas.copy(background = Color.TRANSPARENT))
        } else {
            document
        }
        val (w, h) = ExportPlanner.outputSize(doc.canvas, options.scale)
        val images = decodeImages(doc, options.scale)
        try {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // JPEG has no alpha: transparent areas would turn black, so start from white.
            if (options.format == ExportFormat.JPEG) canvas.drawColor(Color.WHITE.argb)
            canvas.scale(w.toFloat() / doc.canvas.width, h.toFloat() / doc.canvas.height)
            DocumentRenderer(TextLayouts(maxEntries = 64)).draw(canvas, doc, { images[it] }, clipToCanvas = true)
            return bitmap
        } finally {
            images.values.forEach(Bitmap::recycle)
        }
    }

    /** Each image once, at the largest size any visible layer shows it in the output. */
    private fun decodeImages(doc: Document, scale: Double): Map<AssetId, Bitmap> {
        val sides = doc.layers.filterIsInstance<ImageLayer>().filter { it.visible }
            .groupBy { it.assetId }
            .mapValues { (id, layers) -> layers.maxOf { ExportPlanner.decodeSide(doc.assets.getValue(id), it.transform, scale) } }
        val decoded = mutableMapOf<AssetId, Bitmap>()
        try {
            sides.forEach { (id, side) ->
                checkCancelled()
                decoded[id] = PreviewImages.decodeBounded(fileFor(id), side, software = true)
            }
        } catch (e: Throwable) {
            decoded.values.forEach(Bitmap::recycle)
            throw e
        }
        return decoded
    }

    private fun checkCancelled() {
        if (cancelled) throw CancellationException("Export cancelled")
    }
}
