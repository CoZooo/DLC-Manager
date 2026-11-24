package com.cozooo.dlc_manager.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

object DlcDcConfigIO {
    enum class Layout {
        DLC_STRUCTURED,
        GENERIC,
    }

    @JvmStatic
    fun read(path: Path?, layout: Layout): JsonObject? {
        if (path == null || !Files.exists(path)) {
            return null
        }
        if (!isDc(path)) {
            return null
        }
        val result = if (layout == Layout.DLC_STRUCTURED && !isNoticeConfigPath(path)) {
            DlcDcFormatUtil.read(path)
        } else {
            DlcDcFormatUtil.readGeneric(path)
        }
        canonicalizeAliases(result)
        return result
    }

    @JvmStatic
    fun write(originalPath: Path?, obj: JsonObject?, layout: Layout): Boolean {
        if (originalPath == null || obj == null) {
            return false
        }
        val target = DlcDcFormatUtil.toDcPath(originalPath) ?: return false
        canonicalizeAliases(obj)
        var source = obj
        if (layout == Layout.DLC_STRUCTURED && isInfoPath(target)) {
            source = stripDownloadKeys(obj)
        }
        if (layout == Layout.DLC_STRUCTURED) {
            migrateAliasSpelling(target)
        }
        val written = if (layout == Layout.DLC_STRUCTURED && !isNoticeConfigPath(target)) {
            DlcDcFormatUtil.write(target, source)
        } else {
            DlcDcFormatUtil.writeGeneric(target, source)
        }
        if (!written) {
            return false
        }
        if (target != originalPath) {
            try {
                Files.deleteIfExists(originalPath)
            } catch (_: IOException) {
            }
        }
        return true
    }

    private fun migrateAliasSpelling(path: Path) {
        if (!Files.exists(path)) return
        try {
            val original = Files.readString(path)
            val migrated = original.replace(Regex("(?m)^(\\s*)(file_name|filename|appliedTarget|isHidden)(\\s*=)")) { match ->
                val canonical = when (match.groupValues[2].lowercase(Locale.ROOT)) {
                    "file_name", "filename" -> "source"
                    "appliedtarget" -> "target"
                    "ishidden" -> "hidden"
                    else -> match.groupValues[2]
                }
                match.groupValues[1] + canonical + match.groupValues[3]
            }
            if (migrated != original) Files.writeString(path, migrated)
        } catch (_: IOException) {
        }
    }

    @JvmStatic
    fun toDcPath(path: Path?): Path? {
        return DlcDcFormatUtil.toDcPath(path)
    }

    @JvmStatic
    fun isDc(path: Path?): Boolean {
        return path != null &&
            path.fileName != null &&
            DlcDcFormatUtil.isDcName(path.fileName.toString())
    }

    @JvmStatic
    fun getIgnoreCase(obj: JsonObject?, key: String?): JsonElement? {
        if (obj == null || key == null) {
            return null
        }
        val direct = obj.get(key)
        if (direct != null) {
            return direct
        }
        for ((candidate, value) in obj.entrySet()) {
            if (candidate != null && candidate.equals(key, ignoreCase = true)) {
                return value
            }
        }
        val aliases = when (key.lowercase(Locale.ROOT)) {
            "source", "file_name", "filename" -> listOf("source", "file_name", "filename")
            "target", "appliedtarget" -> listOf("target", "appliedTarget")
            "hidden", "ishidden" -> listOf("hidden", "isHidden")
            else -> emptyList()
        }
        for (alias in aliases) {
            for ((candidate, value) in obj.entrySet()) {
                if (candidate != null && candidate.equals(alias, ignoreCase = true)) {
                    return value
                }
            }
        }
        return null
    }

    @JvmStatic
    fun hasIgnoreCase(obj: JsonObject?, key: String?): Boolean {
        return getIgnoreCase(obj, key) != null
    }

    @JvmStatic
    fun getObjectIgnoreCase(obj: JsonObject?, key: String?): JsonObject? {
        val element = getIgnoreCase(obj, key)
        return if (element != null && element.isJsonObject) element.asJsonObject else null
    }

    @JvmStatic
    fun getArrayIgnoreCase(obj: JsonObject?, key: String?): JsonArray? {
        val element = getIgnoreCase(obj, key)
        return if (element != null && element.isJsonArray) element.asJsonArray else null
    }

    @JvmStatic
    fun getStringIgnoreCase(obj: JsonObject?, key: String?): String? {
        val element = getIgnoreCase(obj, key)
        return if (element != null && element.isJsonPrimitive) element.asString else null
    }

