package com.cozooo.dlc_manager.client.remote

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil
import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.core.DcFormatUtil
import com.cozooo.dlc_manager.core.DlcTooltipLoader
import com.cozooo.dlc_manager.core.DlcType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.JsonReader
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

object DlcRemoteLoader {
    private val GSON: Gson = GsonBuilder().create()

    @JvmStatic
    fun load(dlcRoot: Path?): List<DlcRemoteEntry> {
        if (dlcRoot == null) {
            return emptyList()
        }
        val remoteRoot = ensureRemoteRoot(dlcRoot)
        val redirectRoot = remoteRoot.resolve("redirect")
        if (!Files.isDirectory(remoteRoot)) {
            return emptyList()
        }
        val entries = ArrayList<DlcRemoteEntry>()
        val selectedFiles = LinkedHashMap<String, Path>()
        try {
            Files.walk(remoteRoot).use { stream ->
                stream.filter(Files::isRegularFile)
                    .filter { path -> isRemoteConfigName(path.fileName.toString()) }
                    .filter { path -> !path.normalize().startsWith(redirectRoot) }
                    .forEach { path ->
                        val rel = remoteRoot.relativize(path).toString().replace('\\', '/')
                        val key = DcFormatUtil.stripConfigExtension(rel)
                        val current = selectedFiles[key]
                        if (current == null || configPriority(path) > configPriority(current)) {
                            selectedFiles[key] = path
                        }
                    }
            }
        } catch (_: IOException) {
        }
        for (candidate in selectedFiles.values) {
            val entry = readEntry(candidate, dlcRoot, remoteRoot)
            if (entry != null) {
                entries.add(entry)
            }
        }
        linkIdentifierDependencies(entries)
        return entries
    }

    @JvmStatic
    fun loadSingle(configPath: Path?, dlcRoot: Path?): DlcRemoteEntry? {
        if (configPath == null || dlcRoot == null) {
            return null
        }
        val remoteRoot = ensureRemoteRoot(dlcRoot)
        val legacyRoot = dlcRoot.resolve("info").resolve("remote")
        if (!Files.isDirectory(remoteRoot)) {
            return null
        }
        var resolved = configPath
        if (!Files.exists(resolved)) {
            val alt = resolveConfigPathWithDc(resolved)
            if (alt != null && Files.exists(alt)) {
                resolved = alt
            }
            try {
                val normalized = resolved.normalize()
                if (normalized.startsWith(legacyRoot.normalize())) {
                    val relative = legacyRoot.relativize(normalized)
                    resolved = remoteRoot.resolve(relative)
                }
            } catch (_: Exception) {
            }
            if (!Files.exists(resolved)) {
                return null
            }
        }
        val dcAlt = DcFormatUtil.toDcPath(resolved)
        if (dcAlt != null && Files.exists(dcAlt)) {
            resolved = dcAlt
        }
        return readEntry(resolved, dlcRoot, remoteRoot)
    }

    private fun linkIdentifierDependencies(entries: List<DlcRemoteEntry>?) {
        if (entries == null || entries.isEmpty()) {
            return
        }
        val index = HashMap<String, DlcRemoteEntry>()
        for (entry in entries) {
            val identifier = entry.getDlcIdentifier()
            if (identifier.isNullOrBlank()) {
                continue
            }
            index.putIfAbsent(identifier, entry)
        }
        for (entry in entries) {
            val linked = ArrayList(entry.getRemoteDependencies())
            val seen = HashSet<String>()
            for (dep in linked) {
                val identifier = dep?.getDlcIdentifier()
                if (identifier != null) {
                    seen.add(identifier)
                }
            }
            for (depId in entry.getDependencyNames()) {
                if (depId.isNullOrBlank()) {
                    continue
                }
                if (seen.contains(depId)) {
                    continue
                }
                val depEntry = index[depId]
                if (depEntry != null && depEntry !== entry) {
                    linked.add(depEntry)
                    seen.add(depId)
                }
            }
            entry.setRemoteDependencies(linked)
        }
    }

