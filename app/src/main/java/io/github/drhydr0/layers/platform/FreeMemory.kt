package io.github.drhydr0.layers.platform

import android.app.ActivityManager
import android.content.Context

/** Memory the phone reports as free right now, which is where image pixels live. */
object FreeMemory {
    fun bytes(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        // Without a reading, fall back to the app's own heap limit: smaller, but always available.
        return info.availMem.takeIf { it > 0 } ?: Runtime.getRuntime().maxMemory()
    }
}
