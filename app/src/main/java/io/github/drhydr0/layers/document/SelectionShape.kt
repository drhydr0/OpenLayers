package io.github.drhydr0.layers.document

import kotlin.math.abs
import kotlin.math.hypot

/** An area drawn on the canvas for copying part of the picture. Canvas coordinates. */
sealed interface SelectionShape {
    val bounds: Rect
    fun contains(point: Vec2): Boolean
    fun translatedBy(offset: Vec2): SelectionShape

    /** Too small to hold anything: a tap or a stray stroke, not a selection. */
    val isEmpty: Boolean
}

data class RectSelection(val rect: Rect) : SelectionShape {
    override val bounds get() = rect
    override val isEmpty get() = rect.width < MIN_SIDE || rect.height < MIN_SIDE
    override fun contains(point: Vec2) = rect.contains(point)
    override fun translatedBy(offset: Vec2) =
        RectSelection(Rect(rect.left + offset.x, rect.top + offset.y, rect.right + offset.x, rect.bottom + offset.y))

    companion object {
        /** From a drag between any two opposite corners. */
        fun between(a: Vec2, b: Vec2) =
            RectSelection(Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)))
    }
}

/** A freehand outline, closed from its last point back to its first. */
data class LassoSelection(val points: List<Vec2>) : SelectionShape {
    override val bounds: Rect by lazy { if (points.isEmpty()) Rect(0.0, 0.0, 0.0, 0.0) else Rect.bounding(points) }

    override val isEmpty: Boolean
        get() = points.size < 3 || bounds.width < MIN_SIDE || bounds.height < MIN_SIDE || area() < MIN_SIDE * MIN_SIDE

    /** Even-odd rule, matching how the outline is filled when it is drawn. */
    override fun contains(point: Vec2): Boolean {
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > point.y) != (b.y > point.y) &&
                point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    override fun translatedBy(offset: Vec2) = LassoSelection(points.map { it + offset })

    /** Adds a point while drawing, skipping ones closer than [minDistance] to keep the outline light. */
    fun extendedBy(point: Vec2, minDistance: Double): LassoSelection {
        val last = points.lastOrNull() ?: return LassoSelection(listOf(point))
        return if (hypot(point.x - last.x, point.y - last.y) < minDistance) this else LassoSelection(points + point)
    }

    private fun area(): Double {
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2
    }
}

private const val MIN_SIDE = 2.0
