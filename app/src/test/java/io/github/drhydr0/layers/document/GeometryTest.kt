package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    @Test
    fun rotationIsClockwiseOnScreen() {
        // Canvas y points down, so +90 degrees takes the +x axis to +y (down).
        assertVec(Vec2(0.0, 1.0), Matrix.rotateDegrees(90.0).map(Vec2(1.0, 0.0)))
    }

    @Test
    fun timesAppliesRightOperandFirst() {
        val m = Matrix.translate(10.0, 0.0) * Matrix.scale(2.0, 2.0)
        assertVec(Vec2(12.0, 2.0), m.map(Vec2(1.0, 1.0)))
    }

    @Test
    fun inverseUndoesMapping() {
        val m = Matrix.translate(5.0, -3.0) * Matrix.rotateDegrees(33.0) * Matrix.scale(2.0, 0.5)
        val inv = requireNotNull(m.inverted())
        val p = Vec2(7.0, 11.0)
        assertVec(p, inv.map(m.map(p)))
        assertVec(p, m.map(inv.map(p)))
    }

    @Test
    fun collapsedMatrixHasNoInverse() {
        assertNull(Matrix.scale(0.0, 1.0).inverted())
    }

    @Test
    fun mapRectBoundsRotatedSquare() {
        val r = Matrix.rotateDegrees(45.0).mapRect(Rect.centered(2.0, 2.0))
        val half = Math.sqrt(2.0)
        assertEquals(-half, r.left, EPS)
        assertEquals(half, r.right, EPS)
        assertEquals(-half, r.top, EPS)
        assertEquals(half, r.bottom, EPS)
    }

    @Test
    fun rectBasics() {
        val r = Rect(0.0, 0.0, 10.0, 20.0)
        assertTrue(r.contains(Vec2(10.0, 20.0)))
        assertFalse(r.contains(Vec2(10.1, 5.0)))
        assertVec(Vec2(5.0, 10.0), r.center)
        assertEquals(Rect(-5.0, 0.0, 10.0, 25.0), r.union(Rect(-5.0, 5.0, 1.0, 25.0)))
        assertTrue(Rect(0.0, 0.0, 0.0, 5.0).isEmpty)
    }

    @Test
    fun colorComponents() {
        val c = Color.argb(0x80, 0x12, 0x34, 0x56)
        assertEquals(0x80, c.alpha)
        assertEquals(0x12, c.red)
        assertEquals(0x34, c.green)
        assertEquals(0x56, c.blue)
        assertEquals(255, Color.WHITE.alpha)
        assertEquals(0, Color.TRANSPARENT.alpha)
    }

    @Test(expected = IllegalArgumentException::class)
    fun colorRejectsOutOfRange() {
        Color.argb(256, 0, 0, 0)
    }
}
