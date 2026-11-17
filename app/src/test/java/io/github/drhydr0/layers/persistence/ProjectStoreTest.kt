package io.github.drhydr0.layers.persistence

import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.Color
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.history.AddLayers
import io.github.drhydr0.layers.history.History
import io.github.drhydr0.layers.persistence.format.ManifestCodec
import io.github.drhydr0.layers.persistence.format.ManifestException
import io.github.drhydr0.layers.persistence.format.ProjectManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ProjectStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val store by lazy { ProjectStore(temp.root) }
    private val canvas = CanvasSpec(1080, 1350, Color.WHITE)

    private fun withText(m: ProjectManifest, id: String, text: String, now: Long) = m.copy(
        info = m.info.copy(modifiedAtMillis = now),
        document = m.document.withLayerInserted(
            m.document.layers.size,
            TextLayer(LayerId(id), id, text, boxWidth = 500.0),
        ),
    )

    private fun manifestFile(id: String) = File(temp.root, "projects/$id/manifest.json")

    @Test
    fun createThenLoadFromNewStoreInstance() {
        val created = store.create("Card", canvas, nowMillis = 100)
        // A new instance stands in for the next app launch.
        assertEquals(created, ProjectStore(temp.root).load(created.info.id))
    }

    @Test
    fun saveReplacesPreviousVersion() {
        val created = store.create("Card", canvas, 100)
        val edited = withText(created, "t1", "Hello", 200)
        store.save(edited)
        assertEquals(edited, store.load(created.info.id))
    }

    @Test
    fun interruptedSaveKeepsLastCommittedVersion() {
        val created = store.create("Card", canvas, 100)
        val committed = withText(created, "t1", "Committed", 200)
        store.save(committed)
        // The app died mid-save: only the temp file holds the partial new version.
        File(manifestFile(created.info.id).path + ".tmp").writeText("{ \"formatVersion\": 1, \"proj")

        val reopened = ProjectStore(temp.root)
        assertEquals(committed, reopened.load(created.info.id))
        reopened.deleteTempFiles()
        assertFalse(File(manifestFile(created.info.id).path + ".tmp").exists())
        // Saving again after recovery works.
        val next = withText(committed, "t2", "After", 300)
        reopened.save(next)
        assertEquals(next, reopened.load(created.info.id))
    }

    @Test
    fun interruptedSaveKeepsDocumentAndHistoryInStep() {
        val created = store.create("Card", canvas, 100)
        val history = History(created.document)
        history.execute(AddLayers.onTop(history.document, TextLayer(LayerId("t1"), "t1", "One", boxWidth = 500.0)))
        val committed = created.copy(document = history.document, history = history.snapshot())
        store.save(committed)

        // A second edit dies mid-save: only the temp file has it.
        history.execute(AddLayers.onTop(history.document, TextLayer(LayerId("t2"), "t2", "Two", boxWidth = 500.0)))
        val partial = ManifestCodec.encode(created.copy(document = history.document, history = history.snapshot()))
        File(manifestFile(created.info.id).path + ".tmp").writeText(partial.take(partial.length / 2))

        val loaded = ProjectStore(temp.root).load(created.info.id)
        assertEquals(committed, loaded)
        // The recovered history matches the recovered document: one undo returns to empty.
        val reopened = History(loaded.document, loaded.history)
        assertTrue(reopened.undo())
        assertTrue(reopened.document.layers.isEmpty())
        assertFalse(reopened.canUndo)
    }

    @Test
    fun interruptedCreateIsNotListed() {
        val good = store.create("Good", canvas, 100)
        // Folder exists but the first save never committed.
        File(temp.root, "projects/half-made").mkdirs()
        File(temp.root, "projects/half-made/manifest.json.tmp").writeText("{")
        assertEquals(listOf(good.info), store.list().projects)
        assertTrue(store.list().unreadableIds.isEmpty())
    }

    @Test
    fun listIsNewestFirstAndReportsDamagedProjects() {
        val old = store.create("Old", canvas, 100)
        val new = store.create("New", canvas, 300)
        val mid = store.create("Mid", canvas, 200)
        val broken = store.create("Broken", canvas, 400)
        manifestFile(broken.info.id).writeText("garbage")

        val list = store.list()
        assertEquals(listOf("New", "Mid", "Old"), list.projects.map { it.name })
        assertEquals(listOf(broken.info.id), list.unreadableIds)
        assertEquals(setOf(old.info.id, new.info.id, mid.info.id), list.projects.map { it.id }.toSet())
    }

    @Test(expected = ManifestException::class)
    fun loadingDamagedProjectFailsClearly() {
        val p = store.create("Card", canvas, 100)
        manifestFile(p.info.id).writeText("garbage")
        store.load(p.info.id)
    }

    @Test
    fun renameUpdatesNameAndTime() {
        val p = store.create("Card", canvas, 100)
        store.rename(p.info.id, "Invitation", 500)
        val info = store.load(p.info.id).info
        assertEquals("Invitation", info.name)
        assertEquals(500L, info.modifiedAtMillis)
        assertEquals(100L, info.createdAtMillis)
    }

    @Test
    fun deleteRemovesProjectAndAssets() {
        val p = store.create("Card", canvas, 100)
        store.assets(p.info.id).put("bytes".byteInputStream())
        store.delete(p.info.id)
        assertTrue(store.list().projects.isEmpty())
        assertFalse(File(temp.root, "projects/${p.info.id}").exists())
    }

    @Test
    fun savedImageLayerNeedsItsAssetOnDisk() {
        val p = store.create("Card", canvas, 100)
        val assets = store.assets(p.info.id)
        val id = assets.put(ByteArray(1000) { it.toByte() }.inputStream())
        val withImage = p.copy(
            document = p.document
                .withAsset(ImageAsset(id, "image/png", 10, 10))
                .withLayerInserted(0, ImageLayer(LayerId("i"), "Pic", id)),
        )
        store.save(withImage)
        assertEquals(withImage, store.load(p.info.id))

        assets.file(id).delete()
        try {
            store.save(withImage)
            throw AssertionError("save should refuse a manifest whose image is missing")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("missing assets"))
        }
    }

    @Test
    fun createWithImportedAssetsUnderReservedId() {
        val id = store.newProjectId()
        // Assets are imported before the first save, as when opening a photo as a project.
        val assetId = store.assets(id).put("photo".byteInputStream())
        assertTrue(store.list().projects.isEmpty())
        val doc = io.github.drhydr0.layers.document.Document(canvas)
            .withAsset(ImageAsset(assetId, "image/jpeg", 10, 10))
            .withLayerInserted(0, ImageLayer(LayerId("i"), "Photo", assetId))
        val created = store.create(id, "Photo", doc, 100)
        assertEquals(created, store.load(id))
        assertEquals(listOf(created.info), store.list().projects)
    }

    @Test
    fun thumbnailWrittenAtomicallyAndSkippedForDeletedProject() {
        val p = store.create("Card", canvas, 100)
        store.writeThumbnail(p.info.id) { it.write(byteArrayOf(1, 2, 3)) }
        assertEquals(listOf<Byte>(1, 2, 3), store.thumbnailFile(p.info.id).readBytes().toList())
        store.delete(p.info.id)
        store.writeThumbnail(p.info.id) { it.write(byteArrayOf(9)) }
        assertFalse(File(temp.root, "projects/${p.info.id}").exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun idsCannotEscapeProjectsFolder() {
        store.load("../../etc")
    }
}
