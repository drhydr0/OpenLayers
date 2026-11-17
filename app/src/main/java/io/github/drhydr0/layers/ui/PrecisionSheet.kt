package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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

        // Nudge by one canvas pixel: finer than a finger can drag.
        val nudges = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun nudge(label: Int, description: Int, target: EditText, by: Double) {
            nudges.addView(
                Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
                    text = activity.getString(label)
                    contentDescription = activity.getString(description)
                    minimumHeight = (TOUCH_DP * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        val now = target.text.toString().replace(',', '.').toDoubleOrNull() ?: return@setOnClickListener
                        target.setText(format(now + by))
                    }
                },
            )
        }
        nudge(R.string.nudge_left, R.string.nudge_left_description, x, -1.0)
        nudge(R.string.nudge_right, R.string.nudge_right_description, x, 1.0)
        nudge(R.string.nudge_up, R.string.nudge_up_description, y, -1.0)
        nudge(R.string.nudge_down, R.string.nudge_down_description, y, 1.0)
        controls.add(nudges)

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
