package io.github.drhydr0.layers.ui

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId

/** One row of the layers panel. [depth] is how many groups the layer sits inside. */
data class LayerRow(val layer: Layer, val depth: Int, val parentId: LayerId?)

/** Flattens the layer tree for the panel. Plain Kotlin, so ordering rules are JVM-tested. */
object LayerRows {

    /** Top of each stack first; a group's children follow it when it is in [expanded]. */
    fun flatten(document: Document, expanded: Set<LayerId>): List<LayerRow> {
        val rows = mutableListOf<LayerRow>()
        fun visit(layers: List<Layer>, depth: Int, parentId: LayerId?) {
            for (layer in layers.asReversed()) {
                rows += LayerRow(layer, depth, parentId)
                if (layer is GroupLayer && layer.id in expanded) visit(layer.children, depth + 1, layer.id)
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
}
