package io.github.drhydr0.layers.platform

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.persistence.AssetStore
import java.io.IOException

/**
 * Copies a picked image into a project's asset store and reads its size. Blocking:
 * call off the main thread. The original bytes are kept unchanged.
 */
object ImageImporter {

    fun import(resolver: ContentResolver, uri: Uri, assets: AssetStore): ImageAsset {
        val id = resolver.openInputStream(uri)?.use { assets.put(it) }
            ?: throw IOException("Could not open $uri")
        var width = 0
        var height = 0
        var mimeType = "image/*"
        // ImageDecoder reports size with EXIF orientation applied, matching how it draws.
        // A heavy subsample keeps this check cheap; the preview decodes separately.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(assets.file(id))) { decoder, info, _ ->
            width = info.size.width
            height = info.size.height
            mimeType = info.mimeType
            decoder.setTargetSampleSize(HEADER_SAMPLE_SIZE)
        }.recycle()
        if (width <= 0 || height <= 0) throw IOException("Image has no size")
        return ImageAsset(id, mimeType, width, height)
    }

    private const val HEADER_SAMPLE_SIZE = 16
}
