package io.github.drhydr0.layers.platform

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

/** Saves images to Pictures/Layers through MediaStore; no storage permission needed. */
object GalleryWriter {

    /**
     * The entry stays pending (hidden from other apps) until [write] finishes, and is
     * deleted if it fails or is cancelled, so no broken half-images reach the gallery.
     */
    fun save(resolver: ContentResolver, displayName: String, mimeType: String, write: (OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Layers")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Gallery refused the new image")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Could not write to the gallery")
            out.use(write)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** A file name from the project name: safe characters only, plus a timestamp. */
    fun displayName(projectName: String, extension: String, nowMillis: Long): String {
        val base = projectName.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Layers" }.take(MAX_BASE)
        return "${base}_$nowMillis.$extension"
    }

    private const val MAX_BASE = 40
}
