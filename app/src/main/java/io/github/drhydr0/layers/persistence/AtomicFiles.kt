/**
 * Transactions, autosave, recovery, and asset storage on disk. Runs off the main thread.
 *
 * Uses java.io and java.nio only, so storage and recovery are tested on the JVM.
 */
package io.github.drhydr0.layers.persistence

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replaces [target] so a reader sees either the old content or the new, never a mix.
 * The data is synced to disk before the rename, so a power loss can't leave the new
 * name pointing at unwritten blocks.
 */
internal fun writeAtomically(target: File, write: (FileOutputStream) -> Unit) {
    target.parentFile?.mkdirs()
    val temp = File(target.parentFile, "${target.name}.tmp")
    FileOutputStream(temp).use { out ->
        write(out)
        out.flush()
        out.fd.sync()
    }
    moveAtomically(temp, target)
}

internal fun moveAtomically(source: File, target: File) {
    Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
