package io.github.drhydr0.layers.interaction

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.Vec2

/**
 * Finds layers under a canvas point. [leafBounds] gives an image or text layer's content
 * box in its own coordinates (images from their asset, text from the shared text layout).
 * A group is hit where any of its visible children is hit.
 */
class HitTest(private val leafBounds: (Document, Layer) -> Rect) {

    /**
     * The topmost visible, unlocked layer under [point] among the layers directly inside
     * [scopeId] (the top level for null). A group counts as one object.
     */
    fun topmost(document: Document, point: Vec2, scopeId: LayerId? = null): Layer? {
        val local = document.matrixOf(scopeId).inverted()?.map(point) ?: return null
        return document.children(scopeId).asReversed()
            .firstOrNull { it.visible && !it.locked && containsLocal(document, it, local, 0.0) }
    }

    /** Whether [point] (canvas coordinates) is on [layer], with [slop] canvas pixels of margin. */
    fun contains(document: Document, layer: Layer, point: Vec2, slop: Double = 0.0): Boolean {
        val path = document.ancestors(layer.id) ?: return false
        val local = document.matrixOf(path.lastOrNull()?.id).inverted()?.map(point) ?: return false
        return containsLocal(document, layer, local, slop)
    }

    /** [point] is in the coordinates of the layer's parent. */
    private fun containsLocal(document: Document, layer: Layer, point: Vec2, slop: Double): Boolean {
        val local = layer.transform.toMatrix().inverted()?.map(point) ?: return false
        if (layer is GroupLayer) {
            val inner = slop / maxOf(layer.transform.scaleX, layer.transform.scaleY)
            return layer.children.any { it.visible && containsLocal(document, it, local, inner) }
        }
        val b = leafBounds(document, layer)
        // Slop is in the parent's pixels; divide by scale so small layers stay easy to grab.
        val sx = slop / layer.transform.scaleX
        val sy = slop / layer.transform.scaleY
        return local.x in (b.left - sx)..(b.right + sx) && local.y in (b.top - sy)..(b.bottom + sy)
    }

    /** The layer's content box in its own coordinates. A group's box encloses its children. */
    fun localBounds(document: Document, layer: Layer): Rect {
        if (layer !is GroupLayer) return leafBounds(document, layer)
        return layer.children
            .map { it.transform.toMatrix().mapRect(localBounds(document, it)) }
            .reduceOrNull(Rect::union) ?: Rect(0.0, 0.0, 0.0, 0.0)
    }

    /** The layer's four corners on the canvas: top-left, top-right, bottom-right, bottom-left. */
    fun corners(document: Document, layer: Layer): List<Vec2> {
        val b = localBounds(document, layer)
        val parent = document.ancestors(layer.id)?.lastOrNull()?.id
        val m = document.matrixOf(parent) * layer.transform.toMatrix()
        return listOf(
            m.map(Vec2(b.left, b.top)), m.map(Vec2(b.right, b.top)),
            m.map(Vec2(b.right, b.bottom)), m.map(Vec2(b.left, b.bottom)),
        )
    }
}
