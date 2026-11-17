package io.github.drhydr0.layers.diagnostics

/**
 * Size of the most recently open project, for the diagnostics report. Counts only:
 * reports get shared, so no names or text from the user's work.
 */
object EditorStats {
    data class Snapshot(val layerCount: Int, val historySize: Int)

    @Volatile
    var latest: Snapshot? = null
}
