package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTest {

    private val canvas = CanvasSpec(1000, 800)
    private val asset = imageAsset()

    @Test
    fun rectangleFromAnyTwoCorners() {
        val r = RectSelection.between(Vec2(300.0, 50.0), Vec2(100.0, 250.0))
        assertEquals(Rect(100.0, 50.0, 300.0, 250.0), r.bounds)
        assertTrue(r.contains(Vec2(200.0, 100.0)))
        assertFalse(r.contains(Vec2(301.0, 100.0)))
        assertEquals(Rect(110.0, 45.0, 310.0, 245.0), r.translatedBy(Vec2(10.0, -5.0)).bounds)
        assertTrue(RectSelection.between(Vec2(5.0, 5.0), Vec2(6.0, 200.0)).isEmpty)
    }

    @Test
    fun lassoContainsPointsInsideItsOutlineOnly() {
        // An L shape: the notch at the top right is outside.
        val l = LassoSelection(
            listOf(Vec2(0.0, 0.0), Vec2(50.0, 0.0), Vec2(50.0, 50.0), Vec2(100.0, 50.0), Vec2(100.0, 100.0), Vec2(0.0, 100.0)),
        )
        assertTrue(l.contains(Vec2(25.0, 25.0)))
        assertTrue(l.contains(Vec2(75.0, 75.0)))
        assertFalse(l.contains(Vec2(75.0, 25.0)))
        assertEquals(Rect(0.0, 0.0, 100.0, 100.0), l.bounds)
        assertFalse(l.isEmpty)
        assertEquals(Rect(5.0, 5.0, 105.0, 105.0), l.translatedBy(Vec2(5.0, 5.0)).bounds)
    }

    @Test
    fun strayLassoStrokesAreEmpty() {
        assertTrue(LassoSelection(listOf(Vec2(0.0, 0.0), Vec2(100.0, 100.0))).isEmpty)
        // A long thin back-and-forth line encloses no area.
        assertTrue(LassoSelection(listOf(Vec2(0.0, 0.0), Vec2(100.0, 100.0), Vec2(0.0, 0.01))).isEmpty)
    }

    @Test
    fun lassoDrawingSkipsCrowdedPoints() {
        var lasso = LassoSelection(emptyList())
        listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(2.0, 0.0), Vec2(10.0, 0.0)).forEach { lasso = lasso.extendedBy(it, 4.0) }
        assertEquals(listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0)), lasso.points)
    }

    @Test
    fun canvasCaptureStopsAtCanvasEdgesAndUsesWholePixels() {
        val shape = RectSelection(Rect(-50.0, 700.4, 200.6, 900.0))
        val plan = CapturePlanner.plan(canvas, shape, CaptureScope.CANVAS, Long.MAX_VALUE)!!
        assertEquals(Rect(0.0, 700.0, 201.0, 800.0), plan.region)
        assertEquals(201 to 100, plan.width to plan.height)
        assertEquals(1.0, plan.scale, EPS)
        assertNull(CapturePlanner.plan(canvas, RectSelection(Rect(2000.0, 0.0, 2100.0, 100.0)), CaptureScope.CANVAS, Long.MAX_VALUE))
    }

    @Test
    fun layerCaptureMayReachBeyondTheCanvas() {
        val shape = RectSelection(Rect(-50.0, 700.0, 200.0, 900.0))
        val plan = CapturePlanner.plan(canvas, shape, CaptureScope.SELECTED_LAYERS, Long.MAX_VALUE)!!
        assertEquals(Rect(-50.0, 700.0, 200.0, 900.0), plan.region)
    }

    @Test
    fun emptySelectionPlansNothing() {
        assertNull(CapturePlanner.plan(canvas, RectSelection(Rect(5.0, 5.0, 5.5, 300.0)), CaptureScope.CANVAS, Long.MAX_VALUE))
    }

    @Test
    fun hugeCapturesShrinkToFitMemoryAndStillLandInPlace() {
        val shape = RectSelection(Rect(0.0, 0.0, 1000.0, 800.0))
        val plan = CapturePlanner.plan(canvas, shape, CaptureScope.CANVAS, maxPixels = 200_000)!!
        assertTrue(plan.width.toLong() * plan.height <= 200_000)
        assertEquals(0.5, plan.scale, EPS)
        val placed = CapturePlanner.placement(plan)
        // The image, scaled back up, covers exactly the region it copied.
        val covered = placed.toMatrix().mapRect(Rect.centered(plan.width.toDouble(), plan.height.toDouble()))
        assertEquals(plan.region.left, covered.left, 1e-6)
        assertEquals(plan.region.right, covered.right, 1e-6)
        assertEquals(plan.region.bottom, covered.bottom, 1e-6)
    }

    private val inner = GroupLayer(LayerId("inner"), "Inner", listOf(textLayer("deep"), imageLayer("pic", asset)))
    private val outer = GroupLayer(LayerId("outer"), "Outer", listOf(textLayer("child"), inner), Transform(Vec2(10.0, 20.0)))
    private val doc = Document(canvas.copy(background = Color.WHITE), listOf(textLayer("a"), outer, textLayer("b")), mapOf(asset.id to asset))

    @Test
    fun canvasSourceIsTheDocumentWithOrWithoutBackground() {
        assertEquals(doc, CapturePlanner.source(doc, CaptureScope.CANVAS, emptySet(), includeBackground = true))
        val clear = CapturePlanner.source(doc, CaptureScope.CANVAS, emptySet(), includeBackground = false)
        assertEquals(Color.TRANSPARENT, clear.canvas.background)
        assertEquals(doc.layers, clear.layers)
    }

    @Test
    fun layerSourceKeepsOnlyChosenLayersInOrderInsideTheirGroups() {
        val src = CapturePlanner.source(doc, CaptureScope.SELECTED_LAYERS, setOf(LayerId("b"), LayerId("pic"), LayerId("a")), true)
        assertEquals(Color.TRANSPARENT, src.canvas.background)
        assertEquals(listOf("a", "outer", "inner", "pic", "b"), src.allLayers().map { it.id.value }.toList())
        // The groups around a nested layer keep their transforms, so it stays in place.
        assertEquals(doc.matrixOf(LayerId("inner")), src.matrixOf(LayerId("inner")))
    }

    @Test
    fun choosingAGroupKeepsEverythingInIt() {
        val src = CapturePlanner.source(doc, CaptureScope.ACTIVE_LAYER, setOf(LayerId("outer")), true)
        assertEquals(listOf(outer), src.layers)
    }

    @Test
    fun hiddenLayersStayHiddenInTheSource() {
        val hidden = doc.withLayerReplaced(doc.requireLayer(LayerId("a")).withVisible(false))
        val src = CapturePlanner.source(hidden, CaptureScope.ACTIVE_LAYER, setOf(LayerId("a")), true)
        assertFalse(src.layers.single().visible)
    }
}
