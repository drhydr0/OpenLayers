package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.Grouping
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Stickers
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.document.selfAndDescendants

/**
 * A layer, the group it sits in ([parentId], null for the top level), and its index there.
 */
data class PlacedLayer(val index: Int, val layer: Layer, val parentId: LayerId? = null) {
    companion object {
        fun of(document: Document, id: LayerId) =
            PlacedLayer(document.indexOf(id), document.requireLayer(id), document.parentOf(id))
    }
}

/** Indices must ascend within each parent, so in-order inserts land where intended. */
private fun requireAscending(placements: List<PlacedLayer>) =
    require(placements.groupBy { it.parentId }.values.all { g -> g.zipWithNext().all { (a, b) -> a.index < b.index } }) {
        "indices must ascend within each parent"
    }

private fun Document.inserting(placements: List<PlacedLayer>) =
    placements.fold(this) { doc, p -> doc.withLayerInserted(p.parentId, p.index, p.layer) }

private fun Document.removing(placements: List<PlacedLayer>) =
    placements.fold(this) { doc, p -> doc.withLayerRemoved(p.layer.id) }

/** The given layers as placements, leaving out any that sit inside another given layer. */
private fun placementsOf(document: Document, ids: Collection<LayerId>): List<PlacedLayer> {
    val unique = ids.toSet()
    val outermost = unique.filter { id -> document.ancestors(id).orEmpty().none { it.id in unique } }
    return outermost.map { PlacedLayer.of(document, it) }.sortedBy { it.index }
}

/**
 * Inserts layers, plus any new assets they need. [placements] give final indices in
 * ascending order, so inserting them in order reproduces the intended stack.
 */
data class AddLayers(
    val placements: List<PlacedLayer>,
    val assets: List<ImageAsset> = emptyList(),
    val verb: String = "Add",
) : Command {
    init {
        require(placements.isNotEmpty()) { "nothing to add" }
        requireAscending(placements)
    }

    override val label
        get() = when {
            // Sticker actions read better without a layer count.
            verb.endsWith("sticker") -> verb
            placements.size == 1 -> "$verb layer"
            else -> "$verb ${placements.size} layers"
        }

    override fun apply(document: Document): Document {
        // Assets stay after undo: they are immutable, and redo or history may need them again.
        val withAssets = assets.fold(document) { doc, asset -> doc.withAsset(asset) }
        return withAssets.inserting(placements)
    }

    override fun revert(document: Document): Document = document.removing(placements)

    companion object {
        /** Adds one layer on top of the stack inside [parentId] (the top level for null). */
        fun onTop(document: Document, layer: Layer, asset: ImageAsset? = null, parentId: LayerId? = null) = AddLayers(
            listOf(PlacedLayer(document.children(parentId).size, layer, parentId)),
            listOfNotNull(asset),
        )

        /**
         * An editable sticker: an independent copy of the given sibling layers as one object,
         * stacked directly above the topmost original and shifted by [offset]. The originals
         * stay in place. [center] is the selection's center in the siblings' coordinates.
         */
        fun sticker(
            document: Document,
            ids: Collection<LayerId>,
            center: Vec2,
            offset: Vec2,
            name: String,
            newId: () -> LayerId,
        ): AddLayers {
            val originals = placementsOf(document, ids)
            require(originals.isNotEmpty()) { "nothing to make a sticker from" }
            val parent = originals.first().parentId
            require(originals.all { it.parentId == parent }) { "sticker layers must share a parent" }
            val content = Stickers.contentOf(originals.map { it.layer }, center, name, newId)
            // A single layer keeps its own center; a group sits on the selection's center.
            val origin = originals.singleOrNull()?.layer?.transform?.position ?: center
            val placed = content.withName(name).withTransform(content.transform.translatedBy(origin + offset))
            return AddLayers(listOf(PlacedLayer(originals.last().index + 1, placed, parent)), verb = CREATE_STICKER)
        }

        /** Inserts a saved sticker's new instance on top of the top level. */
        fun insertSticker(document: Document, instance: Layer, assets: List<ImageAsset>) =
            AddLayers(listOf(PlacedLayer(document.layers.size, instance)), assets, INSERT_STICKER)

        const val CREATE_STICKER = "Create sticker"
        const val INSERT_STICKER = "Insert sticker"

        /**
         * Independent copies of the given sibling layers, stacked directly above the topmost
         * original in the same order and shifted by [offset].
         */
        fun duplicates(document: Document, ids: Collection<LayerId>, offset: Vec2, newId: () -> LayerId): AddLayers {
            val originals = placementsOf(document, ids)
            require(originals.isNotEmpty()) { "nothing to duplicate" }
            val parent = originals.first().parentId
            require(originals.all { it.parentId == parent }) { "duplicated layers must share a parent" }
            val top = originals.last().index
            val copies = originals.mapIndexed { i, p ->
                val copy = Grouping.copy(p.layer, newId)
                PlacedLayer(top + 1 + i, copy.withTransform(copy.transform.translatedBy(offset)), parent)
            }
            return AddLayers(copies, verb = "Duplicate")
        }
    }
}

