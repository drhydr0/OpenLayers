package io.github.drhydr0.layers.interaction

import io.github.drhydr0.layers.document.EPS
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.document.assertVec
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureMathTest {

    private val start = Transform(position = Vec2(100.0, 100.0), scaleX = 2.0, scaleY = 2.0, rotationDegrees = 10.0)

    @Test
    fun dragMovesByFingerDelta() {
        val t = GestureMath.drag(start, Vec2(5.0, 5.0), Vec2(15.0, -5.0))
        assertVec(Vec2(110.0, 90.0), t.position)
        assertEquals(start.copy(position = t.position), t)
    }

    @Test
    fun pinchKeepsContentUnderFingers() {
        val a0 = Vec2(80.0, 100.0)
        val b0 = Vec2(120.0, 100.0)
        // Fingers spread to double distance, turn 90 degrees, and move.
        val a1 = Vec2(300.0, 260.0)
        val b1 = Vec2(300.0, 340.0)
        val t = GestureMath.pinch(start, a0, b0, a1, b1)
        assertEquals(4.0, t.scaleX, EPS)
        assertEquals(100.0, t.rotationDegrees, EPS)
        // The canvas point under each finger at the start maps to where the finger is now.
        val localA = start.toMatrix().inverted()!!.map(a0)
        val localB = start.toMatrix().inverted()!!.map(b0)
        assertVec(a1, t.toMatrix().map(localA))
        assertVec(b1, t.toMatrix().map(localB))
    }

    @Test
    fun pinchWithoutChangeIsIdentity() {
        val a = Vec2(10.0, 10.0)
        val b = Vec2(50.0, 80.0)
        val t = GestureMath.pinch(start, a, b, a, b)
        assertVec(start.position, t.position)
        assertEquals(start.scaleX, t.scaleX, EPS)
        assertEquals(start.rotationDegrees, t.rotationDegrees, EPS)
    }

    @Test
    fun pinchScaleIsClamped() {
        val t = GestureMath.pinch(start, Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(0.0, 0.0), Vec2(0.0001, 0.0))
        assertEquals(GestureMath.MIN_SCALE, t.scaleX, EPS)
    }

    @Test
    fun cornerScalesUniformlyAroundCenter() {
        val t = GestureMath.scaleFromCorner(start, Vec2(150.0, 100.0), Vec2(250.0, 100.0))
        assertEquals(6.0, t.scaleX, EPS)
        assertEquals(6.0, t.scaleY, EPS)
        assertVec(start.position, t.position)
    }

    @Test
    fun rotationHandleTurnsAndSnaps() {
        // Handle starts straight above center; finger moves to the right: +90 degrees.
        val turned = GestureMath.rotateFromHandle(start, Vec2(100.0, 0.0), Vec2(200.0, 100.0), snap = false)
        assertEquals(100.0, turned.rotationDegrees, EPS)
        val snapped = GestureMath.rotateFromHandle(start.copy(rotationDegrees = 0.0), Vec2(100.0, 0.0), Vec2(203.0, 97.0))
        assertEquals(90.0, snapped.rotationDegrees, EPS)
    }

    @Test
    fun textWidthFollowsFingerInLayerSpace() {
        val t = Transform(position = Vec2(100.0, 100.0), scaleX = 2.0, scaleY = 2.0)
        // Finger 300 canvas px right of center = 150 local px, so width 300.
        assertEquals(300.0, GestureMath.textBoxWidth(t, Vec2(400.0, 100.0), 10.0), EPS)
        assertEquals(10.0, GestureMath.textBoxWidth(t, Vec2(100.0, 100.0), 10.0), EPS)
    }

    @Test
    fun anglesNormalizeAndSnap() {
        assertEquals(-170.0, GestureMath.normalizeDegrees(190.0), EPS)
        assertEquals(180.0, GestureMath.normalizeDegrees(-180.0), EPS)
        assertEquals(0.0, GestureMath.snapAngle(3.5), EPS)
        assertEquals(10.0, GestureMath.snapAngle(10.0), EPS)
        assertEquals(180.0, GestureMath.snapAngle(-178.0), EPS)
    }

    @Test
    fun groupScaleKeepsArrangementAroundPivot() {
        val pivot = Vec2(100.0, 100.0)
        val a = Transform(position = Vec2(50.0, 100.0))
        val b = Transform(position = Vec2(200.0, 150.0), scaleX = 2.0, scaleY = 2.0, rotationDegrees = 30.0)
        val factor = GestureMath.groupFactor(listOf(a, b), pivot, Vec2(200.0, 100.0), Vec2(300.0, 100.0))
        assertEquals(2.0, factor, EPS)
        val a2 = GestureMath.scaleAround(a, pivot, factor)
        val b2 = GestureMath.scaleAround(b, pivot, factor)
        assertVec(Vec2(0.0, 100.0), a2.position)
        assertVec(Vec2(300.0, 200.0), b2.position)
        assertEquals(2.0, a2.scaleX, EPS)
        assertEquals(4.0, b2.scaleX, EPS)
        assertEquals(30.0, b2.rotationDegrees, EPS)
    }

    @Test
    fun groupFactorRespectsEveryLayersLimits() {
        val tiny = Transform(scaleX = 0.02, scaleY = 0.02)
        val big = Transform(scaleX = 50.0, scaleY = 50.0)
        val pivot = Vec2(0.0, 0.0)
        // Shrinking stops when the smallest layer reaches the minimum.
        assertEquals(0.5, GestureMath.groupFactor(listOf(tiny, big), pivot, Vec2(100.0, 0.0), Vec2(1.0, 0.0)), EPS)
        // Growing stops when the largest reaches the maximum.
        assertEquals(2.0, GestureMath.groupFactor(listOf(tiny, big), pivot, Vec2(1.0, 0.0), Vec2(100.0, 0.0)), EPS)
    }

    @Test
    fun groupRotationTurnsPositionsAndAngles() {
        val pivot = Vec2(100.0, 100.0)
        val a = Transform(position = Vec2(200.0, 100.0), rotationDegrees = 170.0)
        val turn = GestureMath.turnDegrees(pivot, Vec2(100.0, 0.0), Vec2(200.0, 100.0))
        assertEquals(90.0, turn, EPS)
        val a2 = GestureMath.rotateAround(a, pivot, turn)
        assertVec(Vec2(100.0, 200.0), a2.position)
        assertEquals(-100.0, a2.rotationDegrees, EPS)
    }

    @Test
    fun pinchMovesSeveralLayersAsOneRigidGroup() {
        val a = Transform(position = Vec2(0.0, 0.0))
        val b = Transform(position = Vec2(100.0, 0.0))
        val f0 = Vec2(40.0, 0.0)
        val f1 = Vec2(60.0, 0.0)
        // Same fingers for both layers: spread to double, turned 90 degrees.
        val g0 = Vec2(50.0, -20.0)
        val g1 = Vec2(50.0, 20.0)
        val a2 = GestureMath.pinch(a, f0, f1, g0, g1)
        val b2 = GestureMath.pinch(b, f0, f1, g0, g1)
        // The gap between the layers doubles and turns with the fingers.
        assertVec(Vec2(0.0, 200.0), b2.position - a2.position)
        assertEquals(a2.rotationDegrees, b2.rotationDegrees, EPS)
        assertEquals(2.0, a2.scaleX, EPS)
    }

    @Test
    fun cornerResizeKeepsTheOppositeCornerFixedAndChangesBothAxesFreely() {
        val box = io.github.drhydr0.layers.document.Rect.centered(100.0, 50.0)
        val t = Transform(position = Vec2(200.0, 200.0), scaleX = 1.0, scaleY = 1.0, rotationDegrees = 90.0)
        val anchor = t.toMatrix().map(Vec2(-50.0, -25.0))
        // Bottom-right handle. With 90 degrees, local +x points down and local +y points left.
        val r = GestureMath.resizeFromHandle(t, box, 1, 1, anchor + Vec2(-100.0, 300.0), 4.0)
        assertEquals(3.0, r.scaleX, EPS)
        assertEquals(2.0, r.scaleY, EPS)
        assertVec(anchor, r.toMatrix().map(Vec2(-50.0, -25.0)))
        assertEquals(90.0, r.rotationDegrees, EPS)
    }

    @Test
    fun sideResizeChangesOneAxisOnly() {
        val box = io.github.drhydr0.layers.document.Rect.centered(100.0, 50.0)
        val t = Transform(position = Vec2(0.0, 0.0), scaleX = 2.0, scaleY = 2.0)
        // Left side handle at x = -100 on the canvas; drag it to -300.
        val r = GestureMath.resizeFromHandle(t, box, -1, 0, Vec2(-300.0, 999.0), 4.0)
        assertEquals(4.0, r.scaleX, EPS)
        assertEquals(2.0, r.scaleY, EPS)
        // The right edge (x = 100) stays put.
        assertVec(Vec2(100.0, 0.0), r.toMatrix().map(Vec2(50.0, 0.0)))
    }

    @Test
    fun resizePastTheFixedSideStopsAtTheMinimumAndKeepsFlips() {
        val box = io.github.drhydr0.layers.document.Rect.centered(100.0, 100.0)
        val flipped = Transform(position = Vec2(0.0, 0.0), flipX = true)
        // With flipX, the local right handle sits on the canvas left. Drag it across to the other side.
        val r = GestureMath.resizeFromHandle(flipped, box, 1, 0, Vec2(500.0, 0.0), 4.0)
        assertEquals(0.04, r.scaleX, EPS)
        assertEquals(true, r.flipX)
    }

    @Test
    fun resizeWorksForBoxesNotCenteredOnTheOrigin() {
        // A group's box can sit off-center: the fixed corner must still stay put.
        val box = io.github.drhydr0.layers.document.Rect(10.0, 20.0, 110.0, 70.0)
        val t = Transform(position = Vec2(50.0, 60.0), scaleX = 1.5, scaleY = 1.5, rotationDegrees = 30.0)
        val fixed = t.toMatrix().map(Vec2(10.0, 20.0))
        val r = GestureMath.resizeFromHandle(t, box, 1, 1, fixed + Vec2(200.0, 200.0), 4.0)
        assertVec(fixed, r.toMatrix().map(Vec2(10.0, 20.0)))
    }
}
