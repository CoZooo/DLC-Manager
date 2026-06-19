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
            "mafglib-",
            "malilib-",
            "kotlinforforge-",
            "fabric-language-kotlin-"
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
            return isDefaultForbiddenPath(normalized) || isConfiguredDlcForbiddenPath(normalized, instanceRoot, dlcRoot)
        }

        private fun isDefaultForbiddenPath(normalized: String): Boolean {
            return normalized == "config/dlc_manager" ||
                normalized.startsWith("config/dlc_manager/")
        }

        private fun isConfiguredDlcForbiddenPath(normalized: String, instanceRoot: Path?, dlcRoot: Path?): Boolean {
            val dlcRelative = relativeDlcRoot(instanceRoot, dlcRoot)
            if (dlcRelative.isNullOrBlank()) {
                return false
            }
            return normalized == "$dlcRelative/tmp" ||
                normalized.startsWith("$dlcRelative/tmp/") ||
                normalized == "$dlcRelative/info/version.dc" ||
                normalized == "$dlcRelative/info/notice.dc" ||
                normalized == "$dlcRelative/required/notice.dc" ||
                normalized == "$dlcRelative/required/skip" ||
                normalized == "$dlcRelative/update" ||
                normalized.startsWith("$dlcRelative/update/") ||
                normalized == "$dlcRelative/shownonfirstrun"
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
