package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.platform.FreeMemory
import io.github.drhydr0.layers.render.ExportFormat
import io.github.drhydr0.layers.render.ExportOptions
import io.github.drhydr0.layers.render.ExportPlanner

/** Export choices: format, size, JPEG quality, PNG transparency; then save or share. */
object ExportSheet {

    enum class Action { SAVE, SHARE }

    fun show(activity: Activity, canvas: CanvasSpec, onExport: (ExportOptions, Action) -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_export, null)
        val formats = view.findViewById<RadioGroup>(R.id.export_format)
        val sizes = view.findViewById<RadioGroup>(R.id.export_size)
        val qualityRow = view.findViewById<View>(R.id.export_quality_row)
        val qualityLabel = view.findViewById<TextView>(R.id.export_quality_label)
        val quality = view.findViewById<SeekBar>(R.id.export_quality)
        val transparent = view.findViewById<CheckBox>(R.id.export_transparent)

        val free = FreeMemory.bytes(activity)
        val sizeButtons = ExportPlanner.SCALES.map { scale ->
            val (w, h) = ExportPlanner.outputSize(canvas, scale)
            RadioButton(activity).apply {
                id = View.generateViewId()
                minHeight = (MIN_TOUCH_DP * activity.resources.displayMetrics.density).toInt()
                text = activity.getString(R.string.export_size_option, (scale * 100).toInt(), w, h)
                // Sizes this phone can't hold in memory are shown but disabled, with the reason.
                if (!ExportPlanner.fits(canvas, scale, free)) {
                    isEnabled = false
                    text = activity.getString(R.string.export_size_too_large, text)
                }
                tag = scale
                sizes.addView(this)
            }
        }
        (sizeButtons.firstOrNull { it.tag == 1.0 && it.isEnabled } ?: sizeButtons.firstOrNull { it.isEnabled })
            ?.let { sizes.check(it.id) }

        quality.max = MAX_QUALITY - MIN_QUALITY
        quality.progress = DEFAULT_QUALITY - MIN_QUALITY
        fun updateQuality() {
            qualityLabel.text = activity.getString(R.string.export_quality_label, MIN_QUALITY + quality.progress)
        }
        updateQuality()
        quality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) = updateQuality()
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        transparent.isChecked = canvas.background.alpha < 255
        fun updateFormat() {
            val jpeg = formats.checkedRadioButtonId == R.id.export_jpeg
            qualityRow.visibility = if (jpeg) View.VISIBLE else View.GONE
            transparent.visibility = if (jpeg) View.GONE else View.VISIBLE
        }
        formats.setOnCheckedChangeListener { _, _ -> updateFormat() }
        updateFormat()

        fun options(): ExportOptions? {
            val scale = sizes.findViewById<RadioButton>(sizes.checkedRadioButtonId)?.tag as? Double ?: return null
            val format = if (formats.checkedRadioButtonId == R.id.export_jpeg) ExportFormat.JPEG else ExportFormat.PNG
            return ExportOptions(format, scale, MIN_QUALITY + quality.progress, transparent.isChecked)
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.export_title)
            .setView(view)
            .setPositiveButton(R.string.export_save) { _, _ -> options()?.let { onExport(it, Action.SAVE) } }
            .setNeutralButton(R.string.export_share) { _, _ -> options()?.let { onExport(it, Action.SHARE) } }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private const val MIN_QUALITY = 50
    private const val MAX_QUALITY = 100
    private const val DEFAULT_QUALITY = 92
    private const val MIN_TOUCH_DP = 48
}
