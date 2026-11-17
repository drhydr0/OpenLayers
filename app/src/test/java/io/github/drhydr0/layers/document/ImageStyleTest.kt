package io.github.drhydr0.layers.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageStyleTest {

    private fun apply(m: FloatArray, r: Double, g: Double, b: Double) = listOf(
        m[0] * r + m[1] * g + m[2] * b + m[4],
        m[5] * r + m[6] * g + m[7] * b + m[9],
        m[10] * r + m[11] * g + m[12] * b + m[14],
    )

    @Test
    fun noAdjustmentLeavesColorsUnchanged() {
        assertTrue(ImageAdjustments.NONE.isNone)
        val out = apply(ImageAdjustments.NONE.colorMatrix(), 200.0, 100.0, 50.0)
        assertEquals(listOf(200.0, 100.0, 50.0), out.map { Math.round(it).toDouble() })
    }

    @Test
    fun brightnessShiftsEveryChannelEqually() {
        val out = apply(ImageAdjustments(brightness = 0.2).colorMatrix(), 100.0, 100.0, 100.0)
        out.forEach { assertEquals(151.0, it, 0.01) }
    }

    @Test
    fun contrastPivotsOnMidGray() {
        val m = ImageAdjustments(contrast = 0.5).colorMatrix()
        apply(m, 127.5, 127.5, 127.5).forEach { assertEquals(127.5, it, 0.01) }
        // Lighter gets lighter, darker gets darker.
        assertTrue(apply(m, 200.0, 200.0, 200.0)[0] > 200.0)
        assertTrue(apply(m, 50.0, 50.0, 50.0)[0] < 50.0)
        // Full negative contrast flattens everything to mid gray.
        apply(ImageAdjustments(contrast = -1.0).colorMatrix(), 10.0, 240.0, 99.0).forEach { assertEquals(127.5, it, 0.01) }
    }

    @Test
    fun zeroSaturationMakesGrayAndKeepsGraysAsTheyAre() {
        val m = ImageAdjustments(saturation = -1.0).colorMatrix()
        val out = apply(m, 255.0, 0.0, 0.0)
        assertEquals(out[0], out[1], 0.01)
        assertEquals(out[1], out[2], 0.01)
        apply(ImageAdjustments(saturation = 0.7).colorMatrix(), 80.0, 80.0, 80.0).forEach { assertEquals(80.0, it, 0.01) }
    }

    @Test
    fun cropShrinksTheContentBoxAndKeepsItCentered() {
        val asset = imageAsset(width = 400, height = 200)
        val layer = imageLayer("p", asset).copy(crop = Crop(0.25, 0.0, 0.75, 0.5))
        val doc = Document(canvas(), listOf(layer), mapOf(asset.id to asset))
        assertEquals(Rect.centered(200.0, 100.0), doc.imageLocalBounds(layer))
        assertTrue(Crop.FULL.isFull)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cropMustKeepSomething() {
        Crop(0.5, 0.0, 0.505, 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun adjustmentsStayInRange() {
        ImageAdjustments(brightness = 1.5)
    }

    @Test
    fun textBackgroundAddsPaddingOnlyWhenSet() {
        assertEquals(0.0, TextStyle(fontSize = 100.0).backgroundPadding, EPS)
        assertEquals(25.0, TextStyle(fontSize = 100.0, backgroundColor = Color.BLACK).backgroundPadding, EPS)
        assertTrue(TextStyle(outlineColor = Color.BLACK).hasOutline)
        assertTrue(!TextStyle().hasOutline)
    }
}

class ImageEditsTest {

    private val asset = imageAsset(width = 400, height = 200)

    @Test
    fun trimsBecomeACropAndNeverRemoveEverything() {
        assertEquals(Crop(0.1, 0.2, 0.7, 0.9), ImageEdits.cropFromTrims(0.1, 0.2, 0.3, 0.1).let {
            Crop(Math.round(it.left * 10) / 10.0, Math.round(it.top * 10) / 10.0, Math.round(it.right * 10) / 10.0, Math.round(it.bottom * 10) / 10.0)
        })
        val extreme = ImageEdits.cropFromTrims(0.9, 0.0, 0.9, 0.0)
        assertTrue(extreme.width >= Crop.MIN_SIZE - 1e-9)
        assertEquals(Crop.FULL, ImageEdits.cropFromTrims(0.0, 0.0, 0.0, 0.0))
    }

    @Test
    fun croppingKeepsTheRemainingPictureInPlace() {
        val layer = imageLayer("p", asset, Transform(Vec2(500.0, 300.0), 2.0, 2.0, 90.0))
        val doc = Document(canvas(), listOf(layer), mapOf(asset.id to asset))
        // A pixel of the image: 300 px from its left, 100 px from its top.
        fun onCanvas(l: ImageLayer): Vec2 {
            val box = doc.copy(layers = listOf(l)).imageLocalBounds(l)
            val local = Vec2(box.left + (300 - l.crop.left * asset.width), box.top + (100 - l.crop.top * asset.height))
            return l.transform.toMatrix().map(local)
        }
        val cropped = ImageEdits.recrop(layer, asset, Crop(0.5, 0.25, 1.0, 1.0))
        assertVec(onCanvas(layer), onCanvas(cropped))
        // Cropping back restores the original position exactly.
        assertVec(layer.transform.position, ImageEdits.recrop(cropped, asset, Crop.FULL).transform.position)
    }
}
