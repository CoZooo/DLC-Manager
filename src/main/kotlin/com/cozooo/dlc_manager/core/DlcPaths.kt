package com.cozooo.dlc_manager.core

import com.cozooo.dlc_manager.platform.DlcPlatformServices
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object DlcPaths {
    private const val MANAGER_CONFIG_DIR = "dlc_manager"

    @JvmStatic
    fun gameDir(): Path {
        return DlcPlatformServices.gameDir()
    }

    @JvmStatic
    fun dlcRoot(): Path {
        return DlcConfig.getDlcRoot()
    }

    @JvmStatic
    fun tmpRoot(): Path {
        return dlcRoot().resolve("tmp")
    }

    /** Canonical manager-owned configuration directory under the instance config directory. */
    @JvmStatic
    fun managerConfigDir(): Path {
        return DlcPlatformServices.configDir().resolve(MANAGER_CONFIG_DIR).toAbsolutePath().normalize()
    }

    @JvmStatic
    fun versionConfigPath(): Path = managerConfigDir().resolve("version.dc")

    @JvmStatic
    fun noticeConfigPath(): Path = managerConfigDir().resolve("notice.dc")

    /**
     * Resolve the canonical version path and migrate the old DLC/info/version.dc file when present.
     * The legacy path is returned only when migration cannot be completed.
     */
    @JvmStatic
    fun resolveVersionConfigPath(dlcRoot: Path?): Path {
        val canonical = versionConfigPath()
        if (Files.exists(canonical)) return canonical
        val legacy = legacyDlcConfigPath(dlcRoot, "version")
        return migrateLegacyDlcConfigFile(legacy, canonical)
    }

    /** Resolve the canonical notice path and migrate the old DLC/info/notice.dc file when present. */
    @JvmStatic
    fun resolveNoticeConfigPath(dlcRoot: Path?): Path {
        val canonical = noticeConfigPath()
        if (Files.exists(canonical)) return canonical
        val legacy = legacyDlcConfigPath(dlcRoot, "notice")
        return migrateLegacyDlcConfigFile(legacy, canonical)
    }

    @JvmStatic
    fun legacyDlcConfigPath(dlcRoot: Path?, fileName: String): Path? {
        if (dlcRoot == null || fileName.isBlank()) return null
        return DlcDcFormatUtil.resolveConfigPath(dlcRoot.resolve("info"), fileName)
    }

    private fun migrateLegacyDlcConfigFile(legacy: Path?, canonical: Path): Path {
        if (legacy == null || !Files.exists(legacy)) return canonical
        return try {
            Files.createDirectories(canonical.parent)
            try {
                Files.move(legacy, canonical, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: IOException) {
                Files.move(legacy, canonical)
            }
            canonical
        } catch (_: IOException) {
            legacy
        }
    }

    @JvmStatic
    fun migrateLegacyConfigFile(fileName: String): Path {
        val target = tmpRoot().resolve(fileName)
        val legacy = DlcPlatformServices.configDir().resolve("dlc_manager").resolve(fileName)
        if (Files.exists(target) || !Files.exists(legacy)) {
            return target
        }
        return try {
            Files.createDirectories(target.parent)
            try {
                Files.move(legacy, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: IOException) {
                Files.move(legacy, target)
            }
            target
        } catch (_: IOException) {
            legacy
        }
    }

    @JvmStatic
    fun modsDir(): Path {
        return gameDir().resolve("mods")
    }

    @JvmStatic
    fun resourcePacksDir(): Path {
        return DlcConfig.getResourcepackRoot()
    }

    @JvmStatic
    fun taczDir(): Path {
        return DlcConfig.getTaczRoot()
    }

    @JvmStatic
    fun datapackStagingDir(): Path {
        return DlcConfig.getDatapackRoot()
    }

    @JvmStatic
    fun resolveAppliedTarget(raw: String?): Path? {
        var path = parsePathLenient(raw) ?: return null
        if (!path.isAbsolute) {
            path = gameDir().resolve(path)
        }
        return path.normalize()
    }

    @JvmStatic
    fun resolveAgainstGameDir(raw: String?): Path? {
        var path = parsePathLenient(raw) ?: return null
        if (!path.isAbsolute) {
            path = gameDir().resolve(path)
        }
        return path.normalize()
    }

    @JvmStatic
    fun parsePathLenient(raw: String?): Path? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val normalizedSeparators = raw.replace('\\', '/')
        try {
            return Path.of(normalizedSeparators)
        } catch (_: InvalidPathException) {
        }
        val repaired = repairEscapedControlChars(normalizedSeparators)
        if (repaired == normalizedSeparators || repaired.isBlank()) {
            return null
        }
        return try {
            Path.of(repaired)
        } catch (_: InvalidPathException) {
            null
        }
    }

    @JvmStatic
    fun isAppliedTargetDirectory(raw: String?, resolved: Path?, source: Path?): Boolean {
        if (raw != null && (raw.endsWith("/") || raw.endsWith("\\"))) {
            return true
        }
        if (source != null && Files.isDirectory(source)) {
            return true
        }
        if (resolved == null) {
            return false
        }
        if (!Files.exists(resolved)) {
            return true
        }
        return Files.isDirectory(resolved)
    }

    private fun repairEscapedControlChars(raw: String): String {
        val builder = StringBuilder(raw.length + 8)
        var changed = false
        for (ch in raw) {
            when (ch) {
                '\t' -> {
                    builder.append('\\').append('t')
                    changed = true
                }
                '\n' -> {
                    builder.append('\\').append('n')
                    changed = true
                }
                '\r' -> {
                    builder.append('\\').append('r')
                    changed = true
                }
                '\b' -> {
                    builder.append('\\').append('b')
                    changed = true
                }
                '\u000C' -> {
                    builder.append('\\').append('f')
                    changed = true
                }
                else -> builder.append(ch)
            }
        }
        return if (changed) builder.toString() else raw
    }
}
