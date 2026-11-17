package io.github.drhydr0.layers.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import io.github.drhydr0.layers.LayersApp
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Placement
import io.github.drhydr0.layers.history.AddLayers
import io.github.drhydr0.layers.history.History
import io.github.drhydr0.layers.persistence.SaveQueue
import io.github.drhydr0.layers.platform.ImageImporter
import io.github.drhydr0.layers.platform.ImagePicker
import io.github.drhydr0.layers.render.DocumentView
import io.github.drhydr0.layers.render.PreviewImages

/** The editor for one project: canvas, tools, and saving after every change. */
class EditorActivity : Activity() {

    private val app get() = application as LayersApp
    private lateinit var projectId: String
    private lateinit var canvasView: DocumentView
    private var previewImages: PreviewImages? = null

    /** Null until the project has loaded; tools do nothing before then. */
    private var session: EditorSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<ImageButton>(R.id.editor_back).setOnClickListener { finish() }
        canvasView = findViewById(R.id.editor_canvas)
        findViewById<Button>(R.id.editor_add_image).setOnClickListener {
            if (session != null) ImagePicker.launch(this, REQUEST_ADD_IMAGE)
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
                    val history = History(manifest.document)
                    session = EditorSession(manifest.info, history, newSaveQueue(), System::currentTimeMillis)
                    history.addListener { canvasView.document = it.document }
                    canvasView.document = history.document
                }.onFailure {
                    AppLog.e(TAG, "Could not open project $projectId", it)
                    Toast.makeText(this, R.string.editor_open_failed, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
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
                    AppLog.i(TAG, "Added image ${asset.width}x${asset.height} ${asset.mimeType}")
                }.onFailure {
                    AppLog.e(TAG, "Image import failed", it)
                    if (!isDestroyed) Toast.makeText(this, R.string.editor_import_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val TAG = "EditorActivity"
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val REQUEST_ADD_IMAGE = 1

        fun intent(context: Context, projectId: String) =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)
    }
}
