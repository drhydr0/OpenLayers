package io.github.drhydr0.layers.ui

import android.content.Context
import io.github.drhydr0.layers.document.Color
import io.github.drhydr0.layers.document.FontFamily
import io.github.drhydr0.layers.document.TextAlignment
import io.github.drhydr0.layers.document.TextStyle

/**
 * The last text style used, so new text starts the way the previous text was set up.
 * Kept in the app's preferences, so it survives restarts and applies in every project.
 */
class TextStyleMemory(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The remembered style and opacity, or null before any text has been made. */
    fun load(): Pair<TextStyle, Double>? {
        if (!prefs.contains(KEY_SIZE)) return null
        return runCatching {
            TextStyle(
                fontSize = prefs.getFloat(KEY_SIZE, 64f).toDouble(),
                color = Color(prefs.getInt(KEY_COLOR, Color.BLACK.argb)),
                alignment = TextAlignment.valueOf(prefs.getString(KEY_ALIGNMENT, null) ?: TextAlignment.CENTER.name),
                fontFamily = FontFamily.valueOf(prefs.getString(KEY_FONT, null) ?: FontFamily.SANS.name),
                bold = prefs.getBoolean(KEY_BOLD, false),
                italic = prefs.getBoolean(KEY_ITALIC, false),
                lineSpacing = prefs.getFloat(KEY_LINE, 1f).toDouble(),
                letterSpacing = prefs.getFloat(KEY_LETTER, 0f).toDouble(),
                outlineColor = Color(prefs.getInt(KEY_OUTLINE_COLOR, 0)),
                outlineWidth = prefs.getFloat(KEY_OUTLINE_WIDTH, DEFAULT_OUTLINE).toDouble(),
                backgroundColor = Color(prefs.getInt(KEY_BACKGROUND, 0)),
                shadow = prefs.getBoolean(KEY_SHADOW, false),
            ) to prefs.getFloat(KEY_OPACITY, 1f).toDouble()
        }.getOrNull() // A value from an older version that no longer fits just means no memory.
    }

    fun save(style: TextStyle, opacity: Double) {
        prefs.edit()
            .putFloat(KEY_SIZE, style.fontSize.toFloat())
            .putInt(KEY_COLOR, style.color.argb)
            .putString(KEY_ALIGNMENT, style.alignment.name)
            .putString(KEY_FONT, style.fontFamily.name)
            .putBoolean(KEY_BOLD, style.bold)
            .putBoolean(KEY_ITALIC, style.italic)
            .putFloat(KEY_LINE, style.lineSpacing.toFloat())
            .putFloat(KEY_LETTER, style.letterSpacing.toFloat())
            .putInt(KEY_OUTLINE_COLOR, style.outlineColor.argb)
            .putFloat(KEY_OUTLINE_WIDTH, style.outlineWidth.toFloat())
            .putInt(KEY_BACKGROUND, style.backgroundColor.argb)
            .putBoolean(KEY_SHADOW, style.shadow)
            .putFloat(KEY_OPACITY, opacity.toFloat())
            .apply()
    }

    private companion object {
        const val PREFS = "text_style_memory"
        const val KEY_SIZE = "size"
        const val KEY_COLOR = "color"
        const val KEY_ALIGNMENT = "alignment"
        const val KEY_FONT = "font"
        const val KEY_BOLD = "bold"
        const val KEY_ITALIC = "italic"
        const val KEY_LINE = "line_spacing"
        const val KEY_LETTER = "letter_spacing"
        const val KEY_OUTLINE_COLOR = "outline_color"
        const val KEY_OUTLINE_WIDTH = "outline_width"
        const val KEY_BACKGROUND = "background"
        const val KEY_SHADOW = "shadow"
        const val KEY_OPACITY = "opacity"
        const val DEFAULT_OUTLINE = 0.06f
    }
}
