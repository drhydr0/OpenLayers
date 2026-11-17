package io.github.drhydr0.layers.document

/**
 * A reusable composition saved outside any project. [layer] is positioned around the
 * origin; [assets] lists every image it uses.
 */
data class Sticker(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val layer: Layer,
    val assets: List<ImageAsset>,
) {
    init {
        val have = assets.mapTo(mutableSetOf()) { it.id }
        layer.selfAndDescendants().filterIsInstance<ImageLayer>().forEach {
            require(it.assetId in have) { "sticker is missing asset ${it.assetId}" }
        }
    }
}

/** Pure helpers for turning layers into stickers and stickers into layers. */
object Stickers {

    /**
     * An independent copy of [layers] (bottom first, all in the same coordinates) as one
     * object centered on the origin: the layer itself for a single one, otherwise a group.
     */
    fun contentOf(layers: List<Layer>, center: Vec2, groupName: String, newId: () -> LayerId): Layer {
        require(layers.isNotEmpty()) { "nothing to make a sticker from" }
        val single = layers.singleOrNull()
        val content = if (single != null) {
            single.withTransform(single.transform.copy(position = Vec2.ZERO))
        } else {
            Grouping.group(newId(), groupName, layers, center).withTransform(Transform.IDENTITY)
        }
        return Grouping.copy(content, newId)
    }

    fun create(id: String, name: String, nowMillis: Long, content: Layer, documentAssets: Map<AssetId, ImageAsset>): Sticker {
        val used = content.selfAndDescendants().filterIsInstance<ImageLayer>().map { it.assetId }.distinct().toList()
        return Sticker(id, name, nowMillis, content, used.map { documentAssets.getValue(it) })
    }

    /** A new, independent instance at [position]: new ids everywhere, so edits never reach the saved sticker or other uses. */
    fun instantiate(sticker: Sticker, position: Vec2, newId: () -> LayerId): Layer {
        val copy = Grouping.copy(sticker.layer, newId)
        return copy.withTransform(copy.transform.translatedBy(position))
    }
}
