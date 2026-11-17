package io.github.drhydr0.layers.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportFormatTest {

    private val identity = BuildIdentity(
        versionName = "0.1.0",
        versionCode = 7,
        commitHash = "abc123def456",
        buildType = "debug",
        device = "Google Pixel 9 Pro",
        androidVersion = "16 (API 36)",
    )

    @Test
    fun crashReportContainsIdentityStackAndLog() {
        val text = CrashReport.format(
            identity = identity,
            timestamp = "2026-11-17T10:00Z",
            threadName = "main",
            stackTrace = "java.lang.IllegalStateException: boom\n\tat Foo.bar(Foo.kt:1)\n",
            recentLog = listOf("10:00:00.000 I/Start: shown"),
        )
        listOf(
            "Version: 0.1.0 (7) debug",
            "Commit: abc123def456",
            "Device: Google Pixel 9 Pro",
            "Android: 16 (API 36)",
            "Thread: main",
            "java.lang.IllegalStateException: boom",
            "Recent log (1 lines):",
            "10:00:00.000 I/Start: shown",
        ).forEach { assertTrue("missing: $it", text.contains(it)) }
    }

    @Test
    fun diagnosticsReportShowsMissingProjectClearly() {
        val text = DiagnosticsReport.format(
            DiagnosticsSnapshot(
                identity = identity,
                timestamp = "2026-11-17T10:00Z",
                javaHeapUsedBytes = 10L * 1024 * 1024,
                javaHeapMaxBytes = 512L * 1024 * 1024,
                nativeHeapBytes = 2048,
                deviceAvailableBytes = 3L * 1024 * 1024 * 1024,
                deviceLowMemory = true,
                cacheBytes = 0,
                filesBytes = 500,
                layerCount = null,
                historySize = null,
                recentLog = emptyList(),
            ),
        )
        listOf(
            "Commit: abc123def456",
            "Java heap: 10.0 MB of 512.0 MB",
            "Device available: 3.00 GB (LOW)",
            "Layers: no project opened yet",
            "History entries: no project opened yet",
        ).forEach { assertTrue("missing: $it", text.contains(it)) }
    }

    @Test
    fun formatsByteSizes() {
        assertEquals("0 B", DiagnosticsReport.formatBytes(0))
        assertEquals("1023 B", DiagnosticsReport.formatBytes(1023))
        assertEquals("1.5 KB", DiagnosticsReport.formatBytes(1536))
        assertEquals("1.0 MB", DiagnosticsReport.formatBytes(1024L * 1024))
        assertEquals("2.00 GB", DiagnosticsReport.formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun diagnosticsReportShowsProjectCounts() {
        val text = DiagnosticsReport.format(
            DiagnosticsSnapshot(
                identity, "t", 0, 1, 0, 0, false, 0, 0,
                layerCount = 12, historySize = 40, recentLog = emptyList(),
            ),
        )
        assertTrue(text.contains("Layers: 12"))
        assertTrue(text.contains("History entries: 40"))
    }
}
