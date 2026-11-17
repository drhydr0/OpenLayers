package io.github.drhydr0.layers.render

import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.TextLayer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportFormat(val mimeType: String, val extension: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
}

data class ExportOptions(
    val format: ExportFormat,
    /** Output size relative to the canvas, e.g. 2.0 for double. */
    val scale: Double,
    val jpegQuality: Int = 92,
    /** PNG only: leave out the canvas background so it exports as alpha. */
    val transparentBackground: Boolean = false,
)

/** Export sizing and memory checks. Plain Kotlin so they're JVM-tested. */
object ExportPlanner {

    val SCALES = listOf(0.5, 1.0, 2.0)

    fun outputSize(canvas: CanvasSpec, scale: Double): Pair<Int, Int> =
        (canvas.width * scale).roundToInt().coerceAtLeast(1) to (canvas.height * scale).roundToInt().coerceAtLeast(1)

    /**
     * True when the output bitmap fits comfortably in [heapLimitBytes]. Half the heap is
     * left for decoded images and the encoder, so export fails early instead of crashing.
     */
    fun fits(canvas: CanvasSpec, scale: Double, heapLimitBytes: Long): Boolean {
        val (w, h) = outputSize(canvas, scale)
        return w.toLong() * h * BYTES_PER_PIXEL <= heapLimitBytes / 2
    }

    /**
     * Longest side to decode an image at: enough for its largest size in the output, never
     * more than the original. Saves memory on images shown small. [shownScale] is how
     * much the image is enlarged on the canvas, from [largestScales].
     */
    fun decodeSide(asset: ImageAsset, shownScale: Double, scale: Double): Int {
        val original = max(asset.width, asset.height)
        val needed = ceil(original * shownScale * scale).toInt()
        return needed.coerceIn(1, original)
    }

    /**
     * For each image that will be drawn, the largest scale any visible layer shows it at,
     * including the scale of every group around it.
     */
    fun largestScales(document: Document): Map<AssetId, Double> {
        val result = mutableMapOf<AssetId, Double>()
        fun visit(layers: List<Layer>, outer: Double) {
            for (layer in layers) {
                if (!layer.visible) continue
                val own = outer * max(layer.transform.scaleX, layer.transform.scaleY)
                when (layer) {
                    is ImageLayer -> result[layer.assetId] = max(result[layer.assetId] ?: 0.0, own)
                    is GroupLayer -> visit(layer.children, own)
                    is TextLayer -> Unit
                }
            }
        }
        visit(document.layers, 1.0)
        return result
    }

    private const val BYTES_PER_PIXEL = 4L
}
