package io.github.drhydr0.layers.persistence

import io.github.drhydr0.layers.document.CanvasSpec
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.persistence.format.ManifestCodec
import io.github.drhydr0.layers.persistence.format.ManifestException
import io.github.drhydr0.layers.persistence.format.ProjectInfo
import io.github.drhydr0.layers.persistence.format.ProjectManifest
import java.io.File
import java.io.IOException
import java.util.UUID

/** Projects found on disk. Unreadable ones are reported, not hidden, so nothing vanishes silently. */
data class ProjectList(val projects: List<ProjectInfo>, val unreadableIds: List<String>)

/**
 * Projects on disk, one folder each:
 *
 *     <root>/projects/<id>/manifest.json
 *     <root>/projects/<id>/assets/<sha256>
 *
 * Blocking I/O: call off the main thread, one operation at a time per project.
 */
class ProjectStore(root: File) {

    private val projectsDir = File(root, "projects")

    fun create(name: String, canvas: CanvasSpec, nowMillis: Long): ProjectManifest {
        val info = ProjectInfo(UUID.randomUUID().toString(), name, nowMillis, nowMillis)
        val manifest = ProjectManifest(info, Document(canvas))
        save(manifest)
        return manifest
    }

    /** Most recently edited first. */
    fun list(): ProjectList {
        val projects = mutableListOf<ProjectInfo>()
        val unreadable = mutableListOf<String>()
        projectsDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
            // No committed manifest means a create or delete was interrupted: not a project.
            if (!File(dir, MANIFEST).isFile) return@forEach
            try {
                projects += load(dir.name).info
            } catch (_: ManifestException) {
                unreadable += dir.name
            } catch (_: IOException) {
                unreadable += dir.name
            } catch (_: IllegalArgumentException) {
                unreadable += dir.name
            }
        }
        return ProjectList(projects.sortedByDescending { it.modifiedAtMillis }, unreadable.sorted())
    }

    /**
     * Loads the last committed save. A half-written save from a crash is only ever in the
     * temp file, which is ignored here, so it can't replace the committed version.
     */
    fun load(id: String): ProjectManifest {
        val file = manifestFile(id)
        if (!file.isFile) throw IOException("Project $id has no saved manifest")
        val manifest = ManifestCodec.decode(file.readText())
        if (manifest.info.id != id) throw ManifestException("Project folder $id holds project ${manifest.info.id}")
        return manifest
    }

    /** Commits [manifest] atomically. Its assets must already be in [assets]. */
    fun save(manifest: ProjectManifest) {
        val id = manifest.info.id
        val missing = manifest.document.assets.keys.filterNot { assets(id).contains(it) }
        // Never commit a manifest that points at images that aren't on disk.
        if (missing.isNotEmpty()) throw IOException("Project $id is missing assets $missing")
        val bytes = ManifestCodec.encode(manifest).toByteArray(Charsets.UTF_8)
        writeAtomically(manifestFile(id)) { it.write(bytes) }
    }

    fun rename(id: String, name: String, nowMillis: Long): ProjectManifest {
        val current = load(id)
        val renamed = current.copy(info = current.info.copy(name = name, modifiedAtMillis = nowMillis))
        save(renamed)
        return renamed
    }

    fun delete(id: String) {
        val dir = projectDir(id)
        // Remove the manifest first, so an interrupted delete leaves no half-project in the list.
        manifestFile(id).delete()
        if (dir.exists() && !dir.deleteRecursively()) throw IOException("Could not delete project $id")
    }

    fun assets(id: String) = AssetStore(File(projectDir(id), "assets"))

    /** Cleans leftovers from interrupted writes. Call once at startup. */
    fun deleteTempFiles() {
        projectsDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
            File(dir, MANIFEST + ".tmp").delete()
            assets(dir.name).deleteTempFiles()
        }
    }

    private fun projectDir(id: String): File {
        // Ids become folder names, so refuse anything that could escape the projects folder.
        require(ID_PATTERN.matches(id)) { "invalid project id '$id'" }
        return File(projectsDir, id)
    }

    private fun manifestFile(id: String) = File(projectDir(id), MANIFEST)

    private companion object {
        const val MANIFEST = "manifest.json"
        val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
    }
}
