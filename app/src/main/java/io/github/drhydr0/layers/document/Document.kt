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
 * [layers] is in stacking order, bottom first.
 */
data class Document(
    val canvas: CanvasSpec,
    val layers: List<Layer> = emptyList(),
    val assets: Map<AssetId, ImageAsset> = emptyMap(),
) {
    init {
        val ids = layers.map { it.id }
        require(ids.size == ids.toSet().size) { "layer ids must be unique" }
        layers.filterIsInstance<ImageLayer>().forEach {
            require(it.assetId in assets) { "image layer ${it.id} references missing asset ${it.assetId}" }
        }
        assets.forEach { (key, asset) -> require(key == asset.id) { "asset stored under wrong key" } }
    }

    fun layer(id: LayerId): Layer? = layers.firstOrNull { it.id == id }

    fun requireLayer(id: LayerId): Layer = layer(id) ?: throw NoSuchElementException("no layer $id")

    /** -1 when absent. */
    fun indexOf(id: LayerId): Int = layers.indexOfFirst { it.id == id }

    /** Drops asset records not in [keep]. Assets used by a layer are always kept. */
    fun withOnlyAssets(keep: Set<AssetId>): Document {
        val used = layers.filterIsInstance<ImageLayer>().mapTo(mutableSetOf()) { it.assetId }
        return copy(assets = assets.filterKeys { it in keep || it in used })
    }

    fun withAsset(asset: ImageAsset) = copy(assets = assets + (asset.id to asset))

    fun withLayerInserted(index: Int, layer: Layer): Document {
        require(index in 0..layers.size) { "index $index out of range" }
        return copy(layers = layers.toMutableList().apply { add(index, layer) })
    }

    fun withLayerRemoved(id: LayerId): Document {
        val index = indexOf(id)
        require(index >= 0) { "no layer $id" }
        return copy(layers = layers.toMutableList().apply { removeAt(index) })
    }

    fun withLayerReplaced(layer: Layer): Document {
        val index = indexOf(layer.id)
        require(index >= 0) { "no layer ${layer.id}" }
        return copy(layers = layers.toMutableList().apply { set(index, layer) })
    }

    /** Moves a layer so it ends up at [toIndex] in the new stacking order. */
    fun withLayerMoved(id: LayerId, toIndex: Int): Document {
        val from = indexOf(id)
        require(from >= 0) { "no layer $id" }
        require(toIndex in layers.indices) { "index $toIndex out of range" }
        return copy(layers = layers.toMutableList().apply { add(toIndex, removeAt(from)) })
    }

    /** An image layer's content box in its own coordinates, centered on the origin. Text needs layout, so the renderer measures it. */
    fun imageLocalBounds(layer: ImageLayer): Rect {
        val asset = assets.getValue(layer.assetId)
        return Rect.centered(asset.width.toDouble(), asset.height.toDouble())
    }
}
