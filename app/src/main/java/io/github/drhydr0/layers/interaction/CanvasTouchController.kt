package io.github.drhydr0.layers.interaction

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.history.Command
import io.github.drhydr0.layers.history.TransformLayers
import io.github.drhydr0.layers.history.UpdateLayer
import io.github.drhydr0.layers.render.DocumentView
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Turns touches on the canvas into selection and transform gestures.
 *
 * Gesture ownership is decided when the first finger lands and kept until all fingers
 * lift, so a second finger or the layer sliding under the fingers never retargets it.
 * Changes show as a temporary preview and are committed as one command at the end.
 */
class CanvasTouchController(
    private val view: DocumentView,
    private val hitTest: HitTest,
    private val document: () -> Document?,
    private val commit: (Command) -> Unit,
    private val onSelectionChanged: (LayerId?) -> Unit,
    private val onEditText: (TextLayer) -> Unit,
) : View.OnTouchListener {

    private enum class Mode { NONE, MOVE, CORNER, ROTATE, WIDTH }

    var selectedId: LayerId? = null
        private set

    val overlay = SelectionOverlay(view, hitTest) { selectedId }

    private val density = view.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private var mode = Mode.NONE
    private var startDoc: Document? = null
    private var targetId: LayerId? = null
    private var baseTransform: Transform = Transform.IDENTITY
    private var currentTransform: Transform = Transform.IDENTITY
    private var currentWidth = 0.0
    private val basePoints = mutableMapOf<Int, Vec2>()
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var lastTapTime = 0L
    private var lastTapId: LayerId? = null

    fun select(id: LayerId?) {
        if (selectedId == id) return
        selectedId = id
        onSelectionChanged(id)
        view.invalidate()
    }

    /** Drops a selection whose layer no longer exists, e.g. after undo. */
    fun documentChanged(doc: Document) {
        val id = selectedId
        if (id != null && doc.layer(id) == null) select(null)
        view.invalidate()
    }

    @SuppressLint("ClickableViewAccessibility") // The layers panel offers the accessible route.
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val doc = document() ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(doc, event)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> rebase(event)
            MotionEvent.ACTION_MOVE -> move(event)
            MotionEvent.ACTION_UP -> finish(event)
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }

    private fun begin(doc: Document, event: MotionEvent) {
        startDoc = doc
        downX = event.x
        downY = event.y
        moved = false
        val point = toCanvas(event.x, event.y)
        val selected = selectedId?.let(doc::layer)?.takeIf { !it.locked }

        val handle = selected?.let { overlay.handleAt(doc, it, event.x, event.y) }
        // A finger inside the selected layer keeps it as the target even if another layer covers it.
        val target = when {
            handle != null -> selected
            selected != null && hitTest.contains(doc, selected, point, slop = grabSlop()) -> selected
            else -> hitTest.topmost(doc, point)
        }
        targetId = target?.id
        mode = when {
            target == null -> Mode.NONE
            handle == SelectionOverlay.Handle.CORNER -> Mode.CORNER
            handle == SelectionOverlay.Handle.ROTATE -> Mode.ROTATE
            handle == SelectionOverlay.Handle.WIDTH -> Mode.WIDTH
            else -> Mode.MOVE
        }
        if (target != null) {
            baseTransform = target.transform
            currentTransform = target.transform
            currentWidth = (target as? TextLayer)?.boxWidth ?: 0.0
        }
        basePoints.clear()
        basePoints[event.getPointerId(0)] = point
    }

    /** A finger was added or lifted: keep the result so far and restart from current positions. */
    private fun rebase(event: MotionEvent) {
        baseTransform = currentTransform
        basePoints.clear()
        val lifted = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        for (i in 0 until event.pointerCount) {
            if (i != lifted) basePoints[event.getPointerId(i)] = toCanvas(event.getX(i), event.getY(i))
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) moved = true
    }

    private fun move(event: MotionEvent) {
        val doc = startDoc ?: return
        val target = targetId?.let(doc::layer) ?: return
        if (!moved && hypot(event.x - downX, event.y - downY) < touchSlop) return
        if (!moved) {
            moved = true
            select(target.id)
        }
        val current = (0 until event.pointerCount)
            .mapNotNull { i -> basePoints[event.getPointerId(i)]?.let { it to toCanvas(event.getX(i), event.getY(i)) } }
        if (current.isEmpty()) return
        val (from, to) = current[0]
        when (mode) {
            Mode.MOVE -> currentTransform = if (current.size >= 2) {
                GestureMath.pinch(baseTransform, from, current[1].first, to, current[1].second)
            } else {
                GestureMath.drag(baseTransform, from, to)
            }
            Mode.CORNER -> currentTransform = GestureMath.scaleFromCorner(baseTransform, from, to)
            Mode.ROTATE -> currentTransform = GestureMath.rotateFromHandle(baseTransform, from, to)
            Mode.WIDTH -> currentWidth = GestureMath.textBoxWidth(baseTransform, to, minTextWidth(target))
            Mode.NONE -> return
        }
        view.document = doc.withLayerReplaced(previewOf(target))
    }

    private fun finish(event: MotionEvent) {
        val doc = startDoc
        val target = targetId?.let { doc?.layer(it) }
        if (doc != null && target != null && moved && mode != Mode.NONE) {
            commit(commandFor(doc, target))
        } else if (!moved) {
            view.performClick()
            tap(event, target)
        }
        reset()
    }

    private fun tap(event: MotionEvent, target: Layer?) {
        val now = event.eventTime
        val isDoubleTap = target != null && target.id == lastTapId && now - lastTapTime <= doubleTapTimeout
        if (isDoubleTap && target is TextLayer) {
            onEditText(target)
            lastTapId = null
        } else {
            select(target?.id)
            lastTapId = target?.id
            lastTapTime = now
        }
    }

    private fun cancel() {
        // Restore the committed document; nothing from the interrupted gesture is kept.
        startDoc?.let { view.document = it }
        reset()
    }

    private fun reset() {
        mode = Mode.NONE
        startDoc = null
        targetId = null
        basePoints.clear()
    }

    private fun previewOf(target: Layer): Layer =
        if (mode == Mode.WIDTH && target is TextLayer) target.copy(boxWidth = currentWidth)
        else target.withTransform(currentTransform)

    private fun commandFor(doc: Document, target: Layer): Command = when (mode) {
        Mode.WIDTH -> UpdateLayer.of(doc, previewOf(target), "Resize text box")
        Mode.CORNER -> TransformLayers.of(doc, mapOf(target.id to currentTransform), "Resize")
        Mode.ROTATE -> TransformLayers.of(doc, mapOf(target.id to currentTransform), "Rotate")
        else -> TransformLayers.of(doc, mapOf(target.id to currentTransform), "Move")
    }

    private fun minTextWidth(layer: Layer) = ((layer as? TextLayer)?.style?.fontSize ?: MIN_TEXT_WIDTH)
        .coerceAtLeast(MIN_TEXT_WIDTH)

    private fun toCanvas(x: Float, y: Float): Vec2 =
        view.viewMatrix.inverted()?.map(Vec2(x.toDouble(), y.toDouble())) ?: Vec2(x.toDouble(), y.toDouble())

    /** A finger-sized margin in canvas pixels, so small layers stay easy to grab. */
    private fun grabSlop(): Double {
        val viewScale = sqrt(kotlin.math.abs(view.viewMatrix.determinant)).coerceAtLeast(1e-6)
        return GRAB_SLOP_DP * density / viewScale
    }

    private companion object {
        const val GRAB_SLOP_DP = 12
        const val MIN_TEXT_WIDTH = 16.0
    }
}
