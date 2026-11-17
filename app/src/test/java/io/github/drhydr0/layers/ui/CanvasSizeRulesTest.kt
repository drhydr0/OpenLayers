package io.github.drhydr0.layers.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanvasSizeRulesTest {

    @Test
    fun acceptsSizesInRange() {
        assertEquals(1080 to 1920, CanvasSizeRules.parse("1080", " 1920 "))
        assertEquals(16 to 8192, CanvasSizeRules.parse("16", "8192"))
    }

    @Test
    fun rejectsMissingOrOutOfRange() {
        assertNull(CanvasSizeRules.parse("", "100"))
        assertNull(CanvasSizeRules.parse("abc", "100"))
        assertNull(CanvasSizeRules.parse("15", "100"))
        assertNull(CanvasSizeRules.parse("100", "8193"))
        assertNull(CanvasSizeRules.parse("-5", "100"))
    }

    @Test
    fun presetsHaveSizesExceptCustom() {
        CanvasPreset.entries.filter { it != CanvasPreset.CUSTOM }.forEach {
            assertEquals(true, it.width != null && it.height != null)
        }
        assertNull(CanvasPreset.CUSTOM.width)
    }
}
