package io.github.drhydr0.layers.render

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.document.AssetId
import java.io.File
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes images for the on-screen preview at a bounded size, off the main thread, and
 * keeps recent ones in a memory-bounded cache. Export decodes separately at full size.
 *
 * Main-thread API: [bitmap] returns what's ready and starts decoding what isn't;
 * [onLoaded] runs on the main thread when a decode finishes.
 */
class PreviewImages(
    private val fileFor: (AssetId) -> File,
    private val decodeExecutor: Executor,
    private val onLoaded: () -> Unit,
    private val maxSide: Int = DEFAULT_MAX_SIDE,
) : ImageSource {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = mutableSetOf<AssetId>()
    private val failed = mutableSetOf<AssetId>()

    // A quarter of the app's heap limit; bitmaps beyond that are evicted and re-decoded on demand.
    private val cache = object : LruCache<AssetId, Bitmap>((Runtime.getRuntime().maxMemory() / 4).toInt()) {
        override fun sizeOf(key: AssetId, value: Bitmap) = value.allocationByteCount
    }

    override fun bitmap(id: AssetId): Bitmap? {
        cache.get(id)?.let { return it }
        if (id !in pending && id !in failed) startDecode(id)
        return null
    }

    fun clear() = cache.evictAll()

    private fun startDecode(id: AssetId) {
        pending += id
        decodeExecutor.execute {
            val bitmap = try {
                decodeBounded(fileFor(id), maxSide)
            } catch (e: Exception) {
                AppLog.e(TAG, "Preview decode failed for $id", e)
                null
            }
            mainHandler.post {
                pending -= id
                if (bitmap != null) cache.put(id, bitmap) else failed += id
                onLoaded()
            }
        }
    }

    companion object {
        private const val TAG = "PreviewImages"

        /** Sharp on a phone screen even when zoomed in somewhat, at 16 MB per image at most. */
        const val DEFAULT_MAX_SIDE = 2048

        /**
         * Decodes with EXIF orientation applied, scaled down so neither side exceeds [maxSide].
         * [software] is needed when drawing into a Bitmap (thumbnails, export) rather than the screen.
         */
        fun decodeBounded(file: File, maxSide: Int, software: Boolean = false): Bitmap =
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val scale = maxSide.toDouble() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
    }
}
