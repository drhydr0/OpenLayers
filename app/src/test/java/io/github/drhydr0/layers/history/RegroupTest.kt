package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Matrix
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.document.assertVec
import io.github.drhydr0.layers.document.canvas
import io.github.drhydr0.layers.document.textLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegroupTest {

    private val inner = GroupLayer(
        LayerId("inner"), "Inner", listOf(textLayer("i1").withTransform(Transform(Vec2(5.0, 0.0), rotationDegrees = 20.0)), textLayer("i2")),
        Transform(Vec2(10.0, 0.0), 2.0, 2.0, 30.0),
    )
    private val group = GroupLayer(
        LayerId("g"), "G", listOf(textLayer("c1").withTransform(Transform(Vec2(3.0, 4.0))), inner),
        Transform(Vec2(100.0, 100.0), 1.5, 1.5, 90.0, flipX = true),
    )
    private val base = Document(canvas(), listOf(textLayer("a"), group, textLayer("b")))

    private fun ids(d: Document, parent: String? = null) = d.children(parent?.let(::LayerId)).map { it.id.value }

    /** The same local point maps to the same canvas point before and after. */
    private fun assertSamePlace(before: Document, after: Document, id: String) {
        val p = Vec2(7.0, -3.0)
        fun world(d: Document) = (d.matrixOf(d.parentOf(LayerId(id))) * d.requireLayer(LayerId(id)).transform.toMatrix()).map(p)
        assertVec(world(before), world(after))
    }

    private fun roundTrip(command: Command, check: (Document) -> Unit) {
        val after = command.apply(base)
        check(after)
        assertEquals(base, command.revert(after))
        assertEquals(after, command.apply(command.revert(after)))
    }

    @Test
    fun matrixToTransformRoundTrips() {
        listOf(
            Transform(Vec2(3.0, 4.0), 2.0, 0.5, 33.0),
            Transform(Vec2(-1.0, 9.0), 1.0, 3.0, -120.0, flipY = true),
            Transform(Vec2(0.0, 0.0), 0.3, 0.3, 179.0, flipX = true),
        ).forEach { t ->
            val m = t.toMatrix()
            val back = m.toTransform().toMatrix()
            listOf(Vec2(1.0, 0.0), Vec2(0.0, 1.0), Vec2(5.0, -7.0)).forEach { assertVec(m.map(it), back.map(it)) }
        }
        assertEquals(Matrix.IDENTITY, Transform.IDENTITY.toMatrix())
    }

    @Test
    fun moveOutOfAGroupKeepsTheLayerInPlace() {
        roundTrip(ReplaceLayers.moveTo(base, LayerId("c1"), null, 3)) {
            assertEquals(listOf("a", "g", "b", "c1"), ids(it))
            assertEquals(listOf("inner"), ids(it, "g"))
            assertSamePlace(base, it, "c1")
        }
    }

    @Test
    fun moveIntoANestedGroupKeepsTheLayerInPlace() {
        val cmd = ReplaceLayers.moveTo(base, LayerId("a"), LayerId("inner"), 1)
        assertEquals("Move into group", cmd.label)
        roundTrip(cmd) {
            assertEquals(listOf("i1", "a", "i2"), ids(it, "inner"))
            assertSamePlace(base, it, "a")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun aGroupCantGoInsideItself() {
        ReplaceLayers.moveTo(base, LayerId("g"), LayerId("inner"), 0)
    }

    @Test
    fun removeOneChildFromAGroupLeavesTheRestGrouped() {
        val cmd = ReplaceLayers.removeFromGroup(base, listOf(LayerId("i1")))
        assertEquals("Remove from group", cmd.label)
        roundTrip(cmd) {
            assertEquals(listOf("i2"), ids(it, "inner"))
            // Placed just above the group it left, in the group's own parent.
            assertEquals(listOf("c1", "inner", "i1"), ids(it, "g"))
            assertSamePlace(base, it, "i1")
        }
    }

    @Test
    fun removingEveryChildDissolvesTheGroup() {
        roundTrip(ReplaceLayers.removeFromGroup(base, listOf(LayerId("i1"), LayerId("i2")))) {
            assertEquals(listOf("c1", "i1", "i2"), ids(it, "g"))
            assertSamePlace(base, it, "i1")
            assertSamePlace(base, it, "i2")
        }
    }

    @Test
    fun historyUndoesRegroupingExactly() {
        val history = History(base)
        history.execute(ReplaceLayers.moveTo(history.document, LayerId("b"), LayerId("g"), 0))
        history.execute(ReplaceLayers.removeFromGroup(history.document, listOf(LayerId("c1"))))
        history.execute(ReplaceLayers.moveTo(history.document, LayerId("i2"), null, 0))
        repeat(3) { assertTrue(history.undo()) }
        assertEquals(base, history.document)
    }
}
