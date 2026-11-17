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
)

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
) : LayerDto
