/**
 * Screens, toolbars, sheets, layers panel, and accessibility.
 */
package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.drhydr0.layers.BuildConfig
import io.github.drhydr0.layers.LayersApp
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Placement
import io.github.drhydr0.layers.persistence.format.ProjectInfo
import io.github.drhydr0.layers.platform.ImageImporter
import io.github.drhydr0.layers.platform.ImagePicker
import io.github.drhydr0.layers.platform.shareText

/** Project screen: recent projects, create, rename, delete, and open. */
class ProjectsActivity : Activity() {

    private val app get() = application as LayersApp
    private lateinit var adapter: ProjectListAdapter
    private lateinit var emptyView: TextView
    private lateinit var damagedView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_projects)
        findViewById<View>(R.id.root).padForSystemBars()

        // Debug builds identify their exact code, so screenshots map to a commit.
        findViewById<TextView>(R.id.build_info).apply {
            visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
            text = getString(
                R.string.projects_build_info,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.COMMIT_HASH,
            )
        }
        findViewById<Button>(R.id.open_diagnostics).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        findViewById<Button>(R.id.new_project).setOnClickListener {
            NewProjectDialog.show(this, ::createProject)
        }
        findViewById<Button>(R.id.open_image).setOnClickListener {
            ImagePicker.launch(this, REQUEST_OPEN_IMAGE)
        }
        emptyView = findViewById(R.id.empty)
        damagedView = findViewById(R.id.damaged)

        adapter = ProjectListAdapter(
            onOpen = ::openProject,
            onRename = ::askRename,
            onDelete = ::askDelete,
        )
        findViewById<RecyclerView>(R.id.project_list).apply {
            layoutManager = LinearLayoutManager(this@ProjectsActivity)
            adapter = this@ProjectsActivity.adapter
        }

        AppLog.i(TAG, "Project screen shown")
        // Ask whenever a report is waiting, not only on a "fresh" launch: after a crash,
        // Android relaunches this screen from saved state. The report is deleted once
        // answered, so a rotation re-asks only if the question is still open.
        offerPendingCrashReport()
    }

    override fun onResume() {
        super.onResume()
        // Returning from the editor may have changed names and edit times.
        refresh()
    }

    private fun refresh() {
        app.io.execute {
            val list = app.projects.list()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                adapter.submit(list.projects)
                emptyView.visibility = if (list.projects.isEmpty()) View.VISIBLE else View.GONE
                damagedView.visibility = if (list.unreadableIds.isEmpty()) View.GONE else View.VISIBLE
                damagedView.text = resources.getQuantityString(
                    R.plurals.projects_damaged, list.unreadableIds.size, list.unreadableIds.size,
                )
                if (list.unreadableIds.isNotEmpty()) {
                    AppLog.w(TAG, "Unreadable projects: ${list.unreadableIds}")
                }
            }
        }
    }

    private fun createProject(name: String, canvas: CanvasSpec) {
        app.io.execute {
            val result = runCatching { app.projects.create(name, canvas, System.currentTimeMillis()) }
            runOnUiThread {
                result.onSuccess {
                    AppLog.i(TAG, "Created project ${it.info.id} ${canvas.width}x${canvas.height}")
                    openProject(it.info)
                }.onFailure { showError(R.string.projects_create_failed, it) }
            }
        }
    }

    @Deprecated("Platform Activity result API; this app uses no AndroidX Activity library.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_IMAGE) ImagePicker.result(resultCode, data)?.let(::createFromImage)
    }

    /** A project sized to the photo, with the photo filling the canvas. */
    private fun createFromImage(uri: Uri) {
        val name = getString(R.string.new_project_default_name)
        app.io.execute {
            val store = app.projects
            val id = store.newProjectId()
            val result = runCatching {
                val asset = ImageImporter.import(contentResolver, uri, store.assets(id))
                val canvas = Placement.canvasForImage(asset.width, asset.height, CanvasSizeRules.MAX_SIDE)
                val layer = ImageLayer(
                    LayerId.random(),
                    getString(R.string.editor_default_image_name),
                    asset.id,
                    Placement.fill(canvas, asset.width, asset.height),
                )
                val document = Document(canvas).withAsset(asset).withLayerInserted(0, layer)
                store.create(id, name, document, System.currentTimeMillis())
            }
            // Don't leave imported bytes behind for a project that was never created.
            if (result.isFailure) runCatching { store.delete(id) }
            runOnUiThread {
                result.onSuccess {
                    AppLog.i(TAG, "Created project ${it.info.id} from image ${it.document.canvas.width}x${it.document.canvas.height}")
                    openProject(it.info)
                }.onFailure { showError(R.string.editor_import_failed, it) }
            }
        }
    }

    private fun openProject(info: ProjectInfo) {
        startActivity(EditorActivity.intent(this, info.id))
    }

    private fun askRename(info: ProjectInfo) {
        val input = EditText(this).apply {
            setText(info.name)
            setSelectAllOnFocus(true)
            contentDescription = getString(R.string.projects_rename_field_description)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.projects_rename_title)
            .setView(input)
            .setPositiveButton(R.string.projects_rename_confirm) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { info.name }
                app.io.execute {
                    val result = runCatching { app.projects.rename(info.id, name, System.currentTimeMillis()) }
                    runOnUiThread {
                        result.onFailure { showError(R.string.projects_rename_failed, it) }
                        refresh()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun askDelete(info: ProjectInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.projects_delete_title, info.name))
            .setMessage(R.string.projects_delete_message)
            .setPositiveButton(R.string.projects_delete_confirm) { _, _ ->
                app.io.execute {
                    val result = runCatching { app.projects.delete(info.id) }
                    runOnUiThread {
                        result.onSuccess { AppLog.i(TAG, "Deleted project ${info.id}") }
                            .onFailure { showError(R.string.projects_delete_failed, it) }
                        refresh()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showError(messageRes: Int, error: Throwable) {
        AppLog.e(TAG, getString(messageRes), error)
        if (!isDestroyed) Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }

    private fun offerPendingCrashReport() {
        app.io.execute {
            val report = app.crashReports.read() ?: return@execute
            runOnUiThread { if (!isFinishing && !isDestroyed) showCrashDialog(report) }
        }
    }

    private fun showCrashDialog(report: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_dialog_title)
            .setMessage(R.string.crash_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.crash_dialog_share) { _, _ ->
                shareText(
                    subject = getString(R.string.crash_report_subject),
                    text = report,
                    chooserTitle = getString(R.string.share_chooser_title),
                )
                deleteCrashReport()
            }
            .setNegativeButton(R.string.crash_dialog_dismiss) { _, _ -> deleteCrashReport() }
            .show()
    }

    private fun deleteCrashReport() {
        app.io.execute { app.crashReports.delete() }
    }

    private companion object {
        const val TAG = "ProjectsActivity"
        const val REQUEST_OPEN_IMAGE = 1
    }
}
