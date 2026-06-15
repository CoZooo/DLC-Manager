package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.core.DcConfigIO
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.Locale

class DlcPackPackage(
    @JvmField val root: Path,
    @JvmField val format: Format,
    @JvmField val contentRoot: Path,
    @JvmField val installRoot: Path,
    @JvmField val metadataDir: Path,
    @JvmField val packFile: Path,
    @JvmField val iconFile: Path?,
    @JvmField val manifestFile: Path?,
    @JvmField val manifestFiles: List<DlcPackManifest.FileRef>,
    @JvmField val metadata: DlcPackMetadata,
) {
    enum class Format {
        STANDARD,
        CURSEFORGE,
    }

    companion object {
        private val METADATA_DIRS = arrayOf("metadata", "dlcpack_metadata")
        private val ICON_EXTENSIONS = arrayOf("png", "jpg", "jpeg", "icn", "ico", "webp")

        @JvmStatic
        fun inspect(path: Path?): Result {
            if (path == null || !Files.isDirectory(path)) {
                return Result.invalid("package root is not a directory")
            }
            val root = path.toAbsolutePath().normalize()
            val cf = detectCurseForge(root)
            if (cf.valid) {
                return cf
            }
            return detectStandard(root)
        }

        private fun detectCurseForge(root: Path): Result {
            val candidates = contentRootCandidates(root)
            for (candidate in candidates) {
                val manifestFile = candidate.resolve("manifest.json")
                if (!Files.exists(manifestFile) || !Files.isDirectory(candidate.resolve("overrides"))) {
                    continue
                }
                val metadata = findCurseForgeMetadata(candidate) ?: continue
                val parsed = readMetadata(metadata.packFile)
                if (!parsed.valid || parsed.metadata == null) {
                    return Result.invalid(parsed.error ?: "invalid pack.dc")
                }
                return Result.valid(
                    DlcPackPackage(
                        root,
                        Format.CURSEFORGE,
                        candidate,
                        candidate.resolve("overrides"),
                        metadata.dir,
                        metadata.packFile,
                        metadata.iconFile,
                        manifestFile,
                        DlcPackManifest.read(manifestFile).files,
                        parsed.metadata,
                    )
                )
            }
            return Result.invalid("missing CurseForge metadata")
        }

        private fun detectStandard(root: Path): Result {
            for (candidate in contentRootCandidates(root)) {
                val metadata = findMetadata(candidate) ?: continue
                val parsed = readMetadata(metadata.packFile)
                if (!parsed.valid || parsed.metadata == null) {
                    return Result.invalid(parsed.error ?: "invalid pack.dc")
                }
                return Result.valid(
                    DlcPackPackage(
                        root,
                        Format.STANDARD,
                        candidate,
                        candidate,
                        metadata.dir,
                        metadata.packFile,
                        metadata.iconFile,
                        null,
                        emptyList(),
                        parsed.metadata,
                    )
                )
            }
            return Result.invalid("missing metadata/pack.dc")
        }

        private fun contentRootCandidates(root: Path): List<Path> {
            val candidates = ArrayList<Path>()
            candidates.add(root)
            try {
                Files.list(root).use { stream ->
                    stream.filter { Files.isDirectory(it) }
                        .sorted(Comparator.comparing { it.fileName.toString().lowercase(Locale.ROOT) })
                        .forEach { candidates.add(it) }
                }
            } catch (_: IOException) {
            }
            return candidates
        }

        private fun findCurseForgeMetadata(contentRoot: Path): MetadataLocation? {
            val overrides = contentRoot.resolve("overrides")
            return findMetadata(overrides) ?: findMetadata(contentRoot)
        }

        private fun findMetadata(contentRoot: Path): MetadataLocation? {
            for (name in METADATA_DIRS) {
                val dir = contentRoot.resolve(name)
                val pack = dir.resolve("pack.dc")
                if (Files.isRegularFile(pack)) {
                    return MetadataLocation(dir, pack, findIcon(dir))
                }
            }
            return null
        }

        private fun findIcon(dir: Path): Path? {
            for (ext in ICON_EXTENSIONS) {
                val icon = dir.resolve("icon.$ext")
                if (Files.isRegularFile(icon)) {
                    return icon
                }
            }
            return null
        }

        private fun readMetadata(path: Path): DlcPackMetadata.Result {
            return DlcPackMetadata.read(DcConfigIO.read(path, DcConfigIO.Layout.GENERIC))
        }
    }

    private class MetadataLocation(
        val dir: Path,
        val packFile: Path,
        val iconFile: Path?,
    )

    class Result private constructor(
        @JvmField val valid: Boolean,
        @JvmField val pack: DlcPackPackage?,
        @JvmField val error: String?,
    ) {
        companion object {
            @JvmStatic
            fun valid(pack: DlcPackPackage): Result = Result(true, pack, null)

            @JvmStatic
            fun invalid(error: String): Result = Result(false, null, error)
        }
    }
}
