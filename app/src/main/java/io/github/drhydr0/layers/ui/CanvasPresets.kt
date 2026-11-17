package io.github.drhydr0.layers.ui

import io.github.drhydr0.layers.document.Color

/** Starting canvas sizes on the new-project sheet. Kept free of Android types for JVM tests. */
enum class CanvasPreset(val width: Int?, val height: Int?) {
    SQUARE(1080, 1080),
    PORTRAIT(1080, 1350),
    STORY(1080, 1920),
    LANDSCAPE(1920, 1080),
    CUSTOM(null, null),
}

enum class CanvasBackground(val color: Color) {
    WHITE(Color.WHITE),
    BLACK(Color.BLACK),
    TRANSPARENT(Color.TRANSPARENT),
}

object CanvasSizeRules {
    /**
     * Upper bound per side. 8192 x 8192 ARGB is 256 MB uncompressed, already near what a
     * phone can export; capacity work in M7 may tune it from measurements.
     */
    const val MAX_SIDE = 8192
    const val MIN_SIDE = 16

    /** Parsed size, or null when either side is missing or out of range. */
    fun parse(width: String, height: String): Pair<Int, Int>? {
        val w = width.trim().toIntOrNull() ?: return null
        val h = height.trim().toIntOrNull() ?: return null
        val range = MIN_SIDE..MAX_SIDE
        return if (w in range && h in range) w to h else null
    }
}
