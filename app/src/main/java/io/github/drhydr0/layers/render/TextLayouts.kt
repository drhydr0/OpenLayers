package io.github.drhydr0.layers.render

import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.TextAlignment
import io.github.drhydr0.layers.document.TextLayer
import kotlin.math.ceil

/**
 * Lays out text layers. Preview, hit testing, and export all measure text here, so
 * wrapping and bounds are identical everywhere.
 *
 * Not thread-safe (layouts share a paint): use one instance per thread, e.g. a separate
 * one for a background export.
 */
class TextLayouts(maxEntries: Int = 256) {

    private data class Key(val text: String, val fontSize: Double, val alignment: TextAlignment, val boxWidth: Double)

    private val cache = LruCache<Key, StaticLayout>(maxEntries)

    fun layout(layer: TextLayer): StaticLayout {
        val key = Key(layer.text, layer.style.fontSize, layer.style.alignment, layer.boxWidth)
        cache.get(key)?.let { return it }
        // Color is applied at draw time, so color changes don't need a new layout.
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = layer.style.fontSize.toFloat() }
        val width = ceil(layer.boxWidth).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, paint, width)
            .setAlignment(layer.style.alignment.toLayoutAlignment())
            .setIncludePad(false)
            .build()
        cache.put(key, layout)
        return layout
    }

    /** Content box in the layer's own coordinates, centered on its origin. */
    fun localBounds(layer: TextLayer): Rect {
        val height = layout(layer).height.toDouble().coerceAtLeast(layer.style.fontSize)
        return Rect.centered(layer.boxWidth, height)
    }

    private fun TextAlignment.toLayoutAlignment() = when (this) {
        TextAlignment.START -> Layout.Alignment.ALIGN_NORMAL
        TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        TextAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
    }
}
