package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import io.github.drhydr0.layers.LayersApp
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.diagnostics.EditorStats
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Placement
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.TextStyle
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.history.AddLayers
import io.github.drhydr0.layers.history.History
import io.github.drhydr0.layers.history.MoveLayer
import io.github.drhydr0.layers.history.RemoveLayers
import io.github.drhydr0.layers.history.UpdateLayer
import io.github.drhydr0.layers.interaction.CanvasTouchController
import io.github.drhydr0.layers.interaction.HitTest
import io.github.drhydr0.layers.persistence.SaveQueue
import io.github.drhydr0.layers.persistence.format.ProjectManifest
import io.github.drhydr0.layers.platform.GalleryWriter
import io.github.drhydr0.layers.platform.ImageImporter
import io.github.drhydr0.layers.platform.ImagePicker
import io.github.drhydr0.layers.render.DocumentView
import io.github.drhydr0.layers.render.ExportOptions
import io.github.drhydr0.layers.render.Exporter
import io.github.drhydr0.layers.render.PreviewImages
import io.github.drhydr0.layers.render.ThumbnailRenderer
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
    private lateinit var layersPanel: LayersPanel
    private lateinit var layersContainer: View
    private lateinit var layersEmpty: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<ImageButton>(R.id.editor_back).setOnClickListener { finish() }
        canvasView = findViewById(R.id.editor_canvas)
        findViewById<Button>(R.id.editor_add_image).setOnClickListener {
            if (session != null) ImagePicker.launch(this, REQUEST_ADD_IMAGE)
        }
        findViewById<Button>(R.id.editor_add_text).setOnClickListener { addText() }
        undoButton = findViewById<ImageButton>(R.id.editor_undo).apply { setOnClickListener { session?.history?.undo() } }
        redoButton = findViewById<ImageButton>(R.id.editor_redo).apply { setOnClickListener { session?.history?.redo() } }
        setUpTouch()
        setUpLayersPanel()
        setUpNavigation()
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
            val result = runCatching { app.projects.load(projectId) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.onSuccess { manifest ->
                    AppLog.i(TAG, "Opened project $projectId with ${manifest.document.layers.size} layers")
                    findViewById<TextView>(R.id.editor_title).text = manifest.info.name
                    val history = History(manifest.document, manifest.history)
                    session = EditorSession(manifest.info, history, newSaveQueue(), System::currentTimeMillis)
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
        val hitTest = HitTest { doc, layer ->
            when (layer) {
                is ImageLayer -> doc.imageLocalBounds(layer)
                is TextLayer -> canvasView.textLayouts.localBounds(layer)
            }
        }
        touch = CanvasTouchController(
            view = canvasView,
            hitTest = hitTest,
            document = { session?.document },
            commit = { session?.commit(it) },
            onSelectionChanged = { refreshLayers() },
            onEditText = ::editText,
        )
        canvasView.setOnTouchListener(touch)
        // Draws against what's on screen, so handles follow a gesture preview.
        canvasView.overlay = { c -> canvasView.document?.let { touch.overlay.draw(c, it) } }
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
        findViewById<ImageButton>(R.id.editor_fit).setOnClickListener { canvasView.fitToScreen() }
    }

    private fun setUpLayersPanel() {
        layersContainer = findViewById(R.id.editor_layers_panel)
        layersEmpty = findViewById(R.id.editor_layers_empty)
        layersPanel = LayersPanel(
            list = findViewById(R.id.editor_layers_list),
            onSelect = touch::select,
            onMove = { id, to ->
                val doc = session?.document ?: return@LayersPanel
                session?.commit(MoveLayer.of(doc, id, to))
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
        layersPanel.show(doc, touch.selectedId)
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
            if (error != null) {
                AppLog.e(TAG, "Save of version $version failed", error)
                runOnUiThread {
                    if (!isDestroyed) Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_LONG).show()
                }
            }
        },
    )

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
                        transform = Placement.centeredFit(doc.canvas, asset.width, asset.height),
                    )
                    current.commit(AddLayers.onTop(doc, layer, asset))
                    touch.select(layer.id)
                    AppLog.i(TAG, "Added image ${asset.width}x${asset.height} ${asset.mimeType}")
                }.onFailure {
                    AppLog.e(TAG, "Image import failed", it)
                    if (!isDestroyed) Toast.makeText(this, R.string.editor_import_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun addText() {
        val canvas = session?.document?.canvas ?: return
        val style = TextStyle(fontSize = Placement.defaultFontSize(canvas))
        TextSheet.show(this, R.string.text_add_title, "", style, Placement.maxFontSize(canvas)) { text, chosen ->
            val current = session ?: return@show
            val layer = TextLayer(
                id = LayerId.random(),
                name = getString(R.string.editor_default_text_name),
                text = text,
                style = chosen,
                boxWidth = Placement.defaultTextBoxWidth(canvas),
                transform = Transform(position = canvas.bounds.center),
            )
            current.commit(AddLayers.onTop(current.document, layer))
            touch.select(layer.id)
            AppLog.i(TAG, "Added text layer")
        }
    }

    /** Opens the text sheet for an existing text layer; one undo step for the whole edit. */
    fun editText(layer: TextLayer) {
        val canvas = session?.document?.canvas ?: return
        TextSheet.show(this, R.string.text_edit_title, layer.text, layer.style, Placement.maxFontSize(canvas)) { text, style ->
            val current = session ?: return@show
            // The layer may have moved or been edited meanwhile: start from its current state.
            val latest = current.document.layer(layer.id) as? TextLayer ?: return@show
            current.commit(UpdateLayer.editText(current.document, latest.copy(text = text, style = style)))
        }
    }

    companion object {
        private const val TAG = "EditorActivity"
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val REQUEST_ADD_IMAGE = 1
        private const val DISABLED_ALPHA = 0.38f
        private const val PROGRESS_PADDING = 48

        fun intent(context: Context, projectId: String) =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)
    }
}