    @JvmStatic
    fun getBooleanIgnoreCase(obj: JsonObject?, key: String?): Boolean? {
        val element = getIgnoreCase(obj, key)
        return if (element != null && element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) {
            element.asBoolean
        } else {
            null
        }
    }

    @JvmStatic
    fun getBooleanIgnoreCase(obj: JsonObject?, key: String?, fallback: Boolean): Boolean {
        val value = getBooleanIgnoreCase(obj, key)
        return value ?: fallback
    }

    @JvmStatic
    fun normalizeLangKey(key: String?): String? {
        return key?.lowercase(Locale.ROOT)
    }

    @JvmStatic
    fun removeIgnoreCase(obj: JsonObject?, key: String?) {
        if (obj == null || key == null) {
            return
        }
        if (obj.has(key)) {
            obj.remove(key)
            return
        }
        var matched: String? = null
        for (candidate in obj.keySet()) {
            if (candidate != null && candidate.equals(key, ignoreCase = true)) {
                matched = candidate
                break
            }
        }
        if (matched != null) {
            obj.remove(matched)
        }
    }

    @JvmStatic
    fun canonicalizeAliases(obj: JsonObject?): Boolean {
        if (obj == null) {
            return false
        }
        var changed = false
        changed = promoteAlias(obj, "source", listOf("file_name", "filename")) || changed
        changed = promoteAlias(obj, "target", listOf("appliedTarget")) || changed
        changed = promoteAlias(obj, "hidden", listOf("isHidden")) || changed
        for ((_, value) in obj.entrySet()) {
            if (value.isJsonObject) {
                changed = canonicalizeAliases(value.asJsonObject) || changed
            } else if (value.isJsonArray) {
                for (child in value.asJsonArray) {
                    if (child.isJsonObject) {
                        changed = canonicalizeAliases(child.asJsonObject) || changed
                    }
                }
            }
        }
        return changed
    }

    private fun promoteAlias(obj: JsonObject, canonical: String, aliases: List<String>): Boolean {
        var changed = false
        var replacement: JsonElement? = obj.get(canonical)
        var replaced = false
        val rebuilt = JsonObject()
        for ((key, value) in obj.entrySet()) {
            if (key.equals(canonical, ignoreCase = true)) {
                if (key != canonical) changed = true
                rebuilt.add(canonical, value)
                replacement = value
                replaced = true
            } else if (aliases.any { key.equals(it, ignoreCase = true) }) {
                if (!replaced && replacement == null) {
                    rebuilt.add(canonical, value.deepCopy())
                    replacement = value
                    replaced = true
                }
                changed = true
            } else {
                rebuilt.add(key, value)
            }
        }
        if (!replaced && replacement != null) rebuilt.add(canonical, replacement)
        if (changed) {
            obj.entrySet().clear()
            rebuilt.entrySet().forEach { (key, value) -> obj.add(key, value) }
        }
        return changed
    }

    private fun findIgnoreCase(obj: JsonObject, key: String): JsonElement? {
        for ((candidate, value) in obj.entrySet()) {
            if (candidate.equals(key, ignoreCase = true)) {
                return value
            }
        }
        return null
    }

    private fun isInfoPath(path: Path?): Boolean {
        if (path == null) {
            return false
        }
        val normalized = path.toString().replace('\\', '/').lowercase(Locale.ROOT)
        return normalized.contains("/info/")
    }

    private fun isNoticeConfigPath(path: Path?): Boolean {
        if (path == null || path.fileName == null) {
            return false
        }
        val base = DlcDcFormatUtil.stripConfigExtension(path.fileName.toString())
        return "notice".equals(base, ignoreCase = true)
    }

    private fun stripDownloadKeys(input: JsonObject?): JsonObject? {
        if (input == null) {
            return null
        }
        val out = JsonObject()
        for ((key, value) in input.entrySet()) {
            if (key == null) {
                continue
            }
            if (key.equals("modrinth_projectid", ignoreCase = true) ||
                key.equals("modrinth_versionid", ignoreCase = true) ||
                key.equals("curseforge_projectid", ignoreCase = true) ||
                key.equals("curseforge_fileid", ignoreCase = true) ||
                key.equals("directUrl", ignoreCase = true) ||
                key.equals("priority", ignoreCase = true)
            ) {
                continue
            }
            out.add(key, value)
        }
        return out
    }

}
