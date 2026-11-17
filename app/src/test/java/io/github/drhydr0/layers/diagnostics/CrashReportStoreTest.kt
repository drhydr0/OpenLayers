package io.github.drhydr0.layers.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashReportStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun noReportUntilSaved() {
        assertNull(CrashReportStore(temp.root).read())
    }

    @Test
    fun savedReportSurvivesNewStoreInstance() {
        CrashReportStore(temp.root).save("first crash")
        // A new instance stands in for the next app launch.
        assertEquals("first crash", CrashReportStore(temp.root).read())
    }

    @Test
    fun laterCrashReplacesEarlierReport() {
        val store = CrashReportStore(temp.root)
        store.save("first")
        store.save("second")
        assertEquals("second", store.read())
    }

    @Test
    fun interruptedWriteKeepsLastCompleteReport() {
        val store = CrashReportStore(temp.root)
        store.save("complete report")
        // Simulate dying mid-write: only the temp file has the partial text.
        File(temp.root, "diagnostics/crash.txt.tmp").writeText("partial")
        assertEquals("complete report", CrashReportStore(temp.root).read())
    }

    @Test
    fun deleteRemovesReport() {
        val store = CrashReportStore(temp.root)
        store.save("report")
        store.delete()
        assertNull(store.read())
    }
}
