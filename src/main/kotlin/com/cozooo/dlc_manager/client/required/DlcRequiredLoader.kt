package com.cozooo.dlc_manager.client.required

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcDcFormatUtil
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DlcRequiredLoader {
    @JvmStatic
    fun load(dlcRoot: Path?): List<DlcRequiredEntry> {
        val entries = ArrayList<DlcRequiredEntry>()
        if (dlcRoot == null) {
            return entries
        }
        val requiredDir = dlcRoot.resolve("required")
        if (!Files.exists(requiredDir)) {
            return entries
        }
        try {
            Files.newDirectoryStream(requiredDir).use { stream ->
                for (path in stream) {
                    val fileName = path.fileName?.toString() ?: continue
                    if (!DlcDcFormatUtil.isDcName(fileName)) {
                        continue
                    }
                    if (isNoticeConfig(fileName)) {
                        continue
                    }
                    val entry = readEntry(path, requiredDir)
                    if (entry != null) {
                        entries.add(entry)
                    }
                }
            }
        } catch (_: IOException) {
        }
        return entries
    }

    @JvmStatic
    fun loadSingle(configPath: Path?, dlcRoot: Path?): DlcRequiredEntry? {
        if (configPath == null || dlcRoot == null) {
            return null
        }
        val requiredDir = dlcRoot.resolve("required")
        if (!Files.isDirectory(requiredDir)) {
            return null
        }
        val resolvedConfigPath = resolveExistingConfigPath(configPath)
        if (!Files.exists(resolvedConfigPath)) {
            return null
        }
        return readEntry(resolvedConfigPath, requiredDir)
    }

    private fun readEntry(path: Path?, requiredDir: Path): DlcRequiredEntry? {
        if (path == null) {
            return null
        }
        val obj = readConfigObject(path) ?: return null
        val normalized = normalizeRequiredObject(obj, requiredDir, path, null) ?: return null
        return normalized.entry
    }

    private fun readConfigObject(path: Path?): JsonObject? {
        if (path == null || path.fileName == null) {
            return null
        }
        if (DlcDcFormatUtil.isDcName(path.fileName.toString())) {
            return DlcDcFormatUtil.read(path)
        }
        return null
    }

    private fun resolveExistingConfigPath(configPath: Path?): Path? {
        if (configPath == null || configPath.fileName == null) {
            return configPath
        }
        if (Files.exists(configPath)) {
            return configPath
        }
        val name = configPath.fileName.toString()
        val base = DlcDcFormatUtil.stripConfigExtension(name)
        val parent = configPath.parent ?: return configPath
        val dc = parent.resolve(base + DlcDcFormatUtil.DC_EXTENSION)
        if (Files.exists(dc)) {
            return dc
        }
        return configPath
    }

    private fun isNoticeConfig(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) {
            return false
        }
        return "notice".equals(DlcDcFormatUtil.stripConfigExtension(fileName), ignoreCase = true)
    }

    @JvmStatic
    fun fromJsonObject(obj: JsonObject?, requiredDir: Path?, configPath: Path?, defaultIdentifier: String?): DlcRequiredEntry? {
        if (obj == null) {
            return null
        }
        val entry = DlcRequiredEntry(configPath, requiredDir)
        entry.setMatchMode(getString(obj, "mode"))
        if (!defaultIdentifier.isNullOrBlank()) {
            entry.setIdentifier(defaultIdentifier)
        }
        val identifier = getString(obj, "identifier")
        if (!identifier.isNullOrBlank()) {
            entry.setIdentifier(identifier)
        }
        val fileName = getString(obj, "file_name")
        if (!fileName.isNullOrBlank()) {
            entry.setRequiredFileName(fileName)
        }
        val directUrls = getDirectUrls(obj)
        if (directUrls.isNotEmpty()) {
            entry.setDirectUrls(directUrls)
        } else {
            val directUrl = getString(obj, "directUrl")
            if (!directUrl.isNullOrBlank()) {
                entry.setDirectUrl(directUrl)
            }
        }
        val modrinthProjectId = getString(obj, "modrinth_projectid")
        if (!modrinthProjectId.isNullOrBlank()) {
            entry.setModrinthProjectId(modrinthProjectId)
        }
        val modrinthVersionId = getString(obj, "modrinth_versionid")
        if (!modrinthVersionId.isNullOrBlank()) {
            entry.setModrinthVersionId(modrinthVersionId)
        }
        val curseProjectId = getString(obj, "curseforge_projectid")
        if (!curseProjectId.isNullOrBlank()) {
            entry.setCurseforgeProjectId(curseProjectId)
        }
        val curseFileId = getString(obj, "curseforge_fileid")
        if (!curseFileId.isNullOrBlank()) {
            entry.setCurseforgeFileId(curseFileId)
        }
        val priority = DlcDownloadCandidate.readPriority(obj)
        if (priority.isNotBlank()) {
            entry.setDownloadPriority(priority)
        }
        val applied = getString(obj, "appliedTarget")
        entry.setAppliedTargetRaw(applied ?: "")
        val unzip = DlcDcConfigIO.getIgnoreCase(obj, "unzip")
        entry.setUnzip(unzip != null && unzip.isJsonPrimitive && unzip.asJsonPrimitive.isBoolean && unzip.asBoolean)
        val depsElement = DlcDcConfigIO.getIgnoreCase(obj, "dependencies")
        if (depsElement != null) {
            parseDependencies(depsElement, entry, requiredDir)
        }
        return entry
    }

    private fun parseDependencies(element: JsonElement?, entry: DlcRequiredEntry?, requiredDir: Path?) {
        if (element == null || entry == null) {
            return
        }
        if (element.isJsonPrimitive) {
            val raw = element.asString
            if (raw != null) {
                for (part in raw.split(",")) {
                    val id = part.trim()
                    if (id.isNotBlank()) {
                        entry.addDependency(DlcRequiredDependency(id, null))
                    }
                }
            }
            return
        }
        if (element.isJsonArray) {
            val array = element.asJsonArray
            for (value in array) {
                parseDependencyValue(null, value, entry, requiredDir)
            }
            return
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            for ((key, value) in obj.entrySet()) {
                parseDependencyValue(key, value, entry, requiredDir)
            }
        }
    }

    private fun parseDependencyValue(key: String?, value: JsonElement?, owner: DlcRequiredEntry?, requiredDir: Path?) {
        if (value == null || owner == null) {
            return
        }
        if (value.isJsonPrimitive) {
            var raw = value.asString
            if (raw.isNullOrBlank() && key != null) {
                raw = key
            }
            if (raw != null) {
                for (part in raw.split(",")) {
                    val id = part.trim()
                    if (id.isNotBlank()) {
                        owner.addDependency(DlcRequiredDependency(id, null))
                    }
                }
            }
            return
        }
        if (!value.isJsonObject) {
            return
        }
        val obj = value.asJsonObject
        var identifier = key
        val resolved = getString(obj, "identifier")
        if (!resolved.isNullOrBlank()) {
            identifier = resolved
        }
        var depEntry = fromJsonObject(obj, requiredDir, null, identifier)
        var depId = depEntry?.getIdentifier()
        if (depId.isNullOrBlank()) {
            depId = identifier
        }
        owner.addDependency(DlcRequiredDependency(depId, depEntry))
    }

    private fun getDirectUrls(obj: JsonObject?): List<String> {
        if (obj == null) {
            return emptyList()
        }
        val element = DlcDcConfigIO.getIgnoreCase(obj, "directUrl") ?: return emptyList()
        val urls = ArrayList<String>()
        if (element.isJsonPrimitive) {
            val url = element.asString
            if (!url.isNullOrBlank()) {
                urls.add(url.trim())
            }
            return urls
        }
        if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                if (item != null && item.isJsonPrimitive) {
                    val url = item.asString
                    if (!url.isNullOrBlank()) {
                        urls.add(url.trim())
                    }
                }
            }
        }
        return urls
    }

    private fun normalizeRequiredObject(obj: JsonObject?, requiredDir: Path?, configPath: Path?, defaultIdentifier: String?): NormalizedRequired? {
        if (obj == null) {
            return null
        }
        val entry = fromJsonObject(obj, requiredDir, configPath, defaultIdentifier) ?: return null
        val curseProjectId = entry.getCurseforgeProjectId()
        val curseFileId = entry.getCurseforgeFileId()
        val modrinthProjectId = entry.getModrinthProjectId()
        val modrinthVersionId = entry.getModrinthVersionId()
        val directUrls = entry.getDirectUrls()
        val directUrl = if (directUrls.isEmpty()) "" else directUrls[0]
        val priority = entry.getDownloadPriority()
        val identifier = entry.getIdentifier()
        val fileName = entry.getRequiredFileName()
        val appliedTarget = entry.getAppliedTargetRaw()
        val deps = normalizeDependencies(DlcDcConfigIO.getIgnoreCase(obj, "dependencies"), requiredDir)
        val ordered = JsonObject()
        ordered.addProperty("curseforge_projectid", curseProjectId ?: "")
        ordered.addProperty("curseforge_fileid", curseFileId ?: "")
        ordered.addProperty("modrinth_projectid", modrinthProjectId ?: "")
        ordered.addProperty("modrinth_versionid", modrinthVersionId ?: "")
        if (directUrls.size > 1) {
            val urls = JsonArray()
            for (url in directUrls) {
                urls.add(url)
            }
            ordered.add("directUrl", urls)
        } else {
            ordered.addProperty("directUrl", directUrl)
        }
        ordered.addProperty("priority", if (priority.isNullOrBlank()) "curseforge" else priority)
        ordered.addProperty("identifier", identifier)
        if (!fileName.isNullOrBlank()) {
            ordered.addProperty("file_name", fileName)
        }
        ordered.addProperty("appliedTarget", appliedTarget ?: "")
        ordered.add("dependencies", deps.element ?: JsonPrimitive(""))

        for ((key, value) in obj.entrySet()) {
            if (key == null || ordered.has(key)) {
                continue
            }
            ordered.add(key, value)
        }
        return NormalizedRequired(entry, ordered)
    }

    private class NormalizedRequired(
        var entry: DlcRequiredEntry,
        var ordered: JsonObject,
    )

    private fun normalizeDependencies(element: JsonElement?, requiredDir: Path?): DependencyResult {
        val result = DependencyResult()
        if (element == null || element.isJsonNull) {
            result.element = JsonPrimitive("")
            return result
        }
        if (element.isJsonPrimitive) {
            val raw = element.asString
            result.element = JsonPrimitive(raw)
            result.names.addAll(splitNames(raw))
            return result
        }
        if (element.isJsonArray) {
            val array = JsonArray()
            for (child in element.asJsonArray) {
                if (child == null || child.isJsonNull) {
                    continue
                }
                if (child.isJsonPrimitive) {
                    val raw = child.asString
                    array.add(JsonPrimitive(raw))
                    result.names.addAll(splitNames(raw))
                    continue
                }
                if (child.isJsonObject) {
                    val obj = child.asJsonObject
                    if (isIdentifierOnlyObject(obj)) {
                        val identifier = resolveIdentifier(obj, null)
                        val ordered = JsonObject()
                        ordered.addProperty("identifier", identifier ?: "")
                        array.add(ordered)
                        if (!identifier.isNullOrBlank()) {
                            result.names.add(identifier)
                        }
                        continue
                    }
                    if (isRequiredConfigObject(obj)) {
                        val normalized = normalizeRequiredObject(obj, requiredDir, null, null)
                        if (normalized != null) {
                            array.add(normalized.ordered)
                            result.entries.add(normalized.entry)
                            val identifier = normalized.entry.getIdentifier()
                            if (identifier.isNotBlank()) {
                                result.names.add(identifier)
                            }
                        }
                        continue
                    }
                }
                val childResult = normalizeDependencies(child, requiredDir)
                array.add(childResult.element ?: JsonPrimitive(""))
                result.names.addAll(childResult.names)
                result.entries.addAll(childResult.entries)
            }
            result.element = array
            return result
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (isIdentifierOnlyObject(obj)) {
                val identifier = resolveIdentifier(obj, null)
                val ordered = JsonObject()
                ordered.addProperty("identifier", identifier ?: "")
                result.element = ordered
                if (!identifier.isNullOrBlank()) {
                    result.names.add(identifier)
                }
                return result
            }
            if (isRequiredConfigObject(obj)) {
                val normalized = normalizeRequiredObject(obj, requiredDir, null, null)
                if (normalized != null) {
                    result.element = normalized.ordered
                    result.entries.add(normalized.entry)
                    val identifier = normalized.entry.getIdentifier()
                    if (identifier.isNotBlank()) {
                        result.names.add(identifier)
                    }
                } else {
                    result.element = JsonPrimitive("")
                }
                return result
            }
            val ordered = JsonObject()
            for ((key, value) in obj.entrySet()) {
                if (value == null || value.isJsonNull) {
                    continue
                }
                if (value.isJsonPrimitive) {
                    if (value.asJsonPrimitive.isString) {
                        val raw = value.asString
                        if (raw.isBlank()) {
                            continue
                        }
                        val childOrdered = JsonObject()
                        childOrdered.addProperty("identifier", raw)
                        ordered.add(key, childOrdered)
                        result.names.add(raw)
                    } else {
                        ordered.add(key, value)
                    }
                    continue
                }
                if (value.isJsonObject) {
                    val childObj = value.asJsonObject
                    if (isIdentifierOnlyObject(childObj)) {
                        val identifier = resolveIdentifier(childObj, key)
                        val childOrdered = JsonObject()
                        childOrdered.addProperty("identifier", identifier ?: "")
                        ordered.add(key, childOrdered)
                        if (!identifier.isNullOrBlank()) {
                            result.names.add(identifier)
                        }
                        continue
                    }
                    if (isRequiredConfigObject(childObj)) {
                        val normalized = normalizeRequiredObject(childObj, requiredDir, null, key)
                        if (normalized != null) {
                            ordered.add(key, normalized.ordered)
                            result.entries.add(normalized.entry)
                            val identifier = normalized.entry.getIdentifier()
                            if (identifier.isNotBlank()) {
                                result.names.add(identifier)
                            }
                        }
                        continue
                    }
                    if (!key.isNullOrBlank()) {
                        val childOrdered = JsonObject()
                        childOrdered.addProperty("identifier", key)
                        ordered.add(key, childOrdered)
                        result.names.add(key)
                        continue
                    }
                }
                val childResult = normalizeDependencies(value, requiredDir)
                ordered.add(key, childResult.element ?: JsonPrimitive(""))
                result.names.addAll(childResult.names)
                result.entries.addAll(childResult.entries)
            }
            result.element = ordered
            return result
        }
        result.element = JsonPrimitive("")
        return result
    }

    private fun isRequiredConfigObject(obj: JsonObject?): Boolean {
        if (obj == null) {
            return false
        }
        for (key in obj.keySet()) {
            val lowered = key?.lowercase() ?: continue
            if (lowered == "modrinth_projectid"
                || lowered == "modrinth_versionid"
                || lowered == "directurl"
                || lowered == "curseforge_projectid"
                || lowered == "curseforge_fileid"
                || lowered == "priority"
                || lowered == "identifier"
                || lowered == "file_name"
                || lowered == "appliedtarget"
                || lowered == "appliedtaget"
                || lowered == "dependencies"
            ) {
                return true
            }
        }
        return false
    }

    private fun isIdentifierOnlyObject(obj: JsonObject?): Boolean {
        if (obj == null || obj.size() != 1) {
            return false
        }
        val value = getString(obj, "identifier")
        return !value.isNullOrBlank()
    }

    private fun resolveIdentifier(obj: JsonObject?, fallback: String?): String? {
        val value = getString(obj, "identifier")
        if (!value.isNullOrBlank()) {
            return value
        }
        return fallback
    }

    private fun splitNames(raw: String?): List<String> {
        val names = ArrayList<String>()
        if (raw == null) {
            return names
        }
        for (part in raw.split(",")) {
            val value = part.trim()
            if (value.isNotBlank()) {
                names.add(value)
            }
        }
        return names
    }

    private fun getString(obj: JsonObject?, key: String): String? = DlcDcConfigIO.getStringIgnoreCase(obj, key)

    private class DependencyResult {
        var element: JsonElement? = null
        val names: MutableList<String> = ArrayList()
        val entries: MutableList<DlcRequiredEntry> = ArrayList()
    }
}
