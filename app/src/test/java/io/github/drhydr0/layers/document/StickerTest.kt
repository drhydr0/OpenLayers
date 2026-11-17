package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerTest {

    private val asset = imageAsset()
    private val photo = imageLayer("photo", asset, Transform(Vec2(300.0, 400.0), 0.5, 0.5, 20.0))
    private val caption = textLayer("caption", "Hello").withTransform(Transform(Vec2(300.0, 600.0)))
    private var n = 0
    private val newId = { LayerId("new${n++}") }

    @Test
    fun severalLayersBecomeAGroupCenteredOnTheOriginWithNewIds() {
        val content = Stickers.contentOf(listOf(photo, caption), Vec2(300.0, 500.0), "Sticker", newId) as GroupLayer
        assertEquals(Transform.IDENTITY, content.transform)
        assertVec(Vec2(0.0, -100.0), content.children[0].transform.position)
        assertVec(Vec2(0.0, 100.0), content.children[1].transform.position)
        // Scale, rotation, and text survive; no id is reused.
        assertEquals(20.0, content.children[0].transform.rotationDegrees, EPS)
        assertEquals("Hello", (content.children[1] as TextLayer).text)
        val ids = content.selfAndDescendants().map { it.id.value }.toSet()
        assertTrue(ids.none { it in setOf("photo", "caption") })
        assertEquals(3, ids.size)
    }

    @Test
    fun singleLayerStaysALayer() {
        val content = Stickers.contentOf(listOf(photo), Vec2(300.0, 400.0), "Sticker", newId)
        assertTrue(content is ImageLayer)
        assertVec(Vec2.ZERO, content.transform.position)
        assertEquals(0.5, content.transform.scaleX, EPS)
    }

    @Test
    fun stickerListsExactlyTheImagesItUses() {
        val other = imageAsset('b')
        val content = Stickers.contentOf(listOf(photo, caption), Vec2.ZERO, "Sticker", newId)
        val sticker = Stickers.create("s1", "Card", 5, content, mapOf(asset.id to asset, other.id to other))
        assertEquals(listOf(asset), sticker.assets)
    }

    @Test(expected = IllegalArgumentException::class)
    fun stickerMustCarryItsImages() {
        Sticker("s", "Bad", 0, photo, emptyList())
    }

    @Test
    fun everyInsertIsIndependent() {
        val content = Stickers.contentOf(listOf(photo, caption), Vec2(300.0, 500.0), "Sticker", newId)
        val sticker = Stickers.create("s1", "Card", 5, content, mapOf(asset.id to asset))
        val first = Stickers.instantiate(sticker, Vec2(540.0, 540.0), newId) as GroupLayer
        val second = Stickers.instantiate(sticker, Vec2(100.0, 100.0), newId) as GroupLayer
        assertVec(Vec2(540.0, 540.0), first.transform.position)
        val firstIds = first.selfAndDescendants().map { it.id }.toSet()
        val secondIds = second.selfAndDescendants().map { it.id }.toSet()
        assertTrue(firstIds.intersect(secondIds).isEmpty())
        // Both fit in one document, and editing one leaves the other and the saved sticker alone.
        val doc = Document(canvas(), listOf(first, second), mapOf(asset.id to asset))
        val text = first.children[1] as TextLayer
        val edited = doc.withLayerReplaced(text.copy(text = "Changed"))
        assertEquals("Hello", (second.children[1] as TextLayer).text)
        assertEquals("Hello", ((sticker.layer as GroupLayer).children[1] as TextLayer).text)
        assertNotEquals(doc, edited)
    }
}
