package io.github.drhydr0.layers.interaction

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.Arrange
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.ShapeLayer
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.render.DocumentView
import kotlin.math.hypot

/**
 * Draws the selection outline and handles, and finds which handle a touch lands on.
 * One layer gets handles on its own (possibly rotated) box. Several layers get one
 * upright bounding box with shared handles.
 */
class SelectionOverlay(
    private val view: DocumentView,
    private val hitTest: HitTest,
    private val selectedIds: () -> Set<LayerId>,
    private val scopeId: () -> LayerId? = { null },
) {
    /**
     * CORNER scales evenly (text and several layers). RESIZE stretches freely from a corner
     * or side ([sx], [sy] say which: -1, 0, or 1 per axis). ROTATE turns; WIDTH rewraps text.
     */
    enum class Handle { CORNER, RESIZE, ROTATE, WIDTH }

    data class HandleHit(val kind: Handle, val sx: Int = 0, val sy: Int = 0)

    /** Shapes, images, and stickers (groups) stretch freely; text keeps its proportions. */
    fun canStretch(layer: Layer) = layer is ShapeLayer || layer is ImageLayer || layer is GroupLayer

    private val density = view.resources.displayMetrics.density
    private val handleRadius = HANDLE_RADIUS_DP * density
    private val touchRadius = TOUCH_RADIUS_DP * density
    private val rotateOffset = ROTATE_OFFSET_DP * density

    private val accent = view.context.getColor(R.color.selection_accent)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = accent
    }
    private val memberPaint = Paint(outlinePaint).apply { strokeWidth = density }
    private val scopePaint = Paint(memberPaint).apply {
        pathEffect = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density
        color = view.context.getColor(R.color.snap_guide)
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val handleStroke = Paint(outlinePaint)
    private val path = Path()

    /** The box the handles sit on, in screen coordinates: top-left, top-right, bottom-right, bottom-left. */
    private fun box(doc: Document, layers: List<Layer>): List<Vec2> {
        layers.singleOrNull()?.let { return hitTest.corners(doc, it).map(view.viewMatrix::map) }
        val b = Rect.bounding(layers.flatMap { hitTest.corners(doc, it) }.map(view.viewMatrix::map))
        return listOf(Vec2(b.left, b.top), Vec2(b.right, b.top), Vec2(b.right, b.bottom), Vec2(b.left, b.bottom))
    }

    /** Screen positions of each handle for the movable [layers]. */
    private fun handles(doc: Document, layers: List<Layer>): List<Pair<HandleHit, Vec2>> {
        if (layers.isEmpty()) return emptyList()
        val c = box(doc, layers)
        val topMid = mid(c[0], c[1])
        val up = normalize(topMid - mid(c[0], c[2]))
        val single = layers.singleOrNull()
        val list = mutableListOf<Pair<HandleHit, Vec2>>()
        // Corners in order top-left, top-right, bottom-right, bottom-left, as the layer sees them.
        val cornerSigns = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1)
        if (single != null && canStretch(single)) {
            c.forEachIndexed { i, p -> list += HandleHit(Handle.RESIZE, cornerSigns[i].first, cornerSigns[i].second) to p }
            list += HandleHit(Handle.RESIZE, 0, -1) to mid(c[0], c[1])
            list += HandleHit(Handle.RESIZE, 1, 0) to mid(c[1], c[2])
            list += HandleHit(Handle.RESIZE, 0, 1) to mid(c[2], c[3])
            list += HandleHit(Handle.RESIZE, -1, 0) to mid(c[3], c[0])
        } else {
            c.forEach { list += HandleHit(Handle.CORNER) to it }
            if (single is TextLayer) list += HandleHit(Handle.WIDTH) to mid(c[1], c[2])
        }
        list += HandleHit(Handle.ROTATE) to (topMid + up * rotateOffset.toDouble())
        return list
    }

    fun handleAt(doc: Document, layers: List<Layer>, x: Float, y: Float): HandleHit? =
        handles(doc, layers)
            .map { (h, p) -> h to hypot(p.x - x, p.y - y) }
            .filter { it.second <= touchRadius }
            // Rotate and width handles win ties: they're smaller targets near corners.
            .minWithOrNull(compareBy<Pair<HandleHit, Double>> { it.second }.thenBy { it.first.kind == Handle.CORNER || it.first.kind == Handle.RESIZE })
            ?.first

    /** Guide lines to show while a drag is snapped; null when not snapping. */
    var guides: Arrange.Snap? = null

    private fun drawGuides(canvas: Canvas) {
        val snap = guides ?: return
        // Guides are canvas positions; draw them across the whole view.
        snap.verticalGuides.forEach { x ->
            val sx = view.viewMatrix.map(Vec2(x, 0.0)).x.toFloat()
            canvas.drawLine(sx, 0f, sx, view.height.toFloat(), guidePaint)
        }
        snap.horizontalGuides.forEach { y ->
            val sy = view.viewMatrix.map(Vec2(0.0, y)).y.toFloat()
            canvas.drawLine(0f, sy, view.width.toFloat(), sy, guidePaint)
        }
    }

    fun draw(canvas: Canvas, doc: Document) {
        drawGuides(canvas)
        // A dashed frame marks the group being edited.
        scopeId()?.let(doc::layer)?.let { outline(canvas, hitTest.corners(doc, it).map(view.viewMatrix::map), scopePaint) }
        val selected = selectedIds().mapNotNull(doc::layer)
        if (selected.isEmpty()) return
        if (selected.size > 1) selected.forEach { outline(canvas, hitTest.corners(doc, it).map(view.viewMatrix::map), memberPaint) }

        // Locked or hidden layers show an outline but take no part in handles.
        val movable = selected.filter { !it.locked && it.visible }
        val boxLayers = movable.ifEmpty { if (selected.size == 1) selected else return }
        val c = box(doc, boxLayers)
        outline(canvas, c, outlinePaint)
        if (movable.isEmpty()) return

        val all = handles(doc, movable)
        val topMid = mid(c[0], c[1])
        all.firstOrNull { it.first.kind == Handle.ROTATE }?.second?.let {
            canvas.drawLine(topMid.x.toFloat(), topMid.y.toFloat(), it.x.toFloat(), it.y.toFloat(), outlinePaint)
        }
        all.forEach { (hit, p) ->
            val x = p.x.toFloat()
            val y = p.y.toFloat()
            if (hit.kind == Handle.RESIZE && (hit.sx == 0 || hit.sy == 0)) {
                // Side handles are small squares, so they read differently from corners.
                val r = handleRadius * SIDE_HANDLE_SCALE
                canvas.drawRect(x - r, y - r, x + r, y + r, handleFill)
                canvas.drawRect(x - r, y - r, x + r, y + r, handleStroke)
            } else {
                canvas.drawCircle(x, y, handleRadius, handleFill)
                canvas.drawCircle(x, y, handleRadius, handleStroke)
            }
        }
    }

    private fun outline(canvas: Canvas, corners: List<Vec2>, paint: Paint) {
        path.reset()
        path.moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
        corners.drop(1).forEach { path.lineTo(it.x.toFloat(), it.y.toFloat()) }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun mid(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2, (a.y + b.y) / 2)

    private fun normalize(v: Vec2): Vec2 {
        val len = hypot(v.x, v.y)
        return if (len < 1e-6) Vec2(0.0, -1.0) else Vec2(v.x / len, v.y / len)
    }

    private companion object {
        const val HANDLE_RADIUS_DP = 8
        const val SIDE_HANDLE_SCALE = 0.75f
        // 24 dp radius gives the 48 dp touch target the guidelines ask for.
        const val TOUCH_RADIUS_DP = 24
        const val ROTATE_OFFSET_DP = 36
    }
}
