package io.github.drhydr0.layers.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTest {

    @Test
    fun hasThirteenStepsAndEveryActionIsTaught() {
        val tutorial = Tutorial()
        assertEquals(13, tutorial.total)
        assertEquals(TutorialAction.entries.toSet(), Tutorial.STEPS.flatten().toSet())
    }

    @Test
    fun firstStepIsReadOnlyAndOnlyTheCurrentGoalCompletes() {
        val tutorial = Tutorial()
        assertTrue(tutorial.goals.isEmpty())
        assertFalse(tutorial.completes(TutorialAction.IMAGE_ADDED))
        tutorial.next()
        assertTrue(tutorial.completes(TutorialAction.IMAGE_ADDED))
        // A later step's action done early doesn't skip ahead.
        assertFalse(tutorial.completes(TutorialAction.EXPORTED))
    }

    @Test
    fun movesWithinBoundsAndResumesFromASavedStep() {
        val tutorial = Tutorial()
        assertFalse(tutorial.back())
        repeat(12) { assertTrue(tutorial.next()) }
        assertTrue(tutorial.isLast)
        assertFalse(tutorial.next())
        assertTrue(tutorial.back())
        assertEquals(11, tutorial.step)

        assertEquals(4, Tutorial(4).step)
        assertEquals(12, Tutorial(99).step)
        assertEquals(0, Tutorial(-3).step)
    }
}
