package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.history.History

/**
 * Lists every change newest first, including undos, redos, and earlier jumps, and goes
 * back to the state after any of them. Going back is recorded too, so nothing is lost.
 */
object HistorySheet {

    fun show(activity: Activity, history: History, onJump: (position: Int) -> Unit) {
        val labels = history.journalLabels()
        val rows = rows(labels, activity.getString(R.string.history_oldest))
        // The newest entry is always the current state.
        val current = rowFor(labels.size, rows.size)
        AlertDialog.Builder(activity)
            .setTitle(R.string.history_title)
            .setSingleChoiceItems(rows.toTypedArray(), current) { dialog, row ->
                dialog.dismiss()
                onJump(positionFor(row, rows.size))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Newest action first; the last row is the oldest state undo can reach. */
    fun rows(labels: List<String>, oldestLabel: String): List<String> = labels.reversed() + oldestLabel

    /** Row showing the state after [position] applied steps. */
    fun rowFor(position: Int, rowCount: Int) = rowCount - 1 - position

    fun positionFor(row: Int, rowCount: Int) = rowCount - 1 - row
}
