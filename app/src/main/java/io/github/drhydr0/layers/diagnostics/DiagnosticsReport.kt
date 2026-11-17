package io.github.drhydr0.layers.diagnostics

import java.util.Locale

/** Point-in-time app health, gathered off the main thread by [DiagnosticsCollector]. */
data class DiagnosticsSnapshot(
    val identity: BuildIdentity,
    val timestamp: String,
    val javaHeapUsedBytes: Long,
    val javaHeapMaxBytes: Long,
    val nativeHeapBytes: Long,
    val deviceAvailableBytes: Long,
    val deviceLowMemory: Boolean,
    val cacheBytes: Long,
    val filesBytes: Long,
    // Null until the editor has a document and history.
    val layerCount: Int?,
    val historySize: Int?,
    val recentLog: List<String>,
)

/** Plain-text diagnostics export. Kept free of Android types so its format is JVM-tested. */
object DiagnosticsReport {
    fun format(s: DiagnosticsSnapshot): String = buildString {
        appendLine("Layers diagnostics")
        appendLine("Time: ${s.timestamp}")
        s.identity.lines().forEach(::appendLine)
        appendLine()
        appendLine("Memory:")
        appendLine("  Java heap: ${formatBytes(s.javaHeapUsedBytes)} of ${formatBytes(s.javaHeapMaxBytes)}")
        appendLine("  Native heap: ${formatBytes(s.nativeHeapBytes)}")
        val low = if (s.deviceLowMemory) " (LOW)" else ""
        appendLine("  Device available: ${formatBytes(s.deviceAvailableBytes)}$low")
        appendLine("Storage:")
        appendLine("  Cache: ${formatBytes(s.cacheBytes)}")
        appendLine("  Files: ${formatBytes(s.filesBytes)}")
        appendLine("Document:")
        appendLine("  Layers: ${s.layerCount ?: "no document open"}")
        appendLine("  History entries: ${s.historySize ?: "no document open"}")
        appendLine()
        appendLine("Recent log (${s.recentLog.size} lines):")
        s.recentLog.forEach(::appendLine)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < KB -> "$bytes B"
        bytes < MB -> "%.1f KB".format(Locale.ROOT, bytes.toDouble() / KB)
        bytes < GB -> "%.1f MB".format(Locale.ROOT, bytes.toDouble() / MB)
        else -> "%.2f GB".format(Locale.ROOT, bytes.toDouble() / GB)
    }

    private const val KB = 1024L
    private const val MB = KB * 1024
    private const val GB = MB * 1024
}
