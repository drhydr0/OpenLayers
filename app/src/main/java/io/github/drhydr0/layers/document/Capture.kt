package io.github.drhydr0.layers.document

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/** What an area selection copies. */
enum class CaptureScope {
    /** Everything visible on the canvas, as exported. */
    CANVAS,

    /** Only the one active layer. */
    ACTIVE_LAYER,

    /** Only the selected layers, in stacking order. */
    SELECTED_LAYERS,
}

/** The pixels to produce: [region] of the canvas at [width] x [height]. */
data class CapturePlan(val region: Rect, val width: Int, val height: Int) {
    /** Output pixels per canvas pixel; below 1 only when the region had to be shrunk to fit memory. */
    val scale get() = width / region.width
}

/** Decides what an area capture draws and how large it is. Plain Kotlin, so it is JVM-tested. */
object CapturePlanner {

    /**
     * The document to draw for a capture. Canvas scope is the document itself, with or
     * without its background. Layer scopes keep only the given layers (and the groups
     * around them, so they stay where they are) on a transparent background. Hidden
     * layers stay hidden either way.
     */
    fun source(document: Document, scope: CaptureScope, layerIds: Set<LayerId>, includeBackground: Boolean): Document {
        if (scope == CaptureScope.CANVAS) {
            return if (includeBackground) document
            else document.copy(canvas = document.canvas.copy(background = Color.TRANSPARENT))
        }
        fun keep(layer: Layer): Layer? = when {
            layer.id in layerIds -> layer
            layer is GroupLayer -> layer.children.mapNotNull(::keep).takeIf { it.isNotEmpty() }?.let { layer.copy(children = it) }
            else -> null
        }
        return document.copy(
            canvas = document.canvas.copy(background = Color.TRANSPARENT),
            layers = document.layers.mapNotNull(::keep),
        )
    }

    /**
     * The whole-pixel area to capture, or null when there is nothing to capture. A canvas
     * capture stops at the canvas edges, like export. Layer captures may reach beyond them.
     */
    fun plan(canvas: CanvasSpec, shape: SelectionShape, scope: CaptureScope, maxPixels: Long): CapturePlan? {
        if (shape.isEmpty) return null
        val wanted = if (scope == CaptureScope.CANVAS) shape.bounds.intersect(canvas.bounds) ?: return null else shape.bounds
        val region = Rect(floor(wanted.left), floor(wanted.top), ceil(wanted.right), ceil(wanted.bottom))
        // Document resolution unless that would not fit in memory.
        val scale = min(1.0, sqrt(maxPixels.toDouble() / (region.width * region.height)))
        val width = floor(region.width * scale).toInt().coerceAtLeast(1)
        val height = floor(region.height * scale).toInt().coerceAtLeast(1)
        return CapturePlan(region, width, height)
    }

    /** Where the captured image goes so it sits exactly over what it copied. */
    fun placement(plan: CapturePlan): Transform {
        // Each axis separately: rounding to whole pixels can differ slightly between them.
        return Transform(
            position = plan.region.center,
            scaleX = plan.region.width / plan.width,
            scaleY = plan.region.height / plan.height,
        )
    }
}
