package io.github.drhydr0.layers.persistence

import io.github.drhydr0.layers.document.AssetId
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * A project's image bytes, stored once under their SHA-256 and never modified.
 * Identical imports share one file.
 */
class AssetStore internal constructor(private val dir: File) {

    fun file(id: AssetId) = File(dir, id.sha256)

    fun contains(id: AssetId) = file(id).isFile

    /** Copies [input] into the store, hashing as it goes. Doesn't close [input]. */
    fun put(input: InputStream): AssetId {
        dir.mkdirs()
        // A unique temp name, so concurrent imports never share a partial file.
        val temp = File(dir, "import-${UUID.randomUUID()}.tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(temp).use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                }
                out.fd.sync()
            }
            val id = AssetId(digest.digest().joinToString("") { "%02x".format(it) })
            if (contains(id)) temp.delete() else moveAtomically(temp, file(id))
            return id
        } finally {
            temp.delete()
        }
    }

    /**
     * Deletes stored images not in [keep] and returns how many were removed. Only call
     * when no import for this project is in flight, or a just-imported image could go.
     */
    internal fun deleteExcept(keep: Set<AssetId>): Int {
        val keepNames = keep.mapTo(mutableSetOf()) { it.sha256 }
        val stale = dir.listFiles { f -> f.isFile && HASH_NAME.matches(f.name) && f.name !in keepNames }
        return stale?.count { it.delete() } ?: 0
    }

    /** Leftovers from imports interrupted by a crash. */
    internal fun deleteTempFiles() {
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        val HASH_NAME = Regex("[0-9a-f]{64}")
    }
}
