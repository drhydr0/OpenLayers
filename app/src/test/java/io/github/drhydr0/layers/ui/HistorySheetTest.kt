package io.github.drhydr0.layers.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySheetTest {

    private val rows = HistorySheet.rows(listOf("Add layer", "Move layer", "Change text"), "Oldest")

    @Test
    fun newestActionIsFirstAndOldestStateLast() {
        assertEquals(listOf("Change text", "Move layer", "Add layer", "Oldest"), rows)
    }

    @Test
    fun rowsAndPositionsMapBothWays() {
        // Position 3 = all three steps applied = top row.
        assertEquals(0, HistorySheet.rowFor(3, rows.size))
        assertEquals(3, HistorySheet.rowFor(0, rows.size))
        for (position in 0..3) {
            assertEquals(position, HistorySheet.positionFor(HistorySheet.rowFor(position, rows.size), rows.size))
        }
        // Tapping "Move layer" (row 1) goes to the state right after it: 2 steps applied.
        assertEquals(2, HistorySheet.positionFor(1, rows.size))
    }
}
