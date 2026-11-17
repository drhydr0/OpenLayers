package io.github.drhydr0.layers.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.ShapeKind
import io.github.drhydr0.layers.document.ShapeLayer

/** Draws shape layers. Part of the shared rendering path, so preview and export match. */
internal object ShapeDrawing {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    /** [alpha] is the layer's opacity, 0 to 255. Draws in the layer's own coordinates. */
    fun draw(target: Canvas, shape: ShapeLayer, alpha: Int) {
        val hasFill = shape.fill.alpha > 0 && !shape.isLine
        val hasStroke = shape.stroke.alpha > 0 && shape.strokeWidth > 0
        // Outline and fill overlap along the edge. Fading them as one object avoids a
        // darker band there when the layer is see-through.
        val together = alpha < 255 && hasFill && hasStroke
        val save = if (together) target.saveLayerAlpha(null, alpha) else -1
        val each = if (together) 255 else alpha
        val box = Rect.centered(shape.width, shape.height).toRectF()
        if (hasFill) {
            fillPaint.color = shape.fill.argb
            fillPaint.alpha = shape.fill.alpha * each / 255
            outline(target, shape, box, fillPaint)
        }
        if (hasStroke) {
            strokePaint.color = shape.stroke.argb
            strokePaint.alpha = shape.stroke.alpha * each / 255
            strokePaint.strokeWidth = shape.strokeWidth.toFloat()
            if (shape.isLine) line(target, shape, box) else outline(target, shape, box, strokePaint)
        }
        if (together) target.restoreToCount(save)
    }

    private fun outline(target: Canvas, shape: ShapeLayer, box: RectF, paint: Paint) {
        when (shape.kind) {
            ShapeKind.ELLIPSE -> target.drawOval(box, paint)
            ShapeKind.ROUNDED_RECTANGLE -> {
                // A radius beyond half the shorter side would fold over itself.
                val r = shape.cornerRadius.coerceAtMost(minOf(shape.width, shape.height) / 2).toFloat()
                target.drawRoundRect(box, r, r, paint)
            }
            else -> target.drawRect(box, paint)
        }
    }

    private fun line(target: Canvas, shape: ShapeLayer, box: RectF) {
        strokePaint.strokeCap = Paint.Cap.ROUND
        if (shape.kind == ShapeKind.LINE) {
            target.drawLine(box.left, 0f, box.right, 0f, strokePaint)
        } else {
            val head = (shape.strokeWidth * ShapeLayer.ARROW_HEAD_FACTOR).toFloat().coerceAtMost(box.width())
            // The shaft stops inside the head, so the tip stays sharp.
            target.drawLine(box.left, 0f, box.right - head / 2, 0f, strokePaint)
            path.reset()
            path.moveTo(box.right, 0f)
            path.lineTo(box.right - head, -head / 2)
            path.lineTo(box.right - head, head / 2)
            path.close()
            fillPaint.color = strokePaint.color
            fillPaint.alpha = strokePaint.alpha
            target.drawPath(path, fillPaint)
        }
        strokePaint.strokeCap = Paint.Cap.BUTT
    }
}
