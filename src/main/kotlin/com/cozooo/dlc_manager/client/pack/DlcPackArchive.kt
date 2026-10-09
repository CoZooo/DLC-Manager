package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.client.core.DlcZipUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipFile

object DlcPackArchive {
    private val METADATA_PATHS = arrayOf(
        "overrides/metadata/pack.dc",
        "overrides/dlcpack_metadata/pack.dc",
        "metadata/pack.dc",
        "dlcpack_metadata/pack.dc",
    )

    @JvmStatic @Throws(IOException::class)
    fun findArchives(dlcRoot: Path): List<Path> {
        val archives = LinkedHashSet<Path>()
        val shadowed = HashSet<Path>()
        for (entry in com.cozooo.dlc_manager.client.remote.DlcRemoteLoader.load(dlcRoot)) {
            if (entry.getType() != com.cozooo.dlc_manager.core.DlcType.DLC_PACK) continue
            val preferred = entry.getTargetPath()
            if (preferred != null && Files.isRegularFile(preferred)) archives.add(preferred.toAbsolutePath().normalize())
            val downloaded = entry.resolveTargetPath(entry.getFileNameHint())
            if (downloaded != null && Files.isRegularFile(downloaded)) shadowed.add(dlcRoot.resolve("pack").resolve(downloaded.fileName).toAbsolutePath().normalize())
        }
        val local = dlcRoot.resolve("pack")
        if (Files.isDirectory(local)) {
            Files.list(local).use { paths ->
                paths.filter(Files::isRegularFile).filter { it.fileName.toString().lowercase(java.util.Locale.ROOT).endsWith(".zip") }
                    .map { it.toAbsolutePath().normalize() }.filter { it !in shadowed }.forEach { archives.add(it) }
            }
        }
        return archives.sorted()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun extractToStaging(zipPath: Path?, dlcRoot: Path?): ExtractResult {
        if (zipPath == null || !Files.isRegularFile(zipPath)) {
            throw IOException("DLC Pack archive is not a file")
        }
        if (dlcRoot == null) {
            throw IOException("DLC root is not available")
        }
        val normalizedZip = zipPath.toAbsolutePath().normalize()
        val identifier = readIdentifier(normalizedZip) ?: "pack"
        val stagingRoot = dlcRoot.resolve("tmp").resolve("_staging")
        val staging = DlcZipUtil.createUniqueDir(stagingRoot, identifier)
        try {
            DlcZipUtil.extractZip(normalizedZip, staging)
            return ExtractResult(staging)
        } catch (error: Throwable) {
            DlcZipUtil.deleteRecursive(staging)
            if (error is IOException) {
                throw error
            }
            throw IOException("Failed to extract DLC Pack archive: $normalizedZip", error)
        }
    }

    private fun readIdentifier(zipPath: Path): String? {
        return try {
            ZipFile(zipPath.toFile()).use { zip ->
                for (entryName in metadataEntryCandidates(zip)) {
                    val entry = zip.getEntry(entryName) ?: continue
                    if (entry.isDirectory) {
                        continue
                    }
                    val metadata = Files.createTempFile("dlc-pack-", ".dc")
                    try {
                        zip.getInputStream(entry).use { input ->
                            Files.newOutputStream(metadata).use { output -> input.copyTo(output) }
                        }
                        val parsed = DlcPackMetadata.read(metadata)
                        if (parsed.valid && parsed.metadata != null) {
                            return parsed.metadata.identifier
                        }
                    } finally {
                        try {
                            Files.deleteIfExists(metadata)
                        } catch (_: IOException) {
                        }
                    }
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun metadataEntryCandidates(zip: ZipFile): List<String> {
        val names = LinkedHashSet<String>()
        for (path in METADATA_PATHS) {
            names.add(path)
        }
        val firstLevelDirs = zip.entries().asSequence()
            .mapNotNull { firstSegment(it.name) }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }
            .toList()
        for (dir in firstLevelDirs) {
            for (path in METADATA_PATHS) {
                names.add("$dir/$path")
            }
        }
        return names.toList()
    }

    private fun firstSegment(name: String?): String? {
        if (name.isNullOrBlank()) {
            return null
        }
        val normalized = name.replace('\\', '/').trimStart('/')
        val slash = normalized.indexOf('/')
        if (slash <= 0) {
            return null
        }
        return normalized.substring(0, slash).takeIf { it.isNotBlank() && it != "." && it != ".." }
    }

    @JvmStatic
    fun deleteRecursive(path: Path?) {
        DlcZipUtil.deleteRecursive(path)
    }

    class ExtractResult(
        @JvmField val stagingDir: Path,
    )
}
