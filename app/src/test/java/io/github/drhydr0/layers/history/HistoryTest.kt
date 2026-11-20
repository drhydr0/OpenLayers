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

    @Test
    fun restoredHistoryContinuesWhereItLeftOff() {
        val first = History(empty)
        first.execute(AddLayers.onTop(first.document, textLayer("a")))
        first.execute(AddLayers.onTop(first.document, textLayer("b")))
        first.execute(UpdateLayer.rename(first.document, LayerId("a"), "Title"))
        first.undo()
        val afterTwo = first.document

        // A new History from the saved pieces stands in for reopening the app.
        val reopened = History(first.document, first.snapshot())
        assertEquals("Add layer", reopened.undoLabel)
        assertEquals("Rename layer", reopened.redoLabel)
        assertEquals(listOf("Add layer", "Add layer", "Rename layer", "Undo Rename layer"), reopened.journalLabels())
        assertTrue(reopened.redo())
        assertEquals("Title", reopened.document.requireLayer(LayerId("a")).name)
        reopened.undo()
        assertEquals(afterTwo, reopened.document)
        reopened.undo()
        reopened.undo()
        assertEquals(empty, reopened.document)
    }

    @Test
    fun journalRecordsEveryUndoAndRedo() {
        val history = History(empty)
        history.execute(AddLayers.onTop(history.document, textLayer("a")))
        history.execute(UpdateLayer.rename(history.document, LayerId("a"), "Title"))
        history.undo()
        history.redo()
        history.undo()
        assertEquals(
            listOf("Add layer", "Rename layer", "Undo Rename layer", "Redo Rename layer", "Undo Rename layer"),
            history.journalLabels(),
        )
    }

    @Test
    fun goingBackReachesStatesOnABranchALaterEditReplaced() {
        val history = History(empty)
        history.execute(AddLayers.onTop(history.document, textLayer("a")))
        history.execute(AddLayers.onTop(history.document, textLayer("b")))
        val withB = history.document
        history.undo()
        // A new edit after undo: ordinary redo of "b" is gone...
        history.execute(AddLayers.onTop(history.document, textLayer("c")))
        assertFalse(history.canRedo)
        // ...but the journal still holds the state with "b" (after entry 2), so it can come back.
        var calls = 0
        history.addListener { calls++ }
        assertTrue(history.goTo(2))
        assertEquals(withB, history.document)
        assertEquals(1, calls)
        assertEquals("Go back to: Add layer", history.journalLabels().last())
        // Going back is itself undoable, returning to the state with "c".
        assertTrue(history.undo())
        assertEquals(listOf("a", "c"), history.document.layers.map { it.id.value })
        // And the oldest state is reachable too.
        assertTrue(history.goTo(0))
        assertEquals(empty, history.document)
        assertFalse(history.goTo(history.journalLabels().size))
    }

    @Test
    fun everyJournalStateIsReachableAfterRandomSessions() {
        repeat(10) { seed ->
            val random = Random(seed)
            val history = History(empty)
            val states = mutableListOf(history.document)
            var next = 0
            repeat(80) {
                when (random.nextInt(4)) {
                    0 -> history.undo()
                    1 -> history.redo()
                    else -> history.execute(randomCommand(random, history.document) { "L${next++}" })
                }
                // Track the state after each journal entry.
                while (states.size < history.journalLabels().size + 1) states += history.document
            }
            val count = history.journalLabels().size
            for (k in listOf(0, count / 3, count / 2, count - 1)) {
                val reopened = History(history.document, history.snapshot())
                reopened.goTo(k)
                assertEquals("seed $seed entry $k", states[k], reopened.document)
            }
        }
    }

    @Test
    fun oldestStepsAreDroppedBeyondTheCap() {
        val history = History(empty, maxSteps = 3)
        repeat(5) { history.execute(AddLayers.onTop(history.document, textLayer("L$it"))) }
        assertEquals(3, history.snapshot().undo.size)
        repeat(3) { assertTrue(history.undo()) }
        assertFalse(history.undo())
        // The two oldest adds can no longer be undone, and their layers remain.
        assertEquals(listOf("L0", "L1"), history.document.layers.map { it.id.value })
        // A saved history longer than the cap keeps its newest steps.
        val long = SavedHistory(undo = List(5) { UpdateLayer.rename(history.document, LayerId("L0"), "n$it") })
        assertEquals(2, History(history.document, long, maxSteps = 2).snapshot().undo.size)
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
