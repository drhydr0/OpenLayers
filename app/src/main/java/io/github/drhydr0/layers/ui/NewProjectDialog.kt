package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.CanvasSpec

/** Name, canvas size, and background for a new project. */
object NewProjectDialog {

    fun show(activity: Activity, onCreate: (name: String, canvas: CanvasSpec) -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_new_project, null)
        val name = view.findViewById<EditText>(R.id.new_project_name)
        val sizes = view.findViewById<RadioGroup>(R.id.new_project_size)
        val custom = view.findViewById<View>(R.id.new_project_custom)
        val width = view.findViewById<EditText>(R.id.new_project_width)
        val height = view.findViewById<EditText>(R.id.new_project_height)
        val backgrounds = view.findViewById<RadioGroup>(R.id.new_project_background)

        sizes.setOnCheckedChangeListener { _, checked ->
            custom.visibility = if (checked == R.id.size_custom) View.VISIBLE else View.GONE
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.new_project_title)
            .setView(view)
            .setPositiveButton(R.string.new_project_create, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            // Set here instead of in the builder, so an invalid size keeps the dialog open.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val preset = presetFor(sizes.checkedRadioButtonId)
                val presetSize = preset.width?.let { w -> preset.height?.let { h -> w to h } }
                val size = presetSize ?: CanvasSizeRules.parse(width.text.toString(), height.text.toString())
                if (size == null) {
                    width.error = activity.getString(
                        R.string.new_project_size_error, CanvasSizeRules.MIN_SIDE, CanvasSizeRules.MAX_SIDE,
                    )
                    return@setOnClickListener
                }
                val background = backgroundFor(backgrounds.checkedRadioButtonId)
                val projectName = name.text.toString().trim()
                    .ifEmpty { activity.getString(R.string.new_project_default_name) }
                dialog.dismiss()
                onCreate(projectName, CanvasSpec(size.first, size.second, background.color))
            }
        }
        dialog.show()
    }

    private fun presetFor(id: Int) = when (id) {
        R.id.size_portrait -> CanvasPreset.PORTRAIT
        R.id.size_story -> CanvasPreset.STORY
        R.id.size_landscape -> CanvasPreset.LANDSCAPE
        R.id.size_custom -> CanvasPreset.CUSTOM
        else -> CanvasPreset.SQUARE
    }

    private fun backgroundFor(id: Int) = when (id) {
        R.id.background_black -> CanvasBackground.BLACK
        R.id.background_transparent -> CanvasBackground.TRANSPARENT
        else -> CanvasBackground.WHITE
    }
}
