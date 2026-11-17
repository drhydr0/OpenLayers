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

/** The editor for one project. The canvas itself arrives in the next step. */
class EditorActivity : Activity() {

    private val app get() = application as LayersApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        findViewById<View>(R.id.root).padForSystemBars()
        findViewById<ImageButton>(R.id.editor_back).setOnClickListener { finish() }

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        if (projectId == null) {
            finish()
            return
        }
        app.io.execute {
            val result = runCatching { app.projects.load(projectId) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.onSuccess {
                    AppLog.i(TAG, "Opened project $projectId with ${it.document.layers.size} layers")
                    findViewById<TextView>(R.id.editor_title).text = it.info.name
                    findViewById<TextView>(R.id.editor_canvas_info).text = getString(
                        R.string.editor_canvas_size, it.document.canvas.width, it.document.canvas.height,
                    )
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
