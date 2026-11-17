package io.github.drhydr0.layers.render

import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.ShapeLayer
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.interaction.HitTest

/**
 * A [HitTest] that knows every layer type's content box. One definition for the editor
 * and for background work, so selection outlines and thumbnails agree. Use the same
 * [textLayouts] on one thread only.
 */
fun hitTestFor(textLayouts: TextLayouts) = HitTest { doc, layer ->
    when (layer) {
        is ImageLayer -> doc.imageLocalBounds(layer)
        is TextLayer -> textLayouts.localBounds(layer)
        is ShapeLayer -> layer.localBounds
        // HitTest works out a group's box from its children.
        is GroupLayer -> Rect(0.0, 0.0, 0.0, 0.0)
    }
}
