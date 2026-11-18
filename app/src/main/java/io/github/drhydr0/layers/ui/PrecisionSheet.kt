package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.PrecisionInput
import io.github.drhydr0.layers.document.Transform
import java.util.Locale
import kotlin.math.roundToInt

/** Typed position, size, and angle for one layer, with one-pixel nudge buttons. */
object PrecisionSheet {

    fun show(activity: Activity, transform: Transform, onDone: (Transform) -> Unit) {
        val controls = SheetControls(activity)
        fun field(label: Int, value: Double): EditText {
            controls.label(activity.getString(label))
            return EditText(activity).apply {
                setText(format(value))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                contentDescription = activity.getString(label)
                minimumHeight = (TOUCH_DP * resources.displayMetrics.density).toInt()
                setSelectAllOnFocus(true)
                controls.add(this)
            }
        }
        val x = field(R.string.precision_x, transform.position.x)
        val y = field(R.string.precision_y, transform.position.y)

        val scale = field(R.string.precision_scale, transform.scaleX * 100)
        val rotation = field(R.string.precision_rotation, transform.rotationDegrees)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.precision_title)
            .setView(controls.view)
            .setPositiveButton(R.string.text_done, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            // Set here so a value that can't be used keeps the sheet open and says which.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val result = PrecisionInput.apply(
                    transform, x.text.toString(), y.text.toString(), scale.text.toString(), rotation.text.toString(),
                )
                if (result == null) {
                    scale.error = activity.getString(
                        R.string.precision_error, PrecisionInput.MIN_SCALE_PERCENT.roundToInt(), PrecisionInput.MAX_SCALE_PERCENT.roundToInt(),
                    )
                } else {
                    dialog.dismiss()
                    onDone(result)
                }
            }
        }
        dialog.show()
    }

    /** Up to two decimals, without trailing zeros, always with a dot so it parses back. */
    private fun format(value: Double): String =
        String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

    private const val TOUCH_DP = 48
}
