package io.github.drhydr0.layers.document

/** The finite canvas exports are clipped to. A transparent background exports as alpha. */
data class CanvasSpec(val width: Int, val height: Int, val background: Color = Color.WHITE) {
    init {
        require(width > 0 && height > 0) { "canvas size must be positive" }
    }

    val bounds get() = Rect(0.0, 0.0, width.toDouble(), height.toDouble())
}

/**
 * Immutable document state. Every change makes a new Document, so history commands
 * can keep before and after values cheaply (unchanged layers are shared).
 *
 * [layers] is the top level of the layer tree in stacking order, bottom first. Groups hold
 * further layers. A null parent id always means the top level.
 */
data class Document(
    val canvas: CanvasSpec,
    val layers: List<Layer> = emptyList(),
    val assets: Map<AssetId, ImageAsset> = emptyMap(),
) {
    init {
        val all = allLayers().toList()
        require(all.size == all.mapTo(mutableSetOf()) { it.id }.size) { "layer ids must be unique" }
        all.filterIsInstance<ImageLayer>().forEach {
            require(it.assetId in assets) { "image layer ${it.id} references missing asset ${it.assetId}" }
        }
        assets.forEach { (key, asset) -> require(key == asset.id) { "asset stored under wrong key" } }
    }

    /** Every layer in the tree, depth first, parents before their children. */
    fun allLayers(): Sequence<Layer> = layers.asSequence().flatMap { it.selfAndDescendants() }

    fun layer(id: LayerId): Layer? = allLayers().firstOrNull { it.id == id }

    fun requireLayer(id: LayerId): Layer = layer(id) ?: throw NoSuchElementException("no layer $id")

    /** The layers directly inside [parentId] (the top level for null), bottom first. */
    fun children(parentId: LayerId?): List<Layer> =
        if (parentId == null) layers
        else (requireLayer(parentId) as? GroupLayer)?.children ?: throw IllegalArgumentException("$parentId is not a group")

    /** Groups containing [id], outermost first; empty for a top-level layer. Null when absent. */
    fun ancestors(id: LayerId): List<GroupLayer>? {
        fun search(list: List<Layer>, path: List<GroupLayer>): List<GroupLayer>? {
            for (layer in list) {
                if (layer.id == id) return path
                if (layer is GroupLayer) search(layer.children, path + layer)?.let { return it }
            }
            return null
        }
        return search(layers, emptyList())
    }

    /** The id of the group directly containing [id], or null at the top level. */
    fun parentOf(id: LayerId): LayerId? =
        (ancestors(id) ?: throw NoSuchElementException("no layer $id")).lastOrNull()?.id

    /** Position among its siblings; -1 when absent. */
    fun indexOf(id: LayerId): Int {
        val path = ancestors(id) ?: return -1
        return children(path.lastOrNull()?.id).indexOfFirst { it.id == id }
    }

    /** Maps the coordinates inside [parentId] (the top level for null) to canvas coordinates. */
    fun matrixOf(parentId: LayerId?): Matrix {
        if (parentId == null) return Matrix.IDENTITY
        val group = requireLayer(parentId)
        val path = ancestors(parentId).orEmpty() + (group as GroupLayer)
        return path.fold(Matrix.IDENTITY) { m, g -> m * g.transform.toMatrix() }
    }

    /** Drops asset records not in [keep]. Assets used by a layer are always kept. */
    fun withOnlyAssets(keep: Set<AssetId>): Document {
        val used = allLayers().filterIsInstance<ImageLayer>().mapTo(mutableSetOf()) { it.assetId }
        return copy(assets = assets.filterKeys { it in keep || it in used })
    }

    fun withAsset(asset: ImageAsset) = copy(assets = assets + (asset.id to asset))

    fun withLayerInserted(index: Int, layer: Layer): Document = withLayerInserted(null, index, layer)

    fun withLayerInserted(parentId: LayerId?, index: Int, layer: Layer): Document = editChildren(parentId) {
        require(index in 0..it.size) { "index $index out of range" }
        it.toMutableList().apply { add(index, layer) }
    }

    fun withLayerRemoved(id: LayerId): Document {
        val parent = parentOf(id)
        return editChildren(parent) { list -> list.filterNot { it.id == id } }
    }

    /** Replaces the layer with the same id, wherever it is in the tree. */
    fun withLayerReplaced(layer: Layer): Document {
        val parent = parentOf(layer.id)
        return editChildren(parent) { list -> list.map { if (it.id == layer.id) layer else it } }
    }

    /** Moves a layer among its siblings so it ends up at [toIndex]. */
    fun withLayerMoved(id: LayerId, toIndex: Int): Document {
        val parent = parentOf(id)
        return editChildren(parent) { list ->
            val from = list.indexOfFirst { it.id == id }
            require(toIndex in list.indices) { "index $toIndex out of range" }
            list.toMutableList().apply { add(toIndex, removeAt(from)) }
        }
    }

    /** Rebuilds only the path from the root to [parentId]; every other layer object is shared. */
    private fun editChildren(parentId: LayerId?, edit: (List<Layer>) -> List<Layer>): Document {
        if (parentId == null) return copy(layers = edit(layers))
        require(layer(parentId) is GroupLayer) { "$parentId is not a group" }
        fun rebuild(list: List<Layer>): List<Layer> = list.map { layer ->
            when {
                layer !is GroupLayer -> layer
                layer.id == parentId -> layer.copy(children = edit(layer.children))
                layer.selfAndDescendants().any { it.id == parentId } -> layer.copy(children = rebuild(layer.children))
                else -> layer
            }
        }
        return copy(layers = rebuild(layers))
    }

    /**
     * An image layer's content box in its own coordinates, centered on the origin: the
     * cropped part of the image at full resolution. Text needs layout, so the renderer measures it.
     */
    fun imageLocalBounds(layer: ImageLayer): Rect {
        val asset = assets.getValue(layer.assetId)
        return Rect.centered(asset.width * layer.crop.width, asset.height * layer.crop.height)
    }
}
