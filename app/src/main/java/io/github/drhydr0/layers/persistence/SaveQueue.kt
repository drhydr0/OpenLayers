package io.github.drhydr0.layers.persistence

import io.github.drhydr0.layers.persistence.format.ProjectManifest
import java.util.concurrent.Executor

/**
 * Saves the latest document after each committed edit, off the main thread.
 *
 * Edits arrive faster than disk writes during quick sequences, so only the newest pending
 * version is written: older ones are skipped, never written after a newer one.
 */
class SaveQueue(
    private val executor: Executor,
    private val save: (ProjectManifest) -> Unit,
    private val onResult: (version: Long, error: Exception?) -> Unit = { _, _ -> },
) {
    private val lock = Any()
    private var pending: Pair<Long, ProjectManifest>? = null
    private var lastSavedVersion = -1L
    private var scheduled = false

    fun submit(version: Long, manifest: ProjectManifest) {
        synchronized(lock) {
            if (version <= lastSavedVersion) return
            if (pending?.first?.let { it >= version } == true) return
            pending = version to manifest
            if (scheduled) return
            scheduled = true
        }
        executor.execute(::drain)
    }

    private fun drain() {
        while (true) {
            val (version, manifest) = synchronized(lock) {
                val next = pending
                if (next == null) {
                    scheduled = false
                    return
                }
                pending = null
                next
            }
            val error = try {
                save(manifest)
                null
            } catch (e: Exception) {
                e
            }
            synchronized(lock) { if (error == null) lastSavedVersion = maxOf(lastSavedVersion, version) }
            onResult(version, error)
        }
    }
}
