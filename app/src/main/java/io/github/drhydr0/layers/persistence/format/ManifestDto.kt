/**
 * Manifest schema, serialization, and migrations.
 *
 * Plain Kotlin only: no android.* or androidx.* imports, so this package runs in JVM tests.
 * The on-disk shape lives in these DTOs, separate from the document model, so the
 * model can change without silently changing saved files.
 */
package io.github.drhydr0.layers.persistence.format

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ManifestDto(
    val formatVersion: Int,
    val project: ProjectDto,
    val canvas: CanvasDto,
    val assets: List<AssetDto> = emptyList(),
    /** Bottom of the stack first. */
    val layers: List<LayerDto> = emptyList(),
    /** Since format 2. Saved in the same file so document and history can't disagree. */
    val history: HistoryDto = HistoryDto(),
)

@Serializable
internal data class HistoryDto(
    /** Stack order: the last entry is undone next. */
    val undo: List<CommandDto> = emptyList(),
    /** Stack order: the last entry is redone next. */
    val redo: List<CommandDto> = emptyList(),
)

@Serializable
internal data class PlacedLayerDto(
    val index: Int,
    val layer: LayerDto,
    /** Since format 3. Null means the top level. */
    val parentId: String? = null,
)

@Serializable
internal data class TransformChangeDto(val id: String, val before: TransformDto, val after: TransformDto)

@Serializable
internal sealed interface CommandDto

@Serializable
@SerialName("add")
internal data class AddLayersDto(
    val placements: List<PlacedLayerDto>,
    val assets: List<AssetDto> = emptyList(),
    /** Since format 3: "Add" or "Duplicate", for the history list. */
    val verb: String = "Add",
) : CommandDto

@Serializable
@SerialName("replace")
internal data class ReplaceLayersDto(
    val label: String,
    val removed: List<PlacedLayerDto>,
    val added: List<PlacedLayerDto>,
) : CommandDto

@Serializable
@SerialName("remove")
internal data class RemoveLayersDto(val removed: List<PlacedLayerDto>) : CommandDto

@Serializable
@SerialName("move")
internal data class MoveLayerDto(val id: String, val from: Int, val to: Int) : CommandDto

@Serializable
@SerialName("transform")
internal data class TransformLayersDto(val label: String, val changes: List<TransformChangeDto>) : CommandDto

@Serializable
@SerialName("update")
internal data class UpdateLayerDto(val label: String, val before: LayerDto, val after: LayerDto) : CommandDto

@Serializable
internal data class ProjectDto(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
)

@Serializable
internal data class CanvasDto(
    val width: Int,
    val height: Int,
    /** "#AARRGGBB", readable in a shared project file. */
    val background: String,
)

@Serializable
internal data class AssetDto(
    val id: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

@Serializable
internal data class TransformDto(
    val x: Double,
    val y: Double,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val rotationDegrees: Double = 0.0,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
)

@Serializable
internal sealed interface LayerDto {
    val id: String
    val name: String
    val transform: TransformDto
    val opacity: Double
    val visible: Boolean
    val locked: Boolean
}

@Serializable
@SerialName("image")
internal data class ImageLayerDto(
    override val id: String,
    override val name: String,
    override val transform: TransformDto,
    override val opacity: Double = 1.0,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    val assetId: String,
    /** Since format 5. Each -1 to 1; 0 is unchanged. */
    val brightness: Double = 0.0,
    val contrast: Double = 0.0,
    val saturation: Double = 0.0,
    /** Since format 5. The visible part as fractions of the image. */
    val cropLeft: Double = 0.0,
    val cropTop: Double = 0.0,
    val cropRight: Double = 1.0,
    val cropBottom: Double = 1.0,
) : LayerDto

/** Since format 4. */
@Serializable
@SerialName("shape")
internal data class ShapeLayerDto(
    override val id: String,
    override val name: String,
    override val transform: TransformDto,
    override val opacity: Double = 1.0,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    /** "rectangle", "rounded_rectangle", "ellipse", "line", or "arrow". */
    val kind: String,
    val width: Double,
    val height: Double,
    val fill: String = "#00000000",
    val stroke: String = "#FF000000",
    val strokeWidth: Double = 0.0,
    val cornerRadius: Double = 0.0,
) : LayerDto

/** Since format 3. */
@Serializable
@SerialName("group")
internal data class GroupLayerDto(
    override val id: String,
    override val name: String,
    override val transform: TransformDto,
    override val opacity: Double = 1.0,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    /** Bottom of the stack first. */
    val children: List<LayerDto> = emptyList(),
) : LayerDto

@Serializable
@SerialName("text")
internal data class TextLayerDto(
    override val id: String,
    override val name: String,
    override val transform: TransformDto,
    override val opacity: Double = 1.0,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    val text: String,
    val fontSize: Double,
    val color: String,
    /** "start", "center", or "end". */
    val alignment: String,
    val boxWidth: Double,
    /** Since format 5: "sans", "serif", "monospace", "casual", or "cursive". */
    val fontFamily: String = "sans",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val lineSpacing: Double = 1.0,
    val letterSpacing: Double = 0.0,
    val outlineColor: String = "#00000000",
    val outlineWidth: Double = 0.06,
    val backgroundColor: String = "#00000000",
    val shadow: Boolean = false,
) : LayerDto
