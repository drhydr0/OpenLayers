package io.github.drhydr0.layers.document

import org.junit.Test

class TransformTest {

    @Test
    fun identityPlacesContentCenterAtOrigin() {
        assertVec(Vec2(3.0, 4.0), Transform.IDENTITY.toMatrix().map(Vec2(3.0, 4.0)))
    }

    @Test
    fun positionIsContentCenter() {
        val t = Transform(position = Vec2(100.0, 50.0), scaleX = 2.0, scaleY = 2.0, rotationDegrees = 30.0)
        assertVec(Vec2(100.0, 50.0), t.toMatrix().map(Vec2.ZERO))
    }

    @Test
    fun scaleThenRotateThenTranslate() {
        val t = Transform(position = Vec2(100.0, 100.0), scaleX = 2.0, scaleY = 3.0, rotationDegrees = 90.0)
        // (1, 0) scaled -> (2, 0), rotated 90 -> (0, 2), moved -> (100, 102).
        assertVec(Vec2(100.0, 102.0), t.toMatrix().map(Vec2(1.0, 0.0)))
        // (0, 1) scaled -> (0, 3), rotated 90 -> (-3, 0), moved -> (97, 100).
        assertVec(Vec2(97.0, 100.0), t.toMatrix().map(Vec2(0.0, 1.0)))
    }

    @Test
    fun flipMirrorsAroundCenter() {
        val t = Transform(position = Vec2(10.0, 10.0), flipX = true)
        assertVec(Vec2(5.0, 12.0), t.toMatrix().map(Vec2(5.0, 2.0)))
    }

    @Test
    fun translatedByMovesOnlyPosition() {
        val t = Transform(position = Vec2(1.0, 2.0), scaleX = 2.0, scaleY = 2.0, rotationDegrees = 10.0)
        val moved = t.translatedBy(Vec2(5.0, -1.0))
        assertVec(Vec2(6.0, 1.0), moved.position)
        org.junit.Assert.assertEquals(t.copy(position = moved.position), moved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveScale() {
        Transform(scaleX = 0.0)
    }
}
