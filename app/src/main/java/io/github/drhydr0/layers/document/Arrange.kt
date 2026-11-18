package io.github.drhydr0.layers.document

import kotlin.math.abs

enum class Alignment { LEFT, CENTER_HORIZONTAL, RIGHT, TOP, CENTER_VERTICAL, BOTTOM }

enum class Distribution { HORIZONTAL, VERTICAL }

/**
 * Alignment, distribution, and snapping math. Works on each layer's upright bounding box
 * (as the selection outline shows it) and returns how far each layer should move, so the
 * caller applies it as one transform command. Plain Kotlin, so it is JVM-tested.
 */
object Arrange {

    /**
     * Offsets that line the boxes up against [frame]: the canvas for a single layer, the
     * selection's own bounding box for several.
     */
    fun align(boxes: Map<LayerId, Rect>, frame: Rect, alignment: Alignment): Map<LayerId, Vec2> =
        boxes.mapValues { (_, box) ->
            when (alignment) {
                Alignment.LEFT -> Vec2(frame.left - box.left, 0.0)
                Alignment.CENTER_HORIZONTAL -> Vec2(frame.center.x - box.center.x, 0.0)
                Alignment.RIGHT -> Vec2(frame.right - box.right, 0.0)
                Alignment.TOP -> Vec2(0.0, frame.top - box.top)
                Alignment.CENTER_VERTICAL -> Vec2(0.0, frame.center.y - box.center.y)
                Alignment.BOTTOM -> Vec2(0.0, frame.bottom - box.bottom)
            }
        }

    /**
     * Offsets that space three or more boxes evenly: the two outermost stay put and the
     * gaps between neighbours become equal. Fewer than three boxes need no change.
     */
    fun distribute(boxes: Map<LayerId, Rect>, direction: Distribution): Map<LayerId, Vec2> {
        if (boxes.size < 3) return boxes.mapValues { Vec2.ZERO }
        val horizontal = direction == Distribution.HORIZONTAL
        fun start(r: Rect) = if (horizontal) r.left else r.top
        fun size(r: Rect) = if (horizontal) r.width else r.height
        fun center(r: Rect) = start(r) + size(r) / 2
        // Left to right (or top to bottom) by center, so layers keep their order.
        val ordered = boxes.entries.sortedBy { center(it.value) }
        val first = ordered.first().value
        val last = ordered.last().value
        val gap = (start(last) + size(last) - start(first) - ordered.sumOf { size(it.value) }) / (ordered.size - 1)
        val result = mutableMapOf<LayerId, Vec2>()
        if (gap >= 0) {
            // Room for all of them: equal empty space between neighbours, ends fixed.
            var cursor = start(first)
            for ((id, box) in ordered) {
                val delta = cursor - start(box)
                result[id] = if (horizontal) Vec2(delta, 0.0) else Vec2(0.0, delta)
                cursor += size(box) + gap
            }
        } else {
            // They overlap too much for equal gaps: space their centers evenly instead.
            val step = (center(last) - center(first)) / (ordered.size - 1)
            ordered.forEachIndexed { i, (id, box) ->
                val delta = center(first) + step * i - center(box)
                result[id] = if (horizontal) Vec2(delta, 0.0) else Vec2(0.0, delta)
            }
        }
        return result
    }

    /** A snap correction and the guide lines to show for it. */
    data class Snap(val offset: Vec2, val verticalGuides: List<Double>, val horizontalGuides: List<Double>)

    /**
     * Nudges a moving [box] so its edges or center meet the nearest guide within
     * [threshold] on each axis. [targets] are other boxes to snap to (the canvas included).
     */
    fun snap(box: Rect, targets: List<Rect>, threshold: Double): Snap {
        val xs = targets.flatMap { listOf(it.left, it.center.x, it.right) }
        val ys = targets.flatMap { listOf(it.top, it.center.y, it.bottom) }
        val dx = nearest(listOf(box.left, box.center.x, box.right), xs, threshold)
        val dy = nearest(listOf(box.top, box.center.y, box.bottom), ys, threshold)
        return Snap(
            Vec2(dx?.first ?: 0.0, dy?.first ?: 0.0),
            listOfNotNull(dx?.second),
            listOfNotNull(dy?.second),
        )
    }

    /** The smallest correction that puts one of [own] on one of [guides], with that guide. */
    private fun nearest(own: List<Double>, guides: List<Double>, threshold: Double): Pair<Double, Double>? =
        own.flatMap { o -> guides.map { g -> (g - o) to g } }
            .filter { abs(it.first) <= threshold }
            .minByOrNull { abs(it.first) }
}
