package io.github.drhydr0.layers.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.Matrix
import io.github.drhydr0.layers.document.Rect

/**
 * The on-screen canvas. Draws the document through [DocumentRenderer], plus preview-only
 * decorations: the workspace, a checkerboard behind transparency, and a dimmed area
 * outside the canvas edges.
 */
class DocumentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    val textLayouts = TextLayouts()
    private val renderer = DocumentRenderer(textLayouts)
    private var images: ImageSource = ImageSource { null }

    var document: Document? = null
        set(value) {
            val canvasChanged = field?.canvas != value?.canvas
            field = value
            if (canvasChanged) fitToScreen()
            invalidate()
        }

    /** Canvas pixels to view pixels. */
    var viewMatrix: Matrix = Matrix.IDENTITY
        private set

    /** Extra drawing on top of the document in view coordinates, e.g. selection handles. */
    var overlay: ((Canvas) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val outsidePaint = Paint().apply { color = context.getColor(R.color.canvas_outside_dim) }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = context.getColor(R.color.canvas_edge)
    }
    private val outside = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val checkerPaint = Paint().apply { shader = checkerShader((8 * density).toInt().coerceAtLeast(2)) }

    init {
        setBackgroundColor(context.getColor(R.color.canvas_workspace))
        contentDescription = context.getString(R.string.editor_canvas_description)
    }

    fun setImageSource(source: ImageSource) {
        images = source
        invalidate()
    }

    fun fitToScreen() {
        val doc = document ?: return
        if (width == 0 || height == 0) return
        viewMatrix = Viewport.fit(
            doc.canvas.width, doc.canvas.height,
            Rect(0.0, 0.0, width.toDouble(), height.toDouble()),
            margin = (MARGIN_DP * density).toDouble(),
        )
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitToScreen()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val doc = document ?: return
        val canvasOnScreen = viewMatrix.mapRect(doc.canvas.bounds).toRectF()

        if (doc.canvas.background.alpha < 255) canvas.drawRect(canvasOnScreen, checkerPaint)

        val save = canvas.save()
        canvas.concat(viewMatrix.toAndroid())
        renderer.draw(canvas, doc, images, clipToCanvas = false)
        canvas.restoreToCount(save)

        // Dim what lies outside the canvas: it stays visible and movable but won't export.
        outside.reset()
        outside.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        outside.addRect(canvasOnScreen, Path.Direction.CW)
        canvas.drawPath(outside, outsidePaint)
        canvas.drawRect(canvasOnScreen, edgePaint)

        overlay?.invoke(canvas)
    }

    private fun checkerShader(cell: Int): Shader {
        val tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(tile)
        c.drawColor(Color.WHITE)
        val gray = Paint().apply { color = CHECKER_GRAY }
        c.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), gray)
        c.drawRect(cell.toFloat(), cell.toFloat(), cell * 2f, cell * 2f, gray)
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private companion object {
        const val MARGIN_DP = 16
        const val CHECKER_GRAY = 0xFFD0D0D0.toInt()
    }
}
