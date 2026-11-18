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

    /**
     * Where new content goes: the middle of what the user is looking at, sized for that
     * view. [visible] is the part of the canvas plane on screen (null when unknown). When
     * zoomed in, new content lands in front of the user at a size that suits the zoom;
     * when the whole canvas is in view, it behaves like the canvas itself.
     */
    fun frame(canvas: CanvasSpec, visible: Rect?): PlacementFrame {
        if (visible == null || visible.isEmpty) return PlacementFrame(canvas.bounds.center, canvas)
        val sizing = CanvasSpec(
            min(canvas.width.toDouble(), visible.width).roundToInt().coerceAtLeast(1),
            min(canvas.height.toDouble(), visible.height).roundToInt().coerceAtLeast(1),
        )
        return PlacementFrame(visible.center, sizing)
    }

    /** New text spans most of the canvas width, so it's readable and wraps sensibly. */
    fun defaultTextBoxWidth(canvas: CanvasSpec) = canvas.width * DEFAULT_FRACTION

    /** About a twelfth of the canvas width: 90 px on a 1080 px canvas. */
    fun defaultFontSize(canvas: CanvasSpec) = (canvas.width / 12.0).coerceAtLeast(MIN_FONT_SIZE)

    /** Largest size offered on the text sheet: half the canvas width. */
    fun maxFontSize(canvas: CanvasSpec) = (canvas.width / 2.0).coerceAtLeast(MIN_FONT_SIZE * 2)

    /**
     * A new shape sized for the area being looked at: a third of its shorter side, with
     * lines and arrows as wide as that and thin. The outline scales with it.
     */
    fun defaultShape(id: LayerId, name: String, kind: ShapeKind, frame: PlacementFrame): ShapeLayer {
        val side = min(frame.sizing.width, frame.sizing.height) / 3.0
        val strokeWidth = (side / 25).coerceAtLeast(1.0)
        val line = kind == ShapeKind.LINE || kind == ShapeKind.ARROW
        return ShapeLayer(
            id = id,
            name = name,
            kind = kind,
            width = side,
            height = if (line) strokeWidth * ShapeLayer.ARROW_HEAD_FACTOR else side,
            fill = if (line) Color.TRANSPARENT else DEFAULT_SHAPE_FILL,
            stroke = Color.BLACK,
            // Lines scale their thickness with the view; filled shapes start with a thin outline.
            strokeWidth = if (line) strokeWidth else DEFAULT_OUTLINE_WIDTH,
            cornerRadius = if (kind == ShapeKind.ROUNDED_RECTANGLE) side / 6 else 0.0,
            transform = Transform(position = frame.center),
        )
    }

    const val DEFAULT_OUTLINE_WIDTH = 4.0

    val DEFAULT_SHAPE_FILL = Color(0xFF1E88E5.toInt())

    const val DEFAULT_FRACTION = 0.8
    const val MIN_FONT_SIZE = 8.0
}

/** The spot for new content ([center], canvas coordinates) and the area to size it for. */
data class PlacementFrame(val center: Vec2, val sizing: CanvasSpec)
