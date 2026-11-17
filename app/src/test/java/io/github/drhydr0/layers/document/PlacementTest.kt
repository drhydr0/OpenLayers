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
}
