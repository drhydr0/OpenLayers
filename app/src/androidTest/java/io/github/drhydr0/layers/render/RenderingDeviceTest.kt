package io.github.drhydr0.layers.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.CapturePlanner
import io.github.drhydr0.layers.document.CaptureScope
import io.github.drhydr0.layers.document.Color
import io.github.drhydr0.layers.document.Crop
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageAdjustments
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.LassoSelection
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.RectSelection
import io.github.drhydr0.layers.document.Rect
import io.github.drhydr0.layers.document.ShapeKind
import io.github.drhydr0.layers.document.ShapeLayer
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.TextStyle
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.persistence.AssetStore
import io.github.drhydr0.layers.persistence.ProjectStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

/**
 * ON-DEVICE TEST: runs on an emulator in CI, not in the cloud session. Checks real
 * pixels from the shared renderer: that export and area capture match the editor's
 * drawing, and that transparency, shapes, crop, adjustments, and groups draw as stored.
 */
@RunWith(AndroidJUnit4::class)
class RenderingDeviceTest {

    private lateinit var assets: AssetStore
    private lateinit var photo: ImageAsset

    private val green = Color.argb(255, 0, 200, 0)
    private val square = ShapeLayer(
        LayerId("square"), "Square", ShapeKind.RECTANGLE, 100.0, 100.0, fill = green, strokeWidth = 0.0,
        transform = Transform(Vec2(100.0, 100.0)),
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "render-test-${System.nanoTime()}")
        assets = ProjectStore(root).assets("p")
        // A 40 x 20 picture: left half pure red, right half pure blue.
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        for (x in 0 until 40) for (y in 0 until 20) bitmap.setPixel(x, y, if (x < 20) RED else BLUE)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        photo = ImageAsset(assets.put(bytes.inputStream()), "image/png", 40, 20)
    }

    private fun document(vararg layers: Layer, background: Color = Color.WHITE) =
        Document(CanvasSpec(200, 200, background), layers.toList(), mapOf(photo.id to photo))

    private fun file(id: AssetId) = assets.file(id)

    /** Draws the way the editor does: the shared renderer straight onto a bitmap. */
    private fun preview(doc: Document): Bitmap {
        val bitmap = Bitmap.createBitmap(doc.canvas.width, doc.canvas.height, Bitmap.Config.ARGB_8888)
        val cache = mutableMapOf<AssetId, Bitmap>()
        val images = ImageSource { id -> cache.getOrPut(id) { PreviewImages.decodeBounded(file(id), 2048, software = true) } }
        DocumentRenderer(TextLayouts()).draw(Canvas(bitmap), doc, images, clipToCanvas = true)
        return bitmap
    }

    private fun export(doc: Document, options: ExportOptions): Bitmap {
        val out = ByteArrayOutputStream()
        Exporter(::file).export(doc, options, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    private fun assertColor(expected: Int, actual: Int, tolerance: Int = 0) {
        fun channel(c: Int, shift: Int) = (c ushr shift) and 0xFF
        listOf(24, 16, 8, 0).forEach { shift ->
            assertTrue(
                "expected #${Integer.toHexString(expected)} but was #${Integer.toHexString(actual)}",
                abs(channel(expected, shift) - channel(actual, shift)) <= tolerance,
            )
        }
    }

    private val mixed
        get() = document(
            square,
            ImageLayer(LayerId("photo"), "Photo", photo.id, Transform(Vec2(60.0, 40.0), 2.0, 2.0, 15.0)),
            TextLayer(
                LayerId("text"), "Text", "Hello\nLayers",
                TextStyle(fontSize = 28.0, color = Color.BLACK, outlineColor = Color.WHITE, backgroundColor = Color.argb(255, 250, 220, 0), shadow = true),
                boxWidth = 150.0, transform = Transform(Vec2(110.0, 150.0), rotationDegrees = -10.0),
            ),
            GroupLayer(
                LayerId("group"), "Group",
                listOf(ShapeLayer(LayerId("dot"), "Dot", ShapeKind.ELLIPSE, 30.0, 30.0, fill = Color.argb(255, 200, 0, 200))),
                Transform(Vec2(170.0, 30.0)), opacity = 0.5,
            ),
        )

    @Test
    fun exportMatchesTheEditorDrawingPixelForPixel() {
        val doc = mixed
        assertTrue(preview(doc).sameAs(export(doc, ExportOptions(ExportFormat.PNG, 1.0))))
    }

    @Test
    fun exportAtDoubleSizeIsTwiceAsLargeAndKeepsPlacement() {
        val big = export(document(square), ExportOptions(ExportFormat.PNG, 2.0))
        assertEquals(400 to 400, big.width to big.height)
        assertColor(green.argb, big.getPixel(200, 200))
        assertColor(WHITE, big.getPixel(60, 60))
        assertColor(green.argb, big.getPixel(140, 140))
    }

    @Test
    fun transparentPngKeepsAlphaAndJpegIsOpaqueWhite() {
        val doc = document(square, background = Color.TRANSPARENT)
        val png = export(doc, ExportOptions(ExportFormat.PNG, 1.0))
        assertEquals(0, png.getPixel(5, 5) ushr 24)
        assertColor(green.argb, png.getPixel(100, 100))
        // A colored canvas exported with "transparent background" drops the background too.
        val cleared = export(document(square), ExportOptions(ExportFormat.PNG, 1.0, transparentBackground = true))
        assertEquals(0, cleared.getPixel(5, 5) ushr 24)
        val jpeg = export(doc, ExportOptions(ExportFormat.JPEG, 1.0, jpegQuality = 100))
        assertColor(WHITE, jpeg.getPixel(5, 5), tolerance = 6)
        assertColor(green.argb, jpeg.getPixel(100, 100), tolerance = 12)
    }

    @Test
    fun rectangleCaptureMatchesTheSameAreaOfTheExport() {
        val doc = mixed
        val shape = RectSelection(Rect(30.0, 20.0, 150.0, 170.0))
        val plan = CapturePlanner.plan(doc.canvas, shape, CaptureScope.CANVAS, Long.MAX_VALUE)!!
        val source = CapturePlanner.source(doc, CaptureScope.CANVAS, emptySet(), includeBackground = true)
        val capture = Capturer(::file).render(source, shape, plan, clipToCanvas = true)
        val exported = export(doc, ExportOptions(ExportFormat.PNG, 1.0))
        val sameArea = Bitmap.createBitmap(exported, 30, 20, 120, 150)
        assertTrue(capture.sameAs(sameArea))
        assertEquals(false, Capturer(::file).isBlank(capture))
    }

    @Test
    fun lassoCaptureIsTransparentOutsideTheOutline() {
        val doc = document(square)
        // A triangle over the square: its bounding box corners lie outside the outline.
        val shape = LassoSelection(listOf(Vec2(100.0, 60.0), Vec2(140.0, 140.0), Vec2(60.0, 140.0)))
        val plan = CapturePlanner.plan(doc.canvas, shape, CaptureScope.CANVAS, Long.MAX_VALUE)!!
        val source = CapturePlanner.source(doc, CaptureScope.CANVAS, emptySet(), includeBackground = true)
        val capture = Capturer(::file).render(source, shape, plan, clipToCanvas = true)
        assertEquals(80 to 80, capture.width to capture.height)
        assertEquals(0, capture.getPixel(2, 2) ushr 24)
        assertEquals(0, capture.getPixel(77, 2) ushr 24)
        assertColor(green.argb, capture.getPixel(40, 60))
    }

    @Test
    fun layerScopeCapturesOnlyThatLayerAndEmptyAreasAreBlank() {
        val doc = mixed
        val shape = RectSelection(Rect(60.0, 60.0, 140.0, 140.0))
        val plan = CapturePlanner.plan(doc.canvas, shape, CaptureScope.ACTIVE_LAYER, Long.MAX_VALUE)!!
        val onlySquare = CapturePlanner.source(doc, CaptureScope.ACTIVE_LAYER, setOf(LayerId("square")), true)
        val capture = Capturer(::file).render(onlySquare, shape, plan, clipToCanvas = false)
        // The text overlaps this area in the full picture, but only the square was asked for.
        for (x in listOf(5, 40, 75)) for (y in listOf(5, 40, 75)) assertColor(green.argb, capture.getPixel(x, y))
        val onlyDot = CapturePlanner.source(doc, CaptureScope.ACTIVE_LAYER, setOf(LayerId("dot")), true)
        assertTrue(Capturer(::file).isBlank(Capturer(::file).render(onlyDot, shape, plan, clipToCanvas = false)))
    }

    @Test
    fun cropShowsOnlyTheKeptPartAndFlipMirrorsIt() {
        // Keep the right (blue) half, shown at 4x: an 80 x 80 blue square centered at 100, 100.
        val blueHalf = ImageLayer(
            LayerId("p"), "P", photo.id, Transform(Vec2(100.0, 100.0), 4.0, 4.0), crop = Crop(0.5, 0.0, 1.0, 1.0),
        )
        val cropped = preview(document(blueHalf))
        assertColor(BLUE, cropped.getPixel(65, 100))
        assertColor(BLUE, cropped.getPixel(135, 100))
        assertColor(WHITE, cropped.getPixel(55, 100))
        // The whole picture flipped left to right: blue is now on the left.
        val flipped = ImageLayer(LayerId("p"), "P", photo.id, Transform(Vec2(100.0, 100.0), 4.0, 4.0, flipX = true))
        val mirrored = preview(document(flipped))
        assertColor(BLUE, mirrored.getPixel(40, 100))
        assertColor(RED, mirrored.getPixel(160, 100))
    }

    @Test
    fun adjustmentsChangePixelsWithoutTouchingTheImageFile() {
        val before = file(photo.id).readBytes()
        val gray = ImageLayer(
            LayerId("p"), "P", photo.id, Transform(Vec2(100.0, 100.0), 4.0, 4.0),
            adjustments = ImageAdjustments(saturation = -1.0),
        )
        val pixel = preview(document(gray)).getPixel(60, 100)
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        assertTrue("expected gray but was $r, $g, $b", abs(r - g) <= 1 && abs(g - b) <= 1)
        assertTrue(before.contentEquals(file(photo.id).readBytes()))
    }

    @Test
    fun hiddenLayersAndHiddenGroupsDrawNothing() {
        val hiddenSquare = square.copy(visible = false)
        val hiddenGroup = GroupLayer(LayerId("g"), "G", listOf(square.copy(id = LayerId("inner"))), visible = false)
        val bitmap = preview(document(hiddenSquare, hiddenGroup))
        assertColor(WHITE, bitmap.getPixel(100, 100))
    }

    @Test
    fun groupTransformMovesItsChildren() {
        // The square sits at 100, 100 inside a group shifted by 50, 50 and scaled to half.
        val group = GroupLayer(LayerId("g"), "G", listOf(square), Transform(Vec2(50.0, 50.0), 0.5, 0.5))
        val bitmap = preview(document(group))
        assertColor(green.argb, bitmap.getPixel(100, 100))
        assertColor(green.argb, bitmap.getPixel(80, 80))
        assertColor(WHITE, bitmap.getPixel(130, 130))
        assertColor(WHITE, bitmap.getPixel(70, 70))
    }

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val BLUE = 0xFF0000FF.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
