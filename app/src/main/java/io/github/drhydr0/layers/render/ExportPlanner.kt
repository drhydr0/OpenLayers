package io.github.drhydr0.layers.render

import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.Transform
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
     * more than the original. Saves memory on images shown small.
     */
    fun decodeSide(asset: ImageAsset, transform: Transform, scale: Double): Int {
        val original = max(asset.width, asset.height)
        val needed = ceil(original * max(transform.scaleX, transform.scaleY) * scale).toInt()
        return needed.coerceIn(1, original)
    }

    private const val BYTES_PER_PIXEL = 4L
}
