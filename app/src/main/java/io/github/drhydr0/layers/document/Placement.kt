package io.github.drhydr0.layers.document

import kotlin.math.min
import kotlin.math.roundToInt

/** Where new content goes, so imports land in a predictable, visible spot. */
object Placement {

    /**
     * Centers content of the given size on the canvas, scaled down (never up) so it fits
     * within [fraction] of the canvas in both directions.
     */
    fun centeredFit(canvas: CanvasSpec, width: Int, height: Int, fraction: Double = DEFAULT_FRACTION): Transform {
        val scale = min(1.0, min(canvas.width * fraction / width, canvas.height * fraction / height))
        return Transform(position = canvas.bounds.center, scaleX = scale, scaleY = scale)
    }

    /** Canvas matching an image, scaled down proportionally if a side exceeds [maxSide]. */
    fun canvasForImage(width: Int, height: Int, maxSide: Int, background: Color = Color.WHITE): CanvasSpec {
        val scale = min(1.0, maxSide.toDouble() / maxOf(width, height))
        return CanvasSpec(
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            background,
        )
    }

    /** Fills [canvas] exactly with an image of the given size (same aspect ratio assumed). */
    fun fill(canvas: CanvasSpec, width: Int, height: Int): Transform {
        val scale = min(canvas.width.toDouble() / width, canvas.height.toDouble() / height)
        return Transform(position = canvas.bounds.center, scaleX = scale, scaleY = scale)
    }

    /** New text spans most of the canvas width, so it's readable and wraps sensibly. */
    fun defaultTextBoxWidth(canvas: CanvasSpec) = canvas.width * DEFAULT_FRACTION

    /** About a twelfth of the canvas width: 90 px on a 1080 px canvas. */
    fun defaultFontSize(canvas: CanvasSpec) = (canvas.width / 12.0).coerceAtLeast(MIN_FONT_SIZE)

    /** Largest size offered on the text sheet: half the canvas width. */
    fun maxFontSize(canvas: CanvasSpec) = (canvas.width / 2.0).coerceAtLeast(MIN_FONT_SIZE * 2)

    const val DEFAULT_FRACTION = 0.8
    const val MIN_FONT_SIZE = 8.0
}
