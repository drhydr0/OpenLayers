package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer

/** Which images a project still needs: those in the document or reachable by undo or redo. */
object AssetUsage {

    fun referenced(document: Document, history: SavedHistory): Set<AssetId> {
        val ids = mutableSetOf<AssetId>()
        document.layers.forEach { ids.addAsset(it) }
        (history.undo + history.redo).forEach { command ->
            when (command) {
                is AddLayers -> {
                    command.placements.forEach { ids.addAsset(it.layer) }
                    command.assets.forEach { ids += it.id }
                }
                is RemoveLayers -> command.removed.forEach { ids.addAsset(it.layer) }
                is UpdateLayer -> {
                    ids.addAsset(command.before)
                    ids.addAsset(command.after)
                }
                // These change position or order only; they carry no layer content.
                is MoveLayer, is TransformLayers -> Unit
                // An unknown command might hold anything: fail rather than delete a needed image.
                else -> throw IllegalArgumentException("Unknown command ${command::class.simpleName}")
            }
        }
        return ids
    }

    private fun MutableSet<AssetId>.addAsset(layer: Layer) {
        if (layer is ImageLayer) add(layer.assetId)
    }
}
