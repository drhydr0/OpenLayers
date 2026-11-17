package io.github.drhydr0.layers.render

import android.graphics.Bitmap
import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.Document
import java.io.File

/** Decodes a document's images for drawing into a bitmap (export, capture). */
internal object OutputImages {

    /**
     * Each image once, at the largest size any visible layer shows it at [scale] output
     * pixels per canvas pixel. The caller recycles the bitmaps. [check] may throw to cancel.
     */
    fun decode(doc: Document, scale: Double, fileFor: (AssetId) -> File, check: () -> Unit = {}): Map<AssetId, Bitmap> {
        val sides = ExportPlanner.largestScales(doc)
            .mapValues { (id, shown) -> ExportPlanner.decodeSide(doc.assets.getValue(id), shown, scale) }
        val decoded = mutableMapOf<AssetId, Bitmap>()
        try {
            sides.forEach { (id, side) ->
                check()
                decoded[id] = PreviewImages.decodeBounded(fileFor(id), side, software = true)
            }
        } catch (e: Throwable) {
            decoded.values.forEach(Bitmap::recycle)
            throw e
        }
        return decoded
    }
}
