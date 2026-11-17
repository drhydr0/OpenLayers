package io.github.drhydr0.layers.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import io.github.drhydr0.layers.LayersApp
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.history.History
import io.github.drhydr0.layers.persistence.format.ProjectInfo
import io.github.drhydr0.layers.render.DocumentView
import io.github.drhydr0.layers.render.PreviewImages

/** The editor for one project: canvas, and (in later steps) tools, layers, and export. */
class EditorActivity : Activity() {

    private val app get() = application as LayersApp
    private lateinit var canvasView: DocumentView
    private var previewImages: PreviewImages? = null

    /** Null until the project has loaded. */
    private var history: History? = null
    private var projectInfo: ProjectInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<ImageButton>(R.id.editor_back).setOnClickListener { finish() }
        canvasView = findViewById(R.id.editor_canvas)

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        if (projectId == null) {
            finish()
            return
        }
        previewImages = PreviewImages(
            fileFor = { app.projects.assets(projectId).file(it) },
            decodeExecutor = app.decode,
            onLoaded = { if (!isDestroyed) canvasView.invalidate() },
        ).also(canvasView::setImageSource)
        load(projectId)
    }

    override fun onDestroy() {
        previewImages?.clear()
        super.onDestroy()
    }

    private fun load(projectId: String) {
        app.io.execute {
            val result = runCatching { app.projects.load(projectId) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.onSuccess { manifest ->
                    AppLog.i(TAG, "Opened project $projectId with ${manifest.document.layers.size} layers")
                    projectInfo = manifest.info
                    findViewById<TextView>(R.id.editor_title).text = manifest.info.name
                    history = History(manifest.document).also { h ->
                        h.addListener { canvasView.document = it.document }
                        canvasView.document = h.document
                    }
                }.onFailure {
                    AppLog.e(TAG, "Could not open project $projectId", it)
                    Toast.makeText(this, R.string.editor_open_failed, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "EditorActivity"
        private const val EXTRA_PROJECT_ID = "project_id"

        fun intent(context: Context, projectId: String) =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)
    }
}
