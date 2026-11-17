package io.github.drhydr0.layers.render

import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.Color
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.Sticker
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.document.Rect
import java.io.File
import java.io.OutputStream
import kotlin.math.ceil

/**
 * Draws a saved sticker's preview through the shared renderer, on a transparent canvas
 * just large enough to hold it. Use from a background thread; it owns its text layouts.
 */
object StickerThumbnails {

    fun writePng(sticker: Sticker, fileFor: (AssetId) -> File, out: OutputStream) {
        val textLayouts = TextLayouts(maxEntries = 64)
        val bounds = hitTestFor(textLayouts)
        val assets = sticker.assets.associateBy { it.id }
        // Measure with the sticker alone in a document, then shift it so its box starts at the corner.
        val measuring = Document(CanvasSpec(1, 1, Color.TRANSPARENT), listOf(sticker.layer), assets)
        val box = Rect.bounding(bounds.corners(measuring, sticker.layer))
        val canvas = CanvasSpec(
            ceil(box.width).toInt().coerceIn(1, MAX_SIDE),
            ceil(box.height).toInt().coerceIn(1, MAX_SIDE),
            Color.TRANSPARENT,
        )
        val placed = sticker.layer.withTransform(sticker.layer.transform.translatedBy(Vec2(-box.left, -box.top)))
        ThumbnailRenderer(fileFor).writePng(Document(canvas, listOf(placed), assets), out)
    }

    private const val MAX_SIDE = 8192
}
