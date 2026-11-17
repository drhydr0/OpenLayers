package io.github.drhydr0.layers.interaction

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.render.DocumentView
import kotlin.math.hypot

/** Draws the selection outline and handles, and finds which handle a touch lands on. */
class SelectionOverlay(
    private val view: DocumentView,
    private val hitTest: HitTest,
    private val selectedId: () -> LayerId?,
) {
    enum class Handle { CORNER, ROTATE, WIDTH }

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
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val handleStroke = Paint(outlinePaint)
    private val path = Path()

    /** Screen positions of each handle for [layer]. */
    private fun handles(doc: Document, layer: Layer): List<Pair<Handle, Vec2>> {
        val c = hitTest.corners(doc, layer).map(view.viewMatrix::map)
        val topMid = mid(c[0], c[1])
        val center = mid(c[0], c[2])
        val up = normalize(topMid - center)
        val list = c.map { Handle.CORNER to it }.toMutableList()
        list += Handle.ROTATE to (topMid + up * rotateOffset.toDouble())
        if (layer is TextLayer) list += Handle.WIDTH to mid(c[1], c[2])
        return list
    }

    fun handleAt(doc: Document, layer: Layer, x: Float, y: Float): Handle? =
        handles(doc, layer)
            .map { (h, p) -> h to hypot(p.x - x, p.y - y) }
            .filter { it.second <= touchRadius }
            // Rotate and width handles win ties: they're smaller targets near corners.
            .minWithOrNull(compareBy<Pair<Handle, Double>> { it.second }.thenBy { it.first == Handle.CORNER })
            ?.first

    fun draw(canvas: Canvas, doc: Document) {
        val layer = selectedId()?.let(doc::layer) ?: return
        val c = hitTest.corners(doc, layer).map(view.viewMatrix::map)
        path.reset()
        path.moveTo(c[0].x.toFloat(), c[0].y.toFloat())
        c.drop(1).forEach { path.lineTo(it.x.toFloat(), it.y.toFloat()) }
        path.close()
        canvas.drawPath(path, outlinePaint)
        if (layer.locked) return

        val all = handles(doc, layer)
        val topMid = mid(c[0], c[1])
        all.firstOrNull { it.first == Handle.ROTATE }?.second?.let {
            canvas.drawLine(topMid.x.toFloat(), topMid.y.toFloat(), it.x.toFloat(), it.y.toFloat(), outlinePaint)
        }
        all.forEach { (_, p) ->
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), handleRadius, handleFill)
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), handleRadius, handleStroke)
        }
    }

    private fun mid(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2, (a.y + b.y) / 2)

    private fun normalize(v: Vec2): Vec2 {
        val len = hypot(v.x, v.y)
        return if (len < 1e-6) Vec2(0.0, -1.0) else Vec2(v.x / len, v.y / len)
    }

    private companion object {
        const val HANDLE_RADIUS_DP = 8
        // 24 dp radius gives the 48 dp touch target the guidelines ask for.
        const val TOUCH_RADIUS_DP = 24
        const val ROTATE_OFFSET_DP = 36
    }
}
