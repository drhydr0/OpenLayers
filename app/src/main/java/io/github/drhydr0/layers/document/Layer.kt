package io.github.drhydr0.layers.document

/** Properties every layer has, whatever it draws. */
sealed interface Layer {
    val id: LayerId
    val name: String
    val transform: Transform

    /** 0.0 (invisible) to 1.0 (opaque). */
    val opacity: Double
    val visible: Boolean
    val locked: Boolean

    fun withTransform(transform: Transform): Layer
    fun withName(name: String): Layer
    fun withVisible(visible: Boolean): Layer
    fun withLocked(locked: Boolean): Layer
    fun withOpacity(opacity: Double): Layer
}

data class ImageLayer(
    override val id: LayerId,
    override val name: String,
    val assetId: AssetId,
    override val transform: Transform = Transform.IDENTITY,
    override val opacity: Double = 1.0,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
) : Layer {
    init {
        requireOpacity(opacity)
    }

    override fun withTransform(transform: Transform) = copy(transform = transform)
    override fun withName(name: String) = copy(name = name)
    override fun withVisible(visible: Boolean) = copy(visible = visible)
    override fun withLocked(locked: Boolean) = copy(locked = locked)
    override fun withOpacity(opacity: Double) = copy(opacity = opacity)
}

enum class TextAlignment { START, CENTER, END }

data class TextStyle(
    /** Canvas pixels at scale 1. */
    val fontSize: Double = 64.0,
    val color: Color = Color.BLACK,
    val alignment: TextAlignment = TextAlignment.CENTER,
) {
    init {
        require(fontSize > 0) { "fontSize must be positive" }
    }
}

data class TextLayer(
    override val id: LayerId,
    override val name: String,
    val text: String,
    val style: TextStyle = TextStyle(),
    /** Wrapping width in canvas pixels at scale 1. Scaling the layer doesn't change wrapping. */
    val boxWidth: Double,
    override val transform: Transform = Transform.IDENTITY,
    override val opacity: Double = 1.0,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
) : Layer {
    init {
        requireOpacity(opacity)
        require(boxWidth > 0) { "boxWidth must be positive" }
    }

    override fun withTransform(transform: Transform) = copy(transform = transform)
    override fun withName(name: String) = copy(name = name)
    override fun withVisible(visible: Boolean) = copy(visible = visible)
    override fun withLocked(locked: Boolean) = copy(locked = locked)
    override fun withOpacity(opacity: Double) = copy(opacity = opacity)
}

enum class ShapeKind { RECTANGLE, ROUNDED_RECTANGLE, ELLIPSE, LINE, ARROW }

/**
 * A drawn shape that stays editable: its kind, size, fill, and outline can change at any
 * time. The shape fills a [width] x [height] box centered on the layer's origin. A line or
 * arrow runs along the middle of that box from left to right and uses the stroke only.
 */
data class ShapeLayer(
    override val id: LayerId,
    override val name: String,
    val kind: ShapeKind,
    val width: Double,
    val height: Double,
    /** Transparent means no fill. */
    val fill: Color = Color.TRANSPARENT,
    /** Transparent means no outline. */
    val stroke: Color = Color.BLACK,
    val strokeWidth: Double = 0.0,
    /** Rounded rectangles only. */
    val cornerRadius: Double = 0.0,
    override val transform: Transform = Transform.IDENTITY,
    override val opacity: Double = 1.0,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
) : Layer {
    init {
        requireOpacity(opacity)
        require(width > 0 && height > 0) { "shape size must be positive" }
        require(strokeWidth >= 0 && cornerRadius >= 0) { "stroke width and corner radius can't be negative" }
    }

    val isLine get() = kind == ShapeKind.LINE || kind == ShapeKind.ARROW

    /** The content box, including the outline, which is drawn centered on the edge. */
    val localBounds: Rect
        get() {
            val grow = if (isLine || stroke.alpha == 0) 0.0 else strokeWidth
            return Rect.centered(width + grow, (if (isLine) maxOf(height, strokeWidth * ARROW_HEAD_FACTOR) else height) + grow)
        }

    override fun withTransform(transform: Transform) = copy(transform = transform)
    override fun withName(name: String) = copy(name = name)
    override fun withVisible(visible: Boolean) = copy(visible = visible)
    override fun withLocked(locked: Boolean) = copy(locked = locked)
    override fun withOpacity(opacity: Double) = copy(opacity = opacity)

    companion object {
        /** An arrow head is this many stroke widths across. */
        const val ARROW_HEAD_FACTOR = 4.0
    }
}

/**
 * Ordered child layers (bottom first) sharing one transform, opacity, visibility, and lock.
 * Children are positioned in the group's own coordinates, so moving the group moves them all.
 */
data class GroupLayer(
    override val id: LayerId,
    override val name: String,
    val children: List<Layer>,
    override val transform: Transform = Transform.IDENTITY,
    override val opacity: Double = 1.0,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
) : Layer {
    init {
        requireOpacity(opacity)
    }

    override fun withTransform(transform: Transform) = copy(transform = transform)
    override fun withName(name: String) = copy(name = name)
    override fun withVisible(visible: Boolean) = copy(visible = visible)
    override fun withLocked(locked: Boolean) = copy(locked = locked)
    override fun withOpacity(opacity: Double) = copy(opacity = opacity)
}

/** This layer and everything inside it, depth first. */
fun Layer.selfAndDescendants(): Sequence<Layer> = sequence {
    yield(this@selfAndDescendants)
    if (this@selfAndDescendants is GroupLayer) children.forEach { yieldAll(it.selfAndDescendants()) }
}

private fun requireOpacity(opacity: Double) =
    require(opacity in 0.0..1.0) { "opacity must be between 0 and 1" }
