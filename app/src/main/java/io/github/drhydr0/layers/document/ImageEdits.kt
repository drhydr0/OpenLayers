package io.github.drhydr0.layers.document

/** Pure helpers for editing image layers. */
object ImageEdits {

    /**
     * Builds a crop from how much to trim off each side (fractions of the image), keeping
     * at least a sliver so the image never disappears.
     */
    fun cropFromTrims(left: Double, top: Double, right: Double, bottom: Double): Crop {
        fun span(start: Double, end: Double): Pair<Double, Double> {
            val a = start.coerceIn(0.0, 1.0 - Crop.MIN_SIZE)
            val b = (1.0 - end).coerceIn(a + Crop.MIN_SIZE, 1.0)
            return a to b
        }
        val (l, r) = span(left, right)
        val (t, b) = span(top, bottom)
        return Crop(l, t, r, b)
    }

    /**
     * Applies [crop] and moves the layer so the part of the picture that stays visible
     * doesn't shift on the canvas. Without this, trimming one side would slide the image,
     * because a layer's position is the center of what it shows.
     */
    fun recrop(layer: ImageLayer, asset: ImageAsset, crop: Crop): ImageLayer {
        fun center(c: Crop) = Vec2((c.left + c.right) / 2 * asset.width, (c.top + c.bottom) / 2 * asset.height)
        val shift = center(crop) - center(layer.crop)
        val m = layer.transform.toMatrix()
        // Only the rotation, scale, and flip part: a direction, not a point.
        val onCanvas = m.map(shift) - m.map(Vec2.ZERO)
        return layer.copy(crop = crop, transform = layer.transform.translatedBy(onCanvas))
    }
}
