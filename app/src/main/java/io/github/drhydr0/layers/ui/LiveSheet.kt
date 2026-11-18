package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.WindowManager

/**
 * Makes an edit sheet see-through and keeps it to the lower part of the screen, without
 * darkening the canvas, so changes can be watched live while adjusting.
 */
object LiveSheet {

    fun style(activity: Activity, dialog: AlertDialog) {
        val window = dialog.window ?: return
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setGravity(Gravity.BOTTOM)
        val height = (activity.resources.displayMetrics.heightPixels * HEIGHT_FRACTION).toInt()
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, height)
        window.attributes = window.attributes.apply { alpha = SHEET_ALPHA }
    }

    // About half the screen leaves the canvas above in view; slight transparency shows the rest.
    private const val HEIGHT_FRACTION = 0.5
    private const val SHEET_ALPHA = 0.88f
}
