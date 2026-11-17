/**
 * Crash handler, log buffer, diagnostics screen, and export.
 *
 * All logging goes through [AppLog], never bare android.util.Log, so lines reach the in-app buffer.
 */
package io.github.drhydr0.layers.diagnostics

/**
 * Keeps the most recent log lines in memory so the diagnostics screen and crash
 * reports can show them. There is no logcat access on the developer's phone.
 */
class LogBuffer(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val lines = ArrayDeque<String>(capacity)

    @Synchronized
    fun add(line: String) {
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast(line)
    }

    /** Oldest line first. */
    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}
