package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.document.assertVec
import io.github.drhydr0.layers.document.canvas
import io.github.drhydr0.layers.document.imageAsset
import io.github.drhydr0.layers.document.imageLayer
import io.github.drhydr0.layers.document.textLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupCommandsTest {

    private val asset = imageAsset()
    private val inner = GroupLayer(LayerId("inner"), "Inner", listOf(textLayer("deep1"), textLayer("deep2"), imageLayer("pic", asset)))
    private val outer = GroupLayer(
        LayerId("outer"), "Outer", listOf(textLayer("child"), inner),
        Transform(position = Vec2(100.0, 100.0), scaleX = 2.0, scaleY = 2.0, rotationDegrees = 90.0),
    )
    private val base = Document(
        canvas(),
        listOf(textLayer("a"), textLayer("b"), outer, textLayer("c")),
        mapOf(asset.id to asset),
    )

    private fun ids(d: Document, parent: String? = null) = d.children(parent?.let(::LayerId)).map { it.id.value }

    private fun roundTrip(command: Command, start: Document = base, check: (Document) -> Unit) {
        val after = command.apply(start)
        check(after)
        assertEquals(start, command.revert(after))
        assertEquals(after, command.apply(command.revert(after)))
    }

    @Test
    fun groupReplacesMembersAtTheTopmostMembersPlace() {
        val cmd = ReplaceLayers.group(base, listOf(LayerId("c"), LayerId("a")), LayerId("g"), "Group", Vec2(50.0, 50.0))
        assertEquals("Group 2 layers", cmd.label)
        roundTrip(cmd) {
            assertEquals(listOf("b", "outer", "g"), ids(it))
            assertEquals(listOf("a", "c"), ids(it, "g"))
            // Nothing moved on the canvas.
            val m = it.matrixOf(LayerId("g"))
            assertVec(base.requireLayer(LayerId("a")).transform.position, m.map(it.requireLayer(LayerId("a")).transform.position))
        }
    }

    @Test
    fun groupInsideANestedGroup() {
        val cmd = ReplaceLayers.group(base, listOf(LayerId("deep1"), LayerId("pic")), LayerId("g"), "Group", Vec2.ZERO)
        roundTrip(cmd) {
            assertEquals(listOf("deep2", "g"), ids(it, "inner"))
            assertEquals(listOf("deep1", "pic"), ids(it, "g"))
            assertEquals(listOf("outer", "inner"), it.ancestors(LayerId("g"))!!.map { g -> g.id.value })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun groupNeedsSiblings() {
        ReplaceLayers.group(base, listOf(LayerId("a"), LayerId("deep1")), LayerId("g"), "Group", Vec2.ZERO)
    }

    @Test
    fun ungroupPutsChildrenInTheGroupsPlaceLookingTheSame() {
        val cmd = ReplaceLayers.ungroup(base, LayerId("outer"))
        assertEquals("Ungroup", cmd.label)
        roundTrip(cmd) {
            assertEquals(listOf("a", "b", "child", "inner", "c"), ids(it))
            // "inner" kept its children and now carries the outer group's transform.
            assertEquals(listOf("deep1", "deep2", "pic"), ids(it, "inner"))
            assertVec(Vec2(100.0, 100.0), it.requireLayer(LayerId("inner")).transform.position)
            assertEquals(2.0, it.requireLayer(LayerId("child")).transform.scaleX, 1e-9)
            assertEquals(base.matrixOf(LayerId("inner")).map(Vec2(3.0, 4.0)).x, it.matrixOf(LayerId("inner")).map(Vec2(3.0, 4.0)).x, 1e-9)
        }
    }

    @Test
    fun ungroupNestedGroupStaysInsideItsParent() {
        roundTrip(ReplaceLayers.ungroup(base, LayerId("inner"))) {
            assertEquals(listOf("child", "deep1", "deep2", "pic"), ids(it, "outer"))
        }
    }

    @Test
    fun duplicatesStackAboveTheTopmostOriginalInOrder() {
        var n = 0
        val cmd = AddLayers.duplicates(base, listOf(LayerId("outer"), LayerId("a")), Vec2(20.0, 20.0)) { LayerId("n${n++}") }
        assertEquals("Duplicate 2 layers", cmd.label)
        roundTrip(cmd) {
            assertEquals(listOf("a", "b", "outer", "n0", "n1", "c"), ids(it))
            // The copy of "a" comes first (lower), then the copy of the group with new ids inside.
            assertEquals(listOf("n2", "n3"), ids(it, "n1"))
            assertVec(Vec2(120.0, 120.0), it.requireLayer(LayerId("n1")).transform.position)
            // Editing a copy leaves the original alone.
            val edited = UpdateLayer.rename(it, LayerId("n2"), "Changed").apply(it)
            assertEquals("Text child", edited.requireLayer(LayerId("child")).name)
        }
    }

    @Test
    fun duplicateInsideANestedGroup() {
        var n = 0
        roundTrip(AddLayers.duplicates(base, listOf(LayerId("deep1")), Vec2(5.0, 5.0)) { LayerId("n${n++}") }) {
            assertEquals(listOf("deep1", "n0", "deep2", "pic"), ids(it, "inner"))
        }
    }

    @Test
    fun removeSkipsLayersInsideAnotherRemovedLayerAndRestoresNesting() {
        val cmd = RemoveLayers.of(base, listOf(LayerId("deep2"), LayerId("outer"), LayerId("a")))
        assertEquals("Delete 2 layers", cmd.label)
        roundTrip(cmd) { assertEquals(listOf("b", "c"), ids(it)) }
        roundTrip(RemoveLayers.of(base, listOf(LayerId("deep1"), LayerId("pic"), LayerId("child")))) {
            assertEquals(listOf("deep2"), ids(it, "inner"))
            assertEquals(listOf("inner"), ids(it, "outer"))
        }
    }

    @Test
    fun nestedTransformRenameAndReorderUndoExactly() {
        roundTrip(TransformLayers.of(base, mapOf(LayerId("deep1") to Transform(Vec2(7.0, 8.0)), LayerId("a") to Transform(Vec2(1.0, 2.0))))) {
            assertVec(Vec2(7.0, 8.0), it.requireLayer(LayerId("deep1")).transform.position)
        }
        roundTrip(UpdateLayer.rename(base, LayerId("pic"), "Photo")) { assertEquals("Photo", it.requireLayer(LayerId("pic")).name) }
        roundTrip(MoveLayer.of(base, LayerId("pic"), 0)) { assertEquals(listOf("pic", "deep1", "deep2"), ids(it, "inner")) }
        roundTrip(AddLayers.onTop(base, textLayer("new"), parentId = LayerId("inner"))) {
            assertEquals(listOf("deep1", "deep2", "pic", "new"), ids(it, "inner"))
        }
        assertTrue(MoveLayer.of(base, LayerId("pic"), 2).isNoOp)
    }

    @Test
    fun historyUndoesGroupEditsInOrder() {
        val history = History(base)
        history.execute(ReplaceLayers.group(history.document, listOf(LayerId("a"), LayerId("b")), LayerId("g"), "Group", Vec2.ZERO))
        history.execute(TransformLayers.of(history.document, mapOf(LayerId("g") to Transform(Vec2(30.0, 40.0), 2.0, 2.0, 45.0))))
        history.execute(ReplaceLayers.ungroup(history.document, LayerId("g")))
        assertEquals(listOf("a", "b", "outer", "c"), ids(history.document))
        assertEquals(2.0, history.document.requireLayer(LayerId("a")).transform.scaleX, 1e-9)
        repeat(3) { assertTrue(history.undo()) }
        assertEquals(base, history.document)
        repeat(3) { assertTrue(history.redo()) }
        assertEquals(45.0, history.document.requireLayer(LayerId("b")).transform.rotationDegrees, 1e-9)
    }
}
