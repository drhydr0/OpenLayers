package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.Color
import io.github.drhydr0.layers.document.Placement
import io.github.drhydr0.layers.document.TextAlignment
import io.github.drhydr0.layers.document.TextStyle
import kotlin.math.roundToInt

/** Edits a text layer's words, size, color, and alignment. Used for adding and editing. */
object TextSheet {

    private val colorNames = listOf(
        R.string.color_black, R.string.color_white, R.string.color_gray, R.string.color_red,
        R.string.color_orange, R.string.color_yellow, R.string.color_green, R.string.color_blue,
        R.string.color_purple, R.string.color_pink,
    )

    fun show(
        activity: Activity,
        titleRes: Int,
        initialText: String,
        initialStyle: TextStyle,
        maxFontSize: Double,
        onDone: (text: String, style: TextStyle) -> Unit,
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_text, null)
        val input = view.findViewById<EditText>(R.id.text_input)
        val sizeLabel = view.findViewById<TextView>(R.id.text_size_label)
        val size = view.findViewById<SeekBar>(R.id.text_size)
        val swatches = view.findViewById<LinearLayout>(R.id.text_colors)
        val alignment = view.findViewById<RadioGroup>(R.id.text_alignment)

        input.setText(initialText)
        input.setSelection(input.text.length)

        val minSize = Placement.MIN_FONT_SIZE
        size.max = (maxFontSize - minSize).roundToInt().coerceAtLeast(1)
        size.progress = (initialStyle.fontSize - minSize).roundToInt().coerceIn(0, size.max)
        fun currentSize() = minSize + size.progress
        fun updateSizeLabel() {
            sizeLabel.text = activity.getString(R.string.text_size_label, currentSize().roundToInt())
        }
        updateSizeLabel()
        size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) = updateSizeLabel()
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })

        var color = initialStyle.color
        val swatchViews = TextPalette.colors.mapIndexed { i, c ->
            View(activity).apply {
                val px = (SWATCH_DP * activity.resources.displayMetrics.density).roundToInt()
                layoutParams = LinearLayout.LayoutParams(px, px).apply { marginEnd = px / 6 }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c.argb)
                    setStroke(px / 16, ColorStateList.valueOf(OUTLINE))
                }
                contentDescription = activity.getString(colorNames[i])
                setOnClickListener {
                    color = c
                    markSelected(swatches, this)
                }
                swatches.addView(this)
            }
        }
        (swatchViews.getOrNull(TextPalette.colors.indexOf(color)))?.let { markSelected(swatches, it) }

        alignment.check(
            when (initialStyle.alignment) {
                TextAlignment.START -> R.id.align_start
                TextAlignment.CENTER -> R.id.align_center
                TextAlignment.END -> R.id.align_end
            },
        )

        val dialog = AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(view)
            .setPositiveButton(R.string.text_done) { _, _ ->
                val text = input.text.toString()
                // Blank text would be an invisible, unselectable layer.
                if (text.isBlank()) return@setPositiveButton
                val align = when (alignment.checkedRadioButtonId) {
                    R.id.align_start -> TextAlignment.START
                    R.id.align_end -> TextAlignment.END
                    else -> TextAlignment.CENTER
                }
                onDone(text, TextStyle(currentSize(), color, align))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    private fun markSelected(row: LinearLayout, selected: View) {
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            child.isSelected = child == selected
            child.scaleX = if (child == selected) SELECTED_SCALE else 1f
            child.scaleY = child.scaleX
        }
    }

    private const val SWATCH_DP = 40
    private const val SELECTED_SCALE = 1.2f
    private val OUTLINE = Color(0xFF808080.toInt()).argb
}
