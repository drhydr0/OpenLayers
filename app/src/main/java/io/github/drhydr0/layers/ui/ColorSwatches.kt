package io.github.drhydr0.layers.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.Color
import kotlin.math.roundToInt

/** A row of tappable color circles with spoken names, used by the style sheets. */
object ColorSwatches {

    private val names = listOf(
        R.string.color_black, R.string.color_white, R.string.color_gray, R.string.color_red,
        R.string.color_orange, R.string.color_yellow, R.string.color_green, R.string.color_blue,
        R.string.color_purple, R.string.color_pink,
    )

    /**
     * Fills [row] with the palette. With [allowNone], a first "None" swatch stands for
     * transparent. [selected] is marked; [onPick] runs on each tap.
     */
    fun fill(row: LinearLayout, selected: Color, allowNone: Boolean, onPick: (Color) -> Unit) {
        val context = row.context
        row.removeAllViews()
        val entries = buildList {
            if (allowNone) add(Color.TRANSPARENT to context.getString(R.string.color_none))
            TextPalette.colors.forEachIndexed { i, c -> add(c to context.getString(names[i])) }
        }
        val views = entries.map { (color, name) ->
            swatch(context, color).apply {
                contentDescription = name
                setOnClickListener {
                    mark(row, this)
                    onPick(color)
                }
                row.addView(this)
            }
        }
        // A color outside the palette (e.g. from an older project) simply shows none marked.
        entries.indexOfFirst { it.first == selected }.takeIf { it >= 0 }?.let { mark(row, views[it]) }
    }

    private fun swatch(context: Context, color: Color) = View(context).apply {
        val px = (SWATCH_DP * context.resources.displayMetrics.density).roundToInt()
        layoutParams = LinearLayout.LayoutParams(px, px).apply { marginEnd = px / 5 }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            // "None" shows as an empty ring.
            setColor(color.argb)
            setStroke(px / 14, ColorStateList.valueOf(OUTLINE))
        }
    }

    private fun mark(row: LinearLayout, selected: View) {
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            child.isSelected = child == selected
            child.scaleX = if (child == selected) SELECTED_SCALE else UNSELECTED_SCALE
            child.scaleY = child.scaleX
        }
    }

    // 48 dp with the gap between swatches, meeting the touch target size.
    private const val SWATCH_DP = 40
    private const val SELECTED_SCALE = 1.15f
    private const val UNSELECTED_SCALE = 0.85f
    private const val OUTLINE = 0xFF808080.toInt()
}
