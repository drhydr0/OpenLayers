package io.github.drhydr0.layers.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.TextLayer
import kotlin.math.roundToInt

/** Supplies decoded images. Null means not ready yet (preview) and draws a placeholder. */
fun interface ImageSource {
    fun bitmap(id: AssetId): Bitmap?
}

/**
 * Draws a document in canvas coordinates. The single rendering path for preview,
 * thumbnails, captures, and export: callers only choose the target canvas, its
 * transform, and the image resolution. Like [TextLayouts], one instance per thread.
 */
class DocumentRenderer(private val textLayouts: TextLayouts) {

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint().apply { color = PLACEHOLDER_COLOR }
    private val backgroundPaint = Paint()

    /**
     * @param clipToCanvas true for export; the preview shows content beyond the edges so
     *   off-canvas objects stay reachable.
     */
    fun draw(target: Canvas, document: Document, images: ImageSource, clipToCanvas: Boolean) {
        val save = target.save()
        val bounds = document.canvas.bounds.toRectF()
        if (clipToCanvas) target.clipRect(bounds)
        // A transparent background draws nothing, so export keeps the alpha.
        if (document.canvas.background.alpha > 0) {
            backgroundPaint.color = document.canvas.background.argb
            target.drawRect(bounds, backgroundPaint)
        }
        document.layers.forEach { if (it.visible && it.opacity > 0) drawLayer(target, document, it, images) }
        target.restoreToCount(save)
    }

    private fun drawLayer(target: Canvas, document: Document, layer: Layer, images: ImageSource) {
        val save = target.save()
        target.concat(layer.transform.toMatrix().toAndroid())
        val alpha = (layer.opacity * 255).roundToInt()
        when (layer) {
            is ImageLayer -> drawImage(target, document, layer, images, alpha)
            is TextLayer -> drawText(target, layer, alpha)
            is GroupLayer -> drawGroup(target, document, layer, images, alpha)
        }
        target.restoreToCount(save)
    }

    private fun drawGroup(target: Canvas, document: Document, group: GroupLayer, images: ImageSource, alpha: Int) {
        // A see-through group is drawn into an offscreen layer first, so overlapping
        // children fade as one object instead of showing through each other.
        if (alpha < 255) target.saveLayerAlpha(null, alpha)
        group.children.forEach { if (it.visible && it.opacity > 0) drawLayer(target, document, it, images) }
    }

    private fun drawImage(target: Canvas, document: Document, layer: ImageLayer, images: ImageSource, alpha: Int) {
        // The destination is the asset's full size, whatever resolution the bitmap was decoded at.
        val dst = document.imageLocalBounds(layer).toRectF()
        val bitmap = images.bitmap(layer.assetId)
        if (bitmap == null) {
            target.drawRect(dst, placeholderPaint)
            return
        }
        imagePaint.alpha = alpha
        target.drawBitmap(bitmap, null, dst, imagePaint)
    }

    private fun drawText(target: Canvas, layer: TextLayer, alpha: Int) {
        val layout = textLayouts.layout(layer)
        val bounds = textLayouts.localBounds(layer)
        target.translate(bounds.left.toFloat(), bounds.top.toFloat())
        // The cached layout's paint is shared, so set color and alpha right before drawing.
        layout.paint.color = layer.style.color.argb
        layout.paint.alpha = (layer.style.color.alpha * alpha) / 255
        layout.draw(target)
    }

    private companion object {
        const val PLACEHOLDER_COLOR = 0x33808080
    }
}
