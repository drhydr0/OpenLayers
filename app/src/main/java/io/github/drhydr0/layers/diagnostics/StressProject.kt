package io.github.drhydr0.layers.diagnostics

import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Placement
import io.github.drhydr0.layers.document.ShapeKind
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.history.AddLayers
import io.github.drhydr0.layers.history.History
import io.github.drhydr0.layers.history.MoveLayer
import io.github.drhydr0.layers.history.TransformLayers
import io.github.drhydr0.layers.history.UpdateLayer
import kotlin.random.Random

/**
 * Builds the design doc's capacity workload: a 300-layer mixed composition with nested
 * editable stickers and 1,000 history actions. Used by the JVM capacity test and by the
 * debug-only button that creates the same project on a phone. Plain Kotlin; the caller
 * supplies the images.
 */
object StressProject {

    const val LAYERS = 300
    const val ACTIONS = 1_000
    val CANVAS = CanvasSpec(2160, 2160)

    /** [assets] must already be stored for the project; at least one is needed. */
    fun build(assets: List<ImageAsset>, seed: Int = 7): History {
        require(assets.isNotEmpty()) { "the stress project needs at least one image" }
        val random = Random(seed)
        var next = 0
        fun id() = LayerId("stress-${next++}")
        fun spot() = Vec2(random.nextDouble(0.0, CANVAS.width.toDouble()), random.nextDouble(0.0, CANVAS.height.toDouble()))
        fun leaf(i: Int): Layer = when (i % 3) {
            0 -> assets[i % assets.size].let { asset ->
                // About a seventh of the canvas wide, whatever the image's size.
                val scale = CANVAS.width / 7.0 / asset.width
                ImageLayer(id(), "Image $i", asset.id, Transform(spot(), scale, scale, random.nextDouble(-90.0, 90.0)))
            }
            1 -> TextLayer(id(), "Text $i", "Label number $i\nsecond line", boxWidth = 600.0, transform = Transform(spot()))
            else -> Placement.defaultShape(id(), "Shape $i", ShapeKind.entries[i % ShapeKind.entries.size], Placement.frame(CANVAS, null))
                .let { it.copy(width = it.width / 3, height = if (it.isLine) it.height else it.height / 3, transform = Transform(spot())) }
        }

        val history = History(Document(CANVAS), maxSteps = ACTIONS)
        // 216 loose layers, plus 12 nested stickers of 7 (5 layers and the 2 groups holding them).
        repeat(216) { i -> history.execute(AddLayers.onTop(history.document, leaf(i), assets[i % assets.size])) }
        repeat(12) { s ->
            val inner = GroupLayer(id(), "Inner $s", List(2) { leaf(1000 + s * 5 + it) })
            val sticker = GroupLayer(id(), "Sticker $s", List(3) { leaf(2000 + s * 5 + it) } + inner, Transform(spot(), 0.5, 0.5))
            history.execute(AddLayers.insertSticker(history.document, sticker, assets))
        }
        // The rest: everyday edits, some on several layers, some inside groups.
        var step = 0
        while (history.size < ACTIONS) {
            val doc = history.document
            val top = doc.layers
            val any = doc.allLayers().toList()
            val command = when (step++ % 6) {
                0 -> top.random(random).let { TransformLayers.of(doc, mapOf(it.id to it.transform.copy(position = spot())), "Move") }
                1 -> TransformLayers.of(doc, top.shuffled(random).take(5).associate { it.id to it.transform.translatedBy(Vec2(7.0, -3.0)) }, "Move")
                2 -> UpdateLayer.rename(doc, any.random(random).id, "Renamed $step")
                3 -> MoveLayer.of(doc, top.random(random).id, random.nextInt(top.size))
                4 -> any.filterIsInstance<TextLayer>().random(random).let { UpdateLayer.editText(doc, it.copy(text = "Edited $step")) }
                else -> UpdateLayer.setVisible(doc, any.random(random).id, step % 12 != 5)
            }
            history.execute(command)
        }
        return history
    }
}
