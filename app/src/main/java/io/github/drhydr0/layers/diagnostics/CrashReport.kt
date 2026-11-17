package io.github.drhydr0.layers.diagnostics

/** Plain-text crash report. Kept free of Android types so its format is JVM-tested. */
object CrashReport {
    fun format(
        identity: BuildIdentity,
        timestamp: String,
        threadName: String,
        stackTrace: String,
        recentLog: List<String>,
    ): String = buildString {
        appendLine("Layers crash report")
        appendLine("Time: $timestamp")
        identity.lines().forEach(::appendLine)
        appendLine("Thread: $threadName")
        appendLine()
        appendLine("Stack trace:")
        appendLine(stackTrace.trimEnd())
        appendLine()
        appendLine("Recent log (${recentLog.size} lines):")
        recentLog.forEach(::appendLine)
    }
}
