package io.github.drhydr0.layers.persistence.format

import io.github.drhydr0.layers.document.AssetId
import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.Color
import io.github.drhydr0.layers.document.Crop
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.FontFamily
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageAdjustments
import io.github.drhydr0.layers.document.ImageAsset
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.ShapeKind
import io.github.drhydr0.layers.document.ShapeLayer
import io.github.drhydr0.layers.document.TextAlignment
import io.github.drhydr0.layers.document.TextLayer
import io.github.drhydr0.layers.document.TextStyle
import io.github.drhydr0.layers.document.Transform
import io.github.drhydr0.layers.document.Vec2
import io.github.drhydr0.layers.history.AddLayers
import io.github.drhydr0.layers.history.Command
import io.github.drhydr0.layers.history.CommandSequence
import io.github.drhydr0.layers.history.InverseCommand
import io.github.drhydr0.layers.history.JournalEntry
import io.github.drhydr0.layers.history.MoveLayer
import io.github.drhydr0.layers.history.PlacedLayer
import io.github.drhydr0.layers.history.RemoveLayers
import io.github.drhydr0.layers.history.ReplaceLayers
import io.github.drhydr0.layers.history.SavedHistory
import io.github.drhydr0.layers.history.TransformLayers
import io.github.drhydr0.layers.history.UpdateLayer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

data class ProjectManifest(
    val info: ProjectInfo,
    val document: Document,
    val history: SavedHistory = SavedHistory.EMPTY,
)

/** The manifest can't be read: corrupt, from a newer app, or internally inconsistent. */
class ManifestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Converts [ProjectManifest] to and from the versioned JSON file format. */
object ManifestCodec {

    const val CURRENT_VERSION = 6

    /**
     * Upgrades from version N to N+1, keyed by N. A format change bumps [CURRENT_VERSION]
     * and adds a step here, so old projects keep opening.
     */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = mapOf(
        // 1 -> 2 added saved history; a version 1 project simply starts with none.
        1 to { obj -> JsonObject(obj + ("formatVersion" to JsonPrimitive(2))) },
        // 2 -> 3 added groups and parent ids; older files simply have none.
        2 to { obj -> JsonObject(obj + ("formatVersion" to JsonPrimitive(3))) },
        // 3 -> 4 added shape layers; older files simply have none.
        3 to { obj -> JsonObject(obj + ("formatVersion" to JsonPrimitive(4))) },
        // 4 -> 5 added text styling and image adjustments and crop; older layers use the defaults.
        4 to { obj -> JsonObject(obj + ("formatVersion" to JsonPrimitive(5))) },
        // 5 -> 6 added the history journal; older projects start with an empty one.
        5 to { obj -> JsonObject(obj + ("formatVersion" to JsonPrimitive(6))) },
    )

    internal val json = Json {
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
            history = HistoryDto(
                undo = manifest.history.undo.map(::commandToDto),
                redo = manifest.history.redo.map(::commandToDto),
                journal = manifest.history.journal.map { JournalEntryDto(it.label, commandToDto(it.command)) },
            ),
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
        val history = SavedHistory(
            dto.history.undo.map(::commandFromDto),
            dto.history.redo.map(::commandFromDto),
            dto.history.journal.map { JournalEntry(it.label, commandFromDto(it.command)) },
        )
        return ProjectManifest(ProjectInfo(p.id, p.name, p.createdAtMillis, p.modifiedAtMillis), document, history)
    }

    private fun commandToDto(command: Command): CommandDto = when (command) {
        is AddLayers -> AddLayersDto(command.placements.map(::placedToDto), command.assets.map(::assetToDto), command.verb)
        is RemoveLayers -> RemoveLayersDto(command.removed.map(::placedToDto))
        is ReplaceLayers -> ReplaceLayersDto(command.label, command.removed.map(::placedToDto), command.added.map(::placedToDto))
        is MoveLayer -> MoveLayerDto(command.id.value, command.from, command.to)
        is TransformLayers -> TransformLayersDto(
            command.label,
            command.before.map { (id, before) ->
                TransformChangeDto(id.value, transformToDto(before), transformToDto(command.after.getValue(id)))
            },
        )
        is UpdateLayer -> UpdateLayerDto(command.label, layerToDto(command.before), layerToDto(command.after))
        is InverseCommand -> InverseCommandDto(commandToDto(command.of))
        is CommandSequence -> CommandSequenceDto(command.label, command.commands.map(::commandToDto))
        else -> throw IllegalArgumentException("Command ${command::class.simpleName} has no saved form")
    }

    private fun commandFromDto(dto: CommandDto): Command = when (dto) {
        is AddLayersDto -> AddLayers(dto.placements.map(::placedFromDto), dto.assets.map(::assetFromDto), dto.verb)
        is RemoveLayersDto -> RemoveLayers(dto.removed.map(::placedFromDto))
        is ReplaceLayersDto -> ReplaceLayers(dto.removed.map(::placedFromDto), dto.added.map(::placedFromDto), dto.label)
        is MoveLayerDto -> MoveLayer(LayerId(dto.id), dto.from, dto.to)
        is TransformLayersDto -> TransformLayers(
            before = dto.changes.associate { LayerId(it.id) to transformFromDto(it.before) },
            after = dto.changes.associate { LayerId(it.id) to transformFromDto(it.after) },
            label = dto.label,
        )
        is UpdateLayerDto -> UpdateLayer(layerFromDto(dto.before), layerFromDto(dto.after), dto.label)
        is InverseCommandDto -> InverseCommand(commandFromDto(dto.of))
        is CommandSequenceDto -> CommandSequence(dto.commands.map(::commandFromDto), dto.label)
    }

