/**
 * Canvas preview, thumbnails, captures, and export.
 *
 * Preview, sticker capture, and export share one rendering path: [DocumentRenderer].
 */
package io.github.drhydr0.layers.render

import io.github.drhydr0.layers.document.Matrix
import io.github.drhydr0.layers.document.Rect
import kotlin.math.min

/**
 * Maps canvas pixels to screen pixels. Kept free of Android types so the math is JVM-tested.
 * Zooming changes only this mapping, never object positions in the document.
 */
object Viewport {

    /** Scales the canvas to fit inside [view] minus [margin] on each side, centered. */
    fun fit(canvasWidth: Int, canvasHeight: Int, view: Rect, margin: Double): Matrix {
        val availableW = (view.width - 2 * margin).coerceAtLeast(1.0)
        val availableH = (view.height - 2 * margin).coerceAtLeast(1.0)
        val scale = min(availableW / canvasWidth, availableH / canvasHeight)
        val dx = view.left + (view.width - canvasWidth * scale) / 2
        val dy = view.top + (view.height - canvasHeight * scale) / 2
        return Matrix.translate(dx, dy) * Matrix.scale(scale, scale)
    }
}
