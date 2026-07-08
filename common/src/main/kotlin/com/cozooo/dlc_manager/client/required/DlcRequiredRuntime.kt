package com.cozooo.dlc_manager.client.required

import com.cozooo.dlc_manager.client.download.DlcCurseForgeClient
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.client.download.DlcModrinthClient
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcDcFormatUtil
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Locale

object DlcRequiredRuntime {
    private val GSON: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private var promptedThisSession = false

    @JvmStatic
    fun markSkipped() {
        if (hasForceMarker()) {
            return
        }
        val requiredDir = getRequiredDir() ?: return
        try {
            Files.createDirectories(requiredDir)
            Files.writeString(requiredDir.resolve("SKIP"), "")
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun pollMissingDownloadsForTitlePrompt(): List<DlcRequiredEntry> {
        if (promptedThisSession || hasSkipMarker()) {
            return emptyList()
        }
        val missing = collectMissingDownloadsForPrompt()
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
    fun hasSkipMarker(): Boolean {
        if (hasForceMarker()) {
            return false
        }
        val requiredDir = getRequiredDir() ?: return false
        return Files.exists(requiredDir.resolve("SKIP"))
    }

    @JvmStatic
    fun hasForceMarker(): Boolean {
        val requiredDir = getRequiredDir() ?: return false
        return Files.exists(requiredDir.resolve("FORCE"))
    }

    @JvmStatic
    fun hasForceRestartMarker(): Boolean {
        val requiredDir = getRequiredDir() ?: return false
        val marker = requiredDir.resolve("FORCE")
        if (!Files.exists(marker)) {
            return false
        }
        return try {
            Files.readString(marker)?.lowercase(Locale.ROOT)?.contains("forcerestart") == true
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun loadEntries(): List<DlcRequiredEntry> {
        return DlcRequiredLoader.load(getDlcRoot())
    }

    @JvmStatic
    fun loadAllEntries(): List<DlcRequiredEntry> {
        return loadAllEntriesInternal().filterNotNull()
    }

    private fun loadAllEntriesInternal(): List<DlcRequiredEntry?> {
        val entries = ArrayList<DlcRequiredEntry?>(loadEntries())
        val index = HashMap<String, DlcRequiredEntry>()
        for (entry in entries) {
            val id = entry?.getIdentifier()
            if (!id.isNullOrBlank()) {
                index.putIfAbsent(id, entry)
            }
        }
        val queue = ArrayDeque(entries)
        while (!queue.isEmpty()) {
            val entry = queue.poll() ?: continue
            for (dep in entry.getDependencies()) {
                val id = dep?.getIdentifier()
                if (id.isNullOrBlank() || index.containsKey(id)) {
                    continue
                }
                val depEntry = dep.getEntry()
                if (depEntry != null) {
                    depEntry.setIdentifier(id)
                    index[id] = depEntry
                    entries.add(depEntry)
                    queue.add(depEntry)
                }
            }
        }
        return entries
    }

    private fun getDlcRoot(): Path? {
        val roots = DlcConfig.getDlcRoots()
        return if (roots.isEmpty()) null else roots[0]
    }

    private fun getRequiredDir(): Path? {
        val dlcRoot = getDlcRoot() ?: return null
        return dlcRoot.resolve("required")
    }

    @JvmStatic
    fun buildIndex(entries: List<DlcRequiredEntry>?): Map<String, DlcRequiredEntry> {
        val map = HashMap<String, DlcRequiredEntry>()
        if (entries == null) {
            return map
        }
        for (entry in entries) {
            val id = entry?.getIdentifier()
            if (!id.isNullOrBlank()) {
                map.putIfAbsent(id, entry)
            }
        }
        return map
    }

    @JvmStatic
    fun buildDependents(entries: List<DlcRequiredEntry>?): Map<String, Set<String>> {
        val dependents = HashMap<String, MutableSet<String>>()
        if (entries == null) {
            return dependents
        }
        val index = HashMap(buildIndex(entries))
        for (entry in entries) {
            val id = entry?.getIdentifier()
            if (id.isNullOrBlank()) {
                continue
            }
            for (dep in entry.getDependencies()) {
                val depId = dep?.getIdentifier()
                if (depId.isNullOrBlank()) {
                    continue
                }
                val depEntry = dep.getEntry()
                if (!index.containsKey(depId) && depEntry != null) {
                    index[depId] = depEntry
                }
                dependents.computeIfAbsent(depId) { HashSet() }.add(id)
            }
        }
        return dependents
    }

    @JvmStatic
    fun collectDependencies(roots: List<DlcRequiredEntry>?): List<DlcRequiredEntry> {
        val result = ArrayList<DlcRequiredEntry>()
        if (roots.isNullOrEmpty()) {
            return result
        }
        val all = loadAllEntries()
        val index = buildIndex(all)
        val queue = ArrayDeque(roots)
        val seen = HashSet<String>()
        while (!queue.isEmpty()) {
            val entry = queue.poll() ?: continue
            val id = entry.getIdentifier()
            if (id.isNullOrBlank() || !seen.add(id)) {
                continue
            }
            val resolved = index[id] ?: entry
            result.add(resolved)
            for (dep in resolved.getDependencies()) {
                val depId = dep?.getIdentifier()
                if (depId.isNullOrBlank()) {
                    continue
                }
                val depEntry = index[depId]
                if (depEntry != null) {
                    queue.add(depEntry)
                } else if (dep.getEntry() != null) {
                    queue.add(dep.getEntry())
                }
            }
        }
        return result
    }

    @JvmStatic
    fun hasMissingDownloads(): Boolean {
        for (entry in loadAllEntriesInternal()) {
            if (entry == null) {
                continue
            }
            var fileName = normalizeFileName(entry.getRequiredFileName())
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry))
            }
            if (fileName == null) {
                fileName = normalizeFileName(resolveExistingRequiredFile(entry))
            }
            if (isTempFileName(fileName)) {
                fileName = null
            }
            entry.setResolvedFileName(fileName)
            if (fileName == null) {
                return true
            }
            val required = entry.resolveRequiredPath(fileName)
            if (required == null || !Files.exists(required)) {
                return true
            }
            ensureFileNameKey(entry, fileName)
        }
        return false
    }

    @JvmStatic
    fun hasMissingDownloadsForPrompt(): Boolean {
        for (entry in loadAllEntriesInternal()) {
            if (entry == null || entry.isUserRemoved()) {
                continue
            }
            var fileName = normalizeFileName(entry.getRequiredFileName())
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry))
            }
            if (fileName == null) {
                fileName = normalizeFileName(resolveExistingRequiredFile(entry))
            }
            entry.setResolvedFileName(fileName)
            if (isTempFileName(fileName)) {
                fileName = null
            }
            if (fileName == null) {
                return true
            }
            val required = entry.resolveRequiredPath(fileName)
            if (required == null || !Files.exists(required) || entryNeedsApply(entry)) {
                return true
            }
            ensureFileNameKey(entry, fileName)
        }
        return false
    }

    @JvmStatic
    fun hasMissingOrApply(): Boolean {
        for (entry in loadAllEntriesInternal()) {
            if (entry == null) {
                continue
            }
            var fileName = normalizeFileName(entry.getRequiredFileName())
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry))
            }
            entry.setResolvedFileName(fileName)
            if (fileName == null) {
                return true
            }
            val required = entry.resolveRequiredPath(fileName)
            if (required == null || !Files.exists(required)) {
                return true
            }
            ensureFileNameKey(entry, fileName)
            if (entryNeedsApply(entry)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun collectMissingDownloads(): List<DlcRequiredEntry> {
        val missing = ArrayList<DlcRequiredEntry>()
        for (entry in loadAllEntriesInternal()) {
            if (entry == null) {
                continue
            }
            var fileName = normalizeFileName(entry.getRequiredFileName())
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry))
            }
            if (fileName == null) {
                fileName = normalizeFileName(resolveExistingRequiredFile(entry))
            }
            entry.setResolvedFileName(fileName)
            if (isTempFileName(fileName)) {
                fileName = null
            }
            if (fileName == null) {
                missing.add(entry)
                continue
            }
            val required = entry.resolveRequiredPath(fileName)
            if (required == null || !Files.exists(required)) {
                missing.add(entry)
            } else {
                ensureFileNameKey(entry, fileName)
            }
        }
        return missing
    }

    @JvmStatic
    fun collectMissingDownloadsForPrompt(): List<DlcRequiredEntry> {
        val missing = ArrayList<DlcRequiredEntry>()
        for (entry in loadAllEntriesInternal()) {
            if (entry == null || entry.isUserRemoved()) {
                continue
            }
            var fileName = normalizeFileName(entry.getRequiredFileName())
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry))
            }
            if (fileName == null) {
                fileName = normalizeFileName(resolveExistingRequiredFile(entry))
            }
            entry.setResolvedFileName(fileName)
            if (isTempFileName(fileName)) {
                fileName = null
            }
            if (fileName == null) {
                missing.add(entry)
                continue
            }
            val required = entry.resolveRequiredPath(fileName)
            if (required == null || !Files.exists(required) || entryNeedsApply(entry)) {
                missing.add(entry)
            } else {
                ensureFileNameKey(entry, fileName)
            }
        }
        return missing
    }

    @JvmStatic
    fun collectMissingOrApply(): List<DlcRequiredEntry> {
        val list = ArrayList<DlcRequiredEntry>()
        for (entry in loadAllEntriesInternal()) {
            if (entry == null) {
                continue
            }
            var fileName = normalizeFileName(entry.getRequiredFileName())
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry))
            }
            entry.setResolvedFileName(fileName)
            if (isTempFileName(fileName)) {
                fileName = null
            }
            if (fileName == null) {
                list.add(entry)
                continue
            }
            val required = entry.resolveRequiredPath(fileName)
            if (required == null || !Files.exists(required) || entryNeedsApply(entry)) {
                list.add(entry)
            } else {
                ensureFileNameKey(entry, fileName)
            }
        }
        return list
    }

    @JvmStatic
    fun hasMissingAppliedTargets(): Boolean {
        for (entry in loadEntries()) {
            if (entryNeedsApply(entry)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun entryNeedsApply(entry: DlcRequiredEntry?): Boolean {
        if (entry == null) {
            return false
        }
        var fileName = normalizeFileName(entry.getResolvedFileName())
        if (fileName.isNullOrBlank()) {
            fileName = normalizeFileName(resolveFileName(entry))
            entry.setResolvedFileName(fileName)
        }
        if (isTempFileName(fileName)) {
            entry.setResolvedFileName(null)
            return true
        }
        if (fileName == null) {
            return true
        }
        for (target in entry.resolveAppliedTargets()) {
            val targetFile = resolveTarget(target, fileName)
            if (targetFile == null || !Files.exists(targetFile)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun guessFileName(entry: DlcRequiredEntry?): String? {
        if (entry == null) {
            return null
        }
        val directUrl = entry.getDirectUrl()
        if (!directUrl.isNullOrBlank()) {
            val name = DlcDownloadUtil.extractFileNameFromUrl(directUrl)
            if (DlcDownloadUtil.isLikelyFileName(name)) {
                return name
            }
        }
        return null
    }

    private fun resolveFileName(entry: DlcRequiredEntry?): String? {
        if (entry == null) {
            return null
        }
        for (source in entry.getDownloadPriorityList()) {
            when (source) {
                "directurl" -> {
                    val fileName = guessFileName(entry)
                    if (!fileName.isNullOrBlank()) {
                        return fileName
                    }
                }
                "curseforge" -> {
                    val fileName = fetchCurseForgeFileName(entry)
                    if (!fileName.isNullOrBlank()) {
                        return fileName
                    }
                }
                "modrinth" -> {
                    val versionId = entry.getModrinthVersionId()
                    if (!versionId.isNullOrBlank()) {
                        try {
                            val version = DlcModrinthClient.fetchVersion(versionId)
                            if (version != null && !version.fileName.isNullOrBlank()) {
                                return version.fileName
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
        return null
    }

    private fun fetchCurseForgeFileName(entry: DlcRequiredEntry?): String? {
        val fileId = entry?.getCurseforgeFileId()
        if (fileId.isNullOrBlank()) {
            return null
        }
        return try {
            val file = DlcCurseForgeClient.fetchFileByIdOrVersion(
                entry.getCurseforgeProjectId(),
                fileId,
                DlcPlatformServices.mcVersion(),
                DlcPlatformServices.loaderName(),
            )
            if (file != null && !file.fileName.isNullOrBlank()) file.fileName else null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveFileNameLocal(entry: DlcRequiredEntry?): String? {
        val fileName = guessFileName(entry)
        return if (!fileName.isNullOrBlank()) fileName else null
    }

    private fun resolveExistingRequiredFile(entry: DlcRequiredEntry?): String? {
        if (entry == null) {
            return null
        }
        val base = entry.getBaseName()
        val requiredDir = entry.getRequiredDir()
        if (requiredDir == null || base.isNullOrBlank()) {
            return null
        }
        try {
            Files.newDirectoryStream(requiredDir, "$base.*").use { stream ->
                for (path in stream) {
                    val name = path.fileName?.toString() ?: ""
                    if (!DlcDcFormatUtil.isDcName(name) && !name.endsWith(".part")) {
                        return name
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    @JvmStatic
    fun ensureFileNameKey(entry: DlcRequiredEntry?, fileName: String?) {
        if (entry == null) {
            return
        }
        val normalized = normalizeFileName(fileName) ?: return
        if (normalizeFileName(entry.getRequiredFileName()) != null) {
            return
        }
        val config = entry.getConfigPath()
        if (config == null || !Files.exists(config)) {
            return
        }
        try {
            val obj = DlcDcConfigIO.read(config, DlcDcConfigIO.Layout.DLC_STRUCTURED) ?: return
            val reordered = reorderRequiredJson(obj, normalized)
            writeRequiredConfig(config, reordered)
            entry.setRequiredFileName(normalized)
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun updateFileNameKey(entry: DlcRequiredEntry?, fileName: String?) {
        if (entry == null) {
            return
        }
        val normalized = normalizeFileName(fileName) ?: return
        val config = entry.getConfigPath()
        if (config == null || !Files.exists(config)) {
            return
        }
        try {
            val obj = DlcDcConfigIO.read(config, DlcDcConfigIO.Layout.DLC_STRUCTURED) ?: return
            val reordered = reorderRequiredJson(obj, normalized)
            writeRequiredConfig(config, reordered)
            entry.setRequiredFileName(normalized)
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun updateUserRemoved(entry: DlcRequiredEntry?, userRemoved: Boolean) {
        if (entry == null) {
            return
        }
        val config = entry.getConfigPath()
        if (config == null || !Files.exists(config)) {
            return
        }
        try {
            val obj = DlcDcConfigIO.read(config, DlcDcConfigIO.Layout.DLC_STRUCTURED) ?: return
            if (userRemoved) {
                obj.addProperty("UserRemoved", true)
            } else {
                obj.remove("UserRemoved")
                obj.remove("userRemoved")
            }
            val reordered = reorderRequiredJson(obj, entry.getRequiredFileName())
            writeRequiredConfig(config, reordered)
            entry.setUserRemoved(userRemoved)
        } catch (_: Exception) {
        }
    }

    private fun writeRequiredConfig(config: Path?, ordered: JsonObject?): Path? {
        if (config == null || ordered == null) {
            return null
        }
        val target = DlcDcFormatUtil.toDcPath(config) ?: return null
        if (!DlcDcFormatUtil.write(target, ordered)) {
            return null
        }
        if (target != config) {
            try {
                Files.deleteIfExists(config)
            } catch (_: Exception) {
            }
        }
        return target
    }

    private fun reorderRequiredJson(obj: JsonObject, fileNameOverride: String?): JsonObject {
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
        if (priority.isBlank()) {
            priority = "curseforge"
        }
        ordered.addProperty("priority", priority)

        ordered.addProperty("identifier", getString(obj, "identifier"))

        var fileName = fileNameOverride
        if (fileName.isNullOrBlank()) {
            fileName = getString(obj, "file_name")
        }
        if (!fileName.isNullOrBlank()) {
            ordered.addProperty("file_name", fileName)
        }

        val applied = getString(obj, "appliedTarget")
        ordered.addProperty("appliedTarget", if (applied.isEmpty()) "" else applied)

        ordered.addProperty("isHidden", getBoolean(obj, "isHidden", false))
        val userRemoved = getBoolean(obj, "UserRemoved", false) || getBoolean(obj, "userRemoved", false)
        if (userRemoved) {
            ordered.addProperty("UserRemoved", true)
        }

        val deps = obj.get("dependencies")
        ordered.add("dependencies", deps ?: JsonPrimitive(""))

        var display: JsonObject? = null
        if (obj.has("DisplayName") && obj.get("DisplayName").isJsonObject) {
            display = obj.getAsJsonObject("DisplayName")
        } else if (obj.has("displayName") && obj.get("displayName").isJsonObject) {
            display = obj.getAsJsonObject("displayName")
        }
        if (display == null) {
            display = JsonObject()
            display.addProperty("en_us", "")
        }
        ordered.add("DisplayName", display)

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
            if (key == "displayName") {
                continue
            }
            if (key == "UserRemoved" || key == "userRemoved") {
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

    private fun resolveTarget(targetDir: Path?, fileName: String?): Path? {
        if (targetDir == null || fileName.isNullOrBlank()) {
            return null
        }
        val name = targetDir.fileName?.toString() ?: ""
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir
        }
        return targetDir.resolve(fileName)
    }

    private fun normalizeFileName(fileName: String?): String? {
        val trimmedRaw = DlcDownloadUtil.stripQuery(fileName) ?: return null
        val trimmed = trimmedRaw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (DlcDcFormatUtil.isDcName(trimmed)) {
            return null
        }
        return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun isTempFileName(fileName: String?): Boolean {
        if (fileName == null) {
            return false
        }
        return fileName.lowercase(Locale.ROOT).endsWith(".part")
    }
}
