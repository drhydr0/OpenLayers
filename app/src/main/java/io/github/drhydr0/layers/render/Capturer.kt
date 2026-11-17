package io.github.drhydr0.layers.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.CapturePlan
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.LassoSelection
import io.github.drhydr0.layers.document.RectSelection
import io.github.drhydr0.layers.document.SelectionShape
import java.io.File

/**
 * Copies part of a document into a transparent image through the shared
 * [DocumentRenderer], so a capture matches the editor and export pixel for pixel.
 * Blocking: run on a background thread.
 */
class Capturer(private val fileFor: (AssetId) -> File) {

    /**
     * Draws [source] inside [shape]; everything outside the shape stays transparent.
     * [clipToCanvas] is true for a canvas capture, which stops at the canvas edges.
     */
    fun render(source: Document, shape: SelectionShape, plan: CapturePlan, clipToCanvas: Boolean): Bitmap {
        val images = OutputImages.decode(source, plan.scale, fileFor)
        try {
            val bitmap = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Map the captured region of the canvas onto the whole bitmap.
            canvas.scale((plan.width / plan.region.width).toFloat(), (plan.height / plan.region.height).toFloat())
            canvas.translate(-plan.region.left.toFloat(), -plan.region.top.toFloat())
            DocumentRenderer(TextLayouts(maxEntries = 64)).draw(canvas, source, { images[it] }, clipToCanvas)
            mask(canvas, shape)
            return bitmap
        } finally {
            images.values.forEach(Bitmap::recycle)
        }
    }

    /**
     * Keeps only what lies inside the shape. Done by erasing with an anti-aliased path
     * instead of clipping, so a lasso's edge is smooth.
     */
    private fun mask(canvas: Canvas, shape: SelectionShape) {
        val keep = Path()
        when (shape) {
            is RectSelection -> keep.addRect(shape.rect.toRectF(), Path.Direction.CW)
            is LassoSelection -> {
                shape.points.forEachIndexed { i, p ->
                    if (i == 0) keep.moveTo(p.x.toFloat(), p.y.toFloat()) else keep.lineTo(p.x.toFloat(), p.y.toFloat())
                }
                keep.close()
            }
        }
        keep.fillType = Path.FillType.EVEN_ODD
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        canvas.drawPath(keep, paint)
    }

    /** True when every pixel is fully transparent: the selection caught nothing. */
    fun isBlank(bitmap: Bitmap): Boolean {
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (row.any { it ushr 24 != 0 }) return false
        }
        return true
    }
}
