/**
 * Commands, undo and redo, checkpoints, retention, and compaction.
 *
 * Plain Kotlin only: no android.* or androidx.* imports, so this package runs in JVM tests.
 * Every document change goes through a [Command].
 */
package io.github.drhydr0.layers.history

import io.github.drhydr0.layers.document.Document

/**
 * One undoable document change. Commands hold before and after values, so [revert]
 * restores exactly what [apply] replaced, however many layers it touched.
 */
interface Command {
    /** Readable action for the history list, e.g. "Move 3 layers". */
    val label: String

    /** True when applying changes nothing, so it shouldn't use an undo step. */
    val isNoOp: Boolean get() = false

    fun apply(document: Document): Document
    fun revert(document: Document): Document
}

