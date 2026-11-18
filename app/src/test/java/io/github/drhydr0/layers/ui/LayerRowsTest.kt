package io.github.drhydr0.layers.ui

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.canvas
import io.github.drhydr0.layers.document.textLayer
import org.junit.Assert.assertEquals
import org.junit.Test

class LayerRowsTest {

    private val group = GroupLayer(LayerId("g"), "g", listOf(textLayer("g1"), textLayer("g2")))
    private val doc = Document(canvas(), listOf(textLayer("a"), group, textLayer("b")))

    private fun ids(rows: List<LayerRow>) = rows.map { "${it.layer.id.value}:${it.depth}" }

    @Test
    fun collapsedGroupsShowAsOneRowTopFirst() {
        assertEquals(listOf("b:0", "g:0", "a:0"), ids(LayerRows.flatten(doc, emptySet())))
    }

    @Test
    fun expandedGroupListsChildrenTopFirstBelowIt() {
        val rows = LayerRows.flatten(doc, setOf(LayerId("g")))
        assertEquals(listOf("b:0", "g:0", "g2:1", "g1:1", "a:0"), ids(rows))
        assertEquals(LayerId("g"), rows[2].parentId)
    }

    @Test
    fun documentIndexCountsOnlySiblings() {
        val rows = LayerRows.flatten(doc, setOf(LayerId("g")))
        // "b" is top of 3 top-level layers; "g1" is bottom of 2 children.
        assertEquals(2, LayerRows.documentIndex(rows, 0))
        assertEquals(1, LayerRows.documentIndex(rows, 1))
        assertEquals(1, LayerRows.documentIndex(rows, 2))
        assertEquals(0, LayerRows.documentIndex(rows, 3))
        assertEquals(0, LayerRows.documentIndex(rows, 4))
    }

    /** Moves the row at [from] to [to], as a drag does, and reads where it lands. */
    private fun drop(expanded: Set<LayerId>, from: Int, to: Int): Pair<String?, Int> {
        val rows = LayerRows.flatten(doc, expanded).toMutableList()
        rows.add(to, rows.removeAt(from))
        val (parent, index) = LayerRows.dropTarget(doc, rows, to)
        return parent?.value to index
    }

    @Test
    fun dropJustUnderAnOpenGroupGoesInsideOnTop() {
        // Rows: b, g, g2, g1, a. Drag "a" (row 4) to just under "g" (row 2).
        assertEquals("g" to 2, drop(setOf(LayerId("g")), 4, 2))
    }

    @Test
    fun dropAmongAGroupsChildrenJoinsThatGroup() {
        // Drag "b" (row 0) to between g2 and g1: under g2, so into g just below g2.
        assertEquals("g" to 1, drop(setOf(LayerId("g")), 0, 2))
        // Further down, under g1 (the bottom child): into g at the very bottom.
        assertEquals("g" to 0, drop(setOf(LayerId("g")), 0, 3))
    }

    @Test
    fun dropAtTheTopOrUnderATopLevelRowLeavesTheGroup() {
        // Drag "g1" (row 3) to the very top.
        assertEquals(null to 3, drop(setOf(LayerId("g")), 3, 0))
        // Drag "g2" (row 2) to the bottom, under "a": top level, just below "a".
        assertEquals(null to 0, drop(setOf(LayerId("g")), 2, 4))
    }

    @Test
    fun dropUnderAClosedGroupStaysBesideIt() {
        // Rows: b, g, a. Drag "b" to under the closed group "g": top level, just below g.
        assertEquals(null to 1, drop(emptySet(), 0, 1))
    }
}
