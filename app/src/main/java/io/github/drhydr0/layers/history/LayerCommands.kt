package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.Transform

/** A layer and the stacking index it occupies. */
data class PlacedLayer(val index: Int, val layer: Layer)

/**
 * Inserts layers, plus any new assets they need. [placements] give final indices in
 * ascending order, so inserting them in order reproduces the intended stack.
 */
data class AddLayers(
    val placements: List<PlacedLayer>,
    val assets: List<ImageAsset> = emptyList(),
) : Command {
    init {
        require(placements.isNotEmpty()) { "nothing to add" }
        require(placements.zipWithNext().all { (a, b) -> a.index < b.index }) { "indices must ascend" }
    }

    override val label get() = if (placements.size == 1) "Add layer" else "Add ${placements.size} layers"

    override fun apply(document: Document): Document {
        // Assets stay after undo: they are immutable, and redo or history may need them again.
        val withAssets = assets.fold(document) { doc, asset -> doc.withAsset(asset) }
        return placements.fold(withAssets) { doc, p -> doc.withLayerInserted(p.index, p.layer) }
    }

    override fun revert(document: Document): Document =
        placements.fold(document) { doc, p -> doc.withLayerRemoved(p.layer.id) }

    companion object {
        /** Adds one layer on top of the stack. */
        fun onTop(document: Document, layer: Layer, asset: ImageAsset? = null) =
            AddLayers(listOf(PlacedLayer(document.layers.size, layer)), listOfNotNull(asset))
    }
}

/** Removes layers; undo puts each back at its original index. */
data class RemoveLayers(val removed: List<PlacedLayer>) : Command {
    init {
        require(removed.isNotEmpty()) { "nothing to remove" }
        // Ascending order makes revert's in-order inserts land on the original indices.
        require(removed.zipWithNext().all { (a, b) -> a.index < b.index }) { "indices must ascend" }
    }

    override val label get() = if (removed.size == 1) "Delete layer" else "Delete ${removed.size} layers"

    override fun apply(document: Document): Document =
        removed.fold(document) { doc, p -> doc.withLayerRemoved(p.layer.id) }

    override fun revert(document: Document): Document =
        removed.fold(document) { doc, p -> doc.withLayerInserted(p.index, p.layer) }

    companion object {
        fun of(document: Document, ids: Collection<LayerId>): RemoveLayers {
            require(ids.isNotEmpty()) { "nothing to remove" }
            val placed = ids.toSet().map { PlacedLayer(document.indexOf(it), document.requireLayer(it)) }
            return RemoveLayers(placed.sortedBy { it.index })
        }
    }
}

/** Changes one layer's position in the stack. */
data class MoveLayer(
    val id: LayerId,
    val from: Int,
    val to: Int,
) : Command {
    override val label get() = "Reorder layer"
    override val isNoOp get() = from == to

    override fun apply(document: Document) = document.withLayerMoved(id, to)
    override fun revert(document: Document) = document.withLayerMoved(id, from)

    companion object {
        fun of(document: Document, id: LayerId, toIndex: Int): MoveLayer {
            val from = document.indexOf(id)
            require(from >= 0) { "no layer $id" }
            require(toIndex in document.layers.indices) { "index $toIndex out of range" }
            return MoveLayer(id, from, toIndex)
        }
    }
}

/** Sets transforms on one or more layers: one undo step for a whole gesture. */
data class TransformLayers(
    val before: Map<LayerId, Transform>,
    val after: Map<LayerId, Transform>,
    override val label: String,
) : Command {
    init {
        require(before.keys == after.keys) { "before and after must cover the same layers" }
    }

    override val isNoOp get() = before == after

    override fun apply(document: Document) = set(document, after)
    override fun revert(document: Document) = set(document, before)

    private fun set(document: Document, transforms: Map<LayerId, Transform>) =
        transforms.entries.fold(document) { doc, (id, t) ->
            doc.withLayerReplaced(doc.requireLayer(id).withTransform(t))
        }

    companion object {
        fun of(document: Document, after: Map<LayerId, Transform>, verb: String = "Transform"): TransformLayers {
            require(after.isNotEmpty()) { "nothing to transform" }
            val before = after.keys.associateWith { document.requireLayer(it).transform }
            val label = if (after.size == 1) "$verb layer" else "$verb ${after.size} layers"
            return TransformLayers(before, after, label)
        }
    }
}

/** Replaces a layer's properties (text, style, name, ...) while keeping its identity and place. */
data class UpdateLayer(
    val before: Layer,
    val after: Layer,
    override val label: String,
) : Command {
    init {
        require(before.id == after.id) { "a layer update must keep the layer id" }
    }

    override val isNoOp get() = before == after

    override fun apply(document: Document) = document.withLayerReplaced(after)
    override fun revert(document: Document) = document.withLayerReplaced(before)

    companion object {
        fun of(document: Document, after: Layer, label: String) =
            UpdateLayer(document.requireLayer(after.id), after, label)

        fun rename(document: Document, id: LayerId, name: String) =
            of(document, document.requireLayer(id).withName(name), "Rename layer")

        fun editText(document: Document, after: TextLayer) = of(document, after, "Change text")
    }
}