    private fun readEntry(path: Path, dlcRoot: Path, remoteRoot: Path): DlcRemoteEntry? {
        val obj = readConfigObject(path) ?: return null
        val fileName = DcFormatUtil.stripConfigExtension(path.fileName.toString())
        val relative = remoteRoot.relativize(path)
        val parent = relative.parent
        val baseDir = dlcRoot.resolve(parent?.toString() ?: "")
        val normalized = normalizeRemoteObject(obj, fileName, dlcRoot, baseDir) ?: return null
        val entry = normalized.entry
        var finalPath = path
        if (entry.getFileNameHint().isNullOrBlank()) {
            entry.setFileNameHint(fileName)
        }
        val isDc = DcFormatUtil.isDcName(path.fileName.toString())
        if (isDc) {
            if (needsKeyMigration(obj)) {
                DcFormatUtil.write(path, normalized.ordered)
            }
        } else {
            val dcPath = DcFormatUtil.toDcPath(path)
            val migratedToDc = dcPath != null && DcFormatUtil.write(dcPath, normalized.ordered)
            if (migratedToDc) {
                finalPath = dcPath!!
                try {
                    Files.deleteIfExists(path)
                } catch (_: IOException) {
                }
            }
        }
        entry.setConfigPath(finalPath)
        return entry
    }

