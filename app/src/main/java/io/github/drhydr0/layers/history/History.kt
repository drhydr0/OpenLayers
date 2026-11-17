/**
 * Commands, undo and redo, checkpoints, retention, and compaction.

Plain Kotlin only: no android.* or androidx.* imports, so this package runs in JVM tests.
Every document change goes through a command defined here.
 */
package io.github.drhydr0.layers.history
