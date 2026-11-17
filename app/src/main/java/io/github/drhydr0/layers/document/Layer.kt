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
}

private fun requireOpacity(opacity: Double) =
    require(opacity in 0.0..1.0) { "opacity must be between 0 and 1" }
