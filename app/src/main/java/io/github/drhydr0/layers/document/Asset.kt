package io.github.drhydr0.layers.document

/**
 * Identity of stored image bytes: their SHA-256 hash in lowercase hex. Bytes never change
 * once stored, so identical imports share one asset and an edit makes a new one.
 */
@JvmInline
value class AssetId(val sha256: String) {
    init {
        require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "AssetId must be 64 lowercase hex characters"
        }
    }

    override fun toString(): String = sha256
}

/** An imported image. Size is after orientation is applied, as the user sees it. */
data class ImageAsset(
    val id: AssetId,
    val mimeType: String,
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "image size must be positive" }
    }
}
