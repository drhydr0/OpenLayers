package io.github.drhydr0.layers.capacity

import io.github.drhydr0.layers.diagnostics.StressProject
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Placement
import io.github.drhydr0.layers.document.ShapeKind
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.history.AddLayers
import io.github.drhydr0.layers.history.History
import io.github.drhydr0.layers.history.MoveLayer
import io.github.drhydr0.layers.history.ReplaceLayers
import io.github.drhydr0.layers.history.TransformLayers
import io.github.drhydr0.layers.history.UpdateLayer
import io.github.drhydr0.layers.persistence.ProjectStore
import io.github.drhydr0.layers.persistence.format.ManifestCodec
import io.github.drhydr0.layers.persistence.format.ProjectManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * The design doc's validation workload: a 300-layer mixed composition with nested
 * editable stickers and 1,000 history actions, saved and reopened. Checks correctness
 * first, then prints sizes and times so growth is visible in CI logs. The limits are
 * loose guards against something going badly wrong, not performance promises: phones
 * are slower than CI machines, so real capacity is measured on the Pixel.
 */
class LargeProjectTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val canvas = StressProject.CANVAS

    private fun build(store: ProjectStore, projectId: String): Pair<Document, History> {
        val assetStore = store.assets(projectId)
        val assets = List(10) { n ->
            ImageAsset(assetStore.put("image bytes $n".byteInputStream()), "image/jpeg", 4000, 3000)
        }
        val history = StressProject.build(assets)
        return history.document to history
    }

    @Test
    fun threeHundredLayersAndAThousandActionsSaveReopenAndUndo() {
        val store = ProjectStore(temp.root)
        val created = store.create("Large", canvas, 1)
        val (document, history) = build(store, created.info.id)
        assertEquals(300, document.allLayers().count())
        assertEquals(1_000, history.snapshot().undo.size)

        val manifest = ProjectManifest(created.info, document, history.snapshot())
        val saveMs = measureTimeMillis { store.save(manifest) }
        val bytes = File(temp.root, "projects/${created.info.id}/manifest.json").length()
        lateinit var loaded: ProjectManifest
        val loadMs = measureTimeMillis { loaded = ProjectStore(temp.root).load(created.info.id) }
        assertEquals(manifest, loaded)

        // Every one of the 1,000 actions undoes on the reloaded project, back to an empty canvas.
        val reopened = History(loaded.document, loaded.history, maxSteps = 1_000)
        var undone = 0
        val undoMs = measureTimeMillis { while (reopened.undo()) undone++ }
        assertEquals(1_000, undone)
        assertTrue(reopened.document.layers.isEmpty())
        val redoMs = measureTimeMillis { while (reopened.redo()) Unit }
        assertEquals(document, reopened.document)

        // One more edit and save, as after every committed change in the editor.
        reopened.execute(UpdateLayer.rename(reopened.document, reopened.document.layers.first().id, "Again"))
        val resaveMs = measureTimeMillis { store.save(ProjectManifest(created.info, reopened.document, reopened.snapshot())) }

        println(
            "CAPACITY 300 layers, 1000 actions: manifest ${bytes / 1024} KB, save $saveMs ms, load $loadMs ms, " +
                "undo all $undoMs ms, redo all $redoMs ms, save after one edit $resaveMs ms",
        )
        assertTrue("manifest unexpectedly large: $bytes bytes", bytes < MAX_MANIFEST_BYTES)
        assertTrue("save too slow: $saveMs ms", saveMs < MAX_MILLIS)
        assertTrue("load too slow: $loadMs ms", loadMs < MAX_MILLIS)
        assertTrue("undo too slow: $undoMs ms", undoMs < MAX_MILLIS)
    }

    @Test
    fun historyBeyondTheCapDropsOldestStepsAndStillRoundTrips() {
        val history = History(Document(canvas), maxSteps = 100)
        repeat(250) { i ->
            history.execute(AddLayers.onTop(history.document, TextLayer(LayerId("t$i"), "Text $i", "Label $i", boxWidth = 300.0)))
        }
        assertEquals(100, history.snapshot().undo.size)
        val manifest = ProjectManifest(
            io.github.drhydr0.layers.persistence.format.ProjectInfo("p", "Capped", 0, 0), history.document, history.snapshot(),
        )
        val reloaded = ManifestCodec.decode(ManifestCodec.encode(manifest))
        val reopened = History(reloaded.document, reloaded.history, maxSteps = 100)
        var undone = 0
        while (reopened.undo()) undone++
        // The newest 100 steps undo; the 150 oldest layers stay, because their steps were dropped.
        assertEquals(100, undone)
        assertEquals(150, reopened.document.layers.size)
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 20L * 1024 * 1024
        const val MAX_MILLIS = 15_000L
    }
}
