package com.cozooo.dlc_manager.core

import com.cozooo.dlc_manager.client.core.DlcZipUtil
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.pack.DlcPackCFResolver
import com.cozooo.dlc_manager.client.pack.DlcPackInstallPlan
import com.cozooo.dlc_manager.client.pack.DlcPackManifest
import com.cozooo.dlc_manager.client.pack.DlcPackMetadata
import com.cozooo.dlc_manager.client.pack.DlcPackModrinthManifest
import com.cozooo.dlc_manager.client.pack.DlcPackPackage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale

object DlcCollectionSource {
    private val GAME_ROOT_DIRECTORIES = setOf(
        "mods", "config", "kubejs", "resourcepacks", "shaderpacks", "datapacks",
        "defaultconfigs", "scripts", "openloader", "tacz", "saves", "worlds",
    )

    @JvmStatic
    fun resolve(dlcRoot: Path?, raw: String?): Path? {
        if (dlcRoot == null || raw.isNullOrBlank()) return null
        val parsed = try { Path.of(raw.replace('\\', '/').trim()) } catch (_: Exception) { return null }
        if (parsed.isAbsolute) return null
        val root = dlcRoot.toAbsolutePath().normalize()
        val resolved = root.resolve(parsed).normalize()
        return if (resolved.startsWith(root)) resolved else null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun entries(source: Path?, dlcRoot: Path?, gameRoot: Path?, allowDownloads: Boolean): List<DlcPackInstallPlan.FileEntry> {
        return entries(source, dlcRoot, gameRoot, allowDownloads, null)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun entries(source: Path?, dlcRoot: Path?, gameRoot: Path?, allowDownloads: Boolean, collectionIdentifier: String?): List<DlcPackInstallPlan.FileEntry> {
        if (source == null || dlcRoot == null || gameRoot == null) return emptyList()
        val packageRoot = materialize(source, dlcRoot)
        val info = inspect(packageRoot, dlcRoot, source)
        val plan = DlcPackInstallPlan.build(info.pack, gameRoot, dlcRoot)
        if (info.pack.format == DlcPackPackage.Format.STANDARD) return plan.files
        val completion = completionEntries(info, dlcRoot, collectionIdentifier)
        val cached = completion.mapNotNull { entry ->
            val name = entry.getRequiredFileName() ?: return@mapNotNull null
            val path = entry.resolveRequiredPath(name) ?: return@mapNotNull null
            if (!Files.isRegularFile(path)) return@mapNotNull null
            DlcPackInstallPlan.FileEntry(path, entry.getAppliedTargetRaw().orEmpty())
        }
        return DlcPackInstallPlan.merge(plan, if (allowDownloads) cached else emptyList()).files
    }

    @JvmStatic
    @Throws(IOException::class)
    fun completionEntries(source: Path?, dlcRoot: Path?, collectionIdentifier: String?): List<DlcRequiredEntry> {
        if (source == null || dlcRoot == null) return emptyList()
        val packageRoot = materialize(source, dlcRoot)
        val info = inspect(packageRoot, dlcRoot, source)
        return completionEntries(info, dlcRoot, collectionIdentifier)
    }

    private fun completionEntries(info: CollectionInfo, dlcRoot: Path, collectionIdentifier: String?): List<DlcRequiredEntry> {
        val requiredDir = dlcRoot.resolve("required")
        val prefix = collectionIdentifier?.takeIf { it.isNotBlank() } ?: "collection"
        val result = ArrayList<DlcRequiredEntry>()
        when (info.pack.format) {
            DlcPackPackage.Format.CURSEFORGE -> {
                try {
                    for (requirement in DlcPackCFResolver.resolveManifestRequirements(info.pack)) {
                        val entry = DlcRequiredEntry(null, requiredDir)
                        entry.setIdentifier("$prefix:${requirement.relativePath}")
                        entry.setRequiredFileName(requirement.fileName)
                        entry.setCurseforgeProjectId(requirement.projectId)
                        entry.setCurseforgeFileId(requirement.fileId)
                        entry.setAppliedTargetRaw(requirement.relativePath)
                        result.add(entry)
                    }
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Collection file resolution was interrupted", error)
                }
            }
            DlcPackPackage.Format.MODRINTH -> {
                for (file in DlcPackModrinthManifest.resolveManifestRequirements(info.pack)) {
                    val entry = DlcRequiredEntry(null, requiredDir)
                    entry.setIdentifier("$prefix:${file.path}")
                    entry.setRequiredFileName(Path.of(file.path).fileName.toString())
                    entry.setDirectUrls(file.downloads)
                    entry.setAppliedTargetRaw(file.path)
                    result.add(entry)
                }
            }
            DlcPackPackage.Format.STANDARD -> Unit
        }
        return result
    }

    @JvmStatic
    @Throws(IOException::class)
    fun materialize(source: Path, dlcRoot: Path): Path {
        val normalized = source.toAbsolutePath().normalize()
        if (Files.isDirectory(normalized)) return normalizeGameRoot(normalized)
        if (!Files.isRegularFile(normalized) || !isArchive(normalized)) throw IOException("Collection source is not a directory or archive: $source")
        val cache = dlcRoot.toAbsolutePath().normalize().resolve("tmp/collection").resolve(cacheKey(normalized))
        val content = cache.resolve("content")
        val marker = cache.resolve("complete")
        if (!Files.isRegularFile(marker)) {
            DlcZipUtil.deleteRecursive(content)
            Files.createDirectories(content)
            DlcZipUtil.extractZip(normalized, content)
            Files.writeString(marker, "1")
        }
        return normalizeGameRoot(content)
    }

    private fun inspect(root: Path, dlcRoot: Path, source: Path): CollectionInfo {
        val normalized = normalizeGameRoot(root)
        val cacheRoot = dlcRoot.toAbsolutePath().normalize().resolve("tmp/collection").resolve(if (Files.isRegularFile(source)) cacheKey(source) else "dir-" + cacheKeyForDirectory(source))
        val index = normalized.resolve("modrinth.index.json")
        if (Files.isRegularFile(index)) {
            val pack = DlcPackPackage(normalized, DlcPackPackage.Format.MODRINTH, normalized, normalized.resolve("overrides"), normalized, index, null, index, emptyList(), syntheticMetadata(), DlcPackModrinthManifest.read(index))
            return CollectionInfo(pack, cacheRoot)
        }
        val manifest = normalized.resolve("manifest.json")
        if (Files.isRegularFile(manifest)) {
            val pack = DlcPackPackage(normalized, DlcPackPackage.Format.CURSEFORGE, normalized, normalized.resolve("overrides"), normalized, manifest, null, manifest, DlcPackManifest.read(manifest).files, syntheticMetadata())
            return CollectionInfo(pack, cacheRoot)
        }
        val pack = DlcPackPackage(normalized, DlcPackPackage.Format.STANDARD, normalized, normalized, normalized, normalized, null, null, emptyList(), syntheticMetadata())
        return CollectionInfo(pack, cacheRoot)
    }

    private fun normalizeGameRoot(root: Path): Path {
        val children = try { Files.list(root).use { it.toList() } } catch (_: IOException) { return root }
        val directories = children.filter(Files::isDirectory)
        if (children.none(Files::isRegularFile) && directories.size == 1) {
            val candidate = directories[0]
            if (Files.isRegularFile(candidate.resolve("manifest.json")) || Files.isRegularFile(candidate.resolve("modrinth.index.json"))) {
                return candidate
            }
            val recognized = try {
                Files.list(candidate).use { stream -> stream.filter(Files::isDirectory).map { it.fileName.toString().lowercase(Locale.ROOT) }.filter(GAME_ROOT_DIRECTORIES::contains).distinct().count() }
            } catch (_: IOException) { 0 }
            if (recognized >= 2) return candidate
        }
        return root
    }

    private fun isArchive(path: Path): Boolean {
        val name = path.fileName?.toString()?.lowercase(Locale.ROOT) ?: return false
        return name.endsWith(".zip") || name.endsWith(".mrpack")
    }

    private fun cacheKey(path: Path): String {
        val value = path.toString() + "\u0000" + Files.getLastModifiedTime(path).toMillis() + "\u0000" + Files.size(path)
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun cacheKeyForDirectory(path: Path): String = MessageDigest.getInstance("SHA-256").digest(path.toAbsolutePath().normalize().toString().toByteArray()).joinToString("") { "%02x".format(it) }

    private fun syntheticMetadata() = DlcPackMetadata("collection", "0", "universal", "", "", listOf("Unknown Author"), emptyList(), false, emptyList(), emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyList())
    private class CollectionInfo(val pack: DlcPackPackage, val cacheRoot: Path)
}
