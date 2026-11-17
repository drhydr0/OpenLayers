package io.github.drhydr0.layers.diagnostics

import android.util.Log
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The project's logging wrapper. The only place allowed to call android.util.Log. */
object AppLog {
    // Bounded so a crash report or shared export stays well under the share-sheet size limit.
    private const val MAX_LINES = 500
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    val buffer = LogBuffer(MAX_LINES)

    fun i(tag: String, message: String) = log(Log.INFO, "I", tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) =
        log(Log.WARN, "W", tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) =
        log(Log.ERROR, "E", tag, message, error)

    private fun log(priority: Int, level: String, tag: String, message: String, error: Throwable?) {
        val text = if (error == null) message else "$message: $error"
        buffer.add("${LocalTime.now().format(timeFormat)} $level/$tag: $text")
        Log.println(priority, tag, if (error == null) message else "$message\n${Log.getStackTraceString(error)}")
    }
}
