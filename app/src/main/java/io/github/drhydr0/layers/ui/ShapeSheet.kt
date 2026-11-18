package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.ShapeKind
import io.github.drhydr0.layers.document.ShapeLayer
import kotlin.math.roundToInt

/** A shape's fill, outline, size, corners, and opacity, shown live. One undo step on Done. */
object ShapeSheet {

    /** [maxSide] is the largest width or height offered, in canvas pixels. */
    fun show(
        activity: Activity,
        shape: ShapeLayer,
        maxSide: Double,
        onPreview: (ShapeLayer) -> Unit,
        onClose: () -> Unit,
        onDone: (ShapeLayer) -> Unit,
    ) {
        var edited = shape
        fun change(next: ShapeLayer) {
            edited = next
            onPreview(next)
        }
        val line = shape.isLine
        val controls = SheetControls(activity)
        fun px(v: Double) = activity.getString(R.string.pixels_value, v.roundToInt())

        if (!line) {
            controls.colors(activity.getString(R.string.shape_fill), shape.fill, allowNone = true) { change(edited.copy(fill = it)) }
        }
        controls.colors(
            activity.getString(if (line) R.string.shape_line_color else R.string.shape_outline_color),
            shape.stroke, allowNone = !line,
        ) { change(edited.copy(stroke = it)) }
        val maxStroke = (maxSide / MAX_STROKE_DIVISOR).coerceAtLeast(MIN_SIZE)
        controls.slider(
            activity.getString(R.string.shape_outline_width), if (line) 1.0 else 0.0, maxStroke,
            maxStroke.roundToInt().coerceAtLeast(1), shape.strokeWidth, ::px,
        ) {
            // A line's box grows with its thickness, so its arrow head and touch area fit.
            change(if (line) edited.copy(strokeWidth = it, height = it * ShapeLayer.ARROW_HEAD_FACTOR) else edited.copy(strokeWidth = it))
        }
        val steps = maxSide.roundToInt().coerceAtLeast(1)
        controls.slider(
            activity.getString(if (line) R.string.shape_length else R.string.shape_width), MIN_SIZE, maxSide, steps, shape.width, ::px,
        ) { change(edited.copy(width = it)) }
        if (!line) {
            controls.slider(activity.getString(R.string.shape_height), MIN_SIZE, maxSide, steps, shape.height, ::px) {
                change(edited.copy(height = it))
            }
        }
        if (shape.kind == ShapeKind.ROUNDED_RECTANGLE) {
            controls.slider(activity.getString(R.string.shape_corner), 0.0, maxSide / 2, steps / 2, shape.cornerRadius, ::px) {
                change(edited.copy(cornerRadius = it))
            }
        }
        controls.slider(
            activity.getString(R.string.opacity_title), 0.0, 1.0, OPACITY_STEPS, shape.opacity,
            { activity.getString(R.string.percent_value, (it * 100).roundToInt()) },
        ) { change(edited.copy(opacity = it)) }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.shape_edit_title)
            .setView(controls.view)
            .setPositiveButton(R.string.text_done) { _, _ -> onDone(edited) }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { onClose() }
            .show()
        LiveSheet.style(activity, dialog)
    }

    private const val MIN_SIZE = 4.0
    private const val MAX_STROKE_DIVISOR = 10.0
    private const val OPACITY_STEPS = 100
}
