package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.Document

/**
 * The current document plus undo and redo stacks. Main-thread only.
 *
 * In memory for now; Milestone 3 makes it persistent.
 */
class History(
    initial: Document,
    saved: SavedHistory = SavedHistory.EMPTY,
    private val maxSteps: Int = DEFAULT_MAX_STEPS,
) {

    var document: Document = initial
        private set

    /**
     * Increases with every change, including undo and redo. Background work (thumbnails,
     * saves) records the version it started from and drops its result if it's stale.
     */
    var version: Long = 0
        private set

    private val undoStack = ArrayDeque(saved.undo.takeLast(maxSteps))
    private val redoStack = ArrayDeque(saved.redo)
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
        // Dropping the oldest step only shortens how far back undo reaches.
        while (undoStack.size > maxSteps) undoStack.removeFirst()
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

    /** The stacks as saved with the document, so undo and redo survive a restart. */
    fun snapshot() = SavedHistory(undoStack.toList(), redoStack.toList())

    /** Actions from oldest to newest: the undo steps, then redo steps in the order redo applies them. */
    fun labels(): List<String> = undoStack.map { it.label } + redoStack.reversed().map { it.label }

    /** Number of applied steps, i.e. the current position in [labels]. */
    val position get() = undoStack.size

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

    companion object {
        /** Until measured history sizes set a disk budget (M7). */
        const val DEFAULT_MAX_STEPS = 500
    }
}

/**
 * Undo and redo stacks in stack order: the last undo entry is undone next, and the last
 * redo entry is redone next.
 */
data class SavedHistory(val undo: List<Command> = emptyList(), val redo: List<Command> = emptyList()) {
    companion object {
        val EMPTY = SavedHistory()
    }
}
