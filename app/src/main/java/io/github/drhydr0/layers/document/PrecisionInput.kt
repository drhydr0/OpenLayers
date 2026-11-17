package io.github.drhydr0.layers.document

/**
 * Turns typed position, size, and angle values into a transform. Plain Kotlin, so the
 * rules for what counts as valid input are JVM-tested.
 */
object PrecisionInput {

    const val MIN_SCALE_PERCENT = 1.0
    const val MAX_SCALE_PERCENT = 10_000.0

    /**
     * The transform with the typed values, or null when any of them isn't a usable
     * number. Scale is a percentage applied evenly, keeping the layer's proportions;
     * the angle is kept between -180 and 180.
     */
    fun apply(current: Transform, x: String, y: String, scalePercent: String, rotation: String): Transform? {
        val px = number(x) ?: return null
        val py = number(y) ?: return null
        val scale = number(scalePercent)?.takeIf { it in MIN_SCALE_PERCENT..MAX_SCALE_PERCENT } ?: return null
        val angle = number(rotation) ?: return null
        // Keep any uneven stretch the layer already has; only the overall size changes.
        val factor = scale / 100.0 / current.scaleX
        return current.copy(
            position = Vec2(px, py),
            scaleX = scale / 100.0,
            scaleY = current.scaleY * factor,
            rotationDegrees = normalize(angle),
        )
    }

    /** Accepts a comma as the decimal mark too, as many keyboards type it. */
    private fun number(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun normalize(degrees: Double): Double {
        var d = degrees % 360.0
        if (d <= -180.0) d += 360.0
        if (d > 180.0) d -= 360.0
        return d
    }
}
