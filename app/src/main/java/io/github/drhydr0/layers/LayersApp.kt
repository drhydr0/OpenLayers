package io.github.drhydr0.layers

import android.app.Application
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.diagnostics.CrashHandler
import io.github.drhydr0.layers.diagnostics.CrashReportStore
import io.github.drhydr0.layers.persistence.ProjectStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LayersApp : Application() {

    /** Single background thread for disk work, which must stay off the main thread. */
    val io: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "layers-io") }

    lateinit var crashReports: CrashReportStore
        private set

    lateinit var projects: ProjectStore
        private set

    override fun onCreate() {
        super.onCreate()
        crashReports = CrashReportStore(filesDir)
        CrashHandler.install(crashReports)
        projects = ProjectStore(filesDir)
        io.execute { projects.deleteTempFiles() }
        AppLog.i(TAG, "App started: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.COMMIT_HASH}")
    }

    private companion object {
        const val TAG = "LayersApp"
    }
}
