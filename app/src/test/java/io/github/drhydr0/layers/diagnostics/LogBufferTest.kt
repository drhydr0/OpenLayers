package io.github.drhydr0.layers.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class LogBufferTest {

    @Test
    fun keepsLinesInOrder() {
        val buffer = LogBuffer(3)
        buffer.add("a")
        buffer.add("b")
        assertEquals(listOf("a", "b"), buffer.snapshot())
    }

    @Test
    fun dropsOldestLinesBeyondCapacity() {
        val buffer = LogBuffer(3)
        listOf("a", "b", "c", "d", "e").forEach(buffer::add)
        assertEquals(listOf("c", "d", "e"), buffer.snapshot())
    }

    @Test
    fun snapshotIsNotAffectedByLaterLines() {
        val buffer = LogBuffer(3)
        buffer.add("a")
        val snapshot = buffer.snapshot()
        buffer.add("b")
        assertEquals(listOf("a"), snapshot)
    }

    @Test
    fun concurrentWritersNeverExceedCapacity() {
        val buffer = LogBuffer(100)
        val threads = List(8) { t -> Thread { repeat(1_000) { buffer.add("$t-$it") } } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(100, buffer.snapshot().size)
    }
}
