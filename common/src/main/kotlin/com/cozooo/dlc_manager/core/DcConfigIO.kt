package com.cozooo.dlc_manager.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

object DcConfigIO {
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
        if (layout == Layout.DLC_STRUCTURED && !isNoticeConfigPath(path)) {
            return DcFormatBridge.read(path)
        }
        return DcFormatBridge.readGeneric(path)
    }

    @JvmStatic
    fun write(originalPath: Path?, obj: JsonObject?, layout: Layout): Boolean {
        if (originalPath == null || obj == null) {
            return false
        }
        val target = DcFormatBridge.toDcPath(originalPath) ?: return false
        var source = obj
        if (layout == Layout.DLC_STRUCTURED && isInfoPath(target)) {
            source = stripDownloadKeys(obj)
        }
        val written = if (layout == Layout.DLC_STRUCTURED && !isNoticeConfigPath(target)) {
            DcFormatBridge.write(target, source)
        } else {
            DcFormatBridge.writeGeneric(target, source)
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

    @JvmStatic
    fun toDcPath(path: Path?): Path? {
        return DcFormatBridge.toDcPath(path)
    }

    @JvmStatic
    fun isDc(path: Path?): Boolean {
        return path != null &&
            path.fileName != null &&
            DcFormatBridge.isDcName(path.fileName.toString())
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
        val base = DcFormatBridge.stripConfigExtension(path.fileName.toString())
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

    private object DcFormatBridge {
        fun read(path: Path): JsonObject? {
            return DcFormatUtil.read(path)
        }

        fun readGeneric(path: Path): JsonObject? {
            return DcFormatUtil.readGeneric(path)
        }

        fun write(path: Path, obj: JsonObject?): Boolean {
            return DcFormatUtil.write(path, obj)
        }

        fun writeGeneric(path: Path, obj: JsonObject?): Boolean {
            return DcFormatUtil.writeGeneric(path, obj)
        }

        fun toDcPath(path: Path?): Path? {
            return DcFormatUtil.toDcPath(path)
        }

        fun isDcName(name: String): Boolean {
            return DcFormatUtil.isDcName(name)
        }

        fun stripConfigExtension(name: String): String? {
            return DcFormatUtil.stripConfigExtension(name)
        }
    }
}
