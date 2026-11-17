package io.github.drhydr0.layers.persistence.format

import io.github.drhydr0.layers.document.Sticker
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
internal data class StickerDto(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val assets: List<AssetDto> = emptyList(),
    val layer: LayerDto,
)

/** Converts a saved [Sticker] to and from its versioned JSON file, sharing the project layer format. */
object StickerCodec {

    const val CURRENT_VERSION = 1

    fun encode(sticker: Sticker): String = ManifestCodec.json.encodeToString(
        StickerDto.serializer(),
        StickerDto(
            CURRENT_VERSION, sticker.id, sticker.name, sticker.createdAtMillis,
            sticker.assets.map(ManifestCodec::assetToDto), ManifestCodec.layerToDto(sticker.layer),
        ),
    )

    fun decode(text: String): Sticker {
        val dto = try {
            ManifestCodec.json.decodeFromString(StickerDto.serializer(), text)
        } catch (e: SerializationException) {
            throw ManifestException("Sticker file is damaged: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw ManifestException("Sticker file is not valid: ${e.message}", e)
        }
        if (dto.formatVersion > CURRENT_VERSION) {
            throw ManifestException("Sticker was saved by a newer version of Layers (format ${dto.formatVersion})")
        }
        return try {
            Sticker(dto.id, dto.name, dto.createdAtMillis, ManifestCodec.layerFromDto(dto.layer), dto.assets.map(ManifestCodec::assetFromDto))
        } catch (e: IllegalArgumentException) {
            throw ManifestException("Sticker file is inconsistent: ${e.message}", e)
        }
    }
}
