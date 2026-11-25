package com.cozooo.dlc_manager.core

import com.cozooo.dlc_manager.client.core.DlcZipUtil
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredService
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
import com.google.gson.JsonElement
import com.google.gson.JsonObject

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
        return entries(source, dlcRoot, gameRoot, allowDownloads, null, emptyList())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun entries(source: Path?, dlcRoot: Path?, gameRoot: Path?, allowDownloads: Boolean, collectionIdentifier: String?): List<DlcPackInstallPlan.FileEntry> {
        return entries(source, dlcRoot, gameRoot, allowDownloads, collectionIdentifier, emptyList())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun entries(source: Path?, dlcRoot: Path?, gameRoot: Path?, allowDownloads: Boolean, collectionIdentifier: String?, remoteEntries: List<JsonObject>?): List<DlcPackInstallPlan.FileEntry> {
        if (source == null || dlcRoot == null || gameRoot == null) return emptyList()
        val packageRoot = materialize(source, dlcRoot)
        val info = inspect(packageRoot, dlcRoot, source)
        val plan = DlcPackInstallPlan.build(info.pack, gameRoot, dlcRoot)
        if (info.pack.format == DlcPackPackage.Format.STANDARD && remoteEntries.isNullOrEmpty()) return plan.files
        val completion = completionEntries(info, dlcRoot, collectionIdentifier, remoteEntries)
        val cached = completion.mapNotNull { entry ->
            val name = entry.getRequiredFileName() ?: entry.getResolvedFileName() ?: DlcRequiredService.resolveExistingFileName(entry) ?: return@mapNotNull null
            val path = entry.resolveRequiredPath(name) ?: return@mapNotNull null
            if (!Files.isRegularFile(path)) return@mapNotNull null
            entry.resolveInstallationTargets(name).map { target -> DlcPackInstallPlan.FileEntry(path, DlcPathUtil.toGameRelativePath(target)) }
        }.flatten()
        return DlcPackInstallPlan.merge(plan, if (allowDownloads) cached else emptyList()).files
    }

    @JvmStatic
    @Throws(IOException::class)
    fun completionEntries(source: Path?, dlcRoot: Path?, collectionIdentifier: String?): List<DlcRequiredEntry> {
        return completionEntries(source, dlcRoot, collectionIdentifier, emptyList())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun completionEntries(source: Path?, dlcRoot: Path?, collectionIdentifier: String?, remoteEntries: List<JsonObject>?): List<DlcRequiredEntry> {
        if (source == null || dlcRoot == null) return emptyList()
        val packageRoot = materialize(source, dlcRoot)
        val info = inspect(packageRoot, dlcRoot, source)
        return completionEntries(info, dlcRoot, collectionIdentifier, remoteEntries)
    }

    private fun completionEntries(info: CollectionInfo, dlcRoot: Path, collectionIdentifier: String?, remoteEntries: List<JsonObject>?): List<DlcRequiredEntry> {
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
        for (remote in remoteEntries.orEmpty()) {
            val identifier = stringValue(remote, "identifier") ?: continue
            val entry = DlcRequiredEntry(null, requiredDir)
            entry.setIdentifier("$prefix:remote:$identifier")
            entry.setRequiredFileName(stringValue(remote, "source"))
            entry.setModrinthProjectId(stringValue(remote, "modrinth_projectid"))
            entry.setModrinthVersionId(stringValue(remote, "modrinth_versionid"))
            entry.setCurseforgeProjectId(stringValue(remote, "curseforge_projectid"))
            entry.setCurseforgeFileId(stringValue(remote, "curseforge_fileid"))
            entry.setDirectUrls(stringList(remote.get("directUrl")))
            entry.setDownloadPriority(DlcDownloadCandidate.readPriority(remote))
            entry.setAppliedTargetRaw(stringValue(remote, "target"))
            result.add(entry)
        }
        return result
    }

    @JvmStatic
    @Throws(IOException::class)
    fun completionEntries(item: DlcItem?): List<DlcRequiredEntry> {
        if (item == null || item.getType() != DlcType.COLLECTION) return emptyList()
        return completionEntries(item, HashSet())
    }

    private fun completionEntries(item: DlcItem, visited: MutableSet<String>): List<DlcRequiredEntry> {
        val result = ArrayList<DlcRequiredEntry>()
        val id = item.getId()
        if (id != null && !visited.add(id)) return result
        val sources = item.getSourcePaths().ifEmpty { listOfNotNull(item.getSourcePath()) }
        for (source in sources) result.addAll(completionEntries(source, item.getDlcRoot(), item.getIdentifier(), item.getCollectionRemoteEntries()))
        for (included in item.getCollectionIncludedItems()) result.addAll(completionEntries(included, visited))
        if (id != null) visited.remove(id)
        return result
    }

    @JvmStatic
    @Throws(IOException::class)
    fun materialize(source: Path, dlcRoot: Path): Path {
        val normalized = source.toAbsolutePath().normalize()
        if (Files.isDirectory(normalized)) return normalizeGameRoot(normalized)
        if (Files.isRegularFile(normalized) && isManifestFile(normalized)) return normalized
        if (!Files.isRegularFile(normalized) || !isArchive(normalized)) throw IOException("Collection source is not a directory, metadata file, or archive: $source")
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
        if (Files.isRegularFile(source) && isManifestFile(source)) return inspectMetadata(source, dlcRoot)
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

    private fun inspectMetadata(source: Path, dlcRoot: Path): CollectionInfo {
        val parent = source.parent ?: dlcRoot
        val emptyRoot = dlcRoot.resolve("tmp/collection").resolve("standalone-empty").resolve(cacheKey(source))
        val name = source.fileName.toString().lowercase(Locale.ROOT)
        if (name == "modrinth.index.json") {
            val pack = DlcPackPackage(parent, DlcPackPackage.Format.MODRINTH, emptyRoot, emptyRoot, parent, source, null, source, emptyList(), syntheticMetadata(), DlcPackModrinthManifest.read(source))
            return CollectionInfo(pack, dlcRoot.resolve("tmp/collection").resolve(cacheKey(source)))
        }
        val pack = DlcPackPackage(parent, DlcPackPackage.Format.CURSEFORGE, emptyRoot, emptyRoot, parent, source, null, source, DlcPackManifest.read(source).files, syntheticMetadata())
        return CollectionInfo(pack, dlcRoot.resolve("tmp/collection").resolve(cacheKey(source)))
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

    private fun isManifestFile(path: Path): Boolean {
        val name = path.fileName?.toString()?.lowercase(Locale.ROOT) ?: return false
        return name == "manifest.json" || name == "modrinth.index.json"
    }

    private fun stringValue(obj: JsonObject, key: String): String? {
        val value = obj.get(key)
        return if (value != null && value.isJsonPrimitive) value.asString.trim().takeIf { it.isNotBlank() } else null
    }

    private fun stringList(element: JsonElement?): List<String> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element.isJsonPrimitive) return listOfNotNull(element.asString.trim().takeIf { it.isNotBlank() })
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { value -> if (value.isJsonPrimitive) value.asString.trim().takeIf { it.isNotBlank() } else null }
    }

    private fun cacheKey(path: Path): String {
        val value = path.toString() + "\u0000" + Files.getLastModifiedTime(path).toMillis() + "\u0000" + Files.size(path)
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun cacheKeyForDirectory(path: Path): String = MessageDigest.getInstance("SHA-256").digest(path.toAbsolutePath().normalize().toString().toByteArray()).joinToString("") { "%02x".format(it) }

    private fun syntheticMetadata() = DlcPackMetadata("collection", "0", "universal", "", "", listOf("Unknown Author"), emptyList(), false, emptyList(), emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyList())
    private class CollectionInfo(val pack: DlcPackPackage, val cacheRoot: Path)
}
