package io.github.drhydr0.layers.diagnostics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.persistence.AssetStore
import java.io.ByteArrayOutputStream

/** Generates photo-sized test images for the stress project. Call off the main thread. */
object StressImages {

    fun create(assets: AssetStore): List<ImageAsset> = COLORS.map { (from, to) ->
        val bitmap = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, SIDE.toFloat(), SIDE.toFloat(), from, to, Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, SIDE.toFloat(), SIDE.toFloat(), paint)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }.toByteArray()
        bitmap.recycle()
        ImageAsset(assets.put(bytes.inputStream()), "image/jpeg", SIDE, SIDE)
    }

    // 2000 px squares: each decodes to about the memory of a real photo preview.
    private const val SIDE = 2000
    private const val QUALITY = 90
    private val COLORS = listOf(
        Color.RED to Color.YELLOW, Color.BLUE to Color.CYAN, Color.GREEN to Color.WHITE,
        Color.MAGENTA to Color.BLACK, Color.DKGRAY to Color.LTGRAY, Color.rgb(255, 128, 0) to Color.rgb(64, 0, 128),
    )
}
