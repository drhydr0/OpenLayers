package io.github.drhydr0.layers.document

/**
 * Where a layer sits on the canvas. Stored as meaningful values instead of a raw matrix,
 * so gestures and precise controls edit position, scale, and angle directly.
 *
 * A layer's content is centered on its local origin, so [position] is the content's center
 * and rotation and scale happen around it.
 */
data class Transform(
    val position: Vec2 = Vec2.ZERO,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val rotationDegrees: Double = 0.0,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
) {
    init {
        require(scaleX > 0 && scaleY > 0) { "scale must be positive; use flip for mirroring" }
    }

    /** Local content coordinates to canvas coordinates. */
    fun toMatrix(): Matrix =
        Matrix.translate(position.x, position.y) *
            Matrix.rotateDegrees(rotationDegrees) *
            Matrix.scale(if (flipX) -scaleX else scaleX, if (flipY) -scaleY else scaleY)

    fun translatedBy(offset: Vec2) = copy(position = position + offset)

    companion object {
        val IDENTITY = Transform()
    }
}
