package io.github.drhydr0.layers.interaction

import android.view.MotionEvent
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.render.DocumentView
import io.github.drhydr0.layers.render.Viewport
import kotlin.math.hypot

/** Canvas navigation mode: one finger pans, two fingers pan and zoom. Never edits the document. */
class NavigationGestures(private val view: DocumentView) {

    private var lastFocus: Vec2? = null
    private var lastSpan = 0.0

    fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> capture(event)
            MotionEvent.ACTION_MOVE -> move(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lastFocus = null
        }
    }

    /** Restart from the fingers now down, so adding or lifting one never jumps the view. */
    private fun capture(event: MotionEvent) {
        val lifted = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        lastFocus = focus(event, lifted)
        lastSpan = span(event, lifted)
    }

    private fun move(event: MotionEvent) {
        val previous = lastFocus ?: return
        val focus = focus(event, -1)
        var m = Viewport.pan(view.viewMatrix, focus.x - previous.x, focus.y - previous.y)
        val span = span(event, -1)
        if (event.pointerCount >= 2 && lastSpan > 0 && span > 0) {
            m = Viewport.zoom(m, focus, span / lastSpan, view.fitScale)
        }
        view.setUserView(m)
        lastFocus = focus
        lastSpan = span
    }

    private fun focus(event: MotionEvent, skip: Int): Vec2 {
        var x = 0.0
        var y = 0.0
        var n = 0
        for (i in 0 until event.pointerCount) {
            if (i == skip) continue
            x += event.getX(i)
            y += event.getY(i)
            n++
        }
        return if (n == 0) Vec2(0.0, 0.0) else Vec2(x / n, y / n)
    }

    /** Distance between the first two fingers still down, or 0 with fewer than two. */
    private fun span(event: MotionEvent, skip: Int): Double {
        val idx = (0 until event.pointerCount).filter { it != skip }
        if (idx.size < 2) return 0.0
        return hypot(
            (event.getX(idx[1]) - event.getX(idx[0])).toDouble(),
            (event.getY(idx[1]) - event.getY(idx[0])).toDouble(),
        )
    }
}
