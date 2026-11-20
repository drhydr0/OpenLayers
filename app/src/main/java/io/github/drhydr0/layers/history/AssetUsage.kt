package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.selfAndDescendants

/** Which images a project still needs: those in the document or reachable by undo or redo. */
object AssetUsage {

    fun referenced(document: Document, history: SavedHistory): Set<AssetId> {
        val ids = mutableSetOf<AssetId>()
        document.layers.forEach { ids.addAsset(it) }
        (history.undo + history.redo + history.journal.map { it.command }).forEach { addCommand(ids, it) }
        return ids
    }

    private fun addCommand(ids: MutableSet<AssetId>, command: Command) {
        when (command) {
            is AddLayers -> {
                command.placements.forEach { ids.addAsset(it.layer) }
                command.assets.forEach { ids += it.id }
            }
            is RemoveLayers -> command.removed.forEach { ids.addAsset(it.layer) }
            is ReplaceLayers -> (command.removed + command.added).forEach { ids.addAsset(it.layer) }
            is UpdateLayer -> {
                ids.addAsset(command.before)
                ids.addAsset(command.after)
            }
            is InverseCommand -> addCommand(ids, command.of)
            is CommandSequence -> command.commands.forEach { addCommand(ids, it) }
            // These change position or order only; they carry no layer content.
            is MoveLayer, is TransformLayers -> Unit
            // An unknown command might hold anything: fail rather than delete a needed image.
            else -> throw IllegalArgumentException("Unknown command ${command::class.simpleName}")
        }
    }

    /** Includes images anywhere inside a group. */
    private fun MutableSet<AssetId>.addAsset(layer: Layer) {
        layer.selfAndDescendants().filterIsInstance<ImageLayer>().forEach { add(it.assetId) }
    }
}
