package com.cozooo.dlc_manager.client.pack

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.stream.Stream

class DlcPackInstallPlan private constructor(
    @JvmField val files: List<FileEntry>,
    @JvmField val forbiddenPaths: List<String>,
) {
    val valid: Boolean
        get() = true

    class FileEntry(
        @JvmField val source: Path,
        @JvmField val relativePath: String,
    )

    companion object {
        private val METADATA_DIR_NAMES = setOf("metadata", "dlcpack_metadata")
        private val IGNORED_JAR_PREFIXES = listOf(
            "DLC-Manager-",
            "architectury-",
            "kotlinforforge-",
            "fabric-language-kotlin-"
        )
        private val GAME_ROOT_FORBIDDEN_PATHS = listOf(
            "config/dlc_manager/",
        )
        private val DLC_ROOT_FORBIDDEN_PATHS = listOf(
            "tmp/",
            "info/version.dc",
            "info/notice.dc",
            "required/skip",
            "update/",
            "shownonfirstrun",
            "skip"
        )

        @JvmStatic
        fun build(pack: DlcPackPackage?): DlcPackInstallPlan {
            return build(pack, null, null)
        }

        @JvmStatic
        fun build(pack: DlcPackPackage?, instanceRoot: Path?, dlcRoot: Path?): DlcPackInstallPlan {
            if (pack == null || !Files.isDirectory(pack.installRoot)) {
                return DlcPackInstallPlan(emptyList(), emptyList())
            }
            val files = ArrayList<FileEntry>()
            val forbidden = ArrayList<String>()
            try {
                Files.walk(pack.installRoot).use { stream: Stream<Path> ->
                    stream.filter { Files.isRegularFile(it) }.forEach { source ->
                        val relative = normalizeRelative(pack.installRoot.relativize(source)) ?: return@forEach
                        if (isMetadataFile(relative)) {
                            return@forEach
                        }
                        if (isIgnoredModpackJarPath(relative)) {
                            return@forEach
                        }
                        if (isForbiddenPath(relative, instanceRoot, dlcRoot)) {
                            forbidden.add(relative)
                            return@forEach
                        }
                        files.add(FileEntry(source, relative))
                    }
                }
            } catch (_: IOException) {
            }
            files.sortBy { it.relativePath.lowercase(Locale.ROOT) }
            forbidden.sort()
            return DlcPackInstallPlan(files, forbidden)
        }

        @JvmStatic
        fun merge(base: DlcPackInstallPlan?, extraFiles: List<FileEntry>?): DlcPackInstallPlan {
            return merge(base, extraFiles, null, null)
        }

        @JvmStatic
        fun merge(base: DlcPackInstallPlan?, extraFiles: List<FileEntry>?, instanceRoot: Path?, dlcRoot: Path?): DlcPackInstallPlan {
            val current = base ?: DlcPackInstallPlan(emptyList(), emptyList())
            val files = ArrayList(current.files)
            if (extraFiles != null) {
                for (entry in extraFiles) {
                    val relative = entry.relativePath
                    if (relative.isBlank() || isMetadataFile(relative) || isForbiddenPath(relative, instanceRoot, dlcRoot)) {
                        continue
                    }
                    if (isIgnoredModpackJarPath(relative)) {
                        continue
                    }
                    if (files.none { it.relativePath == relative }) {
                        files.add(entry)
                    }
                }
            }
            files.sortBy { it.relativePath.lowercase(Locale.ROOT) }
            return DlcPackInstallPlan(files, current.forbiddenPaths)
        }

        @JvmStatic
        fun isForbiddenPath(relativePath: String?): Boolean {
            return isForbiddenPath(relativePath, null, null)
        }

        @JvmStatic
        fun isForbiddenPath(relativePath: String?, instanceRoot: Path?, dlcRoot: Path?): Boolean {
            val normalized = DlcPackMeta.normalizeRelativePath(relativePath)?.lowercase(Locale.ROOT) ?: return true
            if (matchesForbiddenPathList(normalized, GAME_ROOT_FORBIDDEN_PATHS, null)) {
                return true
            }
            return matchesForbiddenPathList(normalized, DLC_ROOT_FORBIDDEN_PATHS, relativeDlcRoot(instanceRoot, dlcRoot))
        }

        private fun matchesForbiddenPathList(normalized: String, paths: List<String>?, prefix: String?): Boolean {
            if (paths.isNullOrEmpty()) {
                return false
            }
            val normalizedPrefix = normalizeForbiddenPath(prefix)
            for (path in paths) {
                var resolved = normalizeForbiddenPath(path) ?: continue
                if (!normalizedPrefix.isNullOrBlank()) {
                    resolved = "$normalizedPrefix/$resolved"
                }
                val includeChildren = resolved.endsWith("/")
                if (includeChildren) {
                    resolved = resolved.dropLast(1)
                }
                if (normalized == resolved || includeChildren && normalized.startsWith("$resolved/")) {
                    return true
                }
            }
            return false
        }

        private fun normalizeForbiddenPath(path: String?): String? {
            if (path.isNullOrBlank()) {
                return null
            }
            val includeChildren = path.replace('\\', '/').trim().endsWith("/")
            val normalized = DlcPackMeta.normalizeRelativePath(path)?.lowercase(Locale.ROOT) ?: return null
            return if (includeChildren) "$normalized/" else normalized
        }

        private fun relativeDlcRoot(instanceRoot: Path?, dlcRoot: Path?): String? {
            if (instanceRoot == null || dlcRoot == null) {
                return null
            }
            val normalizedInstance = instanceRoot.toAbsolutePath().normalize()
            val normalizedDlc = dlcRoot.toAbsolutePath().normalize()
            if (!normalizedDlc.startsWith(normalizedInstance)) {
                return null
            }
            return DlcPackMeta.normalizeRelativePath(normalizedInstance.relativize(normalizedDlc).toString())
                ?.lowercase(Locale.ROOT)
        }

        private fun normalizeRelative(path: Path): String? {
            return DlcPackMeta.normalizeRelativePath(path.toString())
        }

        private fun isMetadataFile(relative: String): Boolean {
            val first = relative.substringBefore('/').lowercase(Locale.ROOT)
            return METADATA_DIR_NAMES.contains(first)
        }

        @JvmStatic
        fun isIgnoredModpackJarPath(relativePath: String?): Boolean {
            val normalized = DlcPackMeta.normalizeRelativePath(relativePath)?.trim() ?: return false
            val fileName = normalized.substringAfterLast('/')
            return isIgnoredModpackJarFileName(fileName)
        }

        @JvmStatic
        fun isIgnoredModpackJarFileName(fileName: String?): Boolean {
            val normalized = fileName?.trim() ?: return false
            return normalized.endsWith(".jar", ignoreCase = true) &&
                IGNORED_JAR_PREFIXES.any { normalized.startsWith(it) }
        }

    }
}
