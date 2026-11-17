package io.github.drhydr0.layers.persistence

import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Sticker
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.persistence.format.ManifestException
import io.github.drhydr0.layers.persistence.format.StickerCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StickerStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val projects by lazy { ProjectStore(File(temp.root, "app")) }
    private val store by lazy { StickerStore(File(temp.root, "app")) }
    private val projectAssets by lazy { projects.assets("project-1") }

    private fun sticker(id: String, name: String, created: Long): Sticker {
        val assetId = projectAssets.put("photo bytes".byteInputStream())
        val asset = ImageAsset(assetId, "image/png", 10, 10)
        val layer = GroupLayer(
            LayerId("g-$id"), "Sticker",
            listOf(ImageLayer(LayerId("i-$id"), "Photo", assetId), TextLayer(LayerId("t-$id"), "Text", "Hi", boxWidth = 100.0)),
        )
        return Sticker(id, name, created, layer, listOf(asset))
    }

    @Test
    fun savedStickerLoadsFromANewStoreWithItsOwnImageCopy() {
        val s = sticker("s1", "Card", 100)
        store.save(s) { projectAssets.file(it) }
        val reopened = StickerStore(File(temp.root, "app"))
        assertEquals(s, reopened.load("s1"))
        assertTrue(reopened.assets("s1").contains(s.assets[0].id))
        // The sticker survives the project's image being deleted.
        projectAssets.file(s.assets[0].id).delete()
        assertEquals("photo bytes", reopened.assets("s1").file(s.assets[0].id).readText())
    }

    @Test
    fun listIsNewestFirstAndSkipsDamagedAndUnfinished() {
        store.save(sticker("old", "Old", 100)) { projectAssets.file(it) }
        store.save(sticker("new", "New", 300)) { projectAssets.file(it) }
        store.save(sticker("bad", "Bad", 200)) { projectAssets.file(it) }
        File(temp.root, "app/stickers/bad/sticker.json").writeText("garbage")
        // An interrupted save: images copied, sticker file never committed.
        store.assets("half").put("x".byteInputStream())
        File(temp.root, "app/stickers/half/sticker.json.tmp").writeText("{")

        assertEquals(listOf("New", "Old"), store.list().map { it.name })
        store.deleteTempFiles()
        assertFalse(File(temp.root, "app/stickers/half").exists())
        assertEquals(listOf("New", "Old"), store.list().map { it.name })
    }

    @Test
    fun interruptedRenameKeepsTheCommittedSticker() {
        val s = sticker("s1", "Card", 100)
        store.save(s) { projectAssets.file(it) }
        File(temp.root, "app/stickers/s1/sticker.json.tmp").writeText(StickerCodec.encode(s.copy(name = "Half")).take(20))
        assertEquals("Card", StickerStore(File(temp.root, "app")).load("s1").name)
        assertEquals("Renamed", store.rename("s1", "Renamed").name)
        assertEquals("Renamed", store.load("s1").name)
    }

    @Test
    fun deleteRemovesStickerAndImages() {
        store.save(sticker("s1", "Card", 100)) { projectAssets.file(it) }
        store.delete("s1")
        assertTrue(store.list().isEmpty())
        assertFalse(File(temp.root, "app/stickers/s1").exists())
    }

    @Test(expected = ManifestException::class)
    fun damagedStickerFailsClearly() {
        store.save(sticker("s1", "Card", 100)) { projectAssets.file(it) }
        File(temp.root, "app/stickers/s1/sticker.json").writeText("{ \"formatVersion\": 1 }")
        store.load("s1")
    }

    @Test(expected = ManifestException::class)
    fun newerStickerFormatIsRefused() {
        val s = sticker("s1", "Card", 100)
        StickerCodec.decode(StickerCodec.encode(s).replace("\"formatVersion\": 1", "\"formatVersion\": 2"))
    }
}
