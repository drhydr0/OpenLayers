package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.FontFamily
import io.github.drhydr0.layers.document.Placement
import io.github.drhydr0.layers.document.TextAlignment
import io.github.drhydr0.layers.document.TextStyle
import io.github.drhydr0.layers.render.Fonts
import kotlin.math.roundToInt

/** Edits a text layer's words and style. Used for adding and editing; one undo step on Done. */
object TextSheet {

    private val fontNames = listOf(
        FontFamily.SANS to R.string.font_sans,
        FontFamily.SANS_LIGHT to R.string.font_sans_light,
        FontFamily.SANS_CONDENSED to R.string.font_sans_condensed,
        FontFamily.SANS_BLACK to R.string.font_sans_black,
        FontFamily.LATO to R.string.font_lato,
        FontFamily.SERIF to R.string.font_serif,
        FontFamily.MONOSPACE to R.string.font_monospace,
        FontFamily.CASUAL to R.string.font_casual,
        FontFamily.CURSIVE to R.string.font_cursive,
        FontFamily.LOBSTER to R.string.font_lobster,
        FontFamily.PACIFICO to R.string.font_pacifico,
        FontFamily.GREAT_VIBES to R.string.font_great_vibes,
        FontFamily.PERMANENT_MARKER to R.string.font_permanent_marker,
        FontFamily.BEBAS_NEUE to R.string.font_bebas_neue,
        FontFamily.ABRIL_FATFACE to R.string.font_abril_fatface,
        FontFamily.BANGERS to R.string.font_bangers,
        FontFamily.SPECIAL_ELITE to R.string.font_special_elite,
        FontFamily.PRESS_START to R.string.font_press_start,
        FontFamily.CREEPSTER to R.string.font_creepster,
    )

    /** Font names shown in their own font, so the list doubles as a preview. */
    private class FontAdapter(activity: Activity, private val families: List<FontFamily>, names: List<String>) :
        ArrayAdapter<String>(activity, android.R.layout.simple_spinner_dropdown_item, names) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
            styled(super.getView(position, convertView, parent), position)

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) =
            styled(super.getDropDownView(position, convertView, parent), position)

        private fun styled(view: View, position: Int) = view.apply {
            (this as? TextView)?.typeface = Fonts.typeface(families[position])
        }
    }

    fun show(
        activity: Activity,
        titleRes: Int,
        initialText: String,
        initialStyle: TextStyle,
        initialOpacity: Double,
        maxFontSize: Double,
        onPreview: (text: String, style: TextStyle, opacity: Double) -> Unit,
        onClose: () -> Unit,
        onDone: (text: String, style: TextStyle, opacity: Double) -> Unit,
    ) {
        var style = initialStyle
        var opacity = initialOpacity
        lateinit var input: EditText
        // Every change shows on the canvas right away; nothing is saved until Done.
        fun preview() = onPreview(input.text.toString().ifBlank { " " }, style, opacity)
        val controls = SheetControls(activity)

        input = EditText(activity).apply {
            setText(initialText)
            setSelection(text.length)
            hint = activity.getString(R.string.text_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = MAX_INPUT_LINES
            minimumHeight = (TOUCH_DP * resources.displayMetrics.density).toInt()
        }
        controls.add(input)
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = preview()
        })

        val minSize = Placement.MIN_FONT_SIZE
        val top = maxOf(maxFontSize, initialStyle.fontSize, minSize + 1)
        controls.slider(
            activity.getString(R.string.text_size_title), minSize, top, (top - minSize).roundToInt().coerceAtLeast(1),
            initialStyle.fontSize, { activity.getString(R.string.pixels_value, it.roundToInt()) },
        ) { style = style.copy(fontSize = it); preview() }

        controls.colors(activity.getString(R.string.text_color_label), style.color, allowNone = false) { style = style.copy(color = it); preview() }

        controls.label(activity.getString(R.string.text_font_label))
        controls.add(
            Spinner(activity).apply {
                adapter = FontAdapter(activity, fontNames.map { it.first }, fontNames.map { activity.getString(it.second) })
                setSelection(fontNames.indexOfFirst { it.first == style.fontFamily }.coerceAtLeast(0))
                contentDescription = activity.getString(R.string.text_font_label)
                minimumHeight = (TOUCH_DP * resources.displayMetrics.density).toInt()
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: android.view.View?, position: Int, id: Long) {
                        style = style.copy(fontFamily = fontNames[position].first)
                        preview()
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                }
            },
        )
        controls.checkBox(activity.getString(R.string.text_bold), style.bold) { style = style.copy(bold = it); preview() }
        controls.checkBox(activity.getString(R.string.text_italic), style.italic) { style = style.copy(italic = it); preview() }

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
            (group.findViewById<RadioButton>(id)?.tag as? TextAlignment)?.let { style = style.copy(alignment = it); preview() }
        }
        controls.add(alignment)

        fun times(v: Double) = activity.getString(R.string.times_value, v)
        controls.slider(
            activity.getString(R.string.text_line_spacing), TextStyle.MIN_LINE_SPACING, TextStyle.MAX_LINE_SPACING,
            LINE_STEPS, style.lineSpacing, ::times,
        ) { style = style.copy(lineSpacing = it); preview() }
        controls.slider(
            activity.getString(R.string.text_letter_spacing), TextStyle.MIN_LETTER_SPACING, TextStyle.MAX_LETTER_SPACING,
            LETTER_STEPS, style.letterSpacing, { activity.getString(R.string.percent_signed, (it * 100).roundToInt()) },
        ) { style = style.copy(letterSpacing = it); preview() }

        controls.colors(activity.getString(R.string.text_outline_color), style.outlineColor, allowNone = true) {
            style = style.copy(outlineColor = it)
            preview()
        }
        controls.slider(
            activity.getString(R.string.text_outline_width), OUTLINE_MIN, TextStyle.MAX_OUTLINE_WIDTH, OUTLINE_STEPS,
            style.outlineWidth, { activity.getString(R.string.percent_value, (it * 100).roundToInt()) },
        ) { style = style.copy(outlineWidth = it); preview() }
        controls.colors(activity.getString(R.string.text_background_color), style.backgroundColor, allowNone = true) {
            style = style.copy(backgroundColor = it)
            preview()
        }
        controls.checkBox(activity.getString(R.string.text_shadow), style.shadow) { style = style.copy(shadow = it); preview() }
        controls.slider(
            activity.getString(R.string.opacity_title), 0.0, 1.0, OPACITY_STEPS, opacity,
            { activity.getString(R.string.percent_value, (it * 100).roundToInt()) },
        ) { opacity = it; preview() }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(controls.view)
            .setPositiveButton(R.string.text_done) { _, _ ->
                val text = input.text.toString()
                // Blank text would be an invisible, unselectable layer.
                if (text.isNotBlank()) onDone(text, style, opacity)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { onClose() }
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        LiveSheet.style(activity, dialog)
        input.requestFocus()
    }

    private const val TOUCH_DP = 48
    private const val OPACITY_STEPS = 100
    private const val MAX_INPUT_LINES = 6
    private const val LINE_STEPS = 24
    private const val LETTER_STEPS = 22
    private const val OUTLINE_MIN = 0.01
    private const val OUTLINE_STEPS = 29
}
