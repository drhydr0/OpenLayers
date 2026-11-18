package io.github.drhydr0.layers.render

import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.imageAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPlannerTest {

    private val canvas = CanvasSpec(1080, 1350)

    @Test
    fun outputSizeScalesCanvas() {
        assertEquals(540 to 675, ExportPlanner.outputSize(canvas, 0.5))
        assertEquals(2160 to 2700, ExportPlanner.outputSize(canvas, 2.0))
    }

    @Test
    fun memoryCheckUsesFreePhoneMemory() {
        val bitmapBytes = 1080L * 1350 * 4
        assertTrue(ExportPlanner.fits(canvas, 1.0, bitmapBytes * 2))
        assertFalse(ExportPlanner.fits(canvas, 1.0, bitmapBytes * 2 - 1))
        // 8640 x 8640 (a 200% export of a 4320 canvas) is 299 MB: fine with 4 GB free.
        assertTrue(ExportPlanner.fits(CanvasSpec(4320, 4320), 2.0, 4L * 1024 * 1024 * 1024))
        // Over 2 GB in one image is beyond what Android can hold, whatever is free.
        assertFalse(ExportPlanner.fits(CanvasSpec(8192, 8192), 3.0, Long.MAX_VALUE))
    }

    @Test
    fun imagesDecodeOnlyAsLargeAsShown() {
        val photo = imageAsset(width = 4000, height = 3000)
        assertEquals(1000, ExportPlanner.decodeSide(photo, 0.25, 1.0))
        assertEquals(2000, ExportPlanner.decodeSide(photo, 0.25, 2.0))
        // Never beyond the original.
        assertEquals(4000, ExportPlanner.decodeSide(photo, 3.0, 2.0))
    }

    @Test
    fun largestScaleIncludesGroupsAndSkipsHidden() {
        val a = imageAsset('a')
        val b = imageAsset('b')
        val inGroup = ImageLayer(LayerId("in"), "in", a.id, Transform(scaleX = 0.5, scaleY = 0.5))
        val group = GroupLayer(LayerId("g"), "g", listOf(inGroup), Transform(scaleX = 4.0, scaleY = 4.0))
        val small = ImageLayer(LayerId("small"), "small", a.id, Transform(scaleX = 0.1, scaleY = 0.1))
        val hidden = ImageLayer(LayerId("hidden"), "hidden", b.id, visible = false)
        val doc = Document(CanvasSpec(100, 100), listOf(group, small, hidden), mapOf(a.id to a, b.id to b))
        assertEquals(mapOf(a.id to 2.0), ExportPlanner.largestScales(doc))
    }
}

class GalleryNameTest {
    @Test
    fun displayNameIsSafeAndTimestamped() {
        org.junit.Assert.assertEquals(
            "Birthday card_123.png",
            io.github.drhydr0.layers.platform.GalleryWriter.displayName("Birthday card!/../", "png", 123),
        )
        org.junit.Assert.assertEquals(
            "Layers_5.jpg",
            io.github.drhydr0.layers.platform.GalleryWriter.displayName("🎉", "jpg", 5),
        )
    }
}
