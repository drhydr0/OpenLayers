package io.github.drhydr0.layers.interaction

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Matrix
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.history.Command
import io.github.drhydr0.layers.history.TransformLayers
import io.github.drhydr0.layers.history.UpdateLayer
import io.github.drhydr0.layers.render.DocumentView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Turns touches on the canvas into selection and transform gestures.
 *
 * Gesture ownership is decided when the first finger lands and kept until all fingers
 * lift, so a second finger or a layer sliding under the fingers never retargets it.
 * Changes show as a temporary preview and are committed as one command at the end,
 * however many layers they move.
 */
class CanvasTouchController(
    private val view: DocumentView,
    private val hitTest: HitTest,
    private val document: () -> Document?,
    private val commit: (Command) -> Unit,
    private val onSelectionChanged: (Set<LayerId>) -> Unit,
    private val onEditText: (TextLayer) -> Unit,
    private val onScopeChanged: (LayerId?) -> Unit = {},
) : View.OnTouchListener {

    private enum class Mode { NONE, MOVE, CORNER, ROTATE, WIDTH }

    /** In selection order. */
    var selectedIds: Set<LayerId> = emptySet()
        private set

    /**
     * The group being edited, or null for the top level. Touches select and move the
     * layers directly inside it; everything else stays put.
     */
    var scopeId: LayerId? = null
        private set

    val overlay = SelectionOverlay(view, hitTest, { selectedIds }, { scopeId })

    /** While on, taps add layers to or remove them from the selection. */
    var multiSelectMode = false

    /** While on, every touch moves the view instead of editing (canvas navigation mode). */
    var navigationMode = false
        set(value) {
            // Entering navigation drops any half-finished edit gesture.
            if (value && !field) cancel()
            field = value
        }

    /** While set, touches draw and adjust an area selection instead of editing layers. */
    var areaTool: AreaSelectionTool? = null
        set(value) {
            if (value != null && field == null) cancel()
            field = value
            view.invalidate()
        }

    private val navigation = NavigationGestures(view)
    private val density = view.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private var mode = Mode.NONE
    private var startDoc: Document? = null
    private var targetIds: List<LayerId> = emptyList()
    private var tappedId: LayerId? = null
    private var baseTransforms: Map<LayerId, Transform> = emptyMap()
    private var currentTransforms: Map<LayerId, Transform> = emptyMap()
    private var currentWidth = 0.0
    private var groupPivot = Vec2.ZERO
    private var scopeInverse = Matrix.IDENTITY
    private val basePoints = mutableMapOf<Int, Vec2>()
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var lastTapTime = 0L
    private var lastTapId: LayerId? = null

    fun select(id: LayerId?) = setSelection(setOfNotNull(id))

    /** Enters a group to edit its children, or returns to the top level with null. */
    fun enterScope(id: LayerId?) {
        if (scopeId == id) return
        cancel()
        scopeId = id
        setSelection(emptySet())
        onScopeChanged(id)
        view.invalidate()
    }

    /** Selects a layer anywhere in the tree, entering the group that holds it. */
    fun selectInTree(doc: Document, id: LayerId, addToSelection: Boolean) {
        val parent = doc.ancestors(id)?.lastOrNull()?.id
        if (parent != scopeId) {
            enterScope(parent)
            select(id)
        } else if (addToSelection) {
            toggle(id)
        } else {
            select(id)
        }
    }

    fun toggle(id: LayerId) = setSelection(if (id in selectedIds) selectedIds - id else selectedIds + id)

    fun setSelection(ids: Set<LayerId>) {
        if (selectedIds == ids) return
        selectedIds = ids
        onSelectionChanged(ids)
        view.invalidate()
    }

    /**
     * Center of the selection's bounding box, in the coordinates of the group being edited.
     * Null when nothing is selected.
     */
    fun selectionCenter(doc: Document): Vec2? {
        val layers = selectedIds.mapNotNull(doc::layer)
        if (layers.isEmpty()) return null
        val inverse = doc.matrixOf(scopeId).inverted() ?: return null
        return Rect.bounding(layers.flatMap { hitTest.corners(doc, it) }.map(inverse::map)).center
    }

    /** Drops selected layers that no longer exist, e.g. after undo. */
    fun documentChanged(doc: Document) {
        // The edited group may be gone after an undo or ungroup: step out to where it was.
        val scope = scopeId
        if (scope != null && doc.layer(scope) !is GroupLayer) {
            scopeId = null
            onScopeChanged(null)
        }
        setSelection(selectedIds.filterTo(LinkedHashSet()) { doc.layer(it) != null })
        view.invalidate()
    }

    @SuppressLint("ClickableViewAccessibility") // The layers panel offers the accessible route.
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val doc = document() ?: return false
        if (navigationMode) {
            navigation.onTouch(event)
            return true
        }
        areaTool?.let {
            it.onTouch(event)
            return true
        }
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
        // Gestures are computed in the edited group's own coordinates, so the same math
        // moves a top-level layer and a child of a rotated, scaled group.
        scopeInverse = doc.matrixOf(scopeId).inverted() ?: Matrix.IDENTITY
        val canvasPoint = toCanvas(event.x, event.y)
        val point = scopeInverse.map(canvasPoint)
        // Locked or hidden layers stay selectable in the panel but can't be moved by touch.
        val movable = selectedIds.mapNotNull(doc::layer)
            .filter { !it.locked && it.visible && doc.ancestors(it.id)?.lastOrNull()?.id == scopeId }
        val handle = overlay.handleAt(doc, movable, event.x, event.y)
        val hit = hitTest.topmost(doc, canvasPoint, scopeId)
        tappedId = hit?.id

        // A finger on the selection keeps it as the target even where another layer covers it.
        val onSelection = handle != null || movable.any { hitTest.contains(doc, it, canvasPoint, slop = grabSlop()) } ||
            (movable.size > 1 && groupBounds(doc, movable).contains(point))
        val targets = when {
            onSelection -> movable
            // In multiselect mode a drag never replaces the selection; only taps change it.
            multiSelectMode -> emptyList()
            else -> listOfNotNull(hit)
        }
        targetIds = targets.map { it.id }
        mode = when {
            targets.isEmpty() -> Mode.NONE
            handle == SelectionOverlay.Handle.CORNER -> Mode.CORNER
            handle == SelectionOverlay.Handle.ROTATE -> Mode.ROTATE
            handle == SelectionOverlay.Handle.WIDTH -> Mode.WIDTH
            else -> Mode.MOVE
        }
        baseTransforms = targets.associate { it.id to it.transform }
        currentTransforms = baseTransforms
        currentWidth = (targets.singleOrNull() as? TextLayer)?.boxWidth ?: 0.0
        if (targets.isNotEmpty()) {
            groupPivot = targets.singleOrNull()?.transform?.position ?: groupBounds(doc, targets).center
        }
        basePoints.clear()
        basePoints[event.getPointerId(0)] = point
    }

    /** A finger was added or lifted: keep the result so far and restart from current positions. */
    private fun rebase(event: MotionEvent) {
        baseTransforms = currentTransforms
        basePoints.clear()
        val lifted = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        for (i in 0 until event.pointerCount) {
            if (i != lifted) basePoints[event.getPointerId(i)] = toScope(event.getX(i), event.getY(i))
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) moved = true
    }

    private fun move(event: MotionEvent) {
        val doc = startDoc ?: return
        if (mode == Mode.NONE) return
        if (!moved && hypot(event.x - downX, event.y - downY) < touchSlop) return
        if (!moved) {
            moved = true
            // Dragging an unselected layer selects it; dragging the selection keeps it.
            if (targetIds.any { it !in selectedIds }) setSelection(targetIds.toCollection(LinkedHashSet()))
        }
        val fingers = (0 until event.pointerCount)
            .mapNotNull { i -> basePoints[event.getPointerId(i)]?.let { it to toScope(event.getX(i), event.getY(i)) } }
        if (fingers.isEmpty()) return
        val (from, to) = fingers[0]
        val single = targetIds.size == 1
        when (mode) {
            Mode.MOVE -> currentTransforms = baseTransforms.mapValues { (_, base) ->
                // The same finger motion applied to every layer moves them as one rigid group.
                if (fingers.size >= 2) GestureMath.pinch(base, from, fingers[1].first, to, fingers[1].second)
                else GestureMath.drag(base, from, to)
            }
            Mode.CORNER -> {
                val factor = GestureMath.groupFactor(baseTransforms.values, groupPivot, from, to)
                currentTransforms = baseTransforms.mapValues { GestureMath.scaleAround(it.value, groupPivot, factor) }
            }
            Mode.ROTATE -> currentTransforms = if (single) {
                baseTransforms.mapValues { GestureMath.rotateFromHandle(it.value, from, to) }
            } else {
                val turn = GestureMath.turnDegrees(groupPivot, from, to)
                baseTransforms.mapValues { GestureMath.rotateAround(it.value, groupPivot, turn) }
            }
            Mode.WIDTH -> {
                val target = doc.layer(targetIds[0]) ?: return
                currentWidth = GestureMath.textBoxWidth(baseTransforms.getValue(target.id), to, minTextWidth(target))
            }
            Mode.NONE -> return
        }
        view.document = preview(doc)
    }

    private fun finish(event: MotionEvent) {
        val doc = startDoc
        if (doc != null && moved && mode != Mode.NONE) {
            commandFor(doc)?.let(commit)
        } else if (!moved) {
            view.performClick()
            tap(event, tappedId?.let { doc?.layer(it) })
        }
        reset()
    }

    private fun tap(event: MotionEvent, target: Layer?) {
        if (multiSelectMode) {
            if (target != null) toggle(target.id) else setSelection(emptySet())
            return
        }
        val now = event.eventTime
        val isDoubleTap = target != null && target.id == lastTapId && now - lastTapTime <= doubleTapTimeout
        if (isDoubleTap && target is TextLayer) {
            onEditText(target)
            lastTapId = null
        } else if (isDoubleTap && target is GroupLayer) {
            enterScope(target.id)
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
        targetIds = emptyList()
        tappedId = null
        basePoints.clear()
    }

    private fun preview(doc: Document): Document = targetIds.fold(doc) { d, id ->
        val layer = d.layer(id) ?: return@fold d
        val changed = if (mode == Mode.WIDTH && layer is TextLayer) layer.copy(boxWidth = currentWidth)
        else layer.withTransform(currentTransforms.getValue(id))
        d.withLayerReplaced(changed)
    }

    private fun commandFor(doc: Document): Command? {
        if (targetIds.any { doc.layer(it) == null }) return null
        if (mode == Mode.WIDTH) {
            val text = doc.layer(targetIds[0]) as? TextLayer ?: return null
            return UpdateLayer.of(doc, text.copy(boxWidth = currentWidth), "Resize text box")
        }
        val verb = when (mode) {
            Mode.CORNER -> "Resize"
            Mode.ROTATE -> "Rotate"
            else -> "Move"
        }
        return TransformLayers.of(doc, currentTransforms, verb)
    }

    /** In the edited group's coordinates, like every gesture point. */
    private fun groupBounds(doc: Document, layers: List<Layer>): Rect =
        Rect.bounding(layers.flatMap { hitTest.corners(doc, it) }.map(scopeInverse::map))

    private fun toScope(x: Float, y: Float): Vec2 = scopeInverse.map(toCanvas(x, y))

    private fun minTextWidth(layer: Layer) = ((layer as? TextLayer)?.style?.fontSize ?: MIN_TEXT_WIDTH)
        .coerceAtLeast(MIN_TEXT_WIDTH)

    private fun toCanvas(x: Float, y: Float): Vec2 =
        view.viewMatrix.inverted()?.map(Vec2(x.toDouble(), y.toDouble())) ?: Vec2(x.toDouble(), y.toDouble())

    /** A finger-sized margin in canvas pixels, so small layers stay easy to grab. */
    private fun grabSlop(): Double {
        val viewScale = sqrt(abs(view.viewMatrix.determinant)).coerceAtLeast(1e-6)
        return GRAB_SLOP_DP * density / viewScale
    }

    private companion object {
        const val GRAB_SLOP_DP = 12
        const val MIN_TEXT_WIDTH = 16.0
    }
}
