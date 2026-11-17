package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
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
}
