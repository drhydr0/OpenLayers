package io.github.drhydr0.layers.interaction

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.LassoSelection
import io.github.drhydr0.layers.document.RectSelection
import io.github.drhydr0.layers.document.SelectionShape
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.render.DocumentView
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Draws a rectangle or freehand area on the canvas. A drag that starts inside the
 * current area moves it; a drag that starts outside draws a new one. Nothing in the
 * document changes until the area is copied.
 */
class AreaSelectionTool(
    private val view: DocumentView,
    private val lasso: Boolean,
    private val onChanged: (SelectionShape?) -> Unit,
) {
    var shape: SelectionShape? = null
        private set

    private var start = Vec2.ZERO
    private var last = Vec2.ZERO
    private var moving = false
    private val density = view.resources.displayMetrics.density

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = view.context.getColor(R.color.selection_accent)
        pathEffect = DashPathEffect(floatArrayOf(8 * density, 5 * density), 0f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = view.context.getColor(R.color.layer_row_selected) }
    private val path = Path()

    fun onTouch(event: MotionEvent) {
        val point = toCanvas(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                start = point
                last = point
                moving = shape?.contains(point) == true
                if (!moving) shape = if (lasso) LassoSelection(listOf(point)) else null
            }
            MotionEvent.ACTION_MOVE -> {
                val current = shape
                shape = when {
                    moving -> current?.translatedBy(point - last)
                    lasso -> ((current as? LassoSelection) ?: LassoSelection(emptyList())).extendedBy(point, minPointGap())
                    else -> RectSelection.between(start, point)
                }
                last = point
                view.invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // A tap or stray mark outside the area clears it.
                if (shape?.isEmpty == true) shape = null
                onChanged(shape)
                view.invalidate()
            }
        }
    }

    fun draw(canvas: Canvas) {
        val current = shape ?: return
        path.reset()
        when (current) {
            is RectSelection -> {
                val r = view.viewMatrix.mapRect(current.rect)
                path.addRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), Path.Direction.CW)
            }
            is LassoSelection -> {
                current.points.map(view.viewMatrix::map).forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat()) else path.lineTo(p.x.toFloat(), p.y.toFloat())
                }
                path.close()
            }
        }
        path.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(path, fill)
        canvas.drawPath(path, outline)
    }

    private fun toCanvas(x: Float, y: Float): Vec2 =
        view.viewMatrix.inverted()?.map(Vec2(x.toDouble(), y.toDouble())) ?: Vec2(x.toDouble(), y.toDouble())

    /** About 3 dp on screen, whatever the zoom: smooth outlines without thousands of points. */
    private fun minPointGap(): Double {
        val viewScale = sqrt(abs(view.viewMatrix.determinant)).coerceAtLeast(1e-6)
        return POINT_GAP_DP * density / viewScale
    }

    private companion object {
        const val POINT_GAP_DP = 3
    }
}
