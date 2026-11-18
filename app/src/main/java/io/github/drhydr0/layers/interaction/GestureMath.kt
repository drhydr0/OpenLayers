/**
 * Hit testing, gesture ownership, selection, snapping, and transform previews.
 *
 * The math lives in plain Kotlin ([GestureMath], [HitTest]) so it's JVM-tested; the
 * Android touch handling only feeds it points.
 */
package io.github.drhydr0.layers.interaction

import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Pure transform math for gestures. All points are in canvas coordinates. */
object GestureMath {

    /** Smallest scale a gesture can reach, so a layer never collapses to nothing. */
    const val MIN_SCALE = 0.01
    const val MAX_SCALE = 100.0

    /** Rotation snaps to a right angle when within this many degrees of it. */
    const val SNAP_DEGREES = 4.0

    fun drag(start: Transform, from: Vec2, to: Vec2): Transform = start.translatedBy(to - from)

    /**
     * Two-finger move, pinch, and twist. The content under the fingers stays under them:
     * the start transform is moved, scaled, and rotated around the start midpoint so the
     * start finger pair lands on the current finger pair.
     */
    fun pinch(start: Transform, startA: Vec2, startB: Vec2, nowA: Vec2, nowB: Vec2): Transform {
        val startSpan = distance(startA, startB)
        if (startSpan < 1e-6) return drag(start, midpoint(startA, startB), midpoint(nowA, nowB))
        val factor = clampFactor(start, distance(nowA, nowB) / startSpan)
        val turn = angleDegrees(nowB - nowA) - angleDegrees(startB - startA)
        val startMid = midpoint(startA, startB)
        val nowMid = midpoint(nowA, nowB)
        val offset = rotate(start.position - startMid, turn) * factor
        return start.copy(
            position = nowMid + offset,
            scaleX = start.scaleX * factor,
            scaleY = start.scaleY * factor,
            rotationDegrees = normalizeDegrees(start.rotationDegrees + turn),
        )
    }

    /**
     * Group corner handle: scales a layer around a shared [pivot], so several layers grow
     * or shrink together and keep their arrangement. [factor] should come from [groupFactor].
     */
    fun scaleAround(start: Transform, pivot: Vec2, factor: Double): Transform = start.copy(
        position = pivot + (start.position - pivot) * factor,
        scaleX = start.scaleX * factor,
        scaleY = start.scaleY * factor,
    )

    /** Group rotation handle: turns a layer around a shared [pivot]. */
    fun rotateAround(start: Transform, pivot: Vec2, turnDegrees: Double): Transform = start.copy(
        position = pivot + rotate(start.position - pivot, turnDegrees),
        rotationDegrees = normalizeDegrees(start.rotationDegrees + turnDegrees),
    )

    /** Scale factor for a handle dragged [from] to [to] around [pivot], limited so no layer in [starts] leaves the scale range. */
    fun groupFactor(starts: Collection<Transform>, pivot: Vec2, from: Vec2, to: Vec2): Double {
        val startDistance = distance(pivot, from)
        if (startDistance < 1e-6 || starts.isEmpty()) return 1.0
        val smallest = starts.minOf { minOf(it.scaleX, it.scaleY) }
        val largest = starts.maxOf { maxOf(it.scaleX, it.scaleY) }
        return (distance(pivot, to) / startDistance).coerceIn(MIN_SCALE / smallest, MAX_SCALE / largest)
    }

    fun turnDegrees(pivot: Vec2, from: Vec2, to: Vec2): Double =
        angleDegrees(to - pivot) - angleDegrees(from - pivot)

