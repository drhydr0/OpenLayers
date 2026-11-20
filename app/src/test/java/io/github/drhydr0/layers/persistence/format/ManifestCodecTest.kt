package io.github.drhydr0.layers.persistence.format

import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.Color
import io.github.drhydr0.layers.document.Crop
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.FontFamily
import io.github.drhydr0.layers.document.ImageAdjustments
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.ShapeKind
import io.github.drhydr0.layers.document.ShapeLayer
import io.github.drhydr0.layers.document.TextAlignment
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.TextStyle
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.history.AddLayers
import io.github.drhydr0.layers.history.History
import io.github.drhydr0.layers.history.ReplaceLayers
import io.github.drhydr0.layers.history.MoveLayer
import io.github.drhydr0.layers.history.PlacedLayer
import io.github.drhydr0.layers.history.RemoveLayers
import io.github.drhydr0.layers.history.SavedHistory
import io.github.drhydr0.layers.history.TransformLayers
import io.github.drhydr0.layers.history.UpdateLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ManifestCodecTest {

    private val assetA = ImageAsset(AssetId("a".repeat(64)), "image/jpeg", 4032, 3024)
    private val assetB = ImageAsset(AssetId("b".repeat(64)), "image/png", 512, 512)

    private val manifest = ProjectManifest(
        ProjectInfo("p-1", "Birthday card", createdAtMillis = 1_000L, modifiedAtMillis = 2_000L),
        Document(
            canvas = CanvasSpec(1080, 1920, Color.TRANSPARENT),
            assets = mapOf(assetA.id to assetA, assetB.id to assetB),
            layers = listOf(
                ImageLayer(
                    LayerId("img-1"), "Photo", assetA.id,
                    Transform(Vec2(540.0, 960.0), 0.25, 0.25, -12.5, flipX = true),
                    opacity = 0.8,
                ),
                TextLayer(
                    LayerId("txt-1"), "Title",
                    text = "Happy \"birthday\" 🎉\nSecond line",
                    style = TextStyle(96.0, Color.argb(255, 200, 30, 60), TextAlignment.START),
                    boxWidth = 800.0,
                    transform = Transform(Vec2(540.0, 300.0), rotationDegrees = 5.0),
                    visible = false,
                    locked = true,
                ),
                ImageLayer(LayerId("img-2"), "Sticker", assetB.id, Transform(Vec2(100.0, 100.0))),
            ),
        ),
    )

    @Test
    fun roundTripPreservesEverything() {
        assertEquals(manifest, ManifestCodec.decode(ManifestCodec.encode(manifest)))
    }

    @Test
    fun encodedFileIsVersioned() {
        assertTrue(ManifestCodec.encode(manifest).contains("\"formatVersion\": 6"))
    }

    @Test
    fun encodingIsStable() {
        // Same document, same bytes: avoids rewriting unchanged files and keeps diffs readable.
        assertEquals(ManifestCodec.encode(manifest), ManifestCodec.encode(manifest.copy()))
    }

    /** A file in the v1 format exactly as written today. Must keep loading forever. */
    @Test
    fun readsVersion1SampleFile() {
        val v1 = """
            {
              "formatVersion": 1,
              "project": { "id": "p-9", "name": "Old", "createdAtMillis": 5, "modifiedAtMillis": 6 },
              "canvas": { "width": 800, "height": 600, "background": "#FFFFFFFF" },
              "assets": [ { "id": "${"c".repeat(64)}", "mimeType": "image/png", "width": 10, "height": 20 } ],
              "layers": [
                { "type": "image", "id": "i", "name": "Pic", "assetId": "${"c".repeat(64)}",
                  "transform": { "x": 1.0, "y": 2.0, "scaleX": 3.0, "scaleY": 3.0, "rotationDegrees": 90.0,
                                 "flipX": false, "flipY": true },
                  "opacity": 0.5, "visible": true, "locked": false },
                { "type": "text", "id": "t", "name": "Words", "text": "Hi",
                  "transform": { "x": 4.0, "y": 5.0 },
                  "fontSize": 30.0, "color": "#FF000000", "alignment": "end", "boxWidth": 200.0 }
              ]
            }
        """.trimIndent()
        val read = ManifestCodec.decode(v1)
        assertEquals(ProjectInfo("p-9", "Old", 5, 6), read.info)
        assertEquals(CanvasSpec(800, 600, Color.WHITE), read.document.canvas)
        val image = read.document.requireLayer(LayerId("i")) as ImageLayer
        assertEquals(Transform(Vec2(1.0, 2.0), 3.0, 3.0, 90.0, flipY = true), image.transform)
        assertEquals(0.5, image.opacity, 0.0)
        val text = read.document.requireLayer(LayerId("t")) as TextLayer
        // Omitted optional fields take their defaults.
        assertEquals(Transform(Vec2(4.0, 5.0)), text.transform)
        assertEquals(TextStyle(30.0, Color.BLACK, TextAlignment.END), text.style)
        assertTrue(text.visible)
        // Version 1 had no saved history.
        assertEquals(SavedHistory.EMPTY, read.history)
    }

    @Test
    fun roundTripPreservesHistory() {
        val doc = manifest.document
        val text = doc.requireLayer(LayerId("txt-1")) as TextLayer
        val moved = Transform(Vec2(1.5, -2.5), 2.0, 3.0, 45.0, flipY = true)
        val history = SavedHistory(
            undo = listOf(
                AddLayers(listOf(PlacedLayer(0, doc.layers[0]), PlacedLayer(2, doc.layers[2])), listOf(assetA, assetB)),
                TransformLayers.of(doc, mapOf(LayerId("img-1") to moved, LayerId("img-2") to moved), "Move"),
                UpdateLayer.editText(doc, text.copy(text = "New words")),
                MoveLayer.of(doc, LayerId("img-1"), 2),
            ),
            redo = listOf(
                RemoveLayers.of(doc, listOf(LayerId("txt-1"), LayerId("img-2"))),
                UpdateLayer.rename(doc, LayerId("img-1"), "Renamed"),
            ),
        )
        val withHistory = manifest.copy(history = history)
        val read = ManifestCodec.decode(ManifestCodec.encode(withHistory))
        assertEquals(withHistory, read)
        // Restored commands still work: undo the last step and redo the next one.
        assertEquals("Reorder layer", read.history.undo.last().label)
        assertEquals("Move 2 layers", read.history.undo[1].label)
    }

    @Test
    fun roundTripPreservesGroupsAndGroupCommands() {
        val base = manifest.document
        val inner = GroupLayer(LayerId("inner"), "Inner", listOf(base.layers[2]), Transform(Vec2(5.0, 6.0), 2.0, 2.0, 15.0), opacity = 0.5)
        val outer = GroupLayer(LayerId("outer"), "Outer", listOf(base.layers[1], inner), locked = true)
        val doc = base.copy(layers = listOf(base.layers[0], outer))
        var n = 0
        val history = SavedHistory(
            undo = listOf(
                ReplaceLayers.group(doc, listOf(LayerId("img-1")), LayerId("g2"), "Group", Vec2(1.0, 2.0)),
                ReplaceLayers.ungroup(doc, LayerId("inner")),
                AddLayers.duplicates(doc, listOf(LayerId("inner")), Vec2(20.0, 20.0)) { LayerId("copy${n++}") },
                RemoveLayers.of(doc, listOf(LayerId("img-2"))),
            ),
        )
        val withGroups = manifest.copy(document = doc, history = history)
        val read = ManifestCodec.decode(ManifestCodec.encode(withGroups))
        assertEquals(withGroups, read)
        assertEquals("Duplicate layer", read.history.undo[2].label)
        assertEquals(LayerId("inner"), (read.history.undo[3] as RemoveLayers).removed.single().parentId)
    }

    /** A file in the version 2 format (history, no groups) as written before groups existed. */
    @Test
    fun readsVersion2SampleFile() {
        val v2 = """
            {
              "formatVersion": 2,
              "project": { "id": "p-2", "name": "Two", "createdAtMillis": 1, "modifiedAtMillis": 2 },
              "canvas": { "width": 100, "height": 100, "background": "#FFFFFFFF" },
              "layers": [
                { "type": "text", "id": "t", "name": "Words", "text": "Hi", "transform": { "x": 4.0, "y": 5.0 },
                  "fontSize": 30.0, "color": "#FF000000", "alignment": "center", "boxWidth": 200.0 }
              ],
              "history": {
                "undo": [
                  { "type": "add", "placements": [ { "index": 0, "layer":
                    { "type": "text", "id": "t", "name": "Words", "text": "Hi", "transform": { "x": 0.0, "y": 0.0 },
                      "fontSize": 30.0, "color": "#FF000000", "alignment": "center", "boxWidth": 200.0 } } ] },
                  { "type": "transform", "label": "Move layer", "changes": [
                    { "id": "t", "before": { "x": 0.0, "y": 0.0 }, "after": { "x": 4.0, "y": 5.0 } } ] }
                ],
                "redo": []
              }
            }
        """.trimIndent()
        val read = ManifestCodec.decode(v2)
        assertEquals(listOf("Add layer", "Move layer"), read.history.undo.map { it.label })
        // Old placements have no parent: they belong to the top level.
        assertEquals(null, (read.history.undo[0] as AddLayers).placements.single().parentId)
        val history = History(read.document, read.history)
        assertTrue(history.undo() && history.undo())
        assertTrue(history.document.layers.isEmpty())
    }

    @Test
    fun roundTripPreservesShapesOfEveryKind() {
        val shapes = ShapeKind.entries.mapIndexed { i, kind ->
            ShapeLayer(
                LayerId("shape$i"), "Shape $i", kind, 120.5 + i, 60.0,
                fill = Color.argb(128, 10, 20, 30), stroke = Color.argb(255, 200, 100, 0),
                strokeWidth = 4.5, cornerRadius = if (kind == ShapeKind.ROUNDED_RECTANGLE) 12.0 else 0.0,
                transform = Transform(Vec2(10.0 * i, 5.0), 2.0, 2.0, 30.0), opacity = 0.75,
            )
        }
        val doc = manifest.document.copy(layers = manifest.document.layers + shapes)
        val edited = shapes[2].copy(width = 300.0, fill = Color.TRANSPARENT)
        val withShapes = manifest.copy(
            document = doc,
            history = SavedHistory(undo = listOf(UpdateLayer.of(doc, edited, "Change shape"))),
        )
        assertEquals(withShapes, ManifestCodec.decode(ManifestCodec.encode(withShapes)))
        assertFails("inconsistent", ManifestCodec.encode(withShapes).replace("\"ellipse\"", "\"blob\""))
    }

    @Test
    fun roundTripPreservesTextStylingAndImageAdjustments() {
        val styled = TextLayer(
            LayerId("styled"), "Styled", "Fancy",
            TextStyle(
                80.0, Color.WHITE, TextAlignment.END, FontFamily.CURSIVE, bold = true, italic = true,
                lineSpacing = 1.4, letterSpacing = 0.15, outlineColor = Color.BLACK, outlineWidth = 0.1,
                backgroundColor = Color.argb(200, 30, 60, 90), shadow = true,
            ),
            boxWidth = 600.0,
        )
        val adjusted = ImageLayer(
            LayerId("adjusted"), "Adjusted", assetA.id,
            adjustments = ImageAdjustments(0.25, -0.5, 1.0), crop = Crop(0.1, 0.2, 0.9, 0.75),
        )
        val doc = manifest.document.copy(layers = manifest.document.layers + styled + adjusted)
        val withStyles = manifest.copy(document = doc)
        assertEquals(withStyles, ManifestCodec.decode(ManifestCodec.encode(withStyles)))
        assertFails("inconsistent", ManifestCodec.encode(withStyles).replace("\"cursive\"", "\"wingdings\""))
        assertFails("inconsistent", ManifestCodec.encode(withStyles).replace("\"cropRight\": 0.9", "\"cropRight\": 1.5"))
    }

    @Test
    fun journalWithUndosAndJumpsRoundTrips() {
        val start = manifest.document
        val history = History(start)
        history.execute(UpdateLayer.rename(start, LayerId("img-1"), "One"))
        history.execute(TransformLayers.of(history.document, mapOf(LayerId("img-2") to Transform(Vec2(9.0, 9.0))), "Move"))
        history.undo()
        history.redo()
        history.goTo(1)
        val saved = manifest.copy(document = history.document, history = history.snapshot())
        val read = ManifestCodec.decode(ManifestCodec.encode(saved))
        assertEquals(saved, read)
        assertEquals("Go back to: Rename layer", read.history.journal.last().label)
        // The reloaded journal still reaches the very start.
        val reopened = History(read.document, read.history)
        reopened.goTo(0)
        assertEquals(start, reopened.document)
    }

    @Test
    fun damagedHistoryFailsClearly() {
        val encoded = ManifestCodec.encode(manifest)
        val broken = encoded.replace("\"undo\": []", "\"undo\": [ { \"type\": \"teleport\" } ]")
        assertTrue("test must change the file", broken != encoded)
        assertFails("damaged", broken)
    }

    @Test
    fun ignoresUnknownFields() {
        val withExtra = ManifestCodec.encode(manifest).replaceFirst("{", "{ \"futureThing\": [1, 2],")
        assertEquals(manifest, ManifestCodec.decode(withExtra))
    }

    @Test
    fun rejectsNewerFormat() {
        val newer = ManifestCodec.encode(manifest).replace("\"formatVersion\": 6", "\"formatVersion\": 7")
        assertFails("newer version", newer)
    }

    @Test
    fun rejectsBrokenFiles() {
        assertFails("not valid JSON", "{ truncated")
        assertFails("no format version", "{}")
        assertFails("damaged", """{ "formatVersion": 6 }""")
        // A layer pointing at an asset that isn't listed.
        val missingAsset = ManifestCodec.encode(manifest).replace("\"assetId\": \"${"b".repeat(64)}\"", "\"assetId\": \"${"d".repeat(64)}\"")
        assertFails("inconsistent", missingAsset)
        assertFails("inconsistent", ManifestCodec.encode(manifest).replace("\"start\"", "\"sideways\""))
    }

    @Test
    fun colorsUseReadableHex() {
        assertEquals("#80123456", ManifestCodec.colorToHex(Color.argb(0x80, 0x12, 0x34, 0x56)))
        assertEquals(Color.argb(0x80, 0x12, 0x34, 0x56), ManifestCodec.colorFromHex("#80123456"))
        assertEquals("#00000000", ManifestCodec.colorToHex(Color.TRANSPARENT))
    }

    private fun assertFails(messagePart: String, text: String) {
        try {
            ManifestCodec.decode(text)
            fail("expected ManifestException containing '$messagePart'")
        } catch (e: ManifestException) {
            assertTrue("'${e.message}' should contain '$messagePart'", e.message.orEmpty().contains(messagePart))
        }
    }
}
