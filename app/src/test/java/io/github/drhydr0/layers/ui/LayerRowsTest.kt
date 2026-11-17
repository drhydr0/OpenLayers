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
}
