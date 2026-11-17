package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.FontFamily
import io.github.drhydr0.layers.document.Placement
import io.github.drhydr0.layers.document.TextAlignment
import io.github.drhydr0.layers.document.TextStyle
import kotlin.math.roundToInt

/** Edits a text layer's words and style. Used for adding and editing; one undo step on Done. */
object TextSheet {

    private val fontNames = listOf(
        FontFamily.SANS to R.string.font_sans,
        FontFamily.SERIF to R.string.font_serif,
        FontFamily.MONOSPACE to R.string.font_monospace,
        FontFamily.CASUAL to R.string.font_casual,
        FontFamily.CURSIVE to R.string.font_cursive,
    )

    fun show(
        activity: Activity,
        titleRes: Int,
        initialText: String,
        initialStyle: TextStyle,
        maxFontSize: Double,
        onDone: (text: String, style: TextStyle) -> Unit,
    ) {
        var style = initialStyle
        val controls = SheetControls(activity)

        val input = EditText(activity).apply {
            setText(initialText)
            setSelection(text.length)
            hint = activity.getString(R.string.text_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = MAX_INPUT_LINES
            minimumHeight = (TOUCH_DP * resources.displayMetrics.density).toInt()
        }
        controls.add(input)

        val minSize = Placement.MIN_FONT_SIZE
        val top = maxOf(maxFontSize, initialStyle.fontSize, minSize + 1)
        controls.slider(
            activity.getString(R.string.text_size_title), minSize, top, (top - minSize).roundToInt().coerceAtLeast(1),
            initialStyle.fontSize, { activity.getString(R.string.pixels_value, it.roundToInt()) },
        ) { style = style.copy(fontSize = it) }

        controls.colors(activity.getString(R.string.text_color_label), style.color, allowNone = false) { style = style.copy(color = it) }

        controls.label(activity.getString(R.string.text_font_label))
        controls.add(
            Spinner(activity).apply {
                adapter = ArrayAdapter(
                    activity, android.R.layout.simple_spinner_dropdown_item, fontNames.map { activity.getString(it.second) },
                )
                setSelection(fontNames.indexOfFirst { it.first == style.fontFamily }.coerceAtLeast(0))
                contentDescription = activity.getString(R.string.text_font_label)
                minimumHeight = (TOUCH_DP * resources.displayMetrics.density).toInt()
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: android.view.View?, position: Int, id: Long) {
                        style = style.copy(fontFamily = fontNames[position].first)
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                }
            },
        )
        controls.checkBox(activity.getString(R.string.text_bold), style.bold) { style = style.copy(bold = it) }
        controls.checkBox(activity.getString(R.string.text_italic), style.italic) { style = style.copy(italic = it) }

        controls.label(activity.getString(R.string.text_alignment_label))
        val alignment = RadioGroup(activity).apply { orientation = RadioGroup.HORIZONTAL }
        val alignments = listOf(
            TextAlignment.START to R.string.text_align_start,
            TextAlignment.CENTER to R.string.text_align_center,
            TextAlignment.END to R.string.text_align_end,
        )
        alignments.forEach { (value, name) ->
            alignment.addView(
                RadioButton(activity).apply {
                    id = android.view.View.generateViewId()
                    text = activity.getString(name)
                    tag = value
                    minimumHeight = (TOUCH_DP * resources.displayMetrics.density).toInt()
                    isChecked = value == style.alignment
                    layoutParams = RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
        }
        alignment.setOnCheckedChangeListener { group, id ->
            (group.findViewById<RadioButton>(id)?.tag as? TextAlignment)?.let { style = style.copy(alignment = it) }
        }
        controls.add(alignment)

        fun times(v: Double) = activity.getString(R.string.times_value, v)
        controls.slider(
            activity.getString(R.string.text_line_spacing), TextStyle.MIN_LINE_SPACING, TextStyle.MAX_LINE_SPACING,
            LINE_STEPS, style.lineSpacing, ::times,
        ) { style = style.copy(lineSpacing = it) }
        controls.slider(
            activity.getString(R.string.text_letter_spacing), TextStyle.MIN_LETTER_SPACING, TextStyle.MAX_LETTER_SPACING,
            LETTER_STEPS, style.letterSpacing, { activity.getString(R.string.percent_signed, (it * 100).roundToInt()) },
        ) { style = style.copy(letterSpacing = it) }

        controls.colors(activity.getString(R.string.text_outline_color), style.outlineColor, allowNone = true) {
            style = style.copy(outlineColor = it)
        }
        controls.slider(
            activity.getString(R.string.text_outline_width), OUTLINE_MIN, TextStyle.MAX_OUTLINE_WIDTH, OUTLINE_STEPS,
            style.outlineWidth, { activity.getString(R.string.percent_value, (it * 100).roundToInt()) },
        ) { style = style.copy(outlineWidth = it) }
        controls.colors(activity.getString(R.string.text_background_color), style.backgroundColor, allowNone = true) {
            style = style.copy(backgroundColor = it)
        }
        controls.checkBox(activity.getString(R.string.text_shadow), style.shadow) { style = style.copy(shadow = it) }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(controls.view)
            .setPositiveButton(R.string.text_done) { _, _ ->
                val text = input.text.toString()
                // Blank text would be an invisible, unselectable layer.
                if (text.isNotBlank()) onDone(text, style)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    private const val TOUCH_DP = 48
    private const val MAX_INPUT_LINES = 6
    private const val LINE_STEPS = 24
    private const val LETTER_STEPS = 22
    private const val OUTLINE_MIN = 0.01
    private const val OUTLINE_STEPS = 29
}
