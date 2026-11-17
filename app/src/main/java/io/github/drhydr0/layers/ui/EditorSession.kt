package io.github.drhydr0.layers.ui

import io.github.drhydr0.layers.history.Command
import io.github.drhydr0.layers.history.History
import io.github.drhydr0.layers.persistence.SaveQueue
import io.github.drhydr0.layers.persistence.format.ProjectInfo
import io.github.drhydr0.layers.persistence.format.ProjectManifest

/**
 * One open project: its history, and saving after every committed change.
 * Main-thread only, except that saves run on the save queue's executor.
 */
class EditorSession(
    info: ProjectInfo,
    val history: History,
    private val saveQueue: SaveQueue,
    private val clock: () -> Long,
) {
    var info: ProjectInfo = info
        private set

    init {
        history.addListener { save() }
    }

    val document get() = history.document

    /** Applies a change through history, the only way UI code edits the document. */
    fun commit(command: Command) = history.execute(command)

    /** Tries the newest change again after a failed save. */
    fun retrySave() = save()

    private fun save() {
        info = info.copy(modifiedAtMillis = clock())
        // Document and history go into one file, so they can't disagree after a crash.
        saveQueue.submit(history.version, ProjectManifest(info, history.document, history.snapshot()))
    }
}
