package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import io.github.drhydr0.layers.LayersApp
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.diagnostics.EditorStats
import io.github.drhydr0.layers.document.Alignment
import io.github.drhydr0.layers.document.Arrange
import io.github.drhydr0.layers.document.CapturePlan
import io.github.drhydr0.layers.document.CapturePlanner
import io.github.drhydr0.layers.document.CaptureScope
import io.github.drhydr0.layers.document.Distribution
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Placement
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.ShapeKind
import io.github.drhydr0.layers.document.ShapeLayer
import io.github.drhydr0.layers.document.Sticker
import io.github.drhydr0.layers.document.Stickers
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.TextStyle
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.history.AddLayers
import io.github.drhydr0.layers.history.History
import io.github.drhydr0.layers.history.MoveLayer
import io.github.drhydr0.layers.history.PlacedLayer
import io.github.drhydr0.layers.history.RemoveLayers
import io.github.drhydr0.layers.history.ReplaceLayers
import io.github.drhydr0.layers.history.TransformLayers
import io.github.drhydr0.layers.history.UpdateLayer
import io.github.drhydr0.layers.interaction.AreaSelectionTool
import io.github.drhydr0.layers.interaction.CanvasTouchController
import io.github.drhydr0.layers.persistence.SaveQueue
import io.github.drhydr0.layers.persistence.format.ProjectManifest
import io.github.drhydr0.layers.platform.FreeMemory
import io.github.drhydr0.layers.platform.GalleryWriter
import io.github.drhydr0.layers.platform.ImageImporter
import io.github.drhydr0.layers.platform.ImagePicker
import io.github.drhydr0.layers.render.Capturer
import io.github.drhydr0.layers.render.DocumentView
import io.github.drhydr0.layers.render.ExportOptions
import io.github.drhydr0.layers.render.Exporter
import io.github.drhydr0.layers.render.PreviewImages
import io.github.drhydr0.layers.render.hitTestFor
import io.github.drhydr0.layers.render.StickerThumbnails
import io.github.drhydr0.layers.render.ThumbnailRenderer
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException

/** The editor for one project: canvas, tools, and saving after every change. */
class EditorActivity : Activity() {

    private val app get() = application as LayersApp
    private lateinit var projectId: String
    private lateinit var canvasView: DocumentView
    private var previewImages: PreviewImages? = null

    /** Null until the project has loaded; tools do nothing before then. */
    private var session: EditorSession? = null
    private lateinit var touch: CanvasTouchController
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var saveStatus: TextView
    private lateinit var selectionBar: View
    private lateinit var multiButton: Button
    private lateinit var nudgeBar: View
    private lateinit var mainBar: View
    private lateinit var nudgeStepButton: Button
    private var nudgeStep = 1.0
    private var nudgeOffset = Vec2.ZERO
    private lateinit var editTextButton: Button
    private lateinit var scopeBar: View
    private lateinit var areaBar: View
    private lateinit var areaStatus: TextView
    private var areaChoice: AreaSelectSheet.Choice? = null
    private var areaLayerIds: Set<LayerId> = emptySet()
    private lateinit var scopeName: TextView
    private lateinit var selectionCount: TextView
    private val saveTracker = SaveTracker()
    private lateinit var layersPanel: LayersPanel
    private lateinit var layersContainer: View
    private lateinit var layersEmpty: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<ImageButton>(R.id.editor_back).setOnClickListener { finish() }
        canvasView = findViewById(R.id.editor_canvas)
        findViewById<Button>(R.id.editor_add).setOnClickListener(::showAddMenu)
        undoButton = findViewById<ImageButton>(R.id.editor_undo).apply { setOnClickListener { session?.history?.undo() } }
        redoButton = findViewById<ImageButton>(R.id.editor_redo).apply { setOnClickListener { session?.history?.redo() } }
        saveStatus = findViewById<TextView>(R.id.editor_save_status).apply {
            setOnClickListener { if (saveTracker.state == SaveTracker.State.FAILED) session?.retrySave() }
        }
        setUpTouch()
        setUpLayersPanel()
        setUpNavigation()
        setUpSelectionBar()
        findViewById<ImageButton>(R.id.editor_export).setOnClickListener {
            val doc = session?.document ?: return@setOnClickListener
            ExportSheet.show(this, doc.canvas, ::runExport)
        }

        projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: run {
            finish()
            return
        }
        previewImages = PreviewImages(
            fileFor = { app.projects.assets(projectId).file(it) },
            decodeExecutor = app.decode,
            onLoaded = { if (!isDestroyed) canvasView.invalidate() },
        ).also(canvasView::setImageSource)
        load()
    }

    override fun onDestroy() {
        previewImages?.clear()
        super.onDestroy()
    }

    @Deprecated("Platform Activity result API; this app uses no AndroidX Activity library.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_IMAGE) ImagePicker.result(resultCode, data)?.let(::addImage)
    }

    private fun load() {
        app.io.execute {
            val result = runCatching {
                val loaded = app.projects.load(projectId)
                // Safe here: nothing can import into this project until the editor has loaded.
                runCatching { app.projects.pruneAssets(loaded) }
                    .onFailure { AppLog.w(TAG, "Image cleanup failed", it) }
                    .getOrDefault(loaded)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.onSuccess { manifest ->
                    AppLog.i(TAG, "Opened project $projectId with ${manifest.document.layers.size} layers")
                    findViewById<TextView>(R.id.editor_title).text = manifest.info.name
                    val history = History(manifest.document, manifest.history)
                    session = EditorSession(manifest.info, history, newSaveQueue(), System::currentTimeMillis)
                    // Registered after the session's own listener, so the save is already queued.
                    history.addListener { h ->
                        saveTracker.onEdit(h.version)
                        showSaveStatus()
                    }
                    showSaveStatus()
                    val thumbnails = newThumbnailQueue()
                    if (!app.projects.thumbnailFile(projectId).exists()) thumbnails.submit(0, manifest)
                    history.addListener {
                        session?.let { s -> thumbnails.submit(it.version, ProjectManifest(s.info, it.document)) }
                        EditorStats.latest = EditorStats.Snapshot(it.document.layers.size, it.size)
                    }
                    EditorStats.latest = EditorStats.Snapshot(history.document.layers.size, history.size)
                    history.addListener {
                        canvasView.document = it.document
                        touch.documentChanged(it.document)
                        updateUndoRedo(it)
                        refreshLayers()
                        refreshScopeBar()
                    }
                    canvasView.document = history.document
                    updateUndoRedo(history)
                    refreshLayers()
                }.onFailure {
                    AppLog.e(TAG, "Could not open project $projectId", it)
                    Toast.makeText(this, R.string.editor_open_failed, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    // The controller calls performClick on taps; drags have no click equivalent.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setUpTouch() {
        val hitTest = hitTestFor(canvasView.textLayouts)
        touch = CanvasTouchController(
            view = canvasView,
            hitTest = hitTest,
            document = { session?.document },
            commit = { session?.commit(it) },
            onSelectionChanged = {
                refreshLayers()
                refreshSelectionBar()
            },
            onEditText = ::editText,
            onScopeChanged = {
                refreshScopeBar()
                refreshLayers()
            },
            onMultiSelectChanged = { refreshMultiButton() },
        )
        canvasView.setOnTouchListener(touch)
        // Draws against what's on screen, so handles follow a gesture preview.
        canvasView.overlay = { c ->
            canvasView.document?.let { touch.overlay.draw(c, it) }
            touch.areaTool?.draw(c)
        }
    }

    /** Exports a snapshot taken now, so edits made while it runs don't change the output. */
    private fun runExport(options: ExportOptions, action: ExportSheet.Action) {
        val current = session ?: return
        val snapshot = current.document
        val name = GalleryWriter.displayName(current.info.name, options.format.extension, System.currentTimeMillis())
        val exporter = Exporter { app.projects.assets(projectId).file(it) }
        val progress = AlertDialog.Builder(this)
            .setTitle(R.string.export_progress)
            .setView(ProgressBar(this).apply { isIndeterminate = true; setPadding(0, PROGRESS_PADDING, 0, PROGRESS_PADDING) })
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> exporter.cancelled = true }
            .show()
        AppLog.i(TAG, "Export ${options.format} x${options.scale} of ${snapshot.layers.size} layers")
        app.decode.execute {
            val result = runCatching {
                GalleryWriter.save(contentResolver, name, options.format.mimeType) { exporter.export(snapshot, options, it) }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (progress.isShowing) progress.dismiss()
                result.onSuccess { uri ->
                    AppLog.i(TAG, "Export finished")
                    when (action) {
                        ExportSheet.Action.SAVE -> Toast.makeText(this, R.string.export_saved, Toast.LENGTH_SHORT).show()
                        ExportSheet.Action.SHARE -> shareImage(uri, options.format.mimeType)
                    }
                }.onFailure {
                    val cancelled = it is CancellationException
                    if (cancelled) AppLog.i(TAG, "Export cancelled") else AppLog.e(TAG, "Export failed", it)
                    Toast.makeText(
                        this, if (cancelled) R.string.export_cancelled else R.string.export_failed, Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun shareImage(uri: android.net.Uri, mimeType: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_image_chooser)))
    }

    private fun setUpNavigation() {
        val toggle = findViewById<ImageButton>(R.id.editor_navigate)
        toggle.setOnClickListener {
            touch.navigationMode = !touch.navigationMode
            toggle.isActivated = touch.navigationMode
            toggle.contentDescription = getString(
                if (touch.navigationMode) R.string.editor_navigate_on else R.string.editor_navigate_off,
            )
        }
        findViewById<ImageButton>(R.id.editor_more).setOnClickListener(::showMoreMenu)
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_FIT, 0, R.string.editor_menu_fit)
            menu.add(0, MENU_HISTORY, 1, R.string.editor_menu_history)
            menu.add(0, MENU_CHECKPOINT, 4, R.string.editor_menu_checkpoint)
            menu.add(0, MENU_SNAPPING, 3, R.string.editor_menu_snapping).apply {
                isCheckable = true
                isChecked = touch.snappingEnabled
            }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    MENU_FIT -> canvasView.fitToScreen()
                    MENU_HISTORY -> session?.history?.let { h -> HistorySheet.show(this@EditorActivity, h, h::jumpTo) }
                    MENU_SNAPPING -> touch.snappingEnabled = !touch.snappingEnabled
                    MENU_CHECKPOINT -> askCheckpoint()
                }
                true
            }
        }.show()
    }

    private fun setUpSelectionBar() {
        selectionBar = findViewById(R.id.editor_selection_bar)
        areaBar = findViewById(R.id.editor_area_bar)
        areaStatus = findViewById(R.id.editor_area_status)
        findViewById<Button>(R.id.editor_area_copy).setOnClickListener { copyAreaAsSticker() }
        findViewById<Button>(R.id.editor_area_cancel).setOnClickListener { endArea() }
        scopeBar = findViewById(R.id.editor_scope_bar)
        scopeName = findViewById(R.id.editor_scope_name)
        selectionCount = findViewById(R.id.editor_selection_count)
        multiButton = findViewById(R.id.editor_multiselect)
        multiButton.setOnClickListener { touch.multiSelectMode = !touch.multiSelectMode }
        findViewById<Button>(R.id.editor_select_area).setOnClickListener {
            if (session != null) AreaSelectSheet.show(this, touch.selectedIds.size, ::startArea)
        }
        nudgeBar = findViewById(R.id.editor_nudge_bar)
        mainBar = findViewById(R.id.editor_main_bar)
        nudgeStepButton = findViewById(R.id.editor_nudge_step)
        nudgeStepButton.setOnClickListener {
            nudgeStep = if (nudgeStep == 1.0) NUDGE_BIG else 1.0
            refreshNudgeStep()
        }
        listOf(
            R.id.editor_nudge_left to Vec2(-1.0, 0.0), R.id.editor_nudge_right to Vec2(1.0, 0.0),
            R.id.editor_nudge_up to Vec2(0.0, -1.0), R.id.editor_nudge_down to Vec2(0.0, 1.0),
        ).forEach { (id, direction) -> findViewById<View>(id).setOnClickListener { nudge(direction) } }
        findViewById<Button>(R.id.editor_nudge_done).setOnClickListener { endNudge(commit = true) }
        findViewById<Button>(R.id.editor_nudge_cancel).setOnClickListener { endNudge(commit = false) }
        findViewById<Button>(R.id.editor_selection_actions).setOnClickListener(::showSelectionActions)
        findViewById<Button>(R.id.editor_scope_exit).setOnClickListener {
            // One level out at a time, so nested groups step back the way they were entered.
            val doc = session?.document ?: return@setOnClickListener
            val scope = touch.scopeId ?: return@setOnClickListener
            touch.enterScope(doc.ancestors(scope)?.lastOrNull()?.id)
            touch.select(scope)
        }
        editTextButton = findViewById(R.id.editor_edit_text)
        editTextButton.setOnClickListener {
            when (val layer = selectedSingle()) {
                is TextLayer -> editText(layer)
                is ShapeLayer -> editShape(layer)
                is ImageLayer -> editImage(layer)
                is GroupLayer -> editGroup(layer)
                null -> Unit
            }
        }
        findViewById<Button>(R.id.editor_delete_selection).setOnClickListener {
            val current = session ?: return@setOnClickListener
            // Locked layers are protected from deletion along with the rest of the selection.
            val ids = touch.selectedIds.filter { current.document.layer(it)?.locked == false }
            if (ids.isNotEmpty()) current.commit(RemoveLayers.of(current.document, ids))
        }
    }

    /**
     * A named checkpoint: the project as it is now, saved as a separate copy with its undo
     * history, so an experiment can always be abandoned by opening the copy.
     */
    private fun askCheckpoint() {
        val current = session ?: return
        val input = EditText(this).apply {
            setText(getString(R.string.checkpoint_default_name, current.info.name))
            setSelectAllOnFocus(true)
            hint = getString(R.string.checkpoint_name_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.checkpoint_title)
            .setMessage(R.string.checkpoint_message)
            .setView(input)
            .setPositiveButton(R.string.checkpoint_save) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { getString(R.string.checkpoint_default_name, current.info.name) }
                // The state on screen now, not whatever save happens to be on disk.
                val snapshot = ProjectManifest(current.info, current.document, current.history.snapshot())
                app.io.execute {
                    val result = runCatching { app.projects.saveCopy(snapshot, name, System.currentTimeMillis()) }
                    result.onFailure { AppLog.e(TAG, "Checkpoint failed", it) }
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        Toast.makeText(
                            this, if (result.isSuccess) R.string.checkpoint_saved else R.string.checkpoint_failed, Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Starts drawing an area. The layers to copy from are fixed now, so the scope can't drift. */
    private fun startArea(choice: AreaSelectSheet.Choice) {
        areaChoice = choice
        areaLayerIds = touch.selectedIds
        touch.areaTool = AreaSelectionTool(canvasView, choice.lasso) { refreshAreaBar() }
        refreshAreaBar()
        AppLog.i(TAG, "Area selection started: ${if (choice.lasso) "lasso" else "rectangle"}, ${choice.scope}")
    }

    private fun endArea() {
        touch.areaTool = null
        areaChoice = null
        refreshAreaBar()
    }

    private fun refreshAreaBar() {
        val choice = areaChoice
        areaBar.visibility = if (choice == null) View.GONE else View.VISIBLE
        if (choice == null) return
        val shapeName = getString(if (choice.lasso) R.string.area_shape_lasso else R.string.area_shape_rect)
        val scopeName = getString(
            when (choice.scope) {
                CaptureScope.CANVAS -> R.string.area_status_canvas
                CaptureScope.ACTIVE_LAYER -> R.string.area_status_active
                CaptureScope.SELECTED_LAYERS -> R.string.area_status_selected
            },
        )
        areaStatus.text = if (touch.areaTool?.shape == null) {
            getString(if (choice.lasso) R.string.area_hint_lasso else R.string.area_hint_rect)
        } else {
            getString(R.string.area_status, shapeName, scopeName)
        }
    }

    /** Renders the area from a snapshot taken now, then shows what the sticker will be. */
    private fun copyAreaAsSticker() {
        val choice = areaChoice ?: return
        val shape = touch.areaTool?.shape
        val doc = session?.document ?: return
        if (shape == null) {
            Toast.makeText(this, R.string.area_empty, Toast.LENGTH_SHORT).show()
            return
        }
        // Same budget as export: the output bitmap may use half the app's memory at most.
        val maxPixels = FreeMemory.bytes(this) / 2 / BYTES_PER_PIXEL
        val plan = CapturePlanner.plan(doc.canvas, shape, choice.scope, maxPixels)
        if (plan == null) {
            Toast.makeText(this, R.string.capture_nothing, Toast.LENGTH_LONG).show()
            return
        }
        val source = CapturePlanner.source(doc, choice.scope, areaLayerIds, choice.includeBackground)
        val capturer = Capturer { app.projects.assets(projectId).file(it) }
        app.decode.execute {
            val result = runCatching {
                val bitmap = capturer.render(source, shape, plan, clipToCanvas = choice.scope == CaptureScope.CANVAS)
                if (capturer.isBlank(bitmap)) {
                    bitmap.recycle()
                    null
                } else {
                    bitmap
                }
            }
            runOnUiThread {
                val bitmap = result.getOrNull()
                when {
                    isDestroyed -> bitmap?.recycle()
                    result.isFailure -> {
                        AppLog.e(TAG, "Area capture failed", result.exceptionOrNull())
                        Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_LONG).show()
                    }
                    // An empty capture makes no layer and no history entry.
                    bitmap == null -> Toast.makeText(this, R.string.capture_nothing, Toast.LENGTH_LONG).show()
                    else -> confirmCapture(bitmap, plan)
                }
            }
        }
    }

    private fun confirmCapture(bitmap: Bitmap, plan: CapturePlan) {
        val view = layoutInflater.inflate(R.layout.dialog_capture_preview, null)
        val reduced = plan.scale < 1.0
        view.findViewById<TextView>(R.id.capture_summary).text = getString(
            if (reduced) R.string.capture_summary_reduced else R.string.capture_summary, plan.width, plan.height,
        )
        // A screen-sized copy for the dialog: very large bitmaps can't be drawn by a view.
        val longest = maxOf(bitmap.width, bitmap.height)
        val preview = if (longest <= PREVIEW_MAX_SIDE) bitmap else Bitmap.createScaledBitmap(
            bitmap, bitmap.width * PREVIEW_MAX_SIDE / longest, bitmap.height * PREVIEW_MAX_SIDE / longest, true,
        )
        view.findViewById<ImageView>(R.id.capture_preview).setImageBitmap(preview)
        var keep = false
        AlertDialog.Builder(this)
            .setTitle(R.string.capture_title)
            .setView(view)
            .setPositiveButton(R.string.capture_create) { _, _ ->
                keep = true
                storeCapture(bitmap, plan)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { if (!keep) bitmap.recycle() }
            .show()
    }

    /** Stores the picture as a new image and adds it exactly over what it copied. */
    private fun storeCapture(bitmap: Bitmap, plan: CapturePlan) {
        val assets = app.projects.assets(projectId)
        app.io.execute {
            val result = runCatching {
                val temp = File.createTempFile("capture", ".png", cacheDir)
                try {
                    temp.outputStream().use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) throw IOException("Encoding failed")
                    }
                    ImageAsset(assets.importFile(temp), "image/png", bitmap.width, bitmap.height)
                } finally {
                    temp.delete()
                    bitmap.recycle()
                }
            }
            runOnUiThread {
                val current = session ?: return@runOnUiThread
                result.onSuccess { asset ->
                    val layer = ImageLayer(
                        LayerId.random(), getString(R.string.default_image_sticker_name), asset.id, CapturePlanner.placement(plan),
                    )
                    val doc = current.document
                    current.commit(
                        AddLayers(listOf(PlacedLayer(doc.layers.size, layer)), listOf(asset), AddLayers.CREATE_STICKER),
                    )
                    endArea()
                    touch.enterScope(null)
                    touch.select(layer.id)
                    AppLog.i(TAG, "Created image sticker ${asset.width}x${asset.height}")
                }.onFailure {
                    AppLog.e(TAG, "Storing the capture failed", it)
                    if (!isDestroyed) Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSelectionActions(anchor: View) {
        val current = session ?: return
        val doc = current.document
        val selected = touch.selectedIds.mapNotNull(doc::layer)
        if (selected.isEmpty()) return
        val singleGroup = selected.singleOrNull() as? GroupLayer
        PopupMenu(this, anchor).apply {
            menu.add(0, ACTION_DUPLICATE, 0, R.string.action_duplicate)
            menu.add(0, ACTION_GROUP, 1, R.string.action_group)
            val align = menu.addSubMenu(0, 0, 2, R.string.action_align)
            alignNames.forEach { (alignment, name) -> align.add(GROUP_ALIGN, alignment.ordinal, alignment.ordinal, name) }
            if (selected.size >= 3) {
                val spread = menu.addSubMenu(0, 0, 3, R.string.action_distribute)
                spread.add(GROUP_DISTRIBUTE, Distribution.HORIZONTAL.ordinal, 0, R.string.distribute_horizontal)
                spread.add(GROUP_DISTRIBUTE, Distribution.VERTICAL.ordinal, 1, R.string.distribute_vertical)
            }
            if (selected.size == 1) menu.add(0, ACTION_PRECISION, 3, R.string.action_precision)
            menu.add(0, ACTION_NUDGE, 3, R.string.action_nudge)
            if (touch.scopeId != null) menu.add(0, ACTION_REMOVE_FROM_GROUP, 2, R.string.action_remove_from_group)
            menu.add(0, ACTION_MAKE_STICKER, 4, R.string.action_make_sticker)
            menu.add(0, ACTION_SAVE_STICKER, 5, R.string.action_save_sticker)
            if (singleGroup != null) {
                menu.add(0, ACTION_UNGROUP, 2, R.string.action_ungroup)
                menu.add(0, ACTION_EDIT_GROUP, 3, R.string.action_edit_group)
            }
            setOnMenuItemClickListener {
                if (it.groupId == GROUP_ALIGN) {
                    val alignment = Alignment.entries[it.itemId]
                    arrange("Align") { boxes, canvas ->
                        // One layer lines up with the canvas; several line up with each other.
                        val frame = if (boxes.size == 1) canvas else boxes.values.reduce(Rect::union)
                        Arrange.align(boxes, frame, alignment)
                    }
                    return@setOnMenuItemClickListener true
                }
                if (it.groupId == GROUP_DISTRIBUTE) {
                    val direction = Distribution.entries[it.itemId]
                    arrange("Space") { boxes, _ -> Arrange.distribute(boxes, direction) }
                    return@setOnMenuItemClickListener true
                }
                when (it.itemId) {
                    ACTION_DUPLICATE -> duplicateSelection()
                    ACTION_GROUP -> groupSelection()
                    ACTION_UNGROUP -> singleGroup?.let(::ungroup)
                    ACTION_EDIT_GROUP -> singleGroup?.let { g -> touch.enterScope(g.id) }
                    ACTION_NUDGE -> startNudge()
                    ACTION_REMOVE_FROM_GROUP -> removeFromGroup(touch.selectedIds)
                    ACTION_PRECISION -> selected.singleOrNull()?.let { layer ->
                        PrecisionSheet.show(this@EditorActivity, layer.transform) { typed ->
                            session?.let { s -> s.commit(TransformLayers.of(s.document, mapOf(layer.id to typed), "Place")) }
                        }
                    }
                    ACTION_MAKE_STICKER -> makeSticker()
                    ACTION_SAVE_STICKER -> askSaveSticker()
                }
                true
            }
        }.show()
    }

    private val alignNames = listOf(
        Alignment.LEFT to R.string.align_left,
        Alignment.CENTER_HORIZONTAL to R.string.align_center_horizontal,
        Alignment.RIGHT to R.string.align_right,
        Alignment.TOP to R.string.align_top,
        Alignment.CENTER_VERTICAL to R.string.align_center_vertical,
        Alignment.BOTTOM to R.string.align_bottom,
    )

    /** Moves the selected layers by the offsets [plan] works out, as one undo step. */
    private fun arrange(verb: String, plan: (Map<LayerId, Rect>, canvas: Rect) -> Map<LayerId, Vec2>) {
        val current = session ?: return
        val doc = current.document
        // Locked layers stay where they are, and stay out of the calculation.
        val boxes = touch.selectionBoxes(doc).filterKeys { doc.layer(it)?.locked == false }
        if (boxes.isEmpty()) return
        val moved = plan(boxes, doc.canvas.bounds).mapNotNull { (id, offset) ->
            val layer = doc.layer(id) ?: return@mapNotNull null
            id to layer.transform.translatedBy(touch.canvasOffsetToScope(doc, offset))
        }.toMap()
        current.commit(TransformLayers.of(doc, moved, verb))
    }

    /** Independent copies, slightly offset, stacked above the originals and selected. */
    private fun duplicateSelection() {
        val current = session ?: return
        val doc = current.document
        val offset = doc.canvas.width * DUPLICATE_OFFSET_FRACTION
        val command = AddLayers.duplicates(doc, touch.selectedIds, Vec2(offset, offset), LayerId::random)
        current.commit(command)
        touch.setSelection(command.placements.mapTo(LinkedHashSet()) { it.layer.id })
    }

    /** An editable sticker: the selection copied as one independent object; originals stay. */
    private fun makeSticker() {
        val current = session ?: return
        val doc = current.document
        val center = touch.selectionCenter(doc) ?: return
        val offset = doc.canvas.width * DUPLICATE_OFFSET_FRACTION
        val command = AddLayers.sticker(
            doc, touch.selectedIds, center, Vec2(offset, offset), getString(R.string.default_sticker_name), LayerId::random,
        )
        current.commit(command)
        touch.setSelection(command.placements.mapTo(LinkedHashSet()) { it.layer.id })
    }

    private fun askSaveSticker() {
        val current = session ?: return
        val doc = current.document
        // Keep stacking order, whatever order the layers were tapped in.
        val layers = doc.children(touch.scopeId).filter { it.id in touch.selectedIds }
        val center = touch.selectionCenter(doc) ?: return
        if (layers.isEmpty()) return
        val input = EditText(this).apply {
            setText(layers.singleOrNull()?.name ?: getString(R.string.default_sticker_name))
            setSelectAllOnFocus(true)
            hint = getString(R.string.sticker_name_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.sticker_save_title)
            .setView(input)
            .setPositiveButton(R.string.sticker_save_confirm) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { getString(R.string.default_sticker_name) }
                val content = Stickers.contentOf(layers, center, name, LayerId::random)
                saveSticker(Stickers.create(app.stickers.newStickerId(), name, System.currentTimeMillis(), content, doc.assets))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveSticker(sticker: Sticker) {
        val projectAssets = app.projects.assets(projectId)
        app.io.execute {
            val result = runCatching { app.stickers.save(sticker) { projectAssets.file(it) } }
            if (result.isSuccess) {
                app.thumbnails.execute {
                    runCatching {
                        app.stickers.writeThumbnail(sticker.id) { out ->
                            StickerThumbnails.writePng(sticker, { app.stickers.assets(sticker.id).file(it) }, out)
                        }
                    }.onFailure { AppLog.w(TAG, "Sticker thumbnail failed", it) }
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.onSuccess { AppLog.i(TAG, "Saved sticker with ${sticker.assets.size} images") }
                    .onFailure { AppLog.e(TAG, "Sticker save failed", it) }
                Toast.makeText(
                    this, if (result.isSuccess) R.string.sticker_saved else R.string.sticker_save_failed, Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /** Copies the sticker's images into this project, then adds a new independent instance. */
    private fun insertSticker(sticker: Sticker) {
        val projectAssets = app.projects.assets(projectId)
        val stickerAssets = app.stickers.assets(sticker.id)
        app.io.execute {
            val result = runCatching {
                sticker.assets.forEach { if (!projectAssets.contains(it.id)) projectAssets.importFile(stickerAssets.file(it.id)) }
            }
            runOnUiThread {
                val current = session ?: return@runOnUiThread
                result.onSuccess {
                    val doc = current.document
                    val instance = Stickers.instantiate(sticker, placementFrame(doc).center, LayerId::random)
                    current.commit(AddLayers.insertSticker(doc, instance, sticker.assets))
                    touch.enterScope(null)
                    touch.select(instance.id)
                }.onFailure {
                    AppLog.e(TAG, "Sticker insert failed", it)
                    if (!isDestroyed) Toast.makeText(this, R.string.sticker_insert_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun groupSelection() {
        val current = session ?: return
        val doc = current.document
        val center = touch.selectionCenter(doc) ?: return
        val groupId = LayerId.random()
        current.commit(ReplaceLayers.group(doc, touch.selectedIds, groupId, getString(R.string.default_group_name), center))
        touch.select(groupId)
    }

    private fun ungroup(group: GroupLayer) {
        val current = session ?: return
        val command = ReplaceLayers.ungroup(current.document, group.id)
        current.commit(command)
        touch.setSelection(command.added.mapTo(LinkedHashSet()) { it.layer.id })
    }

    /**
     * Nudge mode: everything but the canvas and the arrow bar steps aside, and each tap
     * moves the selection by one (or ten) canvas pixels, shown live. Done saves it as one
     * undo step; Cancel puts it back.
     */
    private fun startNudge() {
        if (touch.selectedIds.isEmpty()) return
        nudgeOffset = Vec2.ZERO
        mainBar.visibility = View.GONE
        selectionBar.visibility = View.GONE
        layersContainer.visibility = View.GONE
        nudgeBar.visibility = View.VISIBLE
        refreshNudgeStep()
    }

    private fun refreshNudgeStep() {
        nudgeStepButton.text = getString(R.string.nudge_step, nudgeStep.toInt())
    }

    private fun nudge(direction: Vec2) {
        val doc = session?.document ?: return
        nudgeOffset += direction * nudgeStep
        canvasView.document = movedSelection(doc, nudgeOffset).fold(doc) { d, (id, t) ->
            d.withLayerReplaced(d.requireLayer(id).withTransform(t))
        }
    }

    /** The selected, unlocked layers moved by [offset] canvas pixels, as new transforms. */
    private fun movedSelection(doc: Document, offset: Vec2): List<Pair<LayerId, Transform>> =
        touch.selectedIds.mapNotNull { id ->
            val layer = doc.layer(id)?.takeIf { !it.locked } ?: return@mapNotNull null
            id to layer.transform.translatedBy(touch.canvasOffsetToScope(doc, offset))
        }

    private fun endNudge(commit: Boolean) {
        val current = session
        if (commit && current != null && nudgeOffset != Vec2.ZERO) {
            val moved = movedSelection(current.document, nudgeOffset).toMap()
            if (moved.isNotEmpty()) current.commit(TransformLayers.of(current.document, moved, "Nudge"))
        }
        nudgeOffset = Vec2.ZERO
        endPreview()
        nudgeBar.visibility = View.GONE
        mainBar.visibility = View.VISIBLE
        refreshSelectionBar()
    }

    /** Takes the selected layers out of their group, leaving the rest of the group as it is. */
    private fun removeFromGroup(ids: Collection<LayerId>) {
        val current = session ?: return
        val doc = current.document
        val command = runCatching { ReplaceLayers.removeFromGroup(doc, ids) }.getOrNull() ?: return
        current.commit(command)
        val parent = command.added.first().parentId
        touch.enterScope(parent)
        touch.setSelection(ids.toSet().intersect(command.added.map { it.layer.id }.toSet()).ifEmpty { command.added.map { it.layer.id }.toSet() })
    }

    private fun refreshMultiButton() {
        multiButton.isActivated = touch.multiSelectMode
        multiButton.contentDescription = getString(
            if (touch.multiSelectMode) R.string.editor_multiselect_on else R.string.editor_multiselect_off,
        )
    }

    private fun refreshScopeBar() {
        val doc = session?.document
        val scope = touch.scopeId
        val group = scope?.let { doc?.layer(it) }
        scopeBar.visibility = if (group == null) View.GONE else View.VISIBLE
        if (doc != null && group != null) {
            val path = doc.ancestors(group.id).orEmpty().map { it.name } + group.name
            scopeName.text = getString(R.string.scope_editing, path.joinToString(" › "))
        }
    }

    private fun selectedSingle(): Layer? = touch.selectedIds.singleOrNull()?.let { session?.document?.layer(it) }

    private fun refreshSelectionBar() {
        // One Edit button for whatever single layer is selected; opacity lives in each sheet.
        editTextButton.visibility = if (selectedSingle() == null) View.GONE else View.VISIBLE
        val count = touch.selectedIds.size
        selectionBar.visibility = if (count == 0) View.GONE else View.VISIBLE
        if (count > 0) selectionCount.text = resources.getQuantityString(R.plurals.selection_count, count, count)
    }

    private fun setUpLayersPanel() {
        layersContainer = findViewById(R.id.editor_layers_panel)
        layersEmpty = findViewById(R.id.editor_layers_empty)
        layersPanel = LayersPanel(
            list = findViewById(R.id.editor_layers_list),
            onSelect = { id -> session?.let { touch.selectInTree(it.document, id, touch.multiSelectMode) } },
            onLongPress = { id -> session?.let { touch.longPressInTree(it.document, id) } },
            onDrop = { id, parentId, index ->
                val current = session ?: return@LayersPanel
                val doc = current.document
                // Within the same group it's a reorder; across groups the layer changes group.
                val command = if (doc.parentOf(id) == parentId) MoveLayer.of(doc, id, index)
                else runCatching { ReplaceLayers.moveTo(doc, id, parentId, index) }.getOrNull()
                if (command != null) current.commit(command) else refreshLayers()
            },
            onRemoveFromGroup = { layer -> removeFromGroup(listOf(layer.id)) },
            onToggleVisible = { layer ->
                session?.let { it.commit(UpdateLayer.setVisible(it.document, layer.id, !layer.visible)) }
            },
            onToggleLocked = { layer ->
                session?.let { it.commit(UpdateLayer.setLocked(it.document, layer.id, !layer.locked)) }
            },
            onRename = ::askRenameLayer,
            onDelete = { layer ->
                val doc = session?.document ?: return@LayersPanel
                session?.commit(RemoveLayers.of(doc, listOf(layer.id)))
            },
        )
        findViewById<Button>(R.id.editor_layers).setOnClickListener {
            layersContainer.visibility = if (layersContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refreshLayers()
        }
    }

    private fun refreshLayers() {
        val doc = session?.document ?: return
        if (layersContainer.visibility != View.VISIBLE) return
        layersEmpty.visibility = if (doc.layers.isEmpty()) View.VISIBLE else View.GONE
        layersPanel.show(doc, touch.selectedIds)
    }

    private fun askRenameLayer(layer: Layer) {
        val input = android.widget.EditText(this).apply {
            setText(layer.name)
            setSelectAllOnFocus(true)
            contentDescription = getString(R.string.layers_rename_title)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.layers_rename_title)
            .setView(input)
            .setPositiveButton(R.string.layers_rename) { _, _ ->
                val name = input.text.toString().trim()
                val doc = session?.document ?: return@setPositiveButton
                if (name.isNotEmpty() && doc.layer(layer.id) != null) {
                    session?.commit(UpdateLayer.rename(doc, layer.id, name))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateUndoRedo(history: History) {
        undoButton.isEnabled = history.canUndo
        undoButton.alpha = if (history.canUndo) 1f else DISABLED_ALPHA
        undoButton.contentDescription = history.undoLabel
            ?.let { getString(R.string.editor_undo_action, it) } ?: getString(R.string.editor_undo)
        redoButton.isEnabled = history.canRedo
        redoButton.alpha = if (history.canRedo) 1f else DISABLED_ALPHA
        redoButton.contentDescription = history.redoLabel
            ?.let { getString(R.string.editor_redo_action, it) } ?: getString(R.string.editor_redo)
    }

    /** Re-renders the project-list thumbnail after edits, newest version only. */
    private fun newThumbnailQueue(): SaveQueue {
        val renderer = ThumbnailRenderer({ app.projects.assets(projectId).file(it) })
        return SaveQueue(
            executor = app.thumbnails,
            save = { m -> app.projects.writeThumbnail(m.info.id) { renderer.writePng(m.document, it) } },
            onResult = { version, error -> if (error != null) AppLog.w(TAG, "Thumbnail $version failed", error) },
        )
    }

    private fun newSaveQueue() = SaveQueue(
        executor = app.io,
        save = app.projects::save,
        onResult = { version, error ->
            if (error != null) AppLog.e(TAG, "Save of version $version failed", error)
            runOnUiThread {
                saveTracker.onSaveResult(version, success = error == null)
                if (isDestroyed) return@runOnUiThread
                showSaveStatus()
                if (error != null) Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_LONG).show()
            }
        },
    )

    /** "Saved" appears only once the newest edit and its history record are on disk. */
    private fun showSaveStatus() {
        saveStatus.setText(
            when (saveTracker.state) {
                SaveTracker.State.SAVED -> R.string.save_status_saved
                SaveTracker.State.SAVING -> R.string.save_status_saving
                SaveTracker.State.FAILED -> R.string.save_status_failed
            },
        )
    }

    private fun addImage(uri: android.net.Uri) {
        val assets = app.projects.assets(projectId)
        app.io.execute {
            val result = runCatching { ImageImporter.import(contentResolver, uri, assets) }
            runOnUiThread {
                val current = session ?: return@runOnUiThread
                result.onSuccess { asset ->
                    // Placed against the document as it is now, so edits made during the import are kept.
                    val doc = current.document
                    val layer = ImageLayer(
                        id = LayerId.random(),
                        name = getString(R.string.editor_default_image_name),
                        assetId = asset.id,
                        transform = placementFrame(doc).let { frame ->
                            Placement.centeredFit(frame.sizing, asset.width, asset.height).copy(position = frame.center)
                        },
                    )
                    current.commit(AddLayers.onTop(doc, layer, asset))
                    // New content goes on the top level, so step out of any group first.
                    touch.enterScope(null)
                    touch.select(layer.id)
                    AppLog.i(TAG, "Added image ${asset.width}x${asset.height} ${asset.mimeType}")
                }.onFailure {
                    AppLog.e(TAG, "Image import failed", it)
                    if (!isDestroyed) Toast.makeText(this, R.string.editor_import_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** New content appears in the middle of what is on screen, not the middle of the canvas. */
    private fun placementFrame(doc: Document) = Placement.frame(doc.canvas, canvasView.visibleCanvasRect())

    private fun showAddMenu(anchor: View) {
        if (session == null) return
        PopupMenu(this, anchor).apply {
            menu.add(0, ADD_IMAGE, 0, R.string.add_image)
            menu.add(0, ADD_TEXT, 1, R.string.add_text)
            val shapes = menu.addSubMenu(0, 0, 2, R.string.add_shape)
            shapeNames.forEach { (kind, name) -> shapes.add(1, kind.ordinal, kind.ordinal, name) }
            menu.add(0, ADD_STICKER, 3, R.string.add_sticker)
            setOnMenuItemClickListener {
                when {
                    it.groupId == 1 -> addShape(ShapeKind.entries[it.itemId])
                    it.itemId == ADD_IMAGE -> ImagePicker.launch(this@EditorActivity, REQUEST_ADD_IMAGE)
                    it.itemId == ADD_TEXT -> addText()
                    it.itemId == ADD_STICKER -> StickerPicker(this@EditorActivity, ::insertSticker).show()
                }
                true
            }
        }.show()
    }

    private val shapeNames = listOf(
        ShapeKind.RECTANGLE to R.string.shape_rectangle,
        ShapeKind.ROUNDED_RECTANGLE to R.string.shape_rounded_rectangle,
        ShapeKind.ELLIPSE to R.string.shape_ellipse,
        ShapeKind.LINE to R.string.shape_line,
        ShapeKind.ARROW to R.string.shape_arrow,
    )

    /** Adds a shape in the middle of the view, sized for it; Edit shape changes it afterwards. */
    private fun addShape(kind: ShapeKind) {
        val current = session ?: return
        val doc = current.document
        val name = getString(shapeNames.first { it.first == kind }.second)
        val layer = Placement.defaultShape(LayerId.random(), name, kind, placementFrame(doc))
        current.commit(AddLayers.onTop(doc, layer))
        touch.enterScope(null)
        touch.select(layer.id)
        AppLog.i(TAG, "Added shape $kind")
    }

    /** Shows an edit on the canvas without saving it; [endPreview] puts back what is saved. */
    private fun previewLayer(layer: Layer) {
        val doc = session?.document ?: return
        if (doc.layer(layer.id) != null) canvasView.document = doc.withLayerReplaced(layer)
    }

    private fun endPreview() {
        session?.document?.let { canvasView.document = it }
    }

    private fun editImage(image: ImageLayer) {
        val asset = session?.document?.assets?.get(image.assetId) ?: return
        // Edit the layer as it is now, so the crop keeps the picture in place from here.
        val latest = session?.document?.layer(image.id) as? ImageLayer ?: return
        ImageSheet.show(this, latest, asset, ::previewLayer, ::endPreview) { edited ->
            val current = session ?: return@show
            if (current.document.layer(image.id) != null) {
                current.commit(UpdateLayer.of(current.document, edited, "Adjust image"))
            }
        }
    }

    /** Groups and stickers: opacity, shown live. */
    private fun editGroup(group: GroupLayer) {
        var opacity = group.opacity
        val controls = SheetControls(this)
        controls.slider(
            getString(R.string.opacity_title), 0.0, 1.0, OPACITY_STEPS, opacity,
            { getString(R.string.percent_value, (it * 100).toInt()) },
        ) {
            opacity = it
            previewLayer(group.withOpacity(it))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.group_edit_title)
            .setView(controls.view)
            .setPositiveButton(R.string.text_done) { _, _ ->
                val current = session ?: return@setPositiveButton
                val latest = current.document.layer(group.id) ?: return@setPositiveButton
                current.commit(UpdateLayer.of(current.document, latest.withOpacity(opacity), "Change opacity"))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { endPreview() }
            .show()
        LiveSheet.style(this, dialog)
    }

    private fun editShape(shape: ShapeLayer) {
        val canvas = session?.document?.canvas ?: return
        ShapeSheet.show(this, shape, maxOf(canvas.width, canvas.height).toDouble(), ::previewLayer, ::endPreview) { edited ->
            val current = session ?: return@show
            // Keep any move made meanwhile: only the shape's own properties come from the sheet.
            val latest = current.document.layer(shape.id) as? ShapeLayer ?: return@show
            current.commit(
                UpdateLayer.of(current.document, edited.copy(transform = latest.transform, name = latest.name), "Change shape"),
            )
        }
    }

    private fun addText() {
        val doc = session?.document ?: return
        // Sized and placed for the view as it is when Text is tapped.
        val frame = placementFrame(doc)
        val style = TextStyle(fontSize = Placement.defaultFontSize(frame.sizing))
        fun layerFor(text: String, chosen: TextStyle, opacity: Double, id: LayerId) = TextLayer(
            id = id,
            name = getString(R.string.editor_default_text_name),
            text = text,
            style = chosen,
            boxWidth = Placement.defaultTextBoxWidth(frame.sizing),
            transform = Transform(position = frame.center),
            opacity = opacity,
        )
        val previewId = LayerId.random()
        TextSheet.show(
            this, R.string.text_add_title, "", style, 1.0, Placement.maxFontSize(doc.canvas),
            onPreview = { text, chosen, opacity ->
                // The new text shows on top while it is being typed; it is added on Done.
                session?.document?.let {
                    canvasView.document = it.withLayerInserted(it.layers.size, layerFor(text, chosen, opacity, previewId))
                }
            },
            onClose = ::endPreview,
        ) { text, chosen, opacity ->
            val current = session ?: return@show
            val layer = layerFor(text, chosen, opacity, LayerId.random())
            current.commit(AddLayers.onTop(current.document, layer))
            touch.enterScope(null)
            touch.select(layer.id)
            AppLog.i(TAG, "Added text layer")
        }
    }

    /** Opens the text sheet for an existing text layer; one undo step for the whole edit. */
    fun editText(layer: TextLayer) {
        val canvas = session?.document?.canvas ?: return
        TextSheet.show(
            this, R.string.text_edit_title, layer.text, layer.style, layer.opacity, Placement.maxFontSize(canvas),
            onPreview = { text, style, opacity -> previewLayer(layer.copy(text = text, style = style, opacity = opacity)) },
            onClose = ::endPreview,
        ) { text, style, opacity ->
            val current = session ?: return@show
            // The layer may have moved or been edited meanwhile: start from its current state.
            val latest = current.document.layer(layer.id) as? TextLayer ?: return@show
            current.commit(UpdateLayer.editText(current.document, latest.copy(text = text, style = style, opacity = opacity)))
        }
    }

    companion object {
        private const val TAG = "EditorActivity"
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val REQUEST_ADD_IMAGE = 1
        private const val DISABLED_ALPHA = 0.38f
        private const val ACTION_DUPLICATE = 1
        private const val ACTION_GROUP = 2
        private const val ACTION_UNGROUP = 3
        private const val ACTION_EDIT_GROUP = 4
        private const val ACTION_MAKE_STICKER = 5
        private const val ACTION_SAVE_STICKER = 6
        private const val ACTION_NUDGE = 7
        private const val ACTION_REMOVE_FROM_GROUP = 9
        private const val NUDGE_BIG = 10.0
        private const val ACTION_PRECISION = 8
        private const val MENU_CHECKPOINT = 5
        private const val OPACITY_STEPS = 100
        private const val DUPLICATE_OFFSET_FRACTION = 0.04
        private const val BYTES_PER_PIXEL = 4
        private const val ADD_IMAGE = 101
        private const val ADD_TEXT = 102
        private const val ADD_STICKER = 103
        private const val MENU_FIT = 1
        private const val MENU_HISTORY = 2
        private const val MENU_SNAPPING = 4
        private const val GROUP_ALIGN = 10
        private const val GROUP_DISTRIBUTE = 11
        private const val PREVIEW_MAX_SIDE = 1024
        private const val PNG_QUALITY = 100
        private const val PROGRESS_PADDING = 48

        fun intent(context: Context, projectId: String) =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)
    }
}
