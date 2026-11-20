package io.github.drhydr0.layers.interaction

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.drhydr0.layers.document.Arrange
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Matrix
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.ShapeLayer
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
    private val onMultiSelectChanged: (Boolean) -> Unit = {},
) : View.OnTouchListener {

    private enum class Mode { NONE, MOVE, CORNER, RESIZE, ROTATE, WIDTH }

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

    /** While on, a dragged layer snaps to the canvas and to other layers. */
    var snappingEnabled = true

    /** While on, taps add layers to or remove them from the selection. */
    var multiSelectMode = false
        set(value) {
            if (field == value) return
            field = value
            onMultiSelectChanged(value)
        }

    /**
     * Long press: turns multiselect on and adds the layer, or, when it is already on,
     * turns it off and keeps only that layer selected.
     */
    fun longPress(id: LayerId) {
        if (multiSelectMode) {
            multiSelectMode = false
            select(id)
        } else {
            multiSelectMode = true
            setSelection(selectedIds + id)
        }
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    /** [longPress] for a layer picked anywhere in the tree, entering its group if needed. */
    fun longPressInTree(doc: Document, id: LayerId) {
        val parent = doc.ancestors(id)?.lastOrNull()?.id
        if (parent != scopeId) enterScope(parent)
        longPress(id)
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
    private var resizeX = 0
    private var resizeY = 0
    private var longPressed = false
    private var navigating = false
    private var startedOnSelection = false
    private val longPressRunnable = Runnable {
        val id = tappedId
        if (!moved && id != null && startDoc != null) {
            longPressed = true
            longPress(id)
        }
    }

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
        if (navigating) {
            navigation.onTouch(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) navigating = false
            return true
        }
        // Two fingers move the view unless they began on the selection: then they move it instead.
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && (areaTool != null || !startedOnSelection && !moved)) {
            startNavigating(event)
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

    /** Hands the rest of this touch to pan and zoom, dropping any half-started edit. */
    private fun startNavigating(event: MotionEvent) {
        view.removeCallbacks(longPressRunnable)
        startDoc?.let { view.document = it }
        reset()
        navigating = true
        navigation.onTouch(event)
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
        startedOnSelection = onSelection
        targetIds = targets.map { it.id }
        mode = when {
            targets.isEmpty() -> Mode.NONE
            handle?.kind == SelectionOverlay.Handle.CORNER -> Mode.CORNER
            handle?.kind == SelectionOverlay.Handle.RESIZE -> Mode.RESIZE
            handle?.kind == SelectionOverlay.Handle.ROTATE -> Mode.ROTATE
            handle?.kind == SelectionOverlay.Handle.WIDTH -> Mode.WIDTH
            else -> Mode.MOVE
        }
        resizeX = handle?.sx ?: 0
        resizeY = handle?.sy ?: 0
        longPressed = false
        // Long press only on layers, never on handles, so resizing can start slowly.
        if (handle == null && hit != null) view.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
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
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            moved = true
            view.removeCallbacks(longPressRunnable)
        }
    }

    private fun move(event: MotionEvent) {
        val doc = startDoc ?: return
        if (mode == Mode.NONE) return
        if (longPressed) return
        if (!moved && hypot(event.x - downX, event.y - downY) < touchSlop) return
        if (!moved) {
            moved = true
            view.removeCallbacks(longPressRunnable)
            // Dragging an unselected layer selects it; dragging the selection keeps it.
            if (targetIds.any { it !in selectedIds }) setSelection(targetIds.toCollection(LinkedHashSet()))
        }
        val fingers = (0 until event.pointerCount)
            .mapNotNull { i -> basePoints[event.getPointerId(i)]?.let { it to toScope(event.getX(i), event.getY(i)) } }
        if (fingers.isEmpty()) return
        val (from, to) = fingers[0]
        val single = targetIds.size == 1
        when (mode) {
            Mode.MOVE -> {
                currentTransforms = baseTransforms.mapValues { (_, base) ->
                    // The same finger motion applied to every layer moves them as one rigid group.
                    if (fingers.size >= 2) GestureMath.pinch(base, from, fingers[1].first, to, fingers[1].second)
                    else GestureMath.drag(base, from, to)
                }
                if (fingers.size == 1) snapMove(doc) else overlay.guides = null
            }
            Mode.CORNER -> {
                val factor = GestureMath.groupFactor(baseTransforms.values, groupPivot, from, to)
                currentTransforms = baseTransforms.mapValues { GestureMath.scaleAround(it.value, groupPivot, factor) }
            }
            Mode.RESIZE -> {
                val target = doc.layer(targetIds[0]) ?: return
                val base = baseTransforms.getValue(target.id)
                currentTransforms = mapOf(
                    target.id to GestureMath.resizeFromHandle(base, resizeBox(doc, target), resizeX, resizeY, to, MIN_RESIZE),
                )
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
        view.removeCallbacks(longPressRunnable)
        if (longPressed) {
            reset()
            return
        }
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
        view.removeCallbacks(longPressRunnable)
        // Restore the committed document; nothing from the interrupted gesture is kept.
        startDoc?.let { view.document = it }
        reset()
    }

    /**
     * Pulls a one-finger drag onto nearby guides: the canvas edges and center, and other
     * layers' edges and centers. Top level only, where layer and canvas coordinates agree.
     */
    private fun snapMove(doc: Document) {
        overlay.guides = null
        if (!snappingEnabled || scopeId != null) return
        val moving = preview(doc)
        val box = Rect.bounding(targetIds.mapNotNull(moving::layer).flatMap { hitTest.corners(moving, it) })
        val others = doc.layers.filter { it.visible && it.id !in targetIds }
            .map { Rect.bounding(hitTest.corners(doc, it)) }
        val snap = Arrange.snap(box, others + doc.canvas.bounds, grabSlop() * SNAP_FRACTION)
        if (snap.offset == Vec2.ZERO) return
        currentTransforms = currentTransforms.mapValues { it.value.translatedBy(snap.offset) }
        overlay.guides = snap
    }

    /** Upright bounding boxes of the selected layers, in canvas coordinates. */
    fun selectionBoxes(doc: Document): Map<LayerId, Rect> =
        selectedIds.mapNotNull(doc::layer).associate { it.id to Rect.bounding(hitTest.corners(doc, it)) }

    /** Converts a move given in canvas pixels into the coordinates of the group being edited. */
    fun canvasOffsetToScope(doc: Document, offset: Vec2): Vec2 {
        val inverse = doc.matrixOf(scopeId).inverted() ?: return offset
        return inverse.map(offset) - inverse.map(Vec2.ZERO)
    }

    private fun reset() {
        overlay.guides = null
        mode = Mode.NONE
        startDoc = null
        targetIds = emptyList()
        tappedId = null
        basePoints.clear()
    }

    private fun preview(doc: Document): Document = targetIds.fold(doc) { d, id ->
        val layer = d.layer(id) ?: return@fold d
        d.withLayerReplaced(previewLayer(layer))
    }

    private fun previewLayer(layer: Layer): Layer = when {
        mode == Mode.WIDTH && layer is TextLayer -> layer.copy(boxWidth = currentWidth)
        mode == Mode.RESIZE && layer is ShapeLayer -> {
            // Shapes change their own size instead of scaling, so outlines keep their thickness.
            val base = baseTransforms.getValue(layer.id)
            val next = currentTransforms.getValue(layer.id)
            layer.copy(
                width = layer.width * next.scaleX / base.scaleX,
                height = if (layer.isLine) layer.height else layer.height * next.scaleY / base.scaleY,
                transform = next.copy(scaleX = base.scaleX, scaleY = base.scaleY),
            )
        }
        else -> layer.withTransform(currentTransforms.getValue(layer.id))
    }

    /** The box a free resize stretches: a shape's own outline box, otherwise the content box. */
    private fun resizeBox(doc: Document, layer: Layer): Rect =
        if (layer is ShapeLayer) Rect.centered(layer.width, layer.height) else hitTest.localBounds(doc, layer)

    private fun commandFor(doc: Document): Command? {
        if (targetIds.any { doc.layer(it) == null }) return null
        if (mode == Mode.WIDTH) {
            val text = doc.layer(targetIds[0]) as? TextLayer ?: return null
            return UpdateLayer.of(doc, text.copy(boxWidth = currentWidth), "Resize text box")
        }
        if (mode == Mode.RESIZE) {
            val layer = doc.layer(targetIds[0]) ?: return null
            return if (layer is ShapeLayer) UpdateLayer.of(doc, previewLayer(layer), "Resize shape")
            else TransformLayers.of(doc, currentTransforms, "Resize")
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
        // Snap within 8 dp on screen: close enough to mean it, far enough to drag past.
        const val SNAP_FRACTION = 8.0 / GRAB_SLOP_DP
        const val MIN_TEXT_WIDTH = 16.0
        const val MIN_RESIZE = 4.0
    }
}
