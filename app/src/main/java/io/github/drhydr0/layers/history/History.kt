package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.Document

/**
 * The current document plus undo and redo stacks. Main-thread only.
 *
 * In memory for now; Milestone 3 makes it persistent.
 */
class History(initial: Document) {

    var document: Document = initial
        private set

    /**
     * Increases with every change, including undo and redo. Background work (thumbnails,
     * saves) records the version it started from and drops its result if it's stale.
     */
    var version: Long = 0
        private set

    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()
    private val listeners = mutableListOf<(History) -> Unit>()

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    val undoLabel: String? get() = undoStack.lastOrNull()?.label
    val redoLabel: String? get() = redoStack.lastOrNull()?.label

    /** Number of undoable steps, shown in diagnostics. */
    val size get() = undoStack.size + redoStack.size

    /** Applies [command]. A new edit after an undo starts a new branch, so redo is cleared. */
    fun execute(command: Command): Boolean {
        if (command.isNoOp) return false
        document = command.apply(document)
        undoStack.addLast(command)
        redoStack.clear()
        changed()
        return true
    }

    fun undo(): Boolean {
        val command = undoStack.removeLastOrNull() ?: return false
        document = command.revert(document)
        redoStack.addLast(command)
        changed()
        return true
    }

    fun redo(): Boolean {
        val command = redoStack.removeLastOrNull() ?: return false
        document = command.apply(document)
        undoStack.addLast(command)
        changed()
        return true
    }

    fun addListener(listener: (History) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (History) -> Unit) {
        listeners -= listener
    }

    private fun changed() {
        version++
        listeners.toList().forEach { it(this) }
    }
}
