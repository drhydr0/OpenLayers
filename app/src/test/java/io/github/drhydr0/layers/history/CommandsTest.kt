package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.document.canvas
import io.github.drhydr0.layers.document.imageAsset
import io.github.drhydr0.layers.document.imageLayer
import io.github.drhydr0.layers.document.textLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandsTest {

    private val asset = imageAsset()
    private val base = Document(canvas())
        .withAsset(asset)
        .withLayerInserted(0, imageLayer("a", asset))
        .withLayerInserted(1, textLayer("b"))
        .withLayerInserted(2, textLayer("c"))
        .withLayerInserted(3, textLayer("d"))

    private fun ids(d: Document) = d.layers.map { it.id.value }

    /** Applies then reverts, checking the after state and an exact return to [base]. */
    private fun roundTrip(command: Command, check: (Document) -> Unit) {
        val after = command.apply(base)
        check(after)
        assertEquals(base, command.revert(after))
        // Redo after undo gives the same result again.
        assertEquals(after, command.apply(command.revert(after)))
    }

    @Test
    fun addOneOnTopWithNewAsset() {
        val newAsset = imageAsset('f')
        val layer = imageLayer("x", newAsset)
        val after = AddLayers.onTop(base, layer, newAsset).apply(base)
        assertEquals(listOf("a", "b", "c", "d", "x"), ids(after))
        assertTrue(newAsset.id in after.assets)
        assertEquals("Add layer", AddLayers.onTop(base, layer).label)
    }

    @Test
    fun addSeveralAtIndicesUndoesAsUnit() {
        val cmd = AddLayers(listOf(PlacedLayer(0, textLayer("x")), PlacedLayer(3, textLayer("y"))))
        assertEquals("Add 2 layers", cmd.label)
        roundTrip(cmd) { assertEquals(listOf("x", "a", "b", "y", "c", "d"), ids(it)) }
    }

    @Test
    fun undoAddKeepsAssetForRedo() {
        val newAsset = imageAsset('f')
        val cmd = AddLayers.onTop(base, imageLayer("x", newAsset), newAsset)
        val undone = cmd.revert(cmd.apply(base))
        assertEquals(base.layers, undone.layers)
        assertTrue(newAsset.id in undone.assets)
    }

    @Test
    fun removeSeveralRestoresOriginalPositions() {
        // Ids given out of order on purpose.
        val cmd = RemoveLayers.of(base, listOf(LayerId("d"), LayerId("b"), LayerId("a")))
        assertEquals("Delete 3 layers", cmd.label)
        roundTrip(cmd) { assertEquals(listOf("c"), ids(it)) }
    }

    @Test
    fun removeOne() {
        val cmd = RemoveLayers.of(base, listOf(LayerId("c")))
        assertEquals("Delete layer", cmd.label)
        roundTrip(cmd) { assertEquals(listOf("a", "b", "d"), ids(it)) }
    }

    @Test
    fun moveUpAndDown() {
        roundTrip(MoveLayer.of(base, LayerId("a"), 3)) { assertEquals(listOf("b", "c", "d", "a"), ids(it)) }
        roundTrip(MoveLayer.of(base, LayerId("d"), 1)) { assertEquals(listOf("a", "d", "b", "c"), ids(it)) }
        assertTrue(MoveLayer.of(base, LayerId("b"), 1).isNoOp)
    }

    @Test
    fun transformSeveralLayersAsOneStep() {
        val moved = Transform(position = Vec2(10.0, 20.0), rotationDegrees = 45.0)
        val cmd = TransformLayers.of(
            base,
            mapOf(LayerId("a") to moved, LayerId("c") to moved.copy(scaleX = 2.0, scaleY = 2.0)),
            verb = "Move",
        )
        assertEquals("Move 2 layers", cmd.label)
        roundTrip(cmd) {
            assertEquals(moved, it.requireLayer(LayerId("a")).transform)
            assertEquals(2.0, it.requireLayer(LayerId("c")).transform.scaleX, 0.0)
            assertEquals(Transform.IDENTITY, it.requireLayer(LayerId("b")).transform)
        }
    }

    @Test
    fun unchangedTransformIsNoOp() {
        val same = mapOf(LayerId("a") to base.requireLayer(LayerId("a")).transform)
        assertTrue(TransformLayers.of(base, same).isNoOp)
    }

    @Test
    fun renameAndEditText() {
        roundTrip(UpdateLayer.rename(base, LayerId("b"), "Title")) {
            assertEquals("Title", it.requireLayer(LayerId("b")).name)
        }
        val text = base.requireLayer(LayerId("c")) as TextLayer
        val edit = UpdateLayer.editText(base, text.copy(text = "Happy birthday", boxWidth = 900.0))
        assertEquals("Change text", edit.label)
        roundTrip(edit) {
            val t = it.requireLayer(LayerId("c")) as TextLayer
            assertEquals("Happy birthday", t.text)
            assertEquals(900.0, t.boxWidth, 0.0)
        }
        assertFalse(edit.isNoOp)
        assertTrue(UpdateLayer.editText(base, text).isNoOp)
    }

    @Test
    fun hideAndLockAreUndoable() {
        val hide = UpdateLayer.setVisible(base, LayerId("b"), false)
        assertEquals("Hide layer", hide.label)
        roundTrip(hide) { assertFalse(it.requireLayer(LayerId("b")).visible) }
        val lock = UpdateLayer.setLocked(base, LayerId("a"), true)
        assertEquals("Lock layer", lock.label)
        roundTrip(lock) { assertTrue(it.requireLayer(LayerId("a")).locked) }
        // Setting the state a layer already has uses no undo step.
        assertTrue(UpdateLayer.setVisible(base, LayerId("b"), true).isNoOp)
        assertEquals("Unlock layer", UpdateLayer.setLocked(lock.apply(base), LayerId("a"), false).label)
    }

    // The update looks the layer up by id, so a changed id can never replace another layer.
    @Test(expected = NoSuchElementException::class)
    fun updateCannotChangeIdentity() {
        val text = base.requireLayer(LayerId("c")) as TextLayer
        UpdateLayer.of(base, text.copy(id = LayerId("zzz")), "Bad")
    }
}
