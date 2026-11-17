package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.CaptureScope

/** What to draw (rectangle or lasso) and what it copies, chosen before drawing. */
object AreaSelectSheet {

    data class Choice(val lasso: Boolean, val scope: CaptureScope, val includeBackground: Boolean)

    /** [selectedCount] is how many layers are selected now; layer scopes need at least one. */
    fun show(activity: Activity, selectedCount: Int, onStart: (Choice) -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_area_select, null)
        val shapes = view.findViewById<RadioGroup>(R.id.area_shape)
        val scopes = view.findViewById<RadioGroup>(R.id.area_scope)
        val background = view.findViewById<CheckBox>(R.id.area_background)
        val active = view.findViewById<RadioButton>(R.id.area_scope_active)
        val selected = view.findViewById<RadioButton>(R.id.area_scope_selected)

        // Offer only what makes sense for the current selection, and say why otherwise.
        active.isEnabled = selectedCount == 1
        selected.isEnabled = selectedCount >= 2
        if (selectedCount != 1) active.setText(R.string.area_scope_active_unavailable)
        if (selectedCount < 2) selected.setText(R.string.area_scope_selected_unavailable)
        else selected.text = activity.resources.getQuantityString(R.plurals.area_scope_selected_count, selectedCount, selectedCount)

        scopes.setOnCheckedChangeListener { _, id ->
            background.visibility = if (id == R.id.area_scope_canvas) View.VISIBLE else View.GONE
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.area_title)
            .setView(view)
            .setPositiveButton(R.string.area_start) { _, _ ->
                val scope = when (scopes.checkedRadioButtonId) {
                    R.id.area_scope_active -> CaptureScope.ACTIVE_LAYER
                    R.id.area_scope_selected -> CaptureScope.SELECTED_LAYERS
                    else -> CaptureScope.CANVAS
                }
                onStart(Choice(shapes.checkedRadioButtonId == R.id.area_shape_lasso, scope, background.isChecked))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
