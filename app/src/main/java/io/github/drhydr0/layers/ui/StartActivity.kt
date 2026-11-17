/**
 * Screens, toolbars, sheets, layers panel, and accessibility.
 */
package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import io.github.drhydr0.layers.BuildConfig
import io.github.drhydr0.layers.LayersApp
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.platform.shareText

/** Start screen. Shows the build identity so a screenshot pins down the exact code. */
class StartActivity : Activity() {

    private val app get() = application as LayersApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        findViewById<TextView>(R.id.version).text =
            getString(R.string.start_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        findViewById<TextView>(R.id.commit).text =
            getString(R.string.start_commit, BuildConfig.COMMIT_HASH)
        findViewById<Button>(R.id.open_diagnostics).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        findViewById<View>(R.id.root).padForSystemBars()

        AppLog.i(TAG, "Start screen shown")
        // Ask whenever a report is waiting, not only on a "fresh" launch: after a crash,
        // Android relaunches this screen from saved state. The report is deleted once
        // answered, so a rotation re-asks only if the question is still open.
        offerPendingCrashReport()
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
        const val TAG = "StartActivity"
    }
}
