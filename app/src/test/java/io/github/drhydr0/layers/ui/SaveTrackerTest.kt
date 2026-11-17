package io.github.drhydr0.layers.ui

import io.github.drhydr0.layers.ui.SaveTracker.State
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveTrackerTest {

    private val tracker = SaveTracker()

    @Test
    fun freshlyOpenedProjectIsSaved() {
        assertEquals(State.SAVED, tracker.state)
    }

    @Test
    fun savedOnlyAfterNewestEditIsOnDisk() {
        tracker.onEdit(1)
        tracker.onEdit(2)
        assertEquals(State.SAVING, tracker.state)
        tracker.onSaveResult(1, success = true)
        assertEquals(State.SAVING, tracker.state)
        tracker.onSaveResult(2, success = true)
        assertEquals(State.SAVED, tracker.state)
    }

    @Test
    fun failureShowsUntilALaterSaveSucceeds() {
        tracker.onEdit(1)
        tracker.onSaveResult(1, success = false)
        assertEquals(State.FAILED, tracker.state)
        tracker.onEdit(2)
        assertEquals(State.SAVING, tracker.state)
        tracker.onSaveResult(2, success = true)
        assertEquals(State.SAVED, tracker.state)
    }

    @Test
    fun retryOfTheSameVersionClearsFailure() {
        tracker.onEdit(1)
        tracker.onSaveResult(1, success = false)
        tracker.onSaveResult(1, success = true)
        assertEquals(State.SAVED, tracker.state)
    }

    @Test
    fun lateFailureOfAnOlderVersionIsIgnored() {
        tracker.onEdit(1)
        tracker.onEdit(2)
        tracker.onSaveResult(2, success = true)
        tracker.onSaveResult(1, success = false)
        assertEquals(State.SAVED, tracker.state)
    }
}