    private fun placedToDto(p: PlacedLayer) = PlacedLayerDto(p.index, layerToDto(p.layer), p.parentId?.value)
    private fun placedFromDto(p: PlacedLayerDto) = PlacedLayer(p.index, layerFromDto(p.layer), p.parentId?.let(::LayerId))
    internal fun assetToDto(a: ImageAsset) = AssetDto(a.id.sha256, a.mimeType, a.width, a.height)
    internal fun assetFromDto(a: AssetDto) = ImageAsset(AssetId(a.id), a.mimeType, a.width, a.height)

    private fun transformToDto(t: Transform) =
        TransformDto(t.position.x, t.position.y, t.scaleX, t.scaleY, t.rotationDegrees, t.flipX, t.flipY)

    private fun transformFromDto(t: TransformDto) =
        Transform(Vec2(t.x, t.y), t.scaleX, t.scaleY, t.rotationDegrees, t.flipX, t.flipY)

    internal fun layerToDto(layer: Layer): LayerDto {
        val t = transformToDto(layer.transform)
        return when (layer) {
            is ImageLayer -> ImageLayerDto(
                layer.id.value, layer.name, t, layer.opacity, layer.visible, layer.locked, layer.assetId.sha256,
                layer.adjustments.brightness, layer.adjustments.contrast, layer.adjustments.saturation,
                layer.crop.left, layer.crop.top, layer.crop.right, layer.crop.bottom,
            )
            is GroupLayer -> GroupLayerDto(
                layer.id.value, layer.name, t, layer.opacity, layer.visible, layer.locked, layer.children.map(::layerToDto),
            )
            is ShapeLayer -> ShapeLayerDto(
                layer.id.value, layer.name, t, layer.opacity, layer.visible, layer.locked,
                kind = layer.kind.name.lowercase(),
                width = layer.width,
                height = layer.height,
                fill = colorToHex(layer.fill),
                stroke = colorToHex(layer.stroke),
                strokeWidth = layer.strokeWidth,
                cornerRadius = layer.cornerRadius,
            )
            is TextLayer -> TextLayerDto(
                layer.id.value, layer.name, t, layer.opacity, layer.visible, layer.locked,
                text = layer.text,
                fontSize = layer.style.fontSize,
                color = colorToHex(layer.style.color),
                alignment = layer.style.alignment.name.lowercase(),
                boxWidth = layer.boxWidth,
                fontFamily = layer.style.fontFamily.name.lowercase(),
                bold = layer.style.bold,
                italic = layer.style.italic,
                lineSpacing = layer.style.lineSpacing,
                letterSpacing = layer.style.letterSpacing,
                outlineColor = colorToHex(layer.style.outlineColor),
                outlineWidth = layer.style.outlineWidth,
                backgroundColor = colorToHex(layer.style.backgroundColor),
                shadow = layer.style.shadow,
            )
        }
    }

    internal fun layerFromDto(dto: LayerDto): Layer {
        val t = transformFromDto(dto.transform)
        return when (dto) {
            is ImageLayerDto -> ImageLayer(
                LayerId(dto.id), dto.name, AssetId(dto.assetId), t, dto.opacity, dto.visible, dto.locked,
                ImageAdjustments(dto.brightness, dto.contrast, dto.saturation),
                Crop(dto.cropLeft, dto.cropTop, dto.cropRight, dto.cropBottom),
            )
            is GroupLayerDto -> GroupLayer(
                LayerId(dto.id), dto.name, dto.children.map(::layerFromDto), t, dto.opacity, dto.visible, dto.locked,
            )
            is ShapeLayerDto -> ShapeLayer(
                LayerId(dto.id), dto.name,
                ShapeKind.entries.firstOrNull { it.name.equals(dto.kind, ignoreCase = true) }
                    ?: throw IllegalArgumentException("unknown shape '${dto.kind}'"),
                dto.width, dto.height, colorFromHex(dto.fill), colorFromHex(dto.stroke), dto.strokeWidth, dto.cornerRadius,
                t, dto.opacity, dto.visible, dto.locked,
            )
            is TextLayerDto -> TextLayer(
                LayerId(dto.id), dto.name, dto.text,
                TextStyle(
                    dto.fontSize, colorFromHex(dto.color), alignmentFromName(dto.alignment),
                    FontFamily.entries.firstOrNull { it.name.equals(dto.fontFamily, ignoreCase = true) }
                        ?: throw IllegalArgumentException("unknown font '${dto.fontFamily}'"),
                    dto.bold, dto.italic, dto.lineSpacing, dto.letterSpacing,
                    colorFromHex(dto.outlineColor), dto.outlineWidth, colorFromHex(dto.backgroundColor), dto.shadow,
                ),
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
