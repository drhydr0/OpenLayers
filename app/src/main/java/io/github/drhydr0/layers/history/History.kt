package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.Document

/**
 * The current document, the undo and redo stacks for the buttons, and a journal of every
 * change in the order it happened: edits, undos, redos, and jumps alike. Undo and redo
 * behave as usual; the journal is what the history list shows, and since nothing is ever
 * removed from it (beyond the size cap), any earlier state stays reachable, even one on a
 * branch a later edit replaced. Main-thread only.
 */
class History(
    initial: Document,
    saved: SavedHistory = SavedHistory.EMPTY,
    private val maxSteps: Int = DEFAULT_MAX_STEPS,
    private val maxJournal: Int = DEFAULT_MAX_JOURNAL,
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
    private val journal = ArrayDeque(saved.journal.takeLast(maxJournal))
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
        record(command.label, command)
        changed()
        return true
    }

    fun undo(): Boolean {
        val command = undoStack.removeLastOrNull() ?: return false
        document = command.revert(document)
        redoStack.addLast(command)
        record("$UNDO_PREFIX${command.label}", InverseCommand(command))
        changed()
        return true
    }

    fun redo(): Boolean {
        val command = redoStack.removeLastOrNull() ?: return false
        document = command.apply(document)
        undoStack.addLast(command)
        record("$REDO_PREFIX${command.label}", command)
        changed()
        return true
    }

    /** Journal entries from oldest to newest; the newest is the current state. */
    fun journalLabels(): List<String> = journal.map { it.label }

    /**
     * Returns to the state right after journal entry [entries] (0: the oldest state the
     * journal reaches) by reversing every later entry. The return is itself an edit: it goes
     * on the journal and the undo stack, so it can be undone and nothing later is lost.
     */
    fun goTo(entries: Int): Boolean {
        require(entries in 0..journal.size) { "journal position $entries out of range" }
        if (entries == journal.size) return false
        val reversals = journal.toList().drop(entries).asReversed().map { InverseCommand(it.command) }
        val target = if (entries == 0) OLDEST_LABEL else journal[entries - 1].label
        return execute(CommandSequence(reversals, "$GO_BACK_PREFIX$target"))
    }

    private fun record(label: String, command: Command) {
        journal.addLast(JournalEntry(label, command))
        // Dropping the oldest entries only shortens how far back the list reaches.
        while (journal.size > maxJournal) journal.removeFirst()
    }

    /** The stacks as saved with the document, so undo and redo survive a restart. */
    fun snapshot() = SavedHistory(undoStack.toList(), redoStack.toList(), journal.toList())

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

        /** Each undo and redo adds an entry too, so the journal reaches back a little further than undo. */
        const val DEFAULT_MAX_JOURNAL = 1_000

        const val UNDO_PREFIX = "Undo "
        const val REDO_PREFIX = "Redo "
        const val GO_BACK_PREFIX = "Go back to: "
        const val OLDEST_LABEL = "the oldest state"
    }
}

/**
 * Undo and redo stacks in stack order: the last undo entry is undone next, and the last
 * redo entry is redone next.
 */
data class SavedHistory(
    val undo: List<Command> = emptyList(),
    val redo: List<Command> = emptyList(),
    /** Every change in order, oldest first. */
    val journal: List<JournalEntry> = emptyList(),
) {
    companion object {
        val EMPTY = SavedHistory()
    }
}

/** One change as it happened, with the command that made it (an undo records its inverse). */
data class JournalEntry(val label: String, val command: Command)

/** The reverse of [of]: applying it undoes [of]. */
data class InverseCommand(val of: Command) : Command {
    override val label get() = "Undo ${of.label}"
    override val isNoOp get() = of.isNoOp
    override fun apply(document: Document) = of.revert(document)
    override fun revert(document: Document) = of.apply(document)
}

/** Several commands as one step, applied in order and reverted in reverse order. */
data class CommandSequence(val commands: List<Command>, override val label: String) : Command {
    override val isNoOp get() = commands.all { it.isNoOp }
    override fun apply(document: Document) = commands.fold(document) { d, c -> c.apply(d) }
    override fun revert(document: Document) = commands.asReversed().fold(document) { d, c -> c.revert(d) }
}
