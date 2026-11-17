package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.Crop
import io.github.drhydr0.layers.document.ImageAdjustments
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageEdits
import io.github.drhydr0.layers.document.ImageLayer
import kotlin.math.roundToInt

/** Brightness, contrast, saturation, flip, and crop for an image layer. One undo step on Done. */
object ImageSheet {

    fun show(activity: Activity, layer: ImageLayer, asset: ImageAsset, onDone: (ImageLayer) -> Unit) {
        var adjustments = layer.adjustments
        var flipX = layer.transform.flipX
        var flipY = layer.transform.flipY
        val trims = doubleArrayOf(layer.crop.left, layer.crop.top, 1 - layer.crop.right, 1 - layer.crop.bottom)

        val controls = SheetControls(activity)
        fun percent(v: Double) = activity.getString(R.string.percent_signed, (v * 100).roundToInt())
        fun trimmed(v: Double) = activity.getString(R.string.percent_value, (v * 100).roundToInt())

        controls.slider(activity.getString(R.string.image_brightness), -1.0, 1.0, STEPS, adjustments.brightness, ::percent) {
            adjustments = adjustments.copy(brightness = it)
        }
        controls.slider(activity.getString(R.string.image_contrast), -1.0, 1.0, STEPS, adjustments.contrast, ::percent) {
            adjustments = adjustments.copy(contrast = it)
        }
        controls.slider(activity.getString(R.string.image_saturation), -1.0, 1.0, STEPS, adjustments.saturation, ::percent) {
            adjustments = adjustments.copy(saturation = it)
        }
        controls.checkBox(activity.getString(R.string.image_flip_horizontal), flipX) { flipX = it }
        controls.checkBox(activity.getString(R.string.image_flip_vertical), flipY) { flipY = it }
        controls.label(activity.getString(R.string.image_crop_label))
        listOf(R.string.image_crop_left, R.string.image_crop_top, R.string.image_crop_right, R.string.image_crop_bottom)
            .forEachIndexed { i, title ->
                controls.slider(activity.getString(title), 0.0, MAX_TRIM, TRIM_STEPS, trims[i], ::trimmed) { trims[i] = it }
            }

        fun result(): ImageLayer {
            val crop = ImageEdits.cropFromTrims(trims[0], trims[1], trims[2], trims[3])
            val recropped = ImageEdits.recrop(layer, asset, crop)
            return recropped.copy(
                adjustments = adjustments,
                transform = recropped.transform.copy(flipX = flipX, flipY = flipY),
            )
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.image_edit_title)
            .setView(controls.view)
            .setPositiveButton(R.string.text_done) { _, _ -> onDone(result()) }
            .setNeutralButton(R.string.image_reset) { _, _ ->
                // Back to the original picture: no adjustments, no crop, not flipped.
                val whole = ImageEdits.recrop(layer, asset, Crop.FULL)
                onDone(
                    whole.copy(
                        adjustments = ImageAdjustments.NONE,
                        transform = whole.transform.copy(flipX = false, flipY = false),
                    ),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private const val STEPS = 200
    private const val MAX_TRIM = 0.9
    private const val TRIM_STEPS = 90
}
