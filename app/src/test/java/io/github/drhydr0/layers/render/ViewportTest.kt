package io.github.drhydr0.layers.render

import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.document.assertVec
import org.junit.Test

class ViewportTest {

    @Test
    fun wideViewCentersTallCanvasHorizontally() {
        // 1000x500 view, 20 margin: height limits, scale = 460/1000 = 0.46.
        val m = Viewport.fit(1000, 1000, Rect(0.0, 0.0, 1000.0, 500.0), margin = 20.0)
        assertVec(Vec2(270.0, 20.0), m.map(Vec2(0.0, 0.0)))
        assertVec(Vec2(730.0, 480.0), m.map(Vec2(1000.0, 1000.0)))
    }

    @Test
    fun tallViewCentersWideCanvasVertically() {
        // 400x800 view, no margin: width limits, scale = 400/1920.
        val m = Viewport.fit(1920, 1080, Rect(0.0, 0.0, 400.0, 800.0), margin = 0.0)
        val scaledHeight = 1080 * 400.0 / 1920
        assertVec(Vec2(0.0, (800 - scaledHeight) / 2), m.map(Vec2(0.0, 0.0)))
        assertVec(Vec2(400.0, (800 + scaledHeight) / 2), m.map(Vec2(1920.0, 1080.0)))
    }

    @Test
    fun fitKeepsCanvasCenterAtViewCenter() {
        val view = Rect(10.0, 30.0, 710.0, 1230.0)
        val m = Viewport.fit(1080, 1350, view, margin = 16.0)
        assertVec(view.center, m.map(Vec2(540.0, 675.0)))
    }
}

class ViewportNavigationTest {

    private val fit = Viewport.fit(1000, 1000, Rect(0.0, 0.0, 500.0, 500.0), margin = 0.0) // scale 0.5

    @Test
    fun zoomKeepsFocusPointStill() {
        val focus = Vec2(100.0, 300.0)
        val canvasPoint = fit.inverted()!!.map(focus)
        val zoomed = Viewport.zoom(fit, focus, 3.0, fitScale = 0.5)
        assertVec(focus, zoomed.map(canvasPoint))
        org.junit.Assert.assertEquals(1.5, Viewport.scaleOf(zoomed), 1e-9)
    }

    @Test
    fun zoomIsClampedRelativeToFit() {
        val inMax = Viewport.zoom(fit, Vec2(0.0, 0.0), 1000.0, fitScale = 0.5)
        org.junit.Assert.assertEquals(0.5 * Viewport.MAX_ZOOM, Viewport.scaleOf(inMax), 1e-9)
        val outMax = Viewport.zoom(fit, Vec2(0.0, 0.0), 0.0001, fitScale = 0.5)
        org.junit.Assert.assertEquals(0.5 * Viewport.MIN_ZOOM, Viewport.scaleOf(outMax), 1e-9)
    }

    @Test
    fun panShiftsEverythingOnScreen() {
        val panned = Viewport.pan(fit, 30.0, -20.0)
        assertVec(Vec2(30.0, -20.0), panned.map(Vec2(0.0, 0.0)))
    }
}
