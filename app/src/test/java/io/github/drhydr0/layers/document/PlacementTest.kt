package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementTest {

    private val canvas = CanvasSpec(1000, 2000)

    @Test
    fun largeImageScaledDownToFitAndCentered() {
        val t = Placement.centeredFit(canvas, 4000, 3000)
        // 80% of 1000 wide limits: 800 / 4000 = 0.2.
        assertEquals(0.2, t.scaleX, EPS)
        assertEquals(t.scaleX, t.scaleY, EPS)
        assertVec(Vec2(500.0, 1000.0), t.position)
    }

    @Test
    fun smallImageIsNeverScaledUp() {
        assertEquals(1.0, Placement.centeredFit(canvas, 100, 100).scaleX, EPS)
    }

    @Test
    fun canvasForImageKeepsSizeOrCapsLongestSide() {
        assertEquals(CanvasSpec(4000, 3000), Placement.canvasForImage(4000, 3000, 8192))
        assertEquals(CanvasSpec(8192, 4096), Placement.canvasForImage(16000, 8000, 8192))
        assertEquals(CanvasSpec(2048, 8192), Placement.canvasForImage(3000, 12000, 8192))
    }

    @Test
    fun fillCoversCappedCanvas() {
        val c = Placement.canvasForImage(16000, 8000, 8192)
        val t = Placement.fill(c, 16000, 8000)
        val bounds = t.toMatrix().mapRect(Rect.centered(16000.0, 8000.0))
        assertEquals(0.0, bounds.left, 1e-6)
        assertEquals(8192.0, bounds.right, 1e-6)
        assertEquals(4096.0, bounds.bottom, 1e-6)
    }

    @Test
    fun textDefaultsScaleWithCanvas() {
        val c = CanvasSpec(1080, 1080)
        assertEquals(864.0, Placement.defaultTextBoxWidth(c), EPS)
        assertEquals(90.0, Placement.defaultFontSize(c), EPS)
        assertEquals(540.0, Placement.maxFontSize(c), EPS)
        // Tiny canvases still get usable sizes.
        assertEquals(Placement.MIN_FONT_SIZE, Placement.defaultFontSize(CanvasSpec(16, 16)), EPS)
    }

    @Test
    fun frameFollowsTheViewWhenZoomedIn() {
        val c = CanvasSpec(1080, 1080)
        // Zoomed into a 200 x 300 area near the top-left corner.
        val zoomed = Placement.frame(c, Rect(100.0, 50.0, 300.0, 350.0))
        assertVec(Vec2(200.0, 200.0), zoomed.center)
        assertEquals(CanvasSpec(200, 300), zoomed.sizing)
        // New text and images are sized for what is on screen.
        assertEquals(160.0, Placement.defaultTextBoxWidth(zoomed.sizing), EPS)
        assertEquals(0.04, Placement.centeredFit(zoomed.sizing, 4000, 3000).scaleX, EPS)
    }

    @Test
    fun frameIsTheCanvasWhenAllOfItIsInView() {
        val c = CanvasSpec(1080, 1080)
        // Fitted view: the screen shows the canvas plus a margin around it.
        val fitted = Placement.frame(c, Rect(-60.0, -400.0, 1140.0, 1480.0))
        assertVec(Vec2(540.0, 540.0), fitted.center)
        assertEquals(c.width to c.height, fitted.sizing.width to fitted.sizing.height)
        assertVec(Vec2(540.0, 540.0), Placement.frame(c, null).center)
    }

    @Test
    fun newShapesAreSizedForTheViewAndPlacedAtItsCenter() {
        val frame = Placement.frame(CanvasSpec(1080, 1920), Rect(100.0, 100.0, 400.0, 700.0))
        val box = Placement.defaultShape(LayerId("s"), "Shape", ShapeKind.ROUNDED_RECTANGLE, frame)
        assertEquals(100.0, box.width, EPS)
        assertEquals(100.0, box.height, EPS)
        assertVec(Vec2(250.0, 400.0), box.transform.position)
        assertEquals(255, box.fill.alpha)
        assertTrue(box.cornerRadius > 0)
        val arrow = Placement.defaultShape(LayerId("a"), "Arrow", ShapeKind.ARROW, frame)
        assertEquals(0, arrow.fill.alpha)
        assertEquals(4.0, arrow.strokeWidth, EPS)
        assertTrue(arrow.isLine)
        // The box is tall enough to hold the arrow head.
        assertEquals(16.0, arrow.localBounds.height, EPS)
    }

    @Test
    fun shapeBoundsIncludeTheOutline() {
        val plain = ShapeLayer(LayerId("s"), "s", ShapeKind.ELLIPSE, 100.0, 50.0, strokeWidth = 0.0)
        assertEquals(Rect.centered(100.0, 50.0), plain.localBounds)
        assertEquals(Rect.centered(110.0, 60.0), plain.copy(strokeWidth = 10.0).localBounds)
        // A transparent outline adds nothing.
        assertEquals(Rect.centered(100.0, 50.0), plain.copy(strokeWidth = 10.0, stroke = Color.TRANSPARENT).localBounds)
    }
}
