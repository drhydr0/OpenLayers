package io.github.drhydr0.layers.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import io.github.drhydr0.layers.BuildConfig
import io.github.drhydr0.layers.LayersApp
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.diagnostics.DiagnosticsCollector
import io.github.drhydr0.layers.diagnostics.DiagnosticsReport
import io.github.drhydr0.layers.platform.shareText

/** Shows app health and recent log lines, and exports them, since there is no logcat. */
class DiagnosticsActivity : Activity() {

    private val app get() = application as LayersApp
    private lateinit var reportView: TextView
    private var report: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        findViewById<View>(R.id.root).padForSystemBars()
        reportView = findViewById(R.id.report)

        findViewById<Button>(R.id.refresh).setOnClickListener { refresh() }
        findViewById<Button>(R.id.share).setOnClickListener { share() }
        findViewById<Button>(R.id.test_crash).apply {
            // Lets the developer check the crash-report flow on the phone. Never in release.
            visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
            setOnClickListener {
                AppLog.w(TAG, "Test crash requested")
                throw IllegalStateException("Test crash from the diagnostics screen")
            }
        }
        refresh()
    }

    private fun refresh() {
        reportView.setText(R.string.diagnostics_loading)
        val collector = DiagnosticsCollector(applicationContext)
        app.io.execute {
            val text = DiagnosticsReport.format(collector.collect())
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                report = text
                reportView.text = text
            }
        }
    }

    private fun share() {
        val text = report ?: return
        AppLog.i(TAG, "Sharing diagnostics")
        shareText(
            subject = getString(R.string.diagnostics_subject),
            text = text,
            chooserTitle = getString(R.string.share_chooser_title),
        )
    }

    private companion object {
        const val TAG = "DiagnosticsActivity"
    }
}
