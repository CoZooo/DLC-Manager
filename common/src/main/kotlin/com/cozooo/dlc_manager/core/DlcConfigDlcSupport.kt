package com.cozooo.dlc_manager.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

object DlcConfigDlcSupport {
    @JvmStatic
    fun isConfigType(item: DlcItem?): Boolean {
        return item != null && item.getType() == DlcType.UNKNOWN && item.isConfigType()
    }

    @JvmStatic
    fun backupRoot(): Path {
        val roots = getDlcRoots()
        val base = if (roots.isEmpty()) gameDir() else roots[0]
        return DlcTmpPaths.resolve(base, "config_backup") ?: base.resolve("tmp").resolve("config_backup")
    }

    @JvmStatic
    fun backupRootForItem(itemId: String?): Path {
        val key = if (itemId.isNullOrBlank()) "unknown" else sanitizeFilePart(itemId)
        return backupRoot().resolve(key)
    }

    @JvmStatic
    fun backupPathForItemTarget(itemId: String?, target: Path): Path {
        val normalized = target.toAbsolutePath().normalize()
        val digest = sha1(normalized.toString())
        val leaf = normalized.fileName?.toString()?.let(::sanitizeFilePart) ?: "root"
        return backupRootForItem(itemId).resolve("${digest}_${leaf}.bak")
    }

    @JvmStatic
    fun backupPathForTarget(target: Path): Path {
        val normalized = target.toAbsolutePath().normalize()
        val digest = sha1(normalized.toString())
        val leaf = normalized.fileName?.toString()?.let(::sanitizeFilePart) ?: "root"
        return backupRoot().resolve("${digest}_${leaf}.bak")
    }

    @JvmStatic
    fun missingMarkerPath(backupPath: Path): Path {
        return backupPath.resolveSibling(backupPath.fileName.toString() + ".missing")
    }

    @JvmStatic
    fun hasBackupForItemTarget(itemId: String?, target: Path): Boolean {
        val backup = backupPathForItemTarget(itemId, target)
        return Files.exists(backup) || Files.exists(missingMarkerPath(backup))
    }

    @JvmStatic
    fun hasBackupForTarget(target: Path): Boolean {
        val backup = backupPathForTarget(target)
        return Files.exists(backup) || Files.exists(missingMarkerPath(backup))
    }

    private fun sha1(value: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(value.toByteArray(StandardCharsets.UTF_8))
            HexFormat.of().formatHex(digest)
        } catch (_: Exception) {
            value.hashCode().toString(16)
        }
    }

    private fun sanitizeFilePart(raw: String): String {
        val cleaned = raw.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return if (cleaned.isBlank()) "target" else cleaned
    }

    private fun getDlcRoots(): List<Path> {
        return DlcConfig.getDlcRoots()
    }

    private fun gameDir(): Path {
        return DlcPaths.gameDir()
    }
}