    /**
     * Free resize from a corner or side handle. [box] is the layer's content box in its own
     * coordinates; ([sx], [sy]) picks the handle: -1, 0, or 1 per axis (corner: both
     * non-zero; side: one zero). The opposite corner or side stays fixed on the canvas and
     * the handle follows the finger, so width and height change independently.
     */
    fun resizeFromHandle(start: Transform, box: Rect, sx: Int, sy: Int, finger: Vec2, minSize: Double): Transform {
        // Signed scales carry the flips, so the math below needs no special cases.
        val signX = if (start.flipX) -start.scaleX else start.scaleX
        val signY = if (start.flipY) -start.scaleY else start.scaleY
        fun pick(s: Int, low: Double, high: Double) = when { s < 0 -> low; s > 0 -> high; else -> (low + high) / 2 }
        val anchor = Vec2(pick(-sx, box.left, box.right), pick(-sy, box.top, box.bottom))
        val handle = Vec2(pick(sx, box.left, box.right), pick(sy, box.top, box.bottom))
        val anchorOnCanvas = start.toMatrix().map(anchor)
        // The finger relative to the fixed point, in the layer's rotated frame.
        val d = rotate(finger - anchorOnCanvas, -start.rotationDegrees)
        fun newScale(s: Int, along: Double, span: Double, current: Double): Double {
            if (s == 0 || abs(span) < 1e-9) return current
            val wanted = along / span
            val minimum = minSize / abs(span)
            // Dragging past the fixed side stops at the minimum instead of flipping.
            return if (wanted * current <= 0) kotlin.math.sign(current) * minimum
            else kotlin.math.sign(current) * abs(wanted).coerceIn(minimum, MAX_SCALE)
        }
        val newX = newScale(sx, d.x, handle.x - anchor.x, signX)
        val newY = newScale(sy, d.y, handle.y - anchor.y, signY)
        // Keep the anchor where it was: position = anchor on canvas minus the rotated, scaled anchor.
        val offset = rotate(Vec2(anchor.x * newX, anchor.y * newY), start.rotationDegrees)
        return start.copy(
            position = anchorOnCanvas - offset,
            scaleX = abs(newX),
            scaleY = abs(newY),
        )
    }

    /** Corner handle: uniform scale around the layer center, keeping the aspect ratio. */
    fun scaleFromCorner(start: Transform, from: Vec2, to: Vec2): Transform {
        val center = start.position
        val startDistance = distance(center, from)
        if (startDistance < 1e-6) return start
        val factor = clampFactor(start, distance(center, to) / startDistance)
        return start.copy(scaleX = start.scaleX * factor, scaleY = start.scaleY * factor)
    }

    /** Rotation handle: turns around the layer center, snapping near right angles if [snap]. */
    fun rotateFromHandle(start: Transform, from: Vec2, to: Vec2, snap: Boolean = true): Transform {
        val center = start.position
        val turn = angleDegrees(to - center) - angleDegrees(from - center)
        var angle = normalizeDegrees(start.rotationDegrees + turn)
        if (snap) angle = snapAngle(angle)
        return start.copy(rotationDegrees = angle)
    }

    /**
     * Text width handle: the box stays centered, so the new width is twice the finger's
     * horizontal distance from the center in the layer's own (unscaled, unrotated) space.
     */
    fun textBoxWidth(start: Transform, finger: Vec2, minWidth: Double): Double {
        val local = start.toMatrix().inverted()?.map(finger) ?: return minWidth
        return (abs(local.x) * 2).coerceAtLeast(minWidth)
    }

    fun snapAngle(degrees: Double): Double {
        val nearest = (degrees / 90.0).roundToInt() * 90.0
        return if (abs(degrees - nearest) <= SNAP_DEGREES) normalizeDegrees(nearest) else degrees
    }

    /** Keeps angles in (-180, 180] so saved values stay small and comparable. */
    fun normalizeDegrees(degrees: Double): Double {
        var d = degrees % 360.0
        if (d <= -180.0) d += 360.0
        if (d > 180.0) d -= 360.0
        return d
    }

    private fun clampFactor(start: Transform, factor: Double): Double {
        val smallest = minOf(start.scaleX, start.scaleY)
        val largest = maxOf(start.scaleX, start.scaleY)
        return factor.coerceIn(MIN_SCALE / smallest, MAX_SCALE / largest)
    }

    private fun distance(a: Vec2, b: Vec2) = hypot(b.x - a.x, b.y - a.y)
    private fun midpoint(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2, (a.y + b.y) / 2)
    private fun angleDegrees(v: Vec2) = Math.toDegrees(atan2(v.y, v.x))

    private fun rotate(v: Vec2, degrees: Double): Vec2 {
        val r = Math.toRadians(degrees)
        val cos = kotlin.math.cos(r)
        val sin = kotlin.math.sin(r)
        return Vec2(v.x * cos - v.y * sin, v.x * sin + v.y * cos)
    }
}
