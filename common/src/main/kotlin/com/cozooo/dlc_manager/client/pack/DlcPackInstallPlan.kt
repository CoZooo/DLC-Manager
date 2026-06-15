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
        get() = forbiddenPaths.isEmpty()

    class FileEntry(
        @JvmField val source: Path,
        @JvmField val relativePath: String,
    )

    companion object {
        private val METADATA_DIR_NAMES = setOf("metadata", "dlcpack_metadata")

        @JvmStatic
        fun build(pack: DlcPackPackage?): DlcPackInstallPlan {
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
                        if (isForbiddenPath(relative)) {
                            forbidden.add(relative)
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
            val current = base ?: DlcPackInstallPlan(emptyList(), emptyList())
            val files = ArrayList(current.files)
            if (extraFiles != null) {
                for (entry in extraFiles) {
                    val relative = entry.relativePath
                    if (relative.isBlank() || isMetadataFile(relative) || isForbiddenPath(relative)) {
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
            val normalized = DlcPackMeta.normalizeRelativePath(relativePath)?.lowercase(Locale.ROOT) ?: return true
            return normalized == "config/dlc_manager" ||
                normalized.startsWith("config/dlc_manager/") ||
                normalized == "dlc/tmp" ||
                normalized.startsWith("dlc/tmp/") ||
                normalized == "dlc/info/version.dc" ||
                normalized == "dlc/info/notice.dc" ||
                normalized == "dlc/required/notice.dc" ||
                normalized == "dlc/required/skip" ||
                normalized == "dlc/update" ||
                normalized.startsWith("dlc/update/") ||
                normalized == "dlc/shownonfirstrun"
        }

        private fun normalizeRelative(path: Path): String? {
            return DlcPackMeta.normalizeRelativePath(path.toString())
        }

        private fun isMetadataFile(relative: String): Boolean {
            val first = relative.substringBefore('/').lowercase(Locale.ROOT)
            return METADATA_DIR_NAMES.contains(first)
        }
    }
}
