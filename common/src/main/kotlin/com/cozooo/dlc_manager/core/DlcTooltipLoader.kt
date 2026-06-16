package com.cozooo.dlc_manager.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class DlcTooltipLoader {
    fun loadTooltips(dlcRoot: Path): Map<String, TooltipData> {
        val result = HashMap<String, TooltipData>()
        val infoDir = dlcRoot.resolve("info")
        if (!Files.isDirectory(infoDir)) {
            return result
        }
        try {
            Files.list(infoDir).use { stream ->
                stream
                    .filter { path -> path.fileName != null && DcFormatUtil.isDcName(path.fileName.toString()) }
                    .filter { path -> !isInfoMetaFile(path) }
                    .forEach { path ->
                        val entry = readEntry(path)
                        if (entry != null && entry.dlcName != null) {
                            val data = TooltipData(
                                entry.dlcIdentifier,
                                entry.tip,
                                entry.displayName,
                                entry.type,
                                entry.typeValid,
                                entry.hasTypeField,
                                entry.configType,
                                entry.inList,
                                entry.hidden,
                                entry.allowEnableByAll,
                                entry.dependencies,
                                entry.conflicts,
                                entry.appliedTargetRaw,
                                entry.redirectUrl,
                                entry.fileNames,
                                entry.alarm,
                            )
                            result[entry.dlcName!!] = data
                        }
                    }
            }
        } catch (_: IOException) {
        }
        return result
    }

    private fun isInfoMetaFile(path: Path?): Boolean {
        if (path?.fileName == null) {
            return false
        }
        val base = DcFormatUtil.stripConfigExtension(path.fileName.toString())
        return "version".equals(base, ignoreCase = true)
    }

    fun ensureTooltipFile(infoDir: Path, dlcName: String?, type: DlcType?, updateExistingType: Boolean) {
        if (dlcName.isNullOrBlank()) {
            return
        }
        try {
            Files.createDirectories(infoDir)
        } catch (_: IOException) {
            return
        }
        var path = DcFormatUtil.resolveConfigPath(infoDir, dlcName)
        if (path != null && Files.exists(path)) {
            updateTooltipFile(path, dlcName, type, updateExistingType)
            return
        }
        val existingByDlcName = findExistingTooltipByDlcName(infoDir, dlcName)
        if (existingByDlcName != null) {
            updateTooltipFile(existingByDlcName, dlcName, type, updateExistingType)
            return
        }
        path = DcFormatUtil.resolveConfigPath(infoDir, dlcName)
        val obj = buildOrderedTooltipObject(
            null,
            dlcName,
            dlcName,
            buildDefaultDisplayName(dlcName),
            toTypeString(type),
            null,
            null,
            true,
            false,
            true,
            JsonPrimitive(""),
            JsonPrimitive(""),
            false,
            JsonObject(),
        )
        writeTooltipObject(path, obj)
    }

    private fun findExistingTooltipByDlcName(infoDir: Path?, dlcName: String?): Path? {
        if (infoDir == null || dlcName.isNullOrBlank() || !Files.isDirectory(infoDir)) {
            return null
        }
        return try {
            Files.list(infoDir).use { stream ->
                stream
                    .filter { path -> path.fileName != null && DcFormatUtil.isDcName(path.fileName.toString()) }
                    .filter { path ->
                        val entry = readEntry(path)
                        entry != null && dlcName == entry.dlcName
                    }
                    .findFirst()
                    .orElse(null)
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun updateTooltipFile(path: Path, dlcName: String, type: DlcType?, updateExistingType: Boolean) {
        var obj = readTooltipObject(path) ?: return
        val legacyShown = getBoolean(obj, "isShownInList", true)
        DcConfigIO.removeIgnoreCase(obj, "isShownInList")
        var displayName = localizedObjectFromAny(obj, "DisplayName")
        if (displayName.entrySet().isEmpty()) {
            displayName = buildDefaultDisplayName(dlcName)
        }
        var identifier = getIdentifier(obj)
        if (identifier.isNullOrBlank()) {
            identifier = dlcName
        }

        var typeValue = if (updateExistingType) toTypeString(type) else getString(obj, "type")
        if (typeValue == null) {
            typeValue = toTypeString(type)
        }
        val inList = getBoolean(obj, "isInList", legacyShown)
        val hidden = getBoolean(obj, "isHidden", false)
        val allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true)
        val dependencies = normalizeRelationElement(DcConfigIO.getIgnoreCase(obj, "dependencies"))
        val conflicts = normalizeRelationElement(DcConfigIO.getIgnoreCase(obj, "conflicts"))
        val alarm = getBoolean(obj, "alarm", false)
        val tip = localizedObjectFromAny(obj, "tip")
        val appliedTarget = getString(obj, "appliedTarget")
        val redirectUrl = getString(obj, "RedirectUrl")
        obj = buildOrderedTooltipObject(
            obj,
            dlcName,
            identifier,
            displayName,
            typeValue,
            appliedTarget,
            redirectUrl,
            inList,
            hidden,
            allowEnableByAll,
            dependencies,
            conflicts,
            alarm,
            tip,
        )
        writeTooltipObject(path, obj)
    }

    private fun readEntry(path: Path?): TooltipEntry? {
        val obj = readTooltipObject(path) ?: return null
        if (!isTooltipConfigObject(obj)) {
            return null
        }
        val entry = TooltipEntry()
        entry.dlcName = getFileName(obj)
        if (entry.dlcName.isNullOrBlank() && path?.fileName != null) {
            entry.dlcName = DcFormatUtil.stripConfigExtension(path.fileName.toString())
        }
        if (entry.dlcName.isNullOrBlank()) {
            return null
        }
        entry.dlcIdentifier = getIdentifier(obj)
        if (entry.dlcIdentifier.isNullOrBlank()) {
            entry.dlcIdentifier = entry.dlcName
        }
        entry.hasTypeField = DcConfigIO.hasIgnoreCase(obj, "type")
        val legacyShown = getBoolean(obj, "isShownInList", true)
        entry.inList = getBoolean(obj, "isInList", legacyShown)
        entry.hidden = getBoolean(obj, "isHidden", false)
        entry.allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true)
        entry.dependencies = parseDependencyNames(DcConfigIO.getIgnoreCase(obj, "dependencies"))
        entry.conflicts = parseDependencyNames(DcConfigIO.getIgnoreCase(obj, "conflicts"))
        entry.alarm = getBoolean(obj, "alarm", false)
        entry.fileNames = parseFileNames(DcConfigIO.getIgnoreCase(obj, "fileNames"))
        if (entry.fileNames.isEmpty()) {
            entry.fileNames = parseFileNames(DcConfigIO.getIgnoreCase(obj, "fileName"))
        }
        if (entry.fileNames.isEmpty()) {
            entry.fileNames = parseFileNames(DcConfigIO.getIgnoreCase(obj, "files"))
        }
        entry.displayName = readLocalized(localizedObjectFromAny(obj, "DisplayName"))
        if (entry.displayName.isEmpty() && entry.dlcName != null) {
            entry.displayName = readLocalized(buildDefaultDisplayName(entry.dlcName))
        }
        entry.appliedTargetRaw = getString(obj, "appliedTarget")
        entry.redirectUrl = getString(obj, "RedirectUrl")
        val type = getString(obj, "type")
        if (type != null) {
            val parsed = parseTypeString(type)
            if (parsed != null) {
                entry.type = parsed
                entry.typeValid = true
                entry.configType = "config".equals(type.trim(), ignoreCase = true)
            }
        }
        val tipObj = localizedObjectFromAny(obj, "tip")
        if (tipObj.entrySet().isNotEmpty()) {
            val tips = HashMap<String, String>()
            for ((key, value) in tipObj.entrySet()) {
                if (value.isJsonPrimitive) {
                    val normalizedKey = DcConfigIO.normalizeLangKey(key)
                    if (normalizedKey != null) {
                        tips[normalizedKey] = value.asString
                    }
                }
            }
            entry.tip = tips
        }
        return entry
    }

    private fun isTooltipConfigObject(obj: JsonObject?): Boolean {
        if (obj == null) {
            return false
        }
        return DcConfigIO.hasIgnoreCase(obj, "file_name") ||
            DcConfigIO.hasIgnoreCase(obj, "dlc_name") ||
            DcConfigIO.hasIgnoreCase(obj, "identifier") ||
            DcConfigIO.hasIgnoreCase(obj, "dlc_identifier") ||
            DcConfigIO.hasIgnoreCase(obj, "DisplayName") ||
            DcConfigIO.hasIgnoreCase(obj, "tip") ||
            DcConfigIO.hasIgnoreCase(obj, "type") ||
            DcConfigIO.hasIgnoreCase(obj, "appliedTarget") ||
            DcConfigIO.hasIgnoreCase(obj, "RedirectUrl") ||
            DcConfigIO.hasIgnoreCase(obj, "dependencies") ||
            DcConfigIO.hasIgnoreCase(obj, "conflicts") ||
            DcConfigIO.hasIgnoreCase(obj, "isInList") ||
            DcConfigIO.hasIgnoreCase(obj, "isShownInList") ||
            DcConfigIO.hasIgnoreCase(obj, "isHidden") ||
            DcConfigIO.hasIgnoreCase(obj, "AllowEnableByAll") ||
            DcConfigIO.hasIgnoreCase(obj, "alarm")
    }

    private fun readTooltipObject(path: Path?): JsonObject? {
        if (path?.fileName == null) {
            return null
        }
        return DcConfigIO.read(path, DcConfigIO.Layout.DLC_STRUCTURED)
    }

    private fun writeTooltipObject(path: Path?, obj: JsonObject?) {
        if (path == null) {
            return
        }
        DcConfigIO.write(path, obj, DcConfigIO.Layout.DLC_STRUCTURED)
    }

    private fun needsDcLayoutMigration(path: Path?): Boolean {
        if (path?.fileName == null || !DcFormatUtil.isDcName(path.fileName.toString())) {
            return false
        }
        return try {
            val text = Files.readString(path)
            if (text.isNullOrBlank()) {
                false
            } else {
                !text.contains("[basic]")
            }
        } catch (_: IOException) {
            false
        }
    }

    private fun getString(obj: JsonObject?, key: String): String? {
        return DcConfigIO.getStringIgnoreCase(obj, key)
    }

    private fun getIdentifier(obj: JsonObject?): String? {
        var identifier = getString(obj, "identifier")
        if (identifier.isNullOrBlank()) {
            identifier = getString(obj, "dlc_identifier")
        }
        return identifier
    }

    private fun getFileName(obj: JsonObject?): String? {
        var fileName = getString(obj, "file_name")
        if (fileName.isNullOrBlank()) {
            fileName = getString(obj, "dlc_name")
        }
        return fileName
    }

    private fun getBoolean(obj: JsonObject?, key: String, fallback: Boolean): Boolean {
        return DcConfigIO.getBooleanIgnoreCase(obj, key, fallback)
    }

    private fun isValidDependencyElement(element: JsonElement?): Boolean {
        if (element == null || element.isJsonNull) {
            return false
        }
        return element.isJsonPrimitive || element.isJsonArray || element.isJsonObject
    }

    private fun needsKeyMigration(obj: JsonObject?): Boolean {
        if (obj == null) {
            return false
        }
        if (DcConfigIO.hasIgnoreCase(obj, "dlc_name") || DcConfigIO.hasIgnoreCase(obj, "dlc_identifier")) {
            return true
        }
        return !DcConfigIO.hasIgnoreCase(obj, "file_name") || !DcConfigIO.hasIgnoreCase(obj, "identifier")
    }

    private fun hasLegacyDownloadKeys(obj: JsonObject?): Boolean {
        if (obj == null) {
            return false
        }
        return DcConfigIO.hasIgnoreCase(obj, "modrinth_projectid") ||
            DcConfigIO.hasIgnoreCase(obj, "modrinth_versionid") ||
            DcConfigIO.hasIgnoreCase(obj, "curseforge_projectid") ||
            DcConfigIO.hasIgnoreCase(obj, "curseforge_fileid") ||
            DcConfigIO.hasIgnoreCase(obj, "directUrl") ||
            DcConfigIO.hasIgnoreCase(obj, "priority")
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
        if (isIdentifierOnlyRelationObject(obj)) {
            val normalized = JsonObject()
            val identifier = resolveRelationIdentifier(obj)
            normalized.addProperty("identifier", identifier ?: "")
            return normalized
        }
        val normalized = JsonObject()
        for ((key, value) in obj.entrySet()) {
            var targetKey = key ?: continue
            if (isIdentifierAlias(key)) {
                targetKey = "identifier"
            } else if (isFileNameAlias(key)) {
                targetKey = "file_name"
            }
            if (normalized.has(targetKey)) {
                continue
            }
            normalized.add(targetKey, normalizeRelationElement(value))
        }
        return normalized
    }

    private fun isIdentifierOnlyRelationObject(obj: JsonObject?): Boolean {
        if (obj == null || obj.entrySet().isEmpty()) {
            return false
        }
        var hasIdField = false
        for ((key) in obj.entrySet()) {
            if (key == null) {
                continue
            }
            if (isIdentifierAlias(key)) {
                hasIdField = true
                continue
            }
            return false
        }
        return hasIdField
    }

    private fun resolveRelationIdentifier(obj: JsonObject?): String? {
        return getString(obj, "identifier")
    }

    private fun isIdentifierAlias(key: String): Boolean {
        return "identifier".equals(key, ignoreCase = true)
    }

    private fun isFileNameAlias(key: String): Boolean {
        return "file_name".equals(key, ignoreCase = true) || "dlc_name".equals(key, ignoreCase = true)
    }

    private fun buildOrderedTooltipObject(
        source: JsonObject?,
        dlcName: String?,
        dlcIdentifier: String?,
        displayName: JsonObject?,
        type: String?,
        appliedTarget: String?,
        redirectUrl: String?,
        inList: Boolean,
        hidden: Boolean,
        allowEnableByAll: Boolean,
        dependencies: JsonElement?,
        conflicts: JsonElement?,
        alarm: Boolean,
        tip: JsonObject?,
    ): JsonObject {
        val ordered = JsonObject()
        ordered.addProperty("file_name", dlcName)
        ordered.addProperty("identifier", dlcIdentifier ?: dlcName)
        var normalizedDisplay = displayName
        if (normalizedDisplay == null || normalizedDisplay.entrySet().isEmpty()) {
            normalizedDisplay = buildDefaultDisplayName(dlcName)
        }
        ordered.add("DisplayName", normalizedDisplay)
        ordered.addProperty("type", type)
        if (!appliedTarget.isNullOrBlank()) {
            ordered.addProperty("appliedTarget", appliedTarget)
        }
        if (!redirectUrl.isNullOrBlank()) {
            ordered.addProperty("RedirectUrl", redirectUrl)
        }
        ordered.addProperty("isInList", inList)
        ordered.addProperty("isHidden", hidden)
        ordered.addProperty("AllowEnableByAll", allowEnableByAll)
        ordered.add("dependencies", dependencies ?: JsonPrimitive(""))
        ordered.add("conflicts", conflicts ?: JsonPrimitive(""))
        ordered.addProperty("alarm", alarm)
        ordered.add("tip", tip ?: JsonObject())
        if (source != null) {
            for ((key, value) in source.entrySet()) {
                if (key == null || ordered.has(key)) {
                    continue
                }
                if ("dlc_name".equals(key, ignoreCase = true) || "dlc_identifier".equals(key, ignoreCase = true)) {
                    continue
                }
                if ("modrinth_projectid".equals(key, ignoreCase = true) ||
                    "modrinth_versionid".equals(key, ignoreCase = true) ||
                    "curseforge_projectid".equals(key, ignoreCase = true) ||
                    "curseforge_fileid".equals(key, ignoreCase = true) ||
                    "directUrl".equals(key, ignoreCase = true) ||
                    "priority".equals(key, ignoreCase = true)
                ) {
                    continue
                }
                ordered.add(key, value)
            }
        }
        return ordered
    }

    private fun buildDefaultDisplayName(dlcName: String?): JsonObject {
        val obj = JsonObject()
        if (dlcName != null) {
            obj.addProperty("en_us", dlcName)
        }
        return obj
    }

    private fun readLocalized(obj: JsonObject?): Map<String, String> {
        val values = HashMap<String, String>()
        if (obj == null) {
            return values
        }
        for ((key, value) in obj.entrySet()) {
            if (value.isJsonPrimitive) {
                val normalizedKey = DcConfigIO.normalizeLangKey(key)
                if (normalizedKey != null) {
                    values[normalizedKey] = value.asString
                }
            }
        }
        return values
    }

    private fun sanitizeLocalized(obj: JsonObject?): JsonObject {
        val result = JsonObject()
        if (obj == null) {
            return result
        }
        for ((key, value) in obj.entrySet()) {
            if (value.isJsonPrimitive) {
                val normalizedKey = DcConfigIO.normalizeLangKey(key)
                if (normalizedKey != null) {
                    result.addProperty(normalizedKey, value.asString)
                }
            }
        }
        return result
    }

    private fun localizedObjectFromAny(obj: JsonObject?, key: String): JsonObject {
        val localized = sanitizeLocalized(DcConfigIO.getObjectIgnoreCase(obj, key))
        if (localized.entrySet().isNotEmpty()) {
            return localized
        }
        val single = DcConfigIO.getStringIgnoreCase(obj, key)
        if (!single.isNullOrBlank()) {
            localized.addProperty("en_us", single)
        }
        return localized
    }

    private fun parseDependencyNames(element: JsonElement?): List<String> {
        val result = LinkedHashSet<String>()
        collectDependencyNames(element, result)
        return ArrayList(result)
    }

    private fun parseFileNames(element: JsonElement?): List<String> {
        val result = LinkedHashSet<String>()
        collectFileNames(element, result)
        return ArrayList(result)
    }

    private fun collectFileNames(element: JsonElement?, result: MutableSet<String>) {
        if (element == null || element.isJsonNull) {
            return
        }
        if (element.isJsonPrimitive) {
            val raw = element.asString
            for (part in raw.split("[\\n\\r,]+".toRegex())) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty()) {
                    result.add(trimmed.replace('\\', '/'))
                }
            }
            return
        }
        if (element.isJsonArray) {
            for (child in element.asJsonArray) {
                collectFileNames(child, result)
            }
            return
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            for ((_, value) in obj.entrySet()) {
                collectFileNames(value, result)
            }
        }
    }

    private fun collectDependencyNames(element: JsonElement?, result: MutableSet<String>) {
        if (element == null || element.isJsonNull) {
            return
        }
        if (element.isJsonPrimitive) {
            val raw = element.asString
            for (part in raw.split("[\\n\\r,]+".toRegex())) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty()) {
                    result.add(trimmed)
                }
            }
            return
        }
        if (element.isJsonArray) {
            for (child in element.asJsonArray) {
                collectDependencyNames(child, result)
            }
            return
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val name = getString(obj, "identifier")
            if (!name.isNullOrBlank()) {
                result.add(name.trim())
                collectDependencyNames(DcConfigIO.getIgnoreCase(obj, "dependencies"), result)
                return
            }
            for ((key, value) in obj.entrySet()) {
                val before = result.size
                collectDependencyNames(value, result)
                if (result.size == before && value != null && value.isJsonObject && !key.isNullOrBlank()) {
                    result.add(key.trim())
                }
            }
        }
    }

    private class TooltipEntry {
        var dlcName: String? = null
        var dlcIdentifier: String? = null
        var displayName: Map<String, String> = emptyMap()
        var tip: Map<String, String>? = null
        var dependencies: List<String> = emptyList()
        var conflicts: List<String> = emptyList()
        var type: DlcType? = null
        var typeValid = false
        var hasTypeField = false
        var configType = false
        var inList = true
        var hidden = false
        var allowEnableByAll = true
        var appliedTargetRaw: String? = null
        var redirectUrl: String? = null
        var fileNames: List<String> = emptyList()
        var alarm = false
    }

    class TooltipData(
        private val identifier: String?,
        tips: Map<String, String>?,
        displayName: Map<String, String>?,
        private val type: DlcType?,
        private val typeValid: Boolean,
        private val hasTypeField: Boolean,
        private val configType: Boolean,
        private val inList: Boolean,
        private val hidden: Boolean,
        private val allowEnableByAll: Boolean,
        dependencies: List<String>?,
        conflicts: List<String>?,
        private val appliedTargetRaw: String?,
        private val redirectUrl: String?,
        fileNames: List<String>?,
        private val alarm: Boolean,
    ) {
        private val tips: Map<String, String> = tips ?: emptyMap()
        private val displayName: Map<String, String> = displayName ?: emptyMap()
        private val dependencies: List<String> = dependencies ?: emptyList()
        private val conflicts: List<String> = conflicts ?: emptyList()
        private val fileNames: List<String> = fileNames ?: emptyList()

        fun getTips(): Map<String, String> = tips
        fun getIdentifier(): String? = identifier
        fun getDisplayName(): Map<String, String> = displayName
        fun getType(): DlcType? = type
        fun isTypeValid(): Boolean = typeValid
        fun hasTypeField(): Boolean = hasTypeField
        fun isConfigType(): Boolean = configType
        fun isInList(): Boolean = inList
        fun isHidden(): Boolean = hidden
        fun AllowEnableByAll(): Boolean = allowEnableByAll
        fun getDependencies(): List<String> = dependencies
        fun getConflicts(): List<String> = conflicts
        fun getAppliedTargetRaw(): String? = appliedTargetRaw
        fun getRedirectUrl(): String? = redirectUrl
        fun getFileNames(): List<String> = fileNames
        fun isAlarm(): Boolean = alarm
    }

    companion object {
        private val GSON: Gson = GsonBuilder().create()
        private val TYPE_MAP: Map<String, DlcType> = mapOf(
            "mod" to DlcType.MOD,
            "resourcepack" to DlcType.RESOURCE_PACK,
            "datapack" to DlcType.DATA_PACK,
            "gunpack" to DlcType.TACZ_PACK,
            "dlcpack" to DlcType.DLC_PACK,
            "config" to DlcType.UNKNOWN,
            "others" to DlcType.UNKNOWN,
        )

        @JvmStatic
        fun parseTypeString(raw: String?): DlcType? {
            if (raw == null) {
                return null
            }
            return TYPE_MAP[raw.lowercase(Locale.ROOT)]
        }

        @JvmStatic
        fun toTypeString(type: DlcType?): String {
            if (type == null) {
                return "others"
            }
            return when (type) {
                DlcType.MOD -> "mod"
                DlcType.RESOURCE_PACK -> "resourcepack"
                DlcType.DATA_PACK -> "datapack"
                DlcType.TACZ_PACK -> "gunpack"
                DlcType.DLC_PACK -> "dlcpack"
                DlcType.UNKNOWN -> "others"
            }
        }

        @JvmStatic
        fun parseType(raw: String?): DlcType? {
            return parseTypeString(raw)
        }
    }
}