    private fun readConfigObject(path: Path?): JsonObject? {
        if (path == null || path.fileName == null) {
            return null
        }
        val name = path.fileName.toString()
        if (DcFormatUtil.isDcName(name)) {
            return DcFormatUtil.read(path)
        }
        return try {
            Files.newBufferedReader(path).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                GSON.fromJson(jsonReader, JsonObject::class.java)
            }
        } catch (_: IOException) {
            null
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private fun isRemoteConfigName(fileName: String): Boolean = DcFormatUtil.isDcName(fileName)

    private fun configPriority(path: Path?): Int {
        if (path == null || path.fileName == null) {
            return 0
        }
        val name = path.fileName.toString().lowercase()
        if (name.endsWith(DcFormatUtil.DC_EXTENSION)) {
            return 2
        }
        return 0
    }

    private fun resolveConfigPathWithDc(path: Path?): Path? {
        if (path == null) {
            return null
        }
        if (path.fileName == null) {
            return path
        }
        val name = path.fileName.toString()
        if (DcFormatUtil.isDcName(name)) {
            val dc = DcFormatUtil.toDcPath(path)
            if (dc != null && Files.exists(dc)) {
                return dc
            }
            return path
        }
        val dir = path.parent ?: return null
        val dc = dir.resolve(name + DcFormatUtil.DC_EXTENSION)
        if (Files.exists(dc)) {
            return dc
        }
        return dc
    }

    private fun ensureRemoteRoot(dlcRoot: Path): Path {
        val remoteRoot = dlcRoot.resolve("remote")
        val legacyRoot = dlcRoot.resolve("info").resolve("remote")
        if (!Files.exists(remoteRoot) && Files.isDirectory(legacyRoot)) {
            moveLegacyRemote(legacyRoot, remoteRoot)
        } else if (Files.isDirectory(legacyRoot)) {
            moveLegacyRemote(legacyRoot, remoteRoot)
        }
        return remoteRoot
    }

    private fun moveLegacyRemote(legacyRoot: Path, remoteRoot: Path) {
        try {
            Files.createDirectories(remoteRoot)
        } catch (_: IOException) {
            return
        }
        try {
            Files.walkFileTree(
                legacyRoot,
                object : SimpleFileVisitor<Path>() {
                    @Throws(IOException::class)
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = legacyRoot.relativize(file)
                        val target = remoteRoot.resolve(relative)
                        if (!Files.exists(target)) {
                            Files.createDirectories(target.parent)
                            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING)
                        } else {
                            Files.deleteIfExists(file)
                        }
                        return FileVisitResult.CONTINUE
                    }

                    @Throws(IOException::class)
                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        if (dir != legacyRoot) {
                            Files.deleteIfExists(dir)
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
            Files.deleteIfExists(legacyRoot)
        } catch (_: IOException) {
        }
    }

    private fun normalizeRemoteObject(obj: JsonObject?, fallbackName: String?, dlcRoot: Path, baseDir: Path): NormalizedRemote? {
        if (obj == null) {
            return null
        }
        val directUrls = getDirectUrls(obj)
        val directUrl = if (directUrls.isEmpty()) getString(obj, "directUrl") else directUrls[0]
        val projectId = getString(obj, "modrinth_projectid")
        val versionId = getString(obj, "modrinth_versionid")
        val curseProjectId = getString(obj, "curseforge_projectid")
        val curseFileId = getString(obj, "curseforge_fileid")
        val priority = DlcDownloadCandidate.readPriority(obj)
        var fileName = getString(obj, "file_name")
        var appliedTarget = getString(obj, "appliedTarget")
        val redirectUrl = getString(obj, "RedirectUrl")
        if (appliedTarget != null) {
            appliedTarget = appliedTarget.trim()
        }
        val fileNameExplicit = !fileName.isNullOrBlank()
        var nameFallback = fallbackName
        if (nameFallback.isNullOrBlank()) {
            nameFallback = when {
                !directUrl.isNullOrBlank() -> extractFileName(directUrl)
                !projectId.isNullOrBlank() -> projectId
                !curseProjectId.isNullOrBlank() -> curseProjectId
                else -> nameFallback
            }
        }
        var identifier = resolveIdentifier(obj, nameFallback)
        if (fileName.isNullOrBlank()) {
            if (!directUrl.isNullOrBlank()) {
                fileName = extractFileName(directUrl)
            }
            if (fileName.isNullOrBlank()) {
                fileName = nameFallback
            }
        }
        var displayObj = sanitizeLocalized(DcConfigIO.getObjectIgnoreCase(obj, "DisplayName"))
        if (displayObj.entrySet().isEmpty()) {
            displayObj = JsonObject()
            displayObj.addProperty("en_us", nameFallback ?: "")
        }
        if (identifier.isNullOrBlank()) {
            identifier = nameFallback ?: ""
        }
        val tipObj = DcConfigIO.getObjectIgnoreCase(obj, "tip") ?: JsonObject()
        val typeRaw = getString(obj, "type")
        var type = DlcTooltipLoader.parseTypeString(typeRaw)
        if (type == null) {
            type = DlcType.MOD
        }
        val legacyShown = getBoolean(obj, "isShownInList", true)
        var inList = getBoolean(obj, "isInList", legacyShown)
        var hidden = getBoolean(obj, "isHidden", false)
        val allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true)
        val alarm = getBoolean(obj, "alarm", false)
        if (type == DlcType.UNKNOWN && appliedTarget.isNullOrBlank()) {
            inList = false
        }
        if (type == DlcType.DLC_PACK) {
            inList = false
            hidden = true
        }

        val deps = normalizeDependencies(DcConfigIO.getIgnoreCase(obj, "dependencies"), dlcRoot, baseDir)
        val conflicts = normalizeConflicts(DcConfigIO.getIgnoreCase(obj, "conflicts"))

        val ordered = JsonObject()
        ordered.addProperty("curseforge_projectid", curseProjectId ?: "")
        ordered.addProperty("curseforge_fileid", curseFileId ?: "")
        ordered.addProperty("modrinth_projectid", projectId ?: "")
        ordered.addProperty("modrinth_versionid", versionId ?: "")
        if (directUrls.size > 1) {
            val urls = JsonArray()
            for (url in directUrls) {
                urls.add(url)
            }
            ordered.add("directUrl", urls)
        } else {
            ordered.addProperty("directUrl", directUrl ?: "")
        }
        ordered.addProperty("priority", if (priority.isBlank()) "curseforge" else priority)
        ordered.addProperty("identifier", identifier)
        ordered.addProperty("type", DlcTooltipLoader.toTypeString(type))
        if (!appliedTarget.isNullOrBlank()) {
            ordered.addProperty("appliedTarget", appliedTarget)
        }
        if (!redirectUrl.isNullOrBlank()) {
            ordered.addProperty("RedirectUrl", redirectUrl)
        }
        if (!fileName.isNullOrBlank()) {
            ordered.addProperty("file_name", fileName)
        }
        ordered.addProperty("isInList", inList)
        ordered.addProperty("isHidden", hidden)
        ordered.addProperty("AllowEnableByAll", allowEnableByAll)
        ordered.add("dependencies", deps.element)
        ordered.add("conflicts", conflicts.element)
        ordered.add("DisplayName", displayObj)
        ordered.addProperty("alarm", alarm)
        ordered.add("tip", tipObj)

        for ((key, value) in obj.entrySet()) {
            if (key == null || ordered.has(key)) {
                continue
            }
            if ("platform".equals(key, ignoreCase = true) || "projectid".equals(key, ignoreCase = true) || "version".equals(key, ignoreCase = true)
                || "modid".equals(key, ignoreCase = true) || "dlc_name".equals(key, ignoreCase = true) || "dlc_identifier".equals(key, ignoreCase = true)
                || "identifier".equals(key, ignoreCase = true)
                || "isShownInList".equals(key, ignoreCase = true)
            ) {
                continue
            }
            ordered.add(key, value)
        }

        val entry = DlcRemoteEntry(null, dlcRoot, baseDir, nameFallback)
        val directElement = ordered.get("directUrl")
        if (directElement != null && directElement.isJsonArray) {
            val urls = ArrayList<String>()
            for (el in directElement.asJsonArray) {
                if (el != null && el.isJsonPrimitive) {
                    val url = el.asString
                    if (!url.isNullOrBlank()) {
                        urls.add(url)
                    }
                }
            }
            entry.setDirectUrls(urls)
        } else {
            entry.setDirectUrl(directElement?.asString)
        }
        entry.setModrinthProjectId(ordered.get("modrinth_projectid").asString)
        entry.setModrinthVersionId(ordered.get("modrinth_versionid").asString)
        entry.setCurseforgeProjectId(ordered.get("curseforge_projectid").asString)
        entry.setCurseforgeFileId(ordered.get("curseforge_fileid").asString)
        entry.setDownloadPriority(ordered.get("priority").asString)
        entry.setDlcIdentifier(ordered.get("identifier").asString)
        var resolvedFileName = fileName
        if (resolvedFileName.isNullOrBlank()) {
            resolvedFileName = nameFallback
        }
        entry.setFileNameHint(resolvedFileName)
        entry.setFileNameExplicit(fileNameExplicit)
        entry.setType(type)
        entry.setConfigType("config".equals(typeRaw?.trim(), ignoreCase = true))
        entry.setAppliedTargetRaw(appliedTarget)
        entry.setRedirectUrl(redirectUrl)
        entry.setInList(inList)
        entry.setHidden(hidden)
        entry.setAllowEnableByAll(allowEnableByAll)
        entry.setDependencyNames(deps.names)
        entry.setConflictNames(conflicts.names)
        entry.setRemoteDependencies(deps.entries)
        entry.setDisplayNames(readLocalized(displayObj))
        entry.setTips(readLocalized(tipObj))
        entry.setAlarm(alarm)
        return NormalizedRemote(entry, ordered)
    }

    private fun normalizeDependencies(element: JsonElement?, dlcRoot: Path, baseDir: Path): DependencyResult {
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
                val childResult = normalizeDependencies(child, dlcRoot, baseDir)
                array.add(childResult.element)
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
            if (isRemoteConfigObject(obj)) {
                val normalized = normalizeRemoteObject(obj, null, dlcRoot, baseDir)
                if (normalized != null) {
                    result.element = normalized.ordered
                    result.entries.add(normalized.entry)
                    val identifier = normalized.entry.getDlcIdentifier()
                    if (!identifier.isNullOrBlank()) {
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
                    val normalized = normalizeRemoteObject(childObj, key, dlcRoot, baseDir)
                    if (normalized != null) {
                        ordered.add(key, normalized.ordered)
                        result.entries.add(normalized.entry)
                        val identifier = normalized.entry.getDlcIdentifier()
                        if (!identifier.isNullOrBlank()) {
                            result.names.add(identifier)
                        }
                    } else {
                        ordered.add(key, value)
                    }
                } else if (value.isJsonArray) {
                    val childResult = normalizeDependencies(value, dlcRoot, baseDir)
                    ordered.add(key, childResult.element)
                    result.names.addAll(childResult.names)
                    result.entries.addAll(childResult.entries)
                } else {
                    ordered.add(key, value)
                }
            }
            result.element = ordered
            return result
        }
        result.element = JsonPrimitive("")
        return result
    }

    private fun normalizeConflicts(element: JsonElement?): DependencyResult {
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
                val childResult = normalizeConflicts(child)
                array.add(childResult.element)
                result.names.addAll(childResult.names)
            }
            result.element = array
            return result
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (isIdentifierOnlyObject(obj)) {
                val name = resolveIdentifier(obj, null)
                result.element = JsonPrimitive(name ?: "")
                result.names.addAll(splitNames(name))
                return result
            }
            val ordered = JsonObject()
            for ((key, value) in obj.entrySet()) {
                if (value == null || value.isJsonNull) {
                    continue
                }
                if (value.isJsonPrimitive) {
                    val raw = value.asString
                    if (raw.isBlank()) {
                        continue
                    }
                    ordered.addProperty(key, raw)
                    result.names.addAll(splitNames(raw))
                    continue
                }
                if (value.isJsonArray) {
                    val childResult = normalizeConflicts(value)
                    ordered.add(key, childResult.element)
                    result.names.addAll(childResult.names)
                    continue
                }
                if (value.isJsonObject) {
                    val identifier = resolveIdentifier(value.asJsonObject, key)
                    val name = if (identifier.isNullOrBlank()) key else identifier
                    if (!name.isNullOrBlank()) {
                        ordered.addProperty(key, name)
                        result.names.add(name)
                    }
                }
            }
            result.element = ordered
            return result
        }
        result.element = JsonPrimitive("")
        return result
    }

    private fun splitNames(raw: String?): List<String> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        val names = ArrayList<String>()
        for (part in raw.split(Regex("[\\n\\r,]+"))) {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                names.add(trimmed)
            }
        }
        return names
    }

    private fun getDirectUrls(obj: JsonObject?): List<String> {
        if (obj == null) {
            return emptyList()
        }
        val element = DcConfigIO.getIgnoreCase(obj, "directUrl") ?: return emptyList()
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

    private fun extractFileName(url: String?): String? {
        if (url == null) {
            return null
        }
        var cleaned = DlcDownloadUtil.stripQuery(url)
        if (cleaned.isNullOrBlank()) {
            cleaned = url
        }
        val idx = cleaned.lastIndexOf('/')
        if (idx >= 0 && idx + 1 < cleaned.length) {
            return DlcDownloadUtil.decodeUrlPathSegment(cleaned.substring(idx + 1))
        }
        return cleaned
    }

    private fun isIdentifierOnlyObject(obj: JsonObject?): Boolean {
        if (obj == null) {
            return false
        }
        val hasIdentifier = DcConfigIO.hasIgnoreCase(obj, "identifier")
            || DcConfigIO.hasIgnoreCase(obj, "dlc_identifier")
            || DcConfigIO.hasIgnoreCase(obj, "file_name")
            || DcConfigIO.hasIgnoreCase(obj, "dlc_name")
            || DcConfigIO.hasIgnoreCase(obj, "name")
        if (!hasIdentifier) {
            return false
        }
        for ((key, _) in obj.entrySet()) {
            if ("identifier".equals(key, ignoreCase = true) || "dlc_identifier".equals(key, ignoreCase = true)
                || "file_name".equals(key, ignoreCase = true)
                || "dlc_name".equals(key, ignoreCase = true) || "name".equals(key, ignoreCase = true)
            ) {
                continue
            }
            return false
        }
        return true
    }

    private fun resolveIdentifier(obj: JsonObject?, fallback: String?): String? {
        if (obj == null) {
            return fallback
        }
        var identifier = getString(obj, "identifier")
        if (identifier.isNullOrBlank()) {
            identifier = getString(obj, "dlc_identifier")
        }
        if (identifier.isNullOrBlank()) {
            identifier = getString(obj, "file_name")
        }
        if (identifier.isNullOrBlank()) {
            identifier = getString(obj, "dlc_name")
        }
        if (identifier.isNullOrBlank()) {
            identifier = getString(obj, "name")
        }
        if (identifier.isNullOrBlank()) {
            identifier = fallback
        }
        return identifier
    }

    private fun isRemoteConfigObject(obj: JsonObject?): Boolean {
        if (obj == null) {
            return false
        }
        return DcConfigIO.hasIgnoreCase(obj, "directUrl")
            || DcConfigIO.hasIgnoreCase(obj, "modrinth_projectid")
            || DcConfigIO.hasIgnoreCase(obj, "modrinth_versionid")
            || DcConfigIO.hasIgnoreCase(obj, "curseforge_projectid")
            || DcConfigIO.hasIgnoreCase(obj, "curseforge_fileid")
            || DcConfigIO.hasIgnoreCase(obj, "identifier")
            || DcConfigIO.hasIgnoreCase(obj, "dlc_identifier")
            || DcConfigIO.hasIgnoreCase(obj, "file_name")
            || DcConfigIO.hasIgnoreCase(obj, "type")
            || DcConfigIO.hasIgnoreCase(obj, "appliedTarget")
            || DcConfigIO.hasIgnoreCase(obj, "DisplayName")
            || DcConfigIO.hasIgnoreCase(obj, "tip")
            || DcConfigIO.hasIgnoreCase(obj, "isInList")
            || DcConfigIO.hasIgnoreCase(obj, "isHidden")
            || DcConfigIO.hasIgnoreCase(obj, "AllowEnableByAll")
            || DcConfigIO.hasIgnoreCase(obj, "dependencies")
            || DcConfigIO.hasIgnoreCase(obj, "conflicts")
            || DcConfigIO.hasIgnoreCase(obj, "alarm")
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

    private fun getString(obj: JsonObject?, key: String): String? = DcConfigIO.getStringIgnoreCase(obj, key)

    private fun getBoolean(obj: JsonObject?, key: String, fallback: Boolean): Boolean = DcConfigIO.getBooleanIgnoreCase(obj, key, fallback)

    private fun sanitizeLocalized(obj: JsonObject?): JsonObject {
        val result = JsonObject()
        if (obj == null) {
            return result
        }
        for ((key, value) in obj.entrySet()) {
            if (value.isJsonPrimitive) {
                val lang = DcConfigIO.normalizeLangKey(key)
                if (lang != null) {
                    result.addProperty(lang, value.asString)
                }
            }
        }
        return result
    }

    private fun readLocalized(obj: JsonObject?): Map<String, String> {
        val values = HashMap<String, String>()
        if (obj == null) {
            return values
        }
        for ((key, value) in obj.entrySet()) {
            if (value.isJsonPrimitive) {
                val lang = DcConfigIO.normalizeLangKey(key)
                if (lang != null) {
                    values[lang] = value.asString
                }
            }
        }
        return values
    }

    private class DependencyResult {
        var element: JsonElement = JsonPrimitive("")
        val names: MutableList<String> = ArrayList()
        val entries: MutableList<DlcRemoteEntry> = ArrayList()
    }

    private class NormalizedRemote(
        val entry: DlcRemoteEntry,
        val ordered: JsonObject,
    )
}
