package com.cozooo.dlc_manager.client.remote

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.core.DcFormatUtil
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.cozooo.dlc_manager.core.DlcType
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.IOException
import java.nio.file.Files

object DlcRemoteRuntime {
    private var promptedThisSession = false

    @JvmStatic
    fun hasMissing(): Boolean {
        return collectMissing(false).isNotEmpty()
    }

    @JvmStatic
    fun hasMissingDlcPacks(): Boolean {
        return collectMissing(true).isNotEmpty()
    }

    @JvmStatic
    fun pollMissingForManagerPrompt(): List<DlcRemoteEntry> {
        if (promptedThisSession) {
            return emptyList()
        }
        val dlcRoot = DlcPathConfig.getDlcRoots().firstOrNull()
        if (dlcRoot != null && DlcRemoteMarker.hasPartMarker(dlcRoot, DlcRemoteMarker.PART_DLC)) {
            return emptyList()
        }
        val missing = collectMissing(false)
        if (missing.isNotEmpty()) {
            promptedThisSession = true
        }
        return missing
    }

    @JvmStatic
    fun markPrompted() {
        promptedThisSession = true
    }

    @JvmStatic
    fun collectMissing(): List<DlcRemoteEntry> {
        return collectMissing(false)
    }

    @JvmStatic
    fun collectMissingDlcPacks(): List<DlcRemoteEntry> {
        return collectMissing(true)
    }

    private fun collectMissing(dlcPackOnly: Boolean): List<DlcRemoteEntry> {
        val missing = ArrayList<DlcRemoteEntry>()
        val dlcRoot = DlcPathConfig.getDlcRoots().firstOrNull() ?: return missing
        val entries = DlcRemoteLoader.load(dlcRoot)
        for (entry in entries) {
            if (!entry.isInList() && !entry.isHidden()) {
                continue
            }
            val isPack = entry.getType() == DlcType.DLC_PACK
            if (dlcPackOnly != isPack) {
                continue
            }
            val target = entry.getTargetPath()
            if (target == null || !Files.exists(target)) {
                missing.add(entry)
            }
        }
        return missing
    }

    @JvmStatic
    fun updateFileNameKey(entry: DlcRemoteEntry?, fileName: String?) {
        if (entry == null) {
            return
        }
        val normalized = normalizeFileName(fileName) ?: return
        val config = entry.getConfigPath()
        if (config == null || !Files.exists(config)) {
            return
        }
        val obj = DcConfigIO.read(config, DcConfigIO.Layout.DLC_STRUCTURED) ?: return
        val reordered = reorderRemoteJson(obj, normalized)
        val target = DcFormatUtil.toDcPath(config)
        if (target != null && DcFormatUtil.write(target, reordered)) {
            if (target != config) {
                try {
                    Files.deleteIfExists(config)
                } catch (_: IOException) {
                }
            }
            entry.setConfigPath(target)
        }
        entry.setFileNameHint(normalized)
    }

    private fun reorderRemoteJson(obj: JsonObject, fileNameOverride: String?): JsonObject {
        val ordered = JsonObject()
        ordered.addProperty("modrinth_projectid", getString(obj, "modrinth_projectid"))
        ordered.addProperty("modrinth_versionid", getString(obj, "modrinth_versionid"))

        val directUrl = obj.get("directUrl")
        if (directUrl == null || directUrl.isJsonNull) {
            ordered.addProperty("directUrl", "")
        } else {
            ordered.add("directUrl", directUrl)
        }

        var priority = DlcDownloadCandidate.readPriority(obj)
        if (priority.isNullOrBlank()) {
            priority = "curseforge"
        }
        ordered.addProperty("priority", priority)

        ordered.addProperty("identifier", getIdentifier(obj))

        var type = getString(obj, "type")
        if (type.isNullOrBlank()) {
            type = "mod"
        }
        ordered.addProperty("type", type)
        val appliedTarget = getString(obj, "appliedTarget")
        if (!appliedTarget.isNullOrBlank()) {
            ordered.addProperty("appliedTarget", appliedTarget)
        }

        var fileName = fileNameOverride
        if (fileName.isNullOrBlank()) {
            fileName = getFileName(obj)
        }
        if (!fileName.isNullOrBlank()) {
            ordered.addProperty("file_name", fileName)
        }

        ordered.addProperty("isInList", getBoolean(obj, "isInList", true))
        ordered.addProperty("isHidden", getBoolean(obj, "isHidden", false))
        ordered.addProperty("AllowEnableByAll", getBoolean(obj, "AllowEnableByAll", true))

        val deps = obj.get("dependencies")
        ordered.add("dependencies", normalizeRelationElement(deps))
        val conflicts = obj.get("conflicts")
        ordered.add("conflicts", normalizeRelationElement(conflicts))

        val display = if (obj.has("DisplayName") && obj.get("DisplayName").isJsonObject) {
            obj.getAsJsonObject("DisplayName")
        } else {
            JsonObject()
        }
        if (display.entrySet().isEmpty()) {
            display.addProperty("en_us", "")
        }
        ordered.add("DisplayName", display)
        ordered.addProperty("alarm", getBoolean(obj, "alarm", false))

        val tip = if (obj.has("tip") && obj.get("tip").isJsonObject) {
            obj.getAsJsonObject("tip")
        } else {
            JsonObject()
        }
        ordered.add("tip", tip)

        for ((key, value) in obj.entrySet()) {
            if (key == null || ordered.has(key)) {
                continue
            }
            if ("dlc_name".equals(key, ignoreCase = true) || "dlc_identifier".equals(key, ignoreCase = true)) {
                continue
            }
            ordered.add(key, value)
        }
        return ordered
    }

