package io.github.drhydr0.layers.diagnostics

import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZonedDateTime

/**
 * Saves a crash report, then hands off to the platform handler so Android still
 * shows its crash dialog and kills the process.
 */
class CrashHandler private constructor(
    private val store: CrashReportStore,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, error: Throwable) {
        // Disk I/O on the crashing thread is deliberate: the process is about to die,
        // so there is no later moment to write the report.
        try {
            AppLog.e(TAG, "Uncaught exception on ${thread.name}", error)
            val report = CrashReport.format(
                identity = currentBuildIdentity(),
                timestamp = ZonedDateTime.now().toString(),
                threadName = thread.name,
                stackTrace = stackTraceOf(error),
                recentLog = AppLog.buffer.snapshot(),
            )
            store.save(report)
        } catch (_: Throwable) {
            // Never let report writing hide the original crash.
        }
        previous?.uncaughtException(thread, error)
    }

    private fun stackTraceOf(error: Throwable): String =
        StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    companion object {
        private const val TAG = "CrashHandler"

        fun install(store: CrashReportStore) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(store, previous))
        }
    }
}
