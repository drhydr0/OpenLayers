package io.github.drhydr0.layers.ui

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.diagnostics.AppLog

/**
 * The tour's card in the editor: "Step n of 13", one instruction, and Back, Next or Skip.
 * A step moves on by itself once the user does what it asks.
 */
class TutorialCard(private val activity: Activity, private val progress: TutorialProgress) {

    private val tutorial = Tutorial(progress.step)
    private val card: View = activity.findViewById(R.id.editor_tutorial)
    private val count: TextView = activity.findViewById(R.id.editor_tutorial_count)
    private val text: TextView = activity.findViewById(R.id.editor_tutorial_text)
    private val back: Button = activity.findViewById(R.id.editor_tutorial_back)
    private val next: Button = activity.findViewById(R.id.editor_tutorial_next)

    /** Set while the "Done" message shows, so one action can't skip two steps. */
    private var advancing = false
    private var ended = false

    init {
        check(STEP_TEXTS.size == tutorial.total) { "every tutorial step needs a text" }
        back.setOnClickListener { if (!advancing && tutorial.back()) show() }
        next.setOnClickListener { if (!advancing) advance() }
        activity.findViewById<Button>(R.id.editor_tutorial_close).setOnClickListener { end(finished = false) }
        card.visibility = View.VISIBLE
        show()
    }

    /** Called by the editor whenever the user does something a step may be waiting for. */
    fun report(action: TutorialAction) {
        if (ended || advancing || !tutorial.completes(action)) return
        advancing = true
        text.setText(R.string.tutorial_step_done)
        card.postDelayed({
            advancing = false
            advance()
        }, DONE_DELAY_MS)
    }

    private fun advance() {
        if (!tutorial.next()) {
            end(finished = true)
            return
        }
        show()
    }

    private fun show() {
        progress.saveStep(tutorial.step)
        count.text = activity.getString(R.string.tutorial_step_count, tutorial.step + 1, tutorial.total)
        text.setText(STEP_TEXTS[tutorial.step])
        back.visibility = if (tutorial.step == 0) View.INVISIBLE else View.VISIBLE
        next.setText(
            when {
                tutorial.isLast -> R.string.tutorial_finish
                tutorial.goals.isEmpty() -> R.string.tutorial_next
                else -> R.string.tutorial_skip
            },
        )
    }

    private fun end(finished: Boolean) {
        AppLog.i(TAG, if (finished) "Tutorial finished" else "Tutorial ended at step ${tutorial.step + 1}")
        ended = true
        progress.end()
        card.visibility = View.GONE
        if (finished) Toast.makeText(activity, R.string.tutorial_finished, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG = "Tutorial"
        const val DONE_DELAY_MS = 1200L
        val STEP_TEXTS = listOf(
            R.string.tutorial_step_1, R.string.tutorial_step_2, R.string.tutorial_step_3,
            R.string.tutorial_step_4, R.string.tutorial_step_5, R.string.tutorial_step_6,
            R.string.tutorial_step_7, R.string.tutorial_step_8, R.string.tutorial_step_9,
            R.string.tutorial_step_10, R.string.tutorial_step_11, R.string.tutorial_step_12,
            R.string.tutorial_step_13,
        )
    }
}
