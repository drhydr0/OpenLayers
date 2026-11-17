package io.github.drhydr0.layers.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.ShapeLayer
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.TextStyle
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
            is ShapeLayer -> ShapeDrawing.draw(target, layer, alpha)
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
        // The destination is the cropped part at the asset's full size, whatever resolution the bitmap was decoded at.
        val dst = document.imageLocalBounds(layer).toRectF()
        val bitmap = images.bitmap(layer.assetId)
        if (bitmap == null) {
            target.drawRect(dst, placeholderPaint)
            return
        }
        imagePaint.alpha = alpha
        imagePaint.colorFilter =
            if (layer.adjustments.isNone) null else ColorMatrixColorFilter(layer.adjustments.colorMatrix())
        val crop = layer.crop
        // Crop is in fractions, so it works at any decode size.
        val src = if (crop.isFull) null else android.graphics.Rect(
            (crop.left * bitmap.width).roundToInt(), (crop.top * bitmap.height).roundToInt(),
            (crop.right * bitmap.width).roundToInt(), (crop.bottom * bitmap.height).roundToInt(),
        )
        target.drawBitmap(bitmap, src, dst, imagePaint)
        imagePaint.colorFilter = null
    }

    private fun drawText(target: Canvas, layer: TextLayer, alpha: Int) {
        val style = layer.style
        val layout = textLayouts.layout(layer)
        if (style.hasBackground) {
            backgroundPaint.color = style.backgroundColor.argb
            backgroundPaint.alpha = style.backgroundColor.alpha * alpha / 255
            val radius = (style.fontSize * BACKGROUND_RADIUS).toFloat()
            target.drawRoundRect(textLayouts.localBounds(layer).toRectF(), radius, radius, backgroundPaint)
        }
        val box = textLayouts.textBox(layer)
        target.translate(box.left.toFloat(), box.top.toFloat())
        // The cached layout's paint is shared, so set everything right before drawing and reset after.
        val paint = layout.paint
        if (style.hasOutline) {
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = (style.fontSize * style.outlineWidth).toFloat()
            paint.color = style.outlineColor.argb
            paint.alpha = style.outlineColor.alpha * alpha / 255
            if (style.shadow) setShadow(paint, style, alpha)
            layout.draw(target)
            paint.clearShadowLayer()
            paint.style = Paint.Style.FILL
        }
        paint.color = style.color.argb
        paint.alpha = (style.color.alpha * alpha) / 255
        // With an outline, the outline pass already cast the shadow.
        if (style.shadow && !style.hasOutline) setShadow(paint, style, alpha)
        layout.draw(target)
        paint.clearShadowLayer()
    }

    private fun setShadow(paint: Paint, style: TextStyle, alpha: Int) {
        val size = style.fontSize.toFloat()
        val color = (SHADOW_ALPHA * alpha / 255 shl 24)
        paint.setShadowLayer(size * SHADOW_BLUR, size * SHADOW_OFFSET, size * SHADOW_OFFSET, color)
    }

    private companion object {
        const val PLACEHOLDER_COLOR = 0x33808080
        // Text effects are fractions of the font size, so they scale with the text.
        const val BACKGROUND_RADIUS = 0.2
        const val SHADOW_BLUR = 0.08f
        const val SHADOW_OFFSET = 0.05f
        const val SHADOW_ALPHA = 0x99
    }
}
