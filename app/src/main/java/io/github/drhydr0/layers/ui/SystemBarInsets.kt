package io.github.drhydr0.layers.ui

import android.view.View
import android.view.WindowInsets

/**
 * targetSdk 35+ draws edge to edge, so content must be padded away from the system bars.
 * Keeps the view's original padding and adds the insets on top.
 */
fun View.padForSystemBars() {
    val left = paddingLeft
    val top = paddingTop
    val right = paddingRight
    val bottom = paddingBottom
    setOnApplyWindowInsetsListener { view, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
        insets
    }
}
