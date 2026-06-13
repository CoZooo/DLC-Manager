package com.cozooo.dlc_manager.client.preset

import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DlcPresetLoader {
    @JvmStatic
    fun loadAll(): List<DlcPresetEntry> {
        return loadAll(getDlcRoots())
    }

    @JvmStatic
    fun loadAll(roots: List<Path>?): List<DlcPresetEntry> {
        val entries = ArrayList<DlcPresetEntry>()
        if (roots.isNullOrEmpty()) {
            return entries
        }
        val seen = HashSet<Path>()
        for (root in roots) {
            if (seen.contains(root)) {
                continue
            }
            seen.add(root)
            val presetRoot = ensurePresetRoot(root) ?: continue
            if (!Files.isDirectory(presetRoot)) {
                continue
            }
            try {
                Files.walk(presetRoot).use { stream ->
                    stream.filter { path -> path.fileName != null && DcConfigIO.isDc(path) }
                        .forEach { path ->
                            val entry = readEntry(path, presetRoot)
                            if (entry != null) {
                                entries.add(entry)
                            }
                        }
                }
            } catch (_: IOException) {
            }
        }
        entries.sortWith { a, b -> (a.getFileName() ?: "").compareTo(b.getFileName() ?: "", ignoreCase = true) }
        return entries
    }

    @JvmStatic
    fun ensurePresetRoot(dlcRoot: Path?): Path? {
        if (dlcRoot == null) {
            return null
        }
        val presetRoot = dlcRoot.resolve("preset")
        if (!Files.exists(presetRoot)) {
            try {
                Files.createDirectories(presetRoot)
            } catch (_: IOException) {
            }
        }
        return presetRoot
    }

    private fun readEntry(path: Path, presetRoot: Path): DlcPresetEntry? {
        val obj = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC) ?: return null
        val displayName = readLangMap(DcConfigIO.getObjectIgnoreCase(obj, "DisplayName"))
        val tip = readLangMap(DcConfigIO.getObjectIgnoreCase(obj, "tip"))
        val enable = readStringArray(DcConfigIO.getArrayIgnoreCase(obj, "enable"))
        val conflicts = readStringArray(DcConfigIO.getArrayIgnoreCase(obj, "conflicts"))
        val alarm = DcConfigIO.getBooleanIgnoreCase(obj, "alarm", false)

        val fileName = stripExtension(path.fileName.toString())
        val id = buildId(path, presetRoot)
        return DlcPresetEntry(id, fileName, path, displayName, tip, enable, conflicts, alarm, DlcPresetStatus.OFF)
    }

    private fun readLangMap(obj: JsonObject?): Map<String, String> {
        if (obj == null || obj.isJsonNull) {
            return emptyMap()
        }
        val map = HashMap<String, String>()
        for ((key, value) in obj.entrySet()) {
            if (value == null || value.isJsonNull) {
                continue
            }
            try {
                val text = value.asString
                if (text != null) {
                    val normalized = DcConfigIO.normalizeLangKey(key)
                    if (normalized != null) {
                        map[normalized] = text
                    }
                }
            } catch (_: Exception) {
            }
        }
        return map
    }

    private fun readStringArray(array: JsonArray?): List<String> {
        if (array == null || array.isJsonNull) {
            return emptyList()
        }
        val result = ArrayList<String>()
        for (element in array) {
            if (element == null || element.isJsonNull) {
                continue
            }
            try {
                val value = element.asString
                if (value != null) {
                    val trimmed = value.trim()
                    if (trimmed.isNotEmpty()) {
                        result.add(trimmed)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun buildId(path: Path, presetRoot: Path?): String {
        if (presetRoot == null) {
            return stripExtension(path.fileName.toString())
        }
        return try {
            val relative = presetRoot.relativize(path).toString().replace('\\', '/')
            stripExtension(relative)
        } catch (_: Exception) {
            stripExtension(path.fileName.toString())
        }
    }

    private fun stripExtension(name: String?): String {
        if (name == null) {
            return ""
        }
        return if (name.lowercase().endsWith(".dc")) name.substring(0, name.length - 3) else name
    }

    private fun getDlcRoots(): List<Path> {
        return DlcPathConfig.getDlcRoots()
    }
}
