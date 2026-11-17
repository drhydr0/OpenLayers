package io.github.drhydr0.layers.interaction

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.Vec2

/**
 * Finds layers under a canvas point. [localBounds] gives each layer's content box in its
 * own coordinates (images from their asset, text from the shared text layout).
 */
class HitTest(private val localBounds: (Document, Layer) -> Rect) {

    /** The topmost visible, unlocked layer under [point], or null. */
    fun topmost(document: Document, point: Vec2): Layer? =
        document.layers.asReversed().firstOrNull { it.visible && !it.locked && contains(document, it, point) }

    fun contains(document: Document, layer: Layer, point: Vec2, slop: Double = 0.0): Boolean {
        val local = layer.transform.toMatrix().inverted()?.map(point) ?: return false
        val b = localBounds(document, layer)
        // Slop is in canvas pixels; divide by scale so small layers stay easy to grab.
        val sx = slop / layer.transform.scaleX
        val sy = slop / layer.transform.scaleY
        return local.x in (b.left - sx)..(b.right + sx) && local.y in (b.top - sy)..(b.bottom + sy)
    }

    /** The layer's four corners on the canvas: top-left, top-right, bottom-right, bottom-left. */
    fun corners(document: Document, layer: Layer): List<Vec2> {
        val b = localBounds(document, layer)
        val m = layer.transform.toMatrix()
        return listOf(
            m.map(Vec2(b.left, b.top)), m.map(Vec2(b.right, b.top)),
            m.map(Vec2(b.right, b.bottom)), m.map(Vec2(b.left, b.bottom)),
        )
    }

    fun find(document: Document, id: LayerId?) = id?.let { document.layer(it) }
}
