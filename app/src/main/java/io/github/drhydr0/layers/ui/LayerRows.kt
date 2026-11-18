package io.github.drhydr0.layers.ui

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId

/** One row of the layers panel. [depth] is how many groups the layer sits inside. */
data class LayerRow(val layer: Layer, val depth: Int, val parentId: LayerId?, val expanded: Boolean = false)

/** Flattens the layer tree for the panel. Plain Kotlin, so ordering rules are JVM-tested. */
object LayerRows {

    /** Top of each stack first; a group's children follow it when it is in [expanded]. */
    fun flatten(document: Document, expanded: Set<LayerId>): List<LayerRow> {
        val rows = mutableListOf<LayerRow>()
        fun visit(layers: List<Layer>, depth: Int, parentId: LayerId?) {
            for (layer in layers.asReversed()) {
                val open = layer is GroupLayer && layer.id in expanded
                rows += LayerRow(layer, depth, parentId, open)
                if (layer is GroupLayer && open) visit(layer.children, depth + 1, layer.id)
            }
        }
        visit(document.layers, 0, null)
        return rows
    }

    /**
     * Where the row at [position] now sits among its siblings, as a document index (bottom
     * first), after rows were reordered by dragging.
     */
    fun documentIndex(rows: List<LayerRow>, position: Int): Int {
        val row = rows[position]
        val siblings = rows.withIndex().filter { it.value.parentId == row.parentId && it.value.depth == row.depth }
        val fromTop = siblings.indexOfFirst { it.index == position }
        return siblings.size - 1 - fromTop
    }

    /**
     * Where a dragged row lands, read from the row now just above it: right under an
     * open group's row means into that group, on top; under any other row means the same
     * group as that row, just below it; at the very top means the top of the stack.
     * Returns the new parent (null for the top level) and the index among the new
     * siblings, counted after the layer leaves its old place.
     */
    fun dropTarget(document: Document, rows: List<LayerRow>, position: Int): Pair<LayerId?, Int> {
        val dragged = rows[position].layer.id
        fun siblingsWithout(parent: LayerId?) = document.children(parent).filter { it.id != dragged }
        val above = rows.getOrNull(position - 1)
        return when {
            above == null -> null to siblingsWithout(null).size
            above.expanded -> above.layer.id to siblingsWithout(above.layer.id).size
            else -> {
                val siblings = siblingsWithout(above.parentId)
                above.parentId to siblings.indexOfFirst { it.id == above.layer.id }.coerceAtLeast(0)
            }
        }
    }
}
