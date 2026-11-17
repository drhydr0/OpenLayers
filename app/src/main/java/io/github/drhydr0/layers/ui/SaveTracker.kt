package io.github.drhydr0.layers.ui

/**
 * Works out what the save indicator may claim. "Saved" is shown only when the newest
 * edit is known to be on disk. Plain Kotlin, so the rules are JVM-tested.
 */
class SaveTracker {
    enum class State { SAVED, SAVING, FAILED }

    private var latestVersion = 0L
    private var savedVersion = 0L
    private var failedVersion: Long? = null

    val state: State
        get() {
            val failed = failedVersion
            return when {
                failed != null && failed > savedVersion -> State.FAILED
                savedVersion >= latestVersion -> State.SAVED
                else -> State.SAVING
            }
        }

    fun onEdit(version: Long) {
        latestVersion = maxOf(latestVersion, version)
        // A new edit starts a new save attempt, which also carries the failed change.
        failedVersion = null
    }

    fun onSaveResult(version: Long, success: Boolean) {
        if (success) {
            savedVersion = maxOf(savedVersion, version)
        } else if (version > savedVersion) {
            failedVersion = version
        }
    }
}
