package io.github.drhydr0.layers.persistence

import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.persistence.format.ProjectInfo
import io.github.drhydr0.layers.persistence.format.ProjectManifest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class SaveQueueTest {

    private fun manifest(name: String) =
        ProjectManifest(ProjectInfo("p", name, 0, 0), Document(CanvasSpec(10, 10)))

    /** Runs submitted work only when told to, like a busy background thread. */
    private class ManualExecutor : java.util.concurrent.Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    @Test
    fun burstOfEditsWritesOnlyNewest() {
        val executor = ManualExecutor()
        val saved = mutableListOf<String>()
        val queue = SaveQueue(executor, { saved += it.info.name })
        queue.submit(1, manifest("v1"))
        queue.submit(2, manifest("v2"))
        queue.submit(3, manifest("v3"))
        executor.runAll()
        assertEquals(listOf("v3"), saved)
    }

    @Test
    fun olderVersionNeverOverwritesNewer() {
        val executor = ManualExecutor()
        val saved = mutableListOf<String>()
        val queue = SaveQueue(executor, { saved += it.info.name })
        queue.submit(5, manifest("v5"))
        executor.runAll()
        queue.submit(4, manifest("stale"))
        queue.submit(5, manifest("same"))
        executor.runAll()
        assertEquals(listOf("v5"), saved)
    }

    @Test
    fun editDuringSaveIsWrittenAfterwards() {
        val executor = ManualExecutor()
        val saved = mutableListOf<String>()
        lateinit var queue: SaveQueue
        queue = SaveQueue(executor, {
            saved += it.info.name
            // A new edit lands while this write is in progress.
            if (it.info.name == "v1") queue.submit(2, manifest("v2"))
        })
        queue.submit(1, manifest("v1"))
        executor.runAll()
        assertEquals(listOf("v1", "v2"), saved)
    }

    @Test
    fun failedSaveIsReportedAndRetriedByNextEdit() {
        val executor = ManualExecutor()
        val results = mutableListOf<Pair<Long, Boolean>>()
        var failNext = true
        val queue = SaveQueue(
            executor,
            { if (failNext) { failNext = false; throw IOException("disk full") } },
            { version, error -> results += version to (error == null) },
        )
        queue.submit(1, manifest("v1"))
        executor.runAll()
        queue.submit(2, manifest("v2"))
        executor.runAll()
        assertEquals(listOf(1L to false, 2L to true), results)
    }
}
