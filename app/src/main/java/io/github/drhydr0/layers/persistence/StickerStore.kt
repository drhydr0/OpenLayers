package io.github.drhydr0.layers.persistence

import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.Sticker
import io.github.drhydr0.layers.persistence.format.ManifestException
import io.github.drhydr0.layers.persistence.format.StickerCodec
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The sticker collection, shared by all projects, one folder per sticker:
 *
 *     <root>/stickers/<id>/sticker.json
 *     <root>/stickers/<id>/assets/<sha256>
 *     <root>/stickers/<id>/thumbnail.png
 *
 * Each sticker keeps its own copies of its images, so deleting a project never breaks
 * it. Blocking I/O: call off the main thread.
 */
class StickerStore(root: File) {

    private val stickersDir = File(root, "stickers")

    fun newStickerId(): String = UUID.randomUUID().toString()

    /**
     * Saves a sticker, copying its images from [imageFile]. The sticker file is committed
     * last, so an interrupted save leaves no half-sticker in the list.
     */
    fun save(sticker: Sticker, imageFile: (AssetId) -> File) {
        val assets = assets(sticker.id)
        sticker.assets.forEach { asset ->
            if (!assets.contains(asset.id)) {
                val copied = assets.importFile(imageFile(asset.id))
                if (copied != asset.id) throw IOException("Image ${asset.id} changed on disk")
            }
        }
        writeAtomically(stickerFile(sticker.id)) { it.write(StickerCodec.encode(sticker).toByteArray(Charsets.UTF_8)) }
    }

    /** Newest first. Unreadable stickers are skipped. */
    fun list(): List<Sticker> = stickersDir.listFiles { f -> f.isDirectory }.orEmpty()
        .filter { File(it, STICKER).isFile }
        .mapNotNull { dir ->
            try {
                load(dir.name)
            } catch (_: ManifestException) {
                null
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        .sortedByDescending { it.createdAtMillis }

    fun load(id: String): Sticker {
        val file = stickerFile(id)
        if (!file.isFile) throw IOException("Sticker $id is not saved")
        return StickerCodec.decode(file.readText())
    }

    fun rename(id: String, name: String): Sticker {
        val renamed = load(id).copy(name = name)
        writeAtomically(stickerFile(id)) { it.write(StickerCodec.encode(renamed).toByteArray(Charsets.UTF_8)) }
        return renamed
    }

    fun delete(id: String) {
        // Remove the sticker file first, so an interrupted delete leaves nothing in the list.
        stickerFile(id).delete()
        val dir = stickerDir(id)
        if (dir.exists() && !dir.deleteRecursively()) throw IOException("Could not delete sticker $id")
    }

    fun assets(id: String) = AssetStore(File(stickerDir(id), "assets"))

    fun thumbnailFile(id: String) = File(stickerDir(id), THUMBNAIL)

    fun writeThumbnail(id: String, write: (java.io.OutputStream) -> Unit) {
        if (!stickerFile(id).isFile) return
        writeAtomically(thumbnailFile(id)) { write(it) }
    }

    /** Cleans leftovers from interrupted writes. Call once at startup. */
    fun deleteTempFiles() {
        stickersDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
            File(dir, "$STICKER.tmp").delete()
            File(dir, "$THUMBNAIL.tmp").delete()
            assets(dir.name).deleteTempFiles()
            // A folder with images but no sticker file is a save that never finished.
            if (!File(dir, STICKER).isFile) dir.deleteRecursively()
        }
    }

    private fun stickerDir(id: String): File {
        require(ID_PATTERN.matches(id)) { "invalid sticker id '$id'" }
        return File(stickersDir, id)
    }

    private fun stickerFile(id: String) = File(stickerDir(id), STICKER)

    private companion object {
        const val STICKER = "sticker.json"
        const val THUMBNAIL = "thumbnail.png"
        val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
    }
}
