package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrecisionInputTest {

    private val current = Transform(Vec2(10.0, 20.0), 2.0, 4.0, 30.0, flipX = true)

    @Test
    fun typedValuesReplacePositionScaleAndAngle() {
        val t = PrecisionInput.apply(current, " 540 ", "960,5", "50", "-45")!!
        assertVec(Vec2(540.0, 960.5), t.position)
        assertEquals(0.5, t.scaleX, EPS)
        // The 1:2 stretch is kept.
        assertEquals(1.0, t.scaleY, EPS)
        assertEquals(-45.0, t.rotationDegrees, EPS)
        assertEquals(true, t.flipX)
    }

    @Test
    fun anglesWrapIntoRange() {
        assertEquals(-90.0, PrecisionInput.apply(current, "0", "0", "100", "270")!!.rotationDegrees, EPS)
        assertEquals(180.0, PrecisionInput.apply(current, "0", "0", "100", "540")!!.rotationDegrees, EPS)
    }

    @Test
    fun unusableInputIsRejected() {
        assertNull(PrecisionInput.apply(current, "", "0", "100", "0"))
        assertNull(PrecisionInput.apply(current, "abc", "0", "100", "0"))
        assertNull(PrecisionInput.apply(current, "0", "0", "0", "0"))
        assertNull(PrecisionInput.apply(current, "0", "0", "-50", "0"))
        assertNull(PrecisionInput.apply(current, "0", "0", "100", "NaN"))
        assertNull(PrecisionInput.apply(current, "0", "0", "1000000", "0"))
    }
}
