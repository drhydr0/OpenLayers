package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeTest {

    private val a = LayerId("a")
    private val b = LayerId("b")
    private val c = LayerId("c")
    private val boxes = mapOf(
        a to Rect(0.0, 0.0, 100.0, 50.0),
        b to Rect(300.0, 100.0, 400.0, 300.0),
        c to Rect(150.0, 400.0, 170.0, 420.0),
    )
    private val frame = Rect(0.0, 0.0, 400.0, 420.0)

    @Test
    fun alignMovesEachBoxOnOneAxisOnly() {
        val right = Arrange.align(boxes, frame, Alignment.RIGHT)
        assertVec(Vec2(300.0, 0.0), right.getValue(a))
        assertVec(Vec2(0.0, 0.0), right.getValue(b))
        assertVec(Vec2(230.0, 0.0), right.getValue(c))
        val middle = Arrange.align(boxes, frame, Alignment.CENTER_VERTICAL)
        assertVec(Vec2(0.0, 185.0), middle.getValue(a))
        assertVec(Vec2(0.0, 10.0), middle.getValue(b))
        assertVec(Vec2(0.0, 370.0), Arrange.align(boxes, frame, Alignment.BOTTOM).getValue(a))
        assertVec(Vec2(-150.0, 0.0), Arrange.align(boxes, frame, Alignment.LEFT).getValue(c))
        assertVec(Vec2(0.0, -400.0), Arrange.align(boxes, frame, Alignment.TOP).getValue(c))
        assertVec(Vec2(150.0, 0.0), Arrange.align(boxes, frame, Alignment.CENTER_HORIZONTAL).getValue(a))
    }

    @Test
    fun aSingleLayerAlignsToTheCanvas() {
        val canvas = Rect(0.0, 0.0, 1000.0, 1000.0)
        val one = mapOf(a to Rect(100.0, 100.0, 300.0, 200.0))
        assertVec(Vec2(300.0, 0.0), Arrange.align(one, canvas, Alignment.CENTER_HORIZONTAL).getValue(a))
    }

    @Test
    fun distributeEqualizesGapsAndKeepsTheEnds() {
        // Widths 100, 20, 100 across 0..400: free space 180, so gaps of 90.
        val moved = Arrange.distribute(boxes, Distribution.HORIZONTAL)
        assertVec(Vec2.ZERO, moved.getValue(a))
        assertVec(Vec2.ZERO, moved.getValue(b))
        assertVec(Vec2(40.0, 0.0), moved.getValue(c))
        // Heights 50, 200, 20 across 0..420: free space 150, so gaps of 75.
        val vertical = Arrange.distribute(boxes, Distribution.VERTICAL)
        assertVec(Vec2(0.0, 25.0), vertical.getValue(b))
        assertVec(Vec2.ZERO, vertical.getValue(c))
    }

    @Test
    fun distributeNeedsThreeBoxes() {
        val two = boxes.filterKeys { it != c }
        assertTrue(Arrange.distribute(two, Distribution.HORIZONTAL).values.all { it == Vec2.ZERO })
    }

    @Test
    fun snapPicksTheNearestGuidePerAxis() {
        val canvas = Rect(0.0, 0.0, 1000.0, 1000.0)
        // Center x is 497 (3 from the canvas center); top is 8 from the canvas top.
        val box = Rect(447.0, 8.0, 547.0, 108.0)
        val snap = Arrange.snap(box, listOf(canvas), threshold = 10.0)
        assertVec(Vec2(3.0, -8.0), snap.offset)
        assertEquals(listOf(500.0), snap.verticalGuides)
        assertEquals(listOf(0.0), snap.horizontalGuides)
    }

    @Test
    fun snapToAnotherLayersEdgeAndNothingWhenFar() {
        val other = Rect(600.0, 600.0, 700.0, 700.0)
        val box = Rect(495.0, 300.0, 595.0, 400.0)
        // Right edge 595 is 5 from the other layer's left edge at 600.
        val snap = Arrange.snap(box, listOf(other), threshold = 6.0)
        assertVec(Vec2(5.0, 0.0), snap.offset)
        assertEquals(listOf(600.0), snap.verticalGuides)
        assertTrue(snap.horizontalGuides.isEmpty())
        assertVec(Vec2.ZERO, Arrange.snap(box, listOf(other), threshold = 2.0).offset)
    }

    @Test
    fun distributeOrdersByCenterAndKeepsTheOuterEdges() {
        // A wide box whose center is in the middle but whose right edge reaches furthest.
        val boxes = mapOf(
            a to Rect(0.0, 0.0, 50.0, 10.0),
            b to Rect(100.0, 0.0, 150.0, 10.0),
            c to Rect(200.0, 0.0, 300.0, 10.0),
        )
        val moved = Arrange.distribute(boxes, Distribution.HORIZONTAL)
        // Free space 300 - 200 = 100, so gaps of 50: b's left edge goes to 100. Nothing else moves.
        assertVec(Vec2.ZERO, moved.getValue(a))
        assertVec(Vec2.ZERO, moved.getValue(b))
        assertVec(Vec2.ZERO, moved.getValue(c))
    }

    @Test
    fun overlappingBoxesSpaceTheirCentersEvenly() {
        val boxes = mapOf(
            a to Rect(0.0, 0.0, 100.0, 10.0),
            b to Rect(10.0, 0.0, 110.0, 10.0),
            c to Rect(100.0, 0.0, 200.0, 10.0),
        )
        val moved = Arrange.distribute(boxes, Distribution.HORIZONTAL)
        // Centers 50, 60, 150 become 50, 100, 150.
        assertVec(Vec2(40.0, 0.0), moved.getValue(b))
        assertVec(Vec2.ZERO, moved.getValue(a))
        assertVec(Vec2.ZERO, moved.getValue(c))
    }
}
