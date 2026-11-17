package io.github.drhydr0.layers.ui

import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import io.github.drhydr0.layers.document.Color
import kotlin.math.roundToInt

/**
 * Builds the labelled sliders, checkboxes, and color rows that the style sheets share, so
 * each sheet lists its controls in a few lines and they all look and behave alike.
 */
class SheetControls(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val side = dp(SIDE_PADDING_DP)
        setPadding(side, dp(8), side, 0)
    }

    /** The finished sheet content, scrollable so it fits small screens and large text. */
    val view: View = ScrollView(context).apply { addView(column) }

    fun add(child: View) = column.addView(child)

    fun label(text: CharSequence): TextView = TextView(context).apply {
        this.text = text
        setTextAppearance(android.R.style.TextAppearance_Small)
        setPadding(0, dp(8), 0, 0)
        column.addView(this)
    }

    /**
     * A slider over [steps] equal steps from [min] to [max]. [format] turns the value into
     * the text shown beside the title; [onChange] runs only for the user's own drags.
     */
    fun slider(
        title: String,
        min: Double,
        max: Double,
        steps: Int,
        initial: Double,
        format: (Double) -> String,
        onChange: (Double) -> Unit,
    ): SeekBar {
        val heading = label("")
        val bar = SeekBar(context).apply {
            this.max = steps
            progress = ((initial - min) / (max - min) * steps).roundToInt().coerceIn(0, steps)
            contentDescription = title
            minimumHeight = dp(TOUCH_DP)
        }
        fun value() = min + (max - min) * bar.progress / steps
        fun show() {
            heading.text = context.getString(io.github.drhydr0.layers.R.string.sheet_slider_heading, title, format(value()))
        }
        show()
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar, progress: Int, fromUser: Boolean) {
                show()
                if (fromUser) onChange(value())
            }
            override fun onStartTrackingTouch(b: SeekBar) = Unit
            override fun onStopTrackingTouch(b: SeekBar) = Unit
        })
        column.addView(bar)
        return bar
    }

    fun checkBox(title: String, checked: Boolean, onChange: (Boolean) -> Unit): CheckBox = CheckBox(context).apply {
        text = title
        isChecked = checked
        minimumHeight = dp(TOUCH_DP)
        setOnCheckedChangeListener { _, value -> onChange(value) }
        column.addView(this)
    }

    fun colors(title: String, selected: Color, allowNone: Boolean, onPick: (Color) -> Unit) {
        label(title)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(SWATCH_ROW_DP)
            setPadding(dp(4), 0, dp(4), 0)
        }
        ColorSwatches.fill(row, selected, allowNone, onPick)
        column.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    private companion object {
        const val SIDE_PADDING_DP = 24
        const val TOUCH_DP = 48
        const val SWATCH_ROW_DP = 56
    }
}