    private fun getString(obj: JsonObject?, key: String?): String {
        if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
            return ""
        }
        return obj.get(key).asString
    }

    private fun getIdentifier(obj: JsonObject): String {
        var identifier = getString(obj, "identifier")
        if (identifier.isBlank()) {
            identifier = getString(obj, "dlc_identifier")
        }
        return identifier
    }

    private fun getFileName(obj: JsonObject): String {
        var fileName = getString(obj, "file_name")
        if (fileName.isBlank()) {
            fileName = getString(obj, "dlc_name")
        }
        return fileName
    }

    private fun normalizeRelationElement(element: JsonElement?): JsonElement {
        if (element == null || element.isJsonNull) {
            return JsonPrimitive("")
        }
        if (element.isJsonPrimitive) {
            return element.deepCopy()
        }
        if (element.isJsonArray) {
            val array = JsonArray()
            for (child in element.asJsonArray) {
                array.add(normalizeRelationElement(child))
            }
            return array
        }
        val obj = element.asJsonObject
        val normalized = JsonObject()
        for ((key, value) in obj.entrySet()) {
            if (key == null) {
                continue
            }
            val targetKey = if ("dlc_identifier".equals(key, ignoreCase = true)) {
                "identifier"
            } else if ("dlc_name".equals(key, ignoreCase = true)) {
                "file_name"
            } else {
                key
            }
            if (normalized.has(targetKey)) {
                continue
            }
            normalized.add(targetKey, normalizeRelationElement(value))
        }
        return normalized
    }

    private fun resolveDisplayNames(entry: DlcRemoteEntry?): Map<String, String> {
        val values = HashMap<String, String>()
        if (entry != null) {
            values.putAll(entry.getDisplayNames())
        }
        if (values.isNotEmpty()) {
            return values
        }
        val obj = if (entry == null) null else DcConfigIO.read(entry.getConfigPath(), DcConfigIO.Layout.DLC_STRUCTURED)
        values.putAll(readLocalizedAny(obj, "DisplayName"))
        return values
    }

    private fun resolveTips(entry: DlcRemoteEntry?): Map<String, String> {
        val values = HashMap<String, String>()
        if (entry != null) {
            values.putAll(entry.getTips())
        }
        if (values.isNotEmpty()) {
            return values
        }
        val obj = if (entry == null) null else DcConfigIO.read(entry.getConfigPath(), DcConfigIO.Layout.DLC_STRUCTURED)
        values.putAll(readLocalizedAny(obj, "tip"))
        return values
    }

    private fun readLocalizedAny(obj: JsonObject?, key: String?): Map<String, String> {
        val values = HashMap<String, String>()
        if (obj == null || key.isNullOrBlank()) {
            return values
        }
        val jsonObject = DcConfigIO.getObjectIgnoreCase(obj, key)
        if (jsonObject != null) {
            for ((entryKey, value) in jsonObject.entrySet()) {
                if (value == null || !value.isJsonPrimitive) {
                    continue
                }
                val lang = DcConfigIO.normalizeLangKey(entryKey) ?: continue
                values[lang] = value.asString
            }
        }
        if (values.isNotEmpty()) {
            return values
        }
        val single = DcConfigIO.getStringIgnoreCase(obj, key)
        if (!single.isNullOrBlank()) {
            values["en_us"] = single
        }
        return values
    }

    private fun getBoolean(obj: JsonObject?, key: String?, fallback: Boolean): Boolean {
        if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
            return fallback
        }
        return try {
            obj.get(key).asBoolean
        } catch (_: Exception) {
            fallback
        }
    }

    private fun normalizeFileName(fileName: String?): String? {
        if (fileName == null) {
            return null
        }
        var trimmed = DlcDownloadUtil.stripQuery(fileName) ?: return null
        trimmed = trimmed.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (DcFormatUtil.isDcName(trimmed)) {
            return null
        }
        return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
