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
    val adjustments: ImageAdjustments = ImageAdjustments.NONE,
    val crop: Crop = Crop.FULL,
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

/** Typefaces every Android phone has built in, so projects never depend on font files. */
enum class FontFamily { SANS, SERIF, MONOSPACE, CASUAL, CURSIVE }

data class TextStyle(
    /** Canvas pixels at scale 1. */
    val fontSize: Double = 64.0,
    val color: Color = Color.BLACK,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val fontFamily: FontFamily = FontFamily.SANS,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** Line height as a multiple of the font's normal line height. */
    val lineSpacing: Double = 1.0,
    /** Extra space between letters, in ems (fractions of the font size). */
    val letterSpacing: Double = 0.0,
    /** Transparent means no outline. */
    val outlineColor: Color = Color.TRANSPARENT,
    /** Outline thickness as a fraction of the font size, so it scales with the text. */
    val outlineWidth: Double = 0.06,
    /** Transparent means no background. Drawn as a rounded box behind the text. */
    val backgroundColor: Color = Color.TRANSPARENT,
    val shadow: Boolean = false,
) {
    init {
        require(fontSize > 0) { "fontSize must be positive" }
        require(lineSpacing in MIN_LINE_SPACING..MAX_LINE_SPACING) { "lineSpacing out of range" }
        require(letterSpacing in MIN_LETTER_SPACING..MAX_LETTER_SPACING) { "letterSpacing out of range" }
        require(outlineWidth in 0.0..MAX_OUTLINE_WIDTH) { "outlineWidth out of range" }
    }

    val hasOutline get() = outlineColor.alpha > 0 && outlineWidth > 0
    val hasBackground get() = backgroundColor.alpha > 0

    /** Space between the text and the edge of its background box. */
    val backgroundPadding get() = if (hasBackground) fontSize * BACKGROUND_PADDING else 0.0

    companion object {
        const val MIN_LINE_SPACING = 0.6
        const val MAX_LINE_SPACING = 3.0
        const val MIN_LETTER_SPACING = -0.1
        const val MAX_LETTER_SPACING = 1.0
        const val MAX_OUTLINE_WIDTH = 0.3
        const val BACKGROUND_PADDING = 0.25
    }
}

/**
 * Editable picture settings. They are stored as numbers and applied when drawing, so the
 * original image is never changed. Each runs from -1 to 1 with 0 meaning unchanged.
 */
data class ImageAdjustments(
    val brightness: Double = 0.0,
    val contrast: Double = 0.0,
    val saturation: Double = 0.0,
) {
    init {
        listOf(brightness, contrast, saturation).forEach { require(it in -1.0..1.0) { "adjustment out of range" } }
    }

    val isNone get() = this == NONE

    /**
     * The 4 x 5 color matrix (row-major, as android.graphics.ColorMatrix takes it) that
     * applies saturation, then contrast, then brightness.
     */
    fun colorMatrix(): FloatArray {
        val s = 1 + saturation
        val c = 1 + contrast
        // Contrast pivots on mid gray; brightness shifts every channel.
        val offset = ((1 - c) * 0.5 + brightness) * 255
        val r = LUMA_R * (1 - s)
        val g = LUMA_G * (1 - s)
        val b = LUMA_B * (1 - s)
        return doubleArrayOf(
            c * (r + s), c * g, c * b, 0.0, offset,
            c * r, c * (g + s), c * b, 0.0, offset,
            c * r, c * g, c * (b + s), 0.0, offset,
            0.0, 0.0, 0.0, 1.0, 0.0,
        ).map { it.toFloat() }.toFloatArray()
    }

    companion object {
        val NONE = ImageAdjustments()
        private const val LUMA_R = 0.213
        private const val LUMA_G = 0.715
        private const val LUMA_B = 0.072
    }
}

/** The part of an image that shows, as fractions of its width and height. The source image is kept whole. */
data class Crop(val left: Double = 0.0, val top: Double = 0.0, val right: Double = 1.0, val bottom: Double = 1.0) {
    init {
        require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1) { "crop must stay inside the image" }
        require(right - left >= MIN_SIZE && bottom - top >= MIN_SIZE) { "crop is too small" }
    }

    val width get() = right - left
    val height get() = bottom - top
    val isFull get() = this == FULL

    companion object {
        val FULL = Crop()
        const val MIN_SIZE = 0.02
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
