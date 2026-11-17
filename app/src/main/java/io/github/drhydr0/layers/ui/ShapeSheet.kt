package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.ShapeKind
import io.github.drhydr0.layers.document.ShapeLayer
import kotlin.math.roundToInt

/** Edits a shape's size, fill, outline, and corner rounding. One undo step on Done. */
object ShapeSheet {

    /** [maxSide] is the largest width or height offered, in canvas pixels. */
    fun show(activity: Activity, shape: ShapeLayer, maxSide: Double, onDone: (ShapeLayer) -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_shape, null)
        var edited = shape

        val line = shape.isLine
        view.findViewById<View>(R.id.shape_fill_section).visibility = if (line) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.shape_height_section).visibility = if (line) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.shape_corner_section).visibility =
            if (shape.kind == ShapeKind.ROUNDED_RECTANGLE) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.shape_stroke_color_label)
            .setText(if (line) R.string.shape_line_color else R.string.shape_outline_color)
        view.findViewById<TextView>(R.id.shape_width_label_title)
            .setText(if (line) R.string.shape_length else R.string.shape_width)

        ColorSwatches.fill(view.findViewById<LinearLayout>(R.id.shape_fill_colors), shape.fill, allowNone = true) {
            edited = edited.copy(fill = it)
        }
        ColorSwatches.fill(view.findViewById<LinearLayout>(R.id.shape_stroke_colors), shape.stroke, allowNone = !line) {
            edited = edited.copy(stroke = it)
        }

        val maxStroke = (maxSide / MAX_STROKE_DIVISOR).coerceAtLeast(MIN_SIZE)
        slider(view, R.id.shape_stroke_width, R.id.shape_stroke_width_value, shape.strokeWidth, if (line) 1.0 else 0.0, maxStroke) {
            // A line's box grows with its thickness, so its arrow head and touch area fit.
            edited = if (line) edited.copy(strokeWidth = it, height = it * ShapeLayer.ARROW_HEAD_FACTOR) else edited.copy(strokeWidth = it)
        }
        slider(view, R.id.shape_width, R.id.shape_width_value, shape.width, MIN_SIZE, maxSide) { edited = edited.copy(width = it) }
        slider(view, R.id.shape_height, R.id.shape_height_value, shape.height, MIN_SIZE, maxSide) {
            if (!line) edited = edited.copy(height = it)
        }
        slider(view, R.id.shape_corner, R.id.shape_corner_value, shape.cornerRadius, 0.0, maxSide / 2) {
            edited = edited.copy(cornerRadius = it)
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.shape_edit_title)
            .setView(view)
            .setPositiveButton(R.string.text_done) { _, _ -> onDone(edited) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** A whole-pixel slider from [min] to [max] with its value shown beside it. */
    private fun slider(root: View, barId: Int, valueId: Int, initial: Double, min: Double, max: Double, onChange: (Double) -> Unit) {
        val bar = root.findViewById<SeekBar>(barId)
        val label = root.findViewById<TextView>(valueId)
        bar.max = (max - min).roundToInt().coerceAtLeast(1)
        bar.progress = (initial - min).roundToInt().coerceIn(0, bar.max)
        fun show() {
            label.text = root.context.getString(R.string.pixels_value, (min + bar.progress).roundToInt())
        }
        show()
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar, progress: Int, fromUser: Boolean) {
                show()
                // Only real drags change the shape, so untouched values keep their exact size.
                if (fromUser) onChange(min + progress)
            }
            override fun onStartTrackingTouch(b: SeekBar) = Unit
            override fun onStopTrackingTouch(b: SeekBar) = Unit
        })
    }

    private const val MIN_SIZE = 4.0
    private const val MAX_STROKE_DIVISOR = 10.0
}
