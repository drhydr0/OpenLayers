package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupTest {

    private val asset = imageAsset()
    private val inner = GroupLayer(
        LayerId("inner"), "Inner", listOf(textLayer("deep"), imageLayer("pic", asset)),
        Transform(position = Vec2(10.0, 0.0), scaleX = 2.0, scaleY = 2.0),
    )
    private val outer = GroupLayer(
        LayerId("outer"), "Outer", listOf(textLayer("child"), inner),
        Transform(position = Vec2(100.0, 100.0), rotationDegrees = 90.0),
    )
    private val doc = Document(canvas(), listOf(textLayer("a"), outer, textLayer("b")), mapOf(asset.id to asset))

    @Test
    fun findsLayersAnywhereInTheTree() {
        assertEquals("deep", doc.requireLayer(LayerId("deep")).id.value)
        assertEquals(listOf("a", "outer", "child", "inner", "deep", "pic", "b"), doc.allLayers().map { it.id.value }.toList())
        assertEquals(LayerId("inner"), doc.parentOf(LayerId("deep")))
        assertNull(doc.parentOf(LayerId("outer")))
        assertEquals(listOf("outer", "inner"), doc.ancestors(LayerId("pic"))!!.map { it.id.value })
        assertEquals(1, doc.indexOf(LayerId("pic")))
        assertEquals(1, doc.indexOf(LayerId("outer")))
        assertEquals(-1, doc.indexOf(LayerId("zzz")))
    }

    @Test
    fun nestedEditsRebuildOnlyThePath() {
        val renamed = doc.withLayerReplaced(doc.requireLayer(LayerId("deep")).withName("Deep!"))
        assertEquals("Deep!", renamed.requireLayer(LayerId("deep")).name)
        assertSame(doc.layers[0], renamed.layers[0])
        assertSame(doc.layers[2], renamed.layers[2])
        assertSame(doc.requireLayer(LayerId("child")), renamed.requireLayer(LayerId("child")))

        val inserted = doc.withLayerInserted(LayerId("inner"), 0, textLayer("new"))
        assertEquals(listOf("new", "deep", "pic"), inserted.children(LayerId("inner")).map { it.id.value })
        val removed = inserted.withLayerRemoved(LayerId("new"))
        assertEquals(doc, removed)
        val moved = doc.withLayerMoved(LayerId("deep"), 1)
        assertEquals(listOf("pic", "deep"), moved.children(LayerId("inner")).map { it.id.value })
    }

    @Test(expected = IllegalArgumentException::class)
    fun idsMustBeUniqueAcrossGroups() {
        doc.withLayerInserted(LayerId("inner"), 0, textLayer("a"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun imagesInsideGroupsNeedTheirAsset() {
        Document(canvas(), listOf(GroupLayer(LayerId("g"), "g", listOf(imageLayer("p", imageAsset('f'))))))
    }

    @Test
    fun matrixOfComposesEveryGroupOnThePath() {
        // inner origin: (10, 0) in outer space, rotated 90 degrees -> (100, 110) on canvas.
        assertVec(Vec2(100.0, 110.0), doc.matrixOf(LayerId("inner")).map(Vec2.ZERO))
        // (1, 0) inside inner: scaled to (2, 0), +10 -> (12, 0), rotated -> (0, 12), moved -> (100, 112).
        assertVec(Vec2(100.0, 112.0), doc.matrixOf(LayerId("inner")).map(Vec2(1.0, 0.0)))
        assertEquals(Matrix.IDENTITY, doc.matrixOf(null))
    }

    @Test
    fun groupingThenUngroupingLeavesLayersWhereTheyWere() {
        val a = textLayer("x").withTransform(Transform(Vec2(100.0, 50.0), 2.0, 3.0, 30.0))
        val b = textLayer("y").withTransform(Transform(Vec2(300.0, 250.0)))
        val group = Grouping.group(LayerId("g"), "Group", listOf(a, b), center = Vec2(200.0, 150.0))
        assertVec(Vec2(-100.0, -100.0), group.children[0].transform.position)
        // Seen from outside, each child is exactly where it was.
        val back = Grouping.ungroup(group)
        assertEquals(listOf(a, b), back)
    }

    @Test
    fun ungroupCarriesTheGroupsMoveScaleRotationOpacityAndVisibility() {
        val child = textLayer("x").withTransform(Transform(Vec2(10.0, 0.0), rotationDegrees = 170.0)).withOpacity(0.5)
        val group = GroupLayer(
            LayerId("g"), "g", listOf(child),
            Transform(Vec2(100.0, 100.0), 2.0, 2.0, 90.0), opacity = 0.5,
        )
        val out = Grouping.ungroup(group).single()
        assertVec(Vec2(100.0, 120.0), out.transform.position)
        assertEquals(2.0, out.transform.scaleX, EPS)
        assertEquals(-100.0, out.transform.rotationDegrees, EPS)
        assertEquals(0.25, out.opacity, EPS)
        // The world position matches what the group drew.
        assertVec(group.transform.toMatrix().map(child.transform.position), out.transform.position)
        assertTrue(Grouping.ungroup(group.copy(visible = false)).none { it.visible })
    }

    @Test
    fun copiesAreIndependentWithNewIdsThroughout() {
        var n = 0
        val copy = Grouping.copy(outer) { LayerId("copy${n++}") } as GroupLayer
        val ids = copy.selfAndDescendants().map { it.id.value }.toList()
        assertEquals(listOf("copy0", "copy1", "copy2", "copy3", "copy4"), ids)
        // Same content and arrangement; image bytes shared through the asset id.
        assertEquals(outer.transform, copy.transform)
        val pic = copy.selfAndDescendants().filterIsInstance<ImageLayer>().single()
        assertEquals(asset.id, pic.assetId)
        // The original and the copy can live in one document.
        Document(canvas(), listOf(outer, copy), mapOf(asset.id to asset))
    }
}