/** Removes layers; undo puts each back at its original place. */
data class RemoveLayers(val removed: List<PlacedLayer>) : Command {
    init {
        require(removed.isNotEmpty()) { "nothing to remove" }
        requireAscending(removed)
    }

    override val label get() = if (removed.size == 1) "Delete layer" else "Delete ${removed.size} layers"

    override fun apply(document: Document): Document = document.removing(removed)

    override fun revert(document: Document): Document = document.inserting(removed)

    companion object {
        fun of(document: Document, ids: Collection<LayerId>): RemoveLayers {
            require(ids.isNotEmpty()) { "nothing to remove" }
            return RemoveLayers(placementsOf(document, ids))
        }
    }
}

/**
 * Swaps one set of layers for another in a single step: grouping (layers out, a group in),
 * ungrouping (a group out, its children in). [removed] holds original places; [added]
 * holds places in the document after the removal.
 */
data class ReplaceLayers(
    val removed: List<PlacedLayer>,
    val added: List<PlacedLayer>,
    override val label: String,
) : Command {
    init {
        require(removed.isNotEmpty() && added.isNotEmpty()) { "nothing to replace" }
        requireAscending(removed)
        requireAscending(added)
    }

    override fun apply(document: Document): Document = document.removing(removed).inserting(added)

    override fun revert(document: Document): Document = document.removing(added).inserting(removed)

    companion object {
        /**
         * Groups sibling layers. The group takes the place of the topmost one, centered on
         * [center] (in the siblings' coordinates), and nothing moves on the canvas.
         */
        fun group(document: Document, ids: Collection<LayerId>, groupId: LayerId, name: String, center: Vec2): ReplaceLayers {
            val members = placementsOf(document, ids)
            require(members.isNotEmpty()) { "nothing to group" }
            val parent = members.first().parentId
            require(members.all { it.parentId == parent }) { "grouped layers must share a parent" }
            val group = Grouping.group(groupId, name, members.map { it.layer }, center)
            // After the members are removed, the topmost one's slot has shifted down by the others below it.
            val index = members.last().index - (members.size - 1)
            return ReplaceLayers(members, listOf(PlacedLayer(index, group, parent)), "Group ${countLabel(members.size)}")
        }

        /** Dissolves a group, leaving its children in its place, looking the same. */
        fun ungroup(document: Document, groupId: LayerId): ReplaceLayers {
            val placed = PlacedLayer.of(document, groupId)
            val group = placed.layer as? GroupLayer ?: throw IllegalArgumentException("$groupId is not a group")
            require(group.children.isNotEmpty()) { "group is empty" }
            val children = Grouping.ungroup(group).mapIndexed { i, child -> PlacedLayer(placed.index + i, child, placed.parentId) }
            return ReplaceLayers(listOf(placed), children, "Ungroup")
        }

        /**
         * Moves a layer into another group, or out to the top level, at [index] among
         * its new siblings (counted after it leaves its old place). It keeps its look
         * and position on the canvas.
         */
        fun moveTo(document: Document, id: LayerId, newParentId: LayerId?, index: Int): ReplaceLayers {
            val from = PlacedLayer.of(document, id)
            require(newParentId == null || document.layer(newParentId) is GroupLayer) { "$newParentId is not a group" }
            require(document.layer(id)?.selfAndDescendants()?.none { it.id == newParentId } ?: false) {
                "a group can't go inside itself"
            }
            val moved = Grouping.reparent(document, from.layer, newParentId)
            val label = if (newParentId == null) "Move out of group" else "Move into group"
            return ReplaceLayers(listOf(from), listOf(PlacedLayer(index, moved, newParentId)), label)
        }

        /**
         * Takes the given layers out of the group holding them, placing them just above
         * it, looking the same. If that would leave the group empty, the group goes too.
         */
        fun removeFromGroup(document: Document, ids: Collection<LayerId>): ReplaceLayers {
            val members = placementsOf(document, ids)
            require(members.isNotEmpty()) { "nothing to remove" }
            val groupId = members.first().parentId ?: throw IllegalArgumentException("layers are not in a group")
            require(members.all { it.parentId == groupId }) { "layers must share a group" }
            val group = PlacedLayer.of(document, groupId)
            val groupLayer = group.layer as GroupLayer
            if (members.size == groupLayer.children.size) return ungroup(document, groupId)
            val added = members.mapIndexed { i, p ->
                PlacedLayer(group.index + 1 + i, Grouping.reparent(document, p.layer, group.parentId), group.parentId)
            }
            return ReplaceLayers(members, added, if (members.size == 1) "Remove from group" else "Remove ${members.size} from group")
        }

        private fun countLabel(count: Int) = if (count == 1) "layer" else "$count layers"
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
            require(toIndex in document.children(document.parentOf(id)).indices) { "index $toIndex out of range" }
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

        fun setVisible(document: Document, id: LayerId, visible: Boolean) =
            of(document, document.requireLayer(id).withVisible(visible), if (visible) "Show layer" else "Hide layer")

        fun setLocked(document: Document, id: LayerId, locked: Boolean) =
            of(document, document.requireLayer(id).withLocked(locked), if (locked) "Lock layer" else "Unlock layer")
    }
}
