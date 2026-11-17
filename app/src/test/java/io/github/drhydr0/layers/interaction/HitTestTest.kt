package io.github.drhydr0.layers.interaction

import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.document.assertVec
import io.github.drhydr0.layers.document.canvas
import io.github.drhydr0.layers.document.imageAsset
import io.github.drhydr0.layers.document.imageLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HitTestTest {

    private val asset = imageAsset(width = 200, height = 100)
    private val hit = HitTest { doc, layer -> doc.imageLocalBounds(layer as ImageLayer) }

    private val bottom = imageLayer("bottom", asset, Transform(position = Vec2(500.0, 500.0)))
    private val top = imageLayer("top", asset, Transform(position = Vec2(550.0, 500.0)))
    private val doc = Document(canvas()).withAsset(asset)
        .withLayerInserted(0, bottom).withLayerInserted(1, top)

    @Test
    fun picksTopmostAtOverlap() {
        assertEquals("top", hit.topmost(doc, Vec2(520.0, 500.0))?.id?.value)
        assertEquals("bottom", hit.topmost(doc, Vec2(420.0, 500.0))?.id?.value)
        assertNull(hit.topmost(doc, Vec2(10.0, 10.0)))
    }

    @Test
    fun skipsHiddenAndLockedLayers() {
        val hidden = doc.withLayerReplaced(top.copy(visible = false))
        assertEquals("bottom", hit.topmost(hidden, Vec2(520.0, 500.0))?.id?.value)
        val locked = doc.withLayerReplaced(top.copy(locked = true))
        assertEquals("bottom", hit.topmost(locked, Vec2(520.0, 500.0))?.id?.value)
    }

    @Test
    fun respectsRotationAndScale() {
        // Rotated 90 degrees: the 200x100 box becomes 100 wide, 200 tall.
        val rotated = bottom.copy(transform = Transform(position = Vec2(500.0, 500.0), rotationDegrees = 90.0))
        val d = Document(canvas()).withAsset(asset).withLayerInserted(0, rotated)
        assertTrue(hit.contains(d, rotated, Vec2(500.0, 590.0)))
        assertFalse(hit.contains(d, rotated, Vec2(590.0, 500.0)))
        val scaled = bottom.copy(transform = Transform(position = Vec2(500.0, 500.0), scaleX = 0.1, scaleY = 0.1))
        // A 20x10 layer: slop makes it grabbable a little outside.
        assertFalse(hit.contains(d, scaled, Vec2(515.0, 500.0)))
        assertTrue(hit.contains(d, scaled, Vec2(515.0, 500.0), slop = 10.0))
    }

    @Test
    fun cornersFollowTransform() {
        val c = hit.corners(doc, bottom)
        assertVec(Vec2(400.0, 450.0), c[0])
        assertVec(Vec2(600.0, 550.0), c[2])
        assertEquals(Rect(400.0, 450.0, 600.0, 550.0), Rect.bounding(c))
    }
}
