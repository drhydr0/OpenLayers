package io.github.drhydr0.layers.render

import io.github.drhydr0.layers.document.Matrix
import io.github.drhydr0.layers.document.Rect
import android.graphics.Matrix as AndroidMatrix
import android.graphics.RectF

/** The one place document geometry becomes Android geometry, so conversions can't drift. */
fun Matrix.toAndroid(): AndroidMatrix = AndroidMatrix().also {
    it.setValues(
        floatArrayOf(
            a.toFloat(), c.toFloat(), tx.toFloat(),
            b.toFloat(), d.toFloat(), ty.toFloat(),
            0f, 0f, 1f,
        ),
    )
}

fun Rect.toRectF() = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
