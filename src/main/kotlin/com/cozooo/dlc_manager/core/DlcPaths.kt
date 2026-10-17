package com.cozooo.dlc_manager.core

import com.cozooo.dlc_manager.platform.DlcPlatformServices
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object DlcPaths {
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
        return gameDir().resolve("tacz")
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
        try {
            return Path.of(raw)
        } catch (_: InvalidPathException) {
        }
        val repaired = repairEscapedControlChars(raw)
        if (repaired == raw || repaired.isBlank()) {
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
