package com.cozooo.dlc_manager.client.preset

import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
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
                            val entry = readEntry(path)
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

    private fun readEntry(path: Path): DlcPresetEntry? {
        val obj = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC) ?: return null
        val basic = DcConfigIO.getObjectIgnoreCase(obj, "basic")
        val displayName = readLangMap(DcConfigIO.getObjectIgnoreCase(obj, "DisplayName"))
        val tip = readLangMap(DcConfigIO.getObjectIgnoreCase(obj, "tip"))
        val enable = readRelationList(DcConfigIO.getIgnoreCase(obj, "dependencies"), DcConfigIO.getIgnoreCase(obj, "enable"))
        val conflicts = readRelationList(DcConfigIO.getIgnoreCase(obj, "conflicts"), null)
        val alarm = DcConfigIO.getBooleanIgnoreCase(basic, "alarm")
            ?: DcConfigIO.getBooleanIgnoreCase(obj, "alarm", false)

        val fileName = stripExtension(path.fileName.toString())
        val id = readIdentifier(basic, obj, fileName)
        migrateIfNeeded(path, obj, id, alarm, enable, conflicts)
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

    private fun readRelationList(primary: JsonElement?, legacy: JsonElement?): List<String> {
        val result = LinkedHashSet<String>()
        collectRelationNames(primary, result)
        collectRelationNames(legacy, result)
        return ArrayList(result)
    }

    private fun collectRelationNames(element: JsonElement?, result: MutableSet<String>) {
        if (element == null || element.isJsonNull) {
            return
        }
        if (element.isJsonPrimitive) {
            addSplitNames(element.asString, result)
            return
        }
        if (element.isJsonArray) {
            for (child in element.asJsonArray) {
                collectRelationNames(child, result)
            }
            return
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val identifier = DcConfigIO.getStringIgnoreCase(obj, "identifier")
            if (!identifier.isNullOrBlank()) {
                result.add(identifier.trim())
                return
            }
            for ((key, value) in obj.entrySet()) {
                val before = result.size
                collectRelationNames(value, result)
                if (result.size == before && !key.isNullOrBlank()) {
                    result.add(key.trim())
                }
            }
        }
    }

    private fun addSplitNames(value: String?, result: MutableSet<String>) {
        if (value.isNullOrBlank()) {
            return
        }
        for (part in value.split(Regex("[\\n\\r,]+"))) {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                result.add(trimmed)
            }
        }
    }

    private fun readIdentifier(basic: JsonObject?, obj: JsonObject, fallback: String): String {
        val identifier = DcConfigIO.getStringIgnoreCase(basic, "identifier")?.trim()
        if (!identifier.isNullOrEmpty()) {
            return identifier
        }
        val legacyIdentifier = DcConfigIO.getStringIgnoreCase(obj, "identifier")?.trim()
        if (!legacyIdentifier.isNullOrEmpty()) {
            return legacyIdentifier
        }
        return fallback
    }

    private fun migrateIfNeeded(path: Path, obj: JsonObject, identifier: String, alarm: Boolean, enable: List<String>, conflicts: List<String>) {
        val basic = DcConfigIO.getObjectIgnoreCase(obj, "basic")
        val hasMissingIdentifier = DcConfigIO.getStringIgnoreCase(basic, "identifier").isNullOrBlank()
        val hasRootIdentifier = DcConfigIO.hasIgnoreCase(obj, "identifier")
        val hasRootAlarm = DcConfigIO.hasIgnoreCase(obj, "alarm")
        val hasLegacyEnable = DcConfigIO.hasIgnoreCase(obj, "enable")
        val conflictsElement = DcConfigIO.getIgnoreCase(obj, "conflicts")
        val hasLegacyConflicts = conflictsElement != null && !conflictsElement.isJsonObject
        if (!hasMissingIdentifier && !hasRootIdentifier && !hasRootAlarm && !hasLegacyEnable && !hasLegacyConflicts) {
            return
        }
        val migrated = JsonObject()
        val migratedBasic = JsonObject()
        migratedBasic.addProperty("identifier", identifier)
        migratedBasic.addProperty("alarm", alarm)
        if (basic != null) {
            for ((key, value) in basic.entrySet()) {
                if (key.equals("identifier", ignoreCase = true) || key.equals("alarm", ignoreCase = true)) {
                    continue
                }
                migratedBasic.add(key, value)
            }
        }
        migrated.add("basic", migratedBasic)
        migrated.add("dependencies", relationObject(enable))
        migrated.add("conflicts", relationObject(conflicts))
        for ((key, value) in obj.entrySet()) {
            if (key.equals("basic", ignoreCase = true) ||
                key.equals("identifier", ignoreCase = true) ||
                key.equals("alarm", ignoreCase = true) ||
                key.equals("enable", ignoreCase = true) ||
                key.equals("dependencies", ignoreCase = true) ||
                key.equals("conflicts", ignoreCase = true)
            ) {
                continue
            }
            migrated.add(key, value)
        }
        DcConfigIO.write(path, migrated, DcConfigIO.Layout.GENERIC)
    }

    private fun relationObject(values: List<String>): JsonObject {
        val out = JsonObject()
        for (value in values) {
            if (value.isNotBlank()) {
                out.add(value.trim(), JsonPrimitive(value.trim()))
            }
        }
        return out
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
