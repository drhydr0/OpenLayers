package io.github.drhydr0.layers.persistence.format

import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.Color
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.TextAlignment
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.TextStyle
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Project details stored alongside the document. */
data class ProjectInfo(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
)

data class ProjectManifest(val info: ProjectInfo, val document: Document)

/** The manifest can't be read: corrupt, from a newer app, or internally inconsistent. */
class ManifestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Converts [ProjectManifest] to and from the versioned JSON file format. */
object ManifestCodec {

    const val CURRENT_VERSION = 1

    /**
     * Upgrades from version N to N+1, keyed by N. A format change bumps [CURRENT_VERSION]
     * and adds a step here, so old projects keep opening.
     */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        // Tolerates fields added by a future minor change instead of refusing to open.
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(manifest: ProjectManifest): String =
        json.encodeToString(ManifestDto.serializer(), toDto(manifest))

    fun decode(text: String): ProjectManifest {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            throw ManifestException("Project file is not valid JSON", e)
        }
        val version = try {
            root.getValue("formatVersion").jsonPrimitive.int
        } catch (e: Exception) {
            throw ManifestException("Project file has no format version", e)
        }
        if (version > CURRENT_VERSION) {
            throw ManifestException("Project was saved by a newer version of Layers (format $version)")
        }
        if (version < 1) throw ManifestException("Unknown project format $version")

        val migrated = (version until CURRENT_VERSION).fold(root) { obj, from ->
            val step = migrations[from] ?: throw ManifestException("No migration from format $from")
            step(obj)
        }
        return try {
            fromDto(json.decodeFromJsonElement(ManifestDto.serializer(), migrated))
        } catch (e: SerializationException) {
            throw ManifestException("Project file is damaged: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            // Domain validation, e.g. a layer pointing at a missing asset.
            throw ManifestException("Project file is inconsistent: ${e.message}", e)
        }
    }

    private fun toDto(manifest: ProjectManifest): ManifestDto {
        val doc = manifest.document
        val info = manifest.info
        return ManifestDto(
            formatVersion = CURRENT_VERSION,
            project = ProjectDto(info.id, info.name, info.createdAtMillis, info.modifiedAtMillis),
            canvas = CanvasDto(doc.canvas.width, doc.canvas.height, colorToHex(doc.canvas.background)),
            assets = doc.assets.values.sortedBy { it.id.sha256 }.map {
                AssetDto(it.id.sha256, it.mimeType, it.width, it.height)
            },
            layers = doc.layers.map(::layerToDto),
        )
    }

    private fun fromDto(dto: ManifestDto): ProjectManifest {
        val p = dto.project
        val document = Document(
            canvas = CanvasSpec(dto.canvas.width, dto.canvas.height, colorFromHex(dto.canvas.background)),
            layers = dto.layers.map(::layerFromDto),
            assets = dto.assets.associate { a ->
                AssetId(a.id).let { it to ImageAsset(it, a.mimeType, a.width, a.height) }
            },
        )
        return ProjectManifest(ProjectInfo(p.id, p.name, p.createdAtMillis, p.modifiedAtMillis), document)
    }

    private fun layerToDto(layer: Layer): LayerDto {
        val t = layer.transform.let {
            TransformDto(it.position.x, it.position.y, it.scaleX, it.scaleY, it.rotationDegrees, it.flipX, it.flipY)
        }
        return when (layer) {
            is ImageLayer -> ImageLayerDto(
                layer.id.value, layer.name, t, layer.opacity, layer.visible, layer.locked, layer.assetId.sha256,
            )
            is TextLayer -> TextLayerDto(
                layer.id.value, layer.name, t, layer.opacity, layer.visible, layer.locked,
                text = layer.text,
                fontSize = layer.style.fontSize,
                color = colorToHex(layer.style.color),
                alignment = layer.style.alignment.name.lowercase(),
                boxWidth = layer.boxWidth,
            )
        }
    }

    private fun layerFromDto(dto: LayerDto): Layer {
        val t = dto.transform.let {
            Transform(Vec2(it.x, it.y), it.scaleX, it.scaleY, it.rotationDegrees, it.flipX, it.flipY)
        }
        return when (dto) {
            is ImageLayerDto -> ImageLayer(
                LayerId(dto.id), dto.name, AssetId(dto.assetId), t, dto.opacity, dto.visible, dto.locked,
            )
            is TextLayerDto -> TextLayer(
                LayerId(dto.id), dto.name, dto.text,
                TextStyle(dto.fontSize, colorFromHex(dto.color), alignmentFromName(dto.alignment)),
                dto.boxWidth, t, dto.opacity, dto.visible, dto.locked,
            )
        }
    }

    private fun alignmentFromName(name: String): TextAlignment =
        TextAlignment.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException("unknown alignment '$name'")

    internal fun colorToHex(color: Color) = "#%08X".format(color.argb)

    internal fun colorFromHex(hex: String): Color {
        require(hex.length == 9 && hex[0] == '#') { "color must be #AARRGGBB, was '$hex'" }
        return Color(hex.substring(1).toLong(16).toInt())
    }
}
