package io.github.drhydr0.layers.render

import io.github.drhydr0.layers.document.CanvasSpec
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
    fun memoryCheckLeavesHalfTheHeap() {
        val bitmapBytes = 1080L * 1350 * 4
        assertTrue(ExportPlanner.fits(canvas, 1.0, bitmapBytes * 2))
        assertFalse(ExportPlanner.fits(canvas, 1.0, bitmapBytes * 2 - 1))
        // A 256 MB heap can't take an 8192 x 8192 export (256 MB bitmap).
        assertFalse(ExportPlanner.fits(CanvasSpec(8192, 8192), 1.0, 256L * 1024 * 1024))
    }

    @Test
    fun imagesDecodeOnlyAsLargeAsShown() {
        val photo = imageAsset(width = 4000, height = 3000)
        assertEquals(1000, ExportPlanner.decodeSide(photo, Transform(scaleX = 0.25, scaleY = 0.25), 1.0))
        assertEquals(2000, ExportPlanner.decodeSide(photo, Transform(scaleX = 0.25, scaleY = 0.25), 2.0))
        // Never beyond the original.
        assertEquals(4000, ExportPlanner.decodeSide(photo, Transform(scaleX = 3.0, scaleY = 3.0), 2.0))
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
