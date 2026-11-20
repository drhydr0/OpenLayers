package io.github.drhydr0.layers.ui

/** Things the user can do that complete a tutorial step. */
enum class TutorialAction {
    IMAGE_ADDED, TRANSFORMED, NAVIGATED, TEXT_ADDED, SHAPE_ADDED, LAYER_ORGANIZED,
    GROUPED, PRECISE_MOVE, IMAGE_ADJUSTED, STICKER_USED, HISTORY_OPENED, EXPORTED,
}

/**
 * The guided tour's steps and progress. Plain Kotlin, so the step logic is tested on
 * the JVM; the screen supplies the words for each step.
 */
class Tutorial(step: Int = 0) {

    /** Zero-based index of the current step. */
    var step: Int = step.coerceIn(0, STEPS.lastIndex)
        private set

    val total get() = STEPS.size
    val isLast get() = step == STEPS.lastIndex

    /** What completes the current step; empty means the user just reads it and taps Next. */
    val goals: Set<TutorialAction> get() = STEPS[step]

    /** True when [action] completes the current step. Doing a later step early doesn't count. */
    fun completes(action: TutorialAction) = action in goals

    fun next(): Boolean {
        if (isLast) return false
        step++
        return true
    }

    fun back(): Boolean {
        if (step == 0) return false
        step--
        return true
    }

    companion object {
        /** In the order a new user meets the features; the screen has one text per entry. */
        val STEPS: List<Set<TutorialAction>> = listOf(
            emptySet(),
            setOf(TutorialAction.IMAGE_ADDED),
            setOf(TutorialAction.TRANSFORMED),
            setOf(TutorialAction.NAVIGATED),
            setOf(TutorialAction.TEXT_ADDED),
            setOf(TutorialAction.SHAPE_ADDED),
            setOf(TutorialAction.LAYER_ORGANIZED),
            setOf(TutorialAction.GROUPED),
            setOf(TutorialAction.PRECISE_MOVE),
            setOf(TutorialAction.IMAGE_ADJUSTED),
            setOf(TutorialAction.STICKER_USED),
            setOf(TutorialAction.HISTORY_OPENED),
            setOf(TutorialAction.EXPORTED),
        )
    }
}
