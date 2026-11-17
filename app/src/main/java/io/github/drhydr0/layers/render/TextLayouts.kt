package io.github.drhydr0.layers.render

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.FontFamily
import io.github.drhydr0.layers.document.TextAlignment
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.TextStyle
import kotlin.math.ceil

/**
 * Lays out text layers. Preview, hit testing, and export all measure text here, so
 * wrapping and bounds are identical everywhere.
 *
 * Not thread-safe (layouts share a paint): use one instance per thread, e.g. a separate
 * one for a background export.
 */
class TextLayouts(maxEntries: Int = 256) {

    /** Everything that changes line breaks or glyph shapes. Colors and effects are applied at draw time. */
    private data class Key(
        val text: String,
        val fontSize: Double,
        val alignment: TextAlignment,
        val boxWidth: Double,
        val fontFamily: FontFamily,
        val bold: Boolean,
        val italic: Boolean,
        val lineSpacing: Double,
        val letterSpacing: Double,
    )

    private val cache = LruCache<Key, StaticLayout>(maxEntries)

    fun layout(layer: TextLayer): StaticLayout {
        val style = layer.style
        val key = Key(
            layer.text, style.fontSize, style.alignment, layer.boxWidth,
            style.fontFamily, style.bold, style.italic, style.lineSpacing, style.letterSpacing,
        )
        cache.get(key)?.let { return it }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = style.fontSize.toFloat()
            typeface = typefaceFor(style)
            letterSpacing = style.letterSpacing.toFloat()
        }
        val width = ceil(layer.boxWidth).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, paint, width)
            .setAlignment(style.alignment.toLayoutAlignment())
            .setLineSpacing(0f, style.lineSpacing.toFloat())
            .setIncludePad(false)
            .build()
        cache.put(key, layout)
        return layout
    }

    /** The box the text is laid out in, in the layer's own coordinates, centered on its origin. */
    fun textBox(layer: TextLayer): Rect {
        val height = layout(layer).height.toDouble().coerceAtLeast(layer.style.fontSize)
        return Rect.centered(layer.boxWidth, height)
    }

    /** Content box for selection and hit testing: the text box plus its background padding. */
    fun localBounds(layer: TextLayer): Rect {
        val box = textBox(layer)
        val pad = layer.style.backgroundPadding
        return Rect(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad)
    }

    private fun typefaceFor(style: TextStyle): Typeface {
        val family = when (style.fontFamily) {
            FontFamily.SANS -> "sans-serif"
            FontFamily.SERIF -> "serif"
            FontFamily.MONOSPACE -> "monospace"
            FontFamily.CASUAL -> "casual"
            FontFamily.CURSIVE -> "cursive"
        }
        val weight = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(family, weight)
    }

    private fun TextAlignment.toLayoutAlignment() = when (this) {
        TextAlignment.START -> Layout.Alignment.ALIGN_NORMAL
        TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        TextAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
    }
}
