/**
 * Canvas preview, thumbnails, captures, and export.
 *
 * Preview, sticker capture, and export share one rendering path: [DocumentRenderer].
 */
package io.github.drhydr0.layers.render

import io.github.drhydr0.layers.document.Matrix
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.Vec2
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

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

    /** Zoom limits relative to the fitted view: far enough out to find stray layers, in to pixels. */
    const val MIN_ZOOM = 0.25
    const val MAX_ZOOM = 20.0

    fun scaleOf(m: Matrix) = sqrt(abs(m.determinant))

    fun pan(view: Matrix, dx: Double, dy: Double) = Matrix.translate(dx, dy) * view

    /**
     * Zooms by [factor] around [focus] (view pixels), so the point under the fingers stays put.
     * The result stays within [MIN_ZOOM]..[MAX_ZOOM] of [fitScale].
     */
    fun zoom(view: Matrix, focus: Vec2, factor: Double, fitScale: Double): Matrix {
        val current = scaleOf(view)
        val target = (current * factor).coerceIn(fitScale * MIN_ZOOM, fitScale * MAX_ZOOM)
        val f = target / current
        return Matrix.translate(focus.x, focus.y) * Matrix.scale(f, f) * Matrix.translate(-focus.x, -focus.y) * view
    }
}
