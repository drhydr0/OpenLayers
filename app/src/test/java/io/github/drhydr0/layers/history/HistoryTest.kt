package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.document.canvas
import io.github.drhydr0.layers.document.textLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HistoryTest {

    private val empty = Document(canvas())

    @Test
    fun undoAndRedoStepThroughStates() {
        val history = History(empty)
        history.execute(AddLayers.onTop(history.document, textLayer("a")))
        val afterAdd = history.document
        history.execute(UpdateLayer.rename(history.document, LayerId("a"), "Title"))
        val afterRename = history.document

        assertEquals("Rename layer", history.undoLabel)
        assertTrue(history.undo())
        assertEquals(afterAdd, history.document)
        assertEquals("Rename layer", history.redoLabel)
        assertTrue(history.undo())
        assertEquals(empty, history.document)
        assertFalse(history.undo())

        assertTrue(history.redo())
        assertTrue(history.redo())
        assertEquals(afterRename, history.document)
        assertFalse(history.redo())
    }

    @Test
    fun newEditAfterUndoClearsRedo() {
        val history = History(empty)
        history.execute(AddLayers.onTop(history.document, textLayer("a")))
        history.execute(AddLayers.onTop(history.document, textLayer("b")))
        history.undo()
        assertTrue(history.canRedo)
        history.execute(AddLayers.onTop(history.document, textLayer("c")))
        assertFalse(history.canRedo)
        assertNull(history.redoLabel)
        assertEquals(listOf("a", "c"), history.document.layers.map { it.id.value })
    }

    @Test
    fun noOpUsesNoUndoStep() {
        val history = History(empty)
        history.execute(AddLayers.onTop(history.document, textLayer("a")))
        val version = history.version
        val unchanged = mapOf(LayerId("a") to Transform.IDENTITY)
        assertFalse(history.execute(TransformLayers.of(history.document, unchanged)))
        assertEquals(1, history.size)
        assertEquals(version, history.version)
    }

    @Test
    fun versionAndListenersTrackEveryChange() {
        val history = History(empty)
        var calls = 0
        history.addListener { calls++ }
        history.execute(AddLayers.onTop(history.document, textLayer("a")))
        history.undo()
        history.redo()
        assertEquals(3L, history.version)
        assertEquals(3, calls)
    }

    /** Long random edit sessions must undo back to the exact start and redo to the exact end. */
    @Test
    fun randomSessionsUndoAndRedoExactly() {
        repeat(20) { seed ->
            val random = Random(seed)
            val history = History(empty)
            val states = mutableListOf(history.document)
            var next = 0
            repeat(200) {
                val doc = history.document
                val command = randomCommand(random, doc) { "L${next++}" }
                if (history.execute(command)) states += history.document
            }
            for (i in states.indices.reversed().drop(1)) {
                assertTrue(history.undo())
                assertEquals("seed $seed step $i", states[i], history.document)
            }
            for (i in 1 until states.size) {
                assertTrue(history.redo())
                assertEquals("seed $seed step $i", states[i], history.document)
            }
        }
    }

    private fun randomCommand(random: Random, doc: Document, newId: () -> String): Command {
        val ids = doc.layers.map { it.id }
        if (ids.size < 2) return AddLayers.onTop(doc, textLayer(newId()))
        return when (random.nextInt(6)) {
            0 -> AddLayers(listOf(PlacedLayer(random.nextInt(ids.size + 1), textLayer(newId()))))
            1 -> RemoveLayers.of(doc, ids.shuffled(random).take(random.nextInt(1, ids.size)))
            2 -> MoveLayer.of(doc, ids.random(random), random.nextInt(ids.size))
            3 -> TransformLayers.of(
                doc,
                ids.shuffled(random).take(random.nextInt(1, ids.size + 1)).associateWith {
                    Transform(
                        position = Vec2(random.nextDouble(-500.0, 1500.0), random.nextDouble(-500.0, 1500.0)),
                        scaleX = random.nextDouble(0.1, 5.0),
                        scaleY = random.nextDouble(0.1, 5.0),
                        rotationDegrees = random.nextDouble(-180.0, 180.0),
                    )
                },
            )
            4 -> UpdateLayer.rename(doc, ids.random(random), "Name ${random.nextInt()}")
            else -> AddLayers.onTop(doc, textLayer(newId()))
        }
    }
}
