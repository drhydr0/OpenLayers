package io.github.drhydr0.layers.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import java.io.File
import java.time.ZonedDateTime

/** Gathers a [DiagnosticsSnapshot]. Walks directories, so call off the main thread. */
class DiagnosticsCollector(private val context: Context) {

    fun collect(): DiagnosticsSnapshot {
        val runtime = Runtime.getRuntime()
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memoryInfo)
        return DiagnosticsSnapshot(
            identity = currentBuildIdentity(),
            timestamp = ZonedDateTime.now().toString(),
            javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            javaHeapMaxBytes = runtime.maxMemory(),
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize(),
            deviceAvailableBytes = memoryInfo.availMem,
            deviceLowMemory = memoryInfo.lowMemory,
            cacheBytes = sizeOf(context.cacheDir),
            filesBytes = sizeOf(context.filesDir),
            layerCount = EditorStats.latest?.layerCount,
            historySize = EditorStats.latest?.historySize,
            recentLog = AppLog.buffer.snapshot(),
        )
    }

    private fun sizeOf(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
