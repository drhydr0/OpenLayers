package io.github.drhydr0.layers.diagnostics

import java.io.File

/** Holds at most one pending crash report until the developer shares or dismisses it. */
class CrashReportStore(filesDir: File) {
    private val dir = File(filesDir, "diagnostics")
    private val report = File(dir, "crash.txt")

    /**
     * Written to a temp file and renamed, so a second failure mid-write can't leave a
     * truncated report in place of a good one.
     */
    fun save(text: String) {
        dir.mkdirs()
        val temp = File(dir, "crash.txt.tmp")
        temp.writeText(text)
        if (!temp.renameTo(report)) {
            report.delete()
            temp.renameTo(report)
        }
    }

    /** Call off the main thread. */
    fun read(): String? = report.takeIf { it.isFile }?.readText()

    /** Call off the main thread. */
    fun delete() {
        report.delete()
    }
}
