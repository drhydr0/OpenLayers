package io.github.drhydr0.layers.ui

import io.github.drhydr0.layers.document.Color

/** Colors offered on the text sheet. Full color picking arrives with text styling (M6). */
object TextPalette {
    val colors: List<Color> = listOf(
        Color.BLACK,
        Color.WHITE,
        Color(0xFF9E9E9E.toInt()),
        Color(0xFFE53935.toInt()),
        Color(0xFFFB8C00.toInt()),
        Color(0xFFFDD835.toInt()),
        Color(0xFF43A047.toInt()),
        Color(0xFF1E88E5.toInt()),
        Color(0xFF8E24AA.toInt()),
        Color(0xFFD81B60.toInt()),
    )
}
