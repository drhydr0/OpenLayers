package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DocumentTest {

    private val asset = imageAsset()
    private val doc = Document(canvas())
        .withAsset(asset)
        .withLayerInserted(0, imageLayer("a", asset))
        .withLayerInserted(1, textLayer("b"))
        .withLayerInserted(2, textLayer("c"))

    private fun ids(d: Document) = d.layers.map { it.id.value }

    @Test
    fun insertKeepsBottomFirstOrder() {
        assertEquals(listOf("a", "b", "c"), ids(doc))
        assertEquals(listOf("x", "a", "b", "c"), ids(doc.withLayerInserted(0, textLayer("x"))))
    }

    @Test
    fun lookupById() {
        assertEquals(1, doc.indexOf(LayerId("b")))
        assertEquals(-1, doc.indexOf(LayerId("zzz")))
        assertNull(doc.layer(LayerId("zzz")))
    }

    @Test
    fun removeAndReplace() {
        assertEquals(listOf("a", "c"), ids(doc.withLayerRemoved(LayerId("b"))))
        val renamed = doc.requireLayer(LayerId("b")).withName("Title")
        val replaced = doc.withLayerReplaced(renamed)
        assertEquals("Title", replaced.requireLayer(LayerId("b")).name)
        assertEquals(listOf("a", "b", "c"), ids(replaced))
    }

    @Test
    fun unchangedLayersAreShared() {
        val replaced = doc.withLayerReplaced(doc.requireLayer(LayerId("b")).withName("New"))
        assertSame(doc.layers[0], replaced.layers[0])
        assertSame(doc.layers[2], replaced.layers[2])
    }

    @Test
    fun moveUpAndDown() {
        assertEquals(listOf("b", "c", "a"), ids(doc.withLayerMoved(LayerId("a"), 2)))
        assertEquals(listOf("c", "a", "b"), ids(doc.withLayerMoved(LayerId("c"), 0)))
        assertEquals(listOf("a", "b", "c"), ids(doc.withLayerMoved(LayerId("b"), 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdsRejected() {
        doc.withLayerInserted(0, textLayer("b"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun imageLayerNeedsItsAsset() {
        Document(canvas()).withLayerInserted(0, imageLayer("a", imageAsset('b')))
    }

    @Test(expected = IllegalArgumentException::class)
    fun removingMissingLayerFails() {
        doc.withLayerRemoved(LayerId("zzz"))
    }

    @Test
    fun imageLocalBoundsCenteredOnAssetSize() {
        val layer = doc.requireLayer(LayerId("a")) as ImageLayer
        assertEquals(Rect(-100.0, -50.0, 100.0, 50.0), doc.imageLocalBounds(layer))
    }

    @Test(expected = IllegalArgumentException::class)
    fun assetIdMustBeSha256Hex() {
        AssetId("not-a-hash")
    }

    @Test(expected = IllegalArgumentException::class)
    fun opacityMustBeInRange() {
        textLayer("a").copy(opacity = 1.5)
    }
}
