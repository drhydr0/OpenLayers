package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LayerIdTest {

    @Test
    fun randomIdsAreDistinct() {
        val ids = List(1_000) { LayerId.random() }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun idsWithSameValueAreEqual() {
        assertEquals(LayerId("layer-1"), LayerId("layer-1"))
        assertNotEquals(LayerId("layer-1"), LayerId("layer-2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankIdIsRejected() {
        LayerId(" ")
    }
}
