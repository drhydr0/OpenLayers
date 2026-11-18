package io.github.drhydr0.layers

import android.app.Application
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.diagnostics.CrashHandler
import io.github.drhydr0.layers.diagnostics.CrashReportStore
import io.github.drhydr0.layers.persistence.ProjectStore
import io.github.drhydr0.layers.render.Fonts
import io.github.drhydr0.layers.persistence.StickerStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LayersApp : Application() {

    /** Single background thread for disk work, which must stay off the main thread. */
    val io: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "layers-io") }

    /** Image decoding, kept apart from [io] so a large decode never delays a save. */
    val decode: ExecutorService = Executors.newFixedThreadPool(2) { Thread(it, "layers-decode") }

    /** Thumbnail rendering: low priority, one at a time, never in the way of saves or the screen. */
    val thumbnails: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "layers-thumbnails").apply { priority = Thread.MIN_PRIORITY }
    }

    lateinit var crashReports: CrashReportStore
        private set

    lateinit var projects: ProjectStore
        private set

    lateinit var stickers: StickerStore
        private set

    override fun onCreate() {
        super.onCreate()
        Fonts.init(this)
        crashReports = CrashReportStore(filesDir)
        CrashHandler.install(crashReports)
        projects = ProjectStore(filesDir)
        stickers = StickerStore(filesDir)
        io.execute {
            projects.deleteTempFiles()
            stickers.deleteTempFiles()
        }
        AppLog.i(TAG, "App started: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.COMMIT_HASH}")
    }

    private companion object {
        const val TAG = "LayersApp"
    }
}
