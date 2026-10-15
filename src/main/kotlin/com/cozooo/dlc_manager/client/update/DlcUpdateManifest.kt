package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.regex.Pattern

class DlcUpdateManifest {
    private val unknownParameters = mutableListOf<String>()
    private var version: VersionInfo? = null
    private var fileOps: FileOps? = null
    private var hidden = false
    private var updateDLCM = false
    private var updateDLCMVersion: String? = null
    private var changelog: Map<String, String>? = LinkedHashMap()
    private var updateOps: UpdateOps = UpdateOps()

    fun getUnknownParameters(): List<String> = unknownParameters.toList()

    fun getVersion(): VersionInfo? {
        return version
    }

    fun getFileOps(): FileOps? {
        return fileOps
    }

    fun isHidden(): Boolean {
        return hidden
    }

    fun isUpdateDLCM(): Boolean {
        return updateDLCM
    }

    fun getUpdateDLCMVersion(): String? {
        return updateDLCMVersion
    }

    fun getChangelog(): Map<String, String> {
        return changelog ?: emptyMap()
    }

    fun getUpdateOps(): UpdateOps {
        return updateOps
    }

    fun resolveChangelog(lang: String?): String {
        val values = changelog
        if (values.isNullOrEmpty()) {
            return ""
        }
        val key = DlcDcConfigIO.normalizeLangKey(lang)
        if (key != null && values.containsKey(key)) {
            return values[key] ?: ""
        }
        if (values.containsKey("en_us")) {
            return values["en_us"] ?: ""
        }
        return values.values.firstOrNull() ?: ""
    }

    class VersionInfo {
        private var platform: String? = "universal"
        private var base: String? = null
        private var baseNew: String? = null
        private var dlcOld: String? = null
        private var dlcNew: String? = null

        fun getPlatform(): String {
            return if (platform.isNullOrBlank()) "universal" else platform!!
        }

        fun getBase(): String? {
            return base
        }

        fun getBaseNew(): String? {
            return baseNew
        }

        fun getDlcOld(): String? {
            return dlcOld
        }

        fun getDlcNew(): String? {
            return dlcNew
        }

        internal fun setPlatform(value: String?) {
            platform = value
        }

        internal fun setBase(value: String?) {
            base = value
        }

        internal fun setBaseNew(value: String?) {
            baseNew = value
        }

        internal fun setDlcOld(value: String?) {
            dlcOld = value
        }

        internal fun setDlcNew(value: String?) {
            dlcNew = value
        }
    }

    class FileOps {
        private val del: MutableList<String> = ArrayList()
        private val deleteHashes: MutableMap<String, String> = LinkedHashMap()
        private val add: MutableMap<String, AddEntry> = LinkedHashMap()
        private val move: MutableMap<String, String> = LinkedHashMap()
        private val modify: MutableList<ModifyEntry> = ArrayList()

        fun getDel(): MutableList<String> {
            return del
        }

        fun getDeleteHashes(): MutableMap<String, String> = deleteHashes

        fun getAdd(): MutableMap<String, AddEntry> {
            return add
        }

        fun getMove(): MutableMap<String, String> {
            return move
        }

        fun getModify(): MutableList<ModifyEntry> {
            return modify
        }
    }

    class UpdateOps {
        private val info: MutableMap<String, UpdateEntry> = LinkedHashMap()
        private val remote: MutableMap<String, UpdateEntry> = LinkedHashMap()
        private val required: MutableMap<String, UpdateEntry> = LinkedHashMap()
        private val preset: MutableMap<String, UpdateEntry> = LinkedHashMap()

        fun getInfo(): MutableMap<String, UpdateEntry> {
            return info
        }

        fun getRemote(): MutableMap<String, UpdateEntry> {
            return remote
        }

        fun getRequired(): MutableMap<String, UpdateEntry> {
            return required
        }

        fun getPreset(): MutableMap<String, UpdateEntry> {
            return preset
        }
    }

    class UpdateEntry {
        private var curseforge: String? = null
        private var modrinth: String? = null
        private val fields: MutableMap<String, JsonElement> = LinkedHashMap()

        fun getCurseforge(): String? {
            return curseforge
        }

        fun getModrinth(): String? {
            return modrinth
        }

        fun getFields(): MutableMap<String, JsonElement> {
            return fields
        }

        internal fun setCurseforge(value: String?) {
            curseforge = value
        }

        internal fun setModrinth(value: String?) {
            modrinth = value
        }
    }

    class AddEntry {
        private var sourcePath: String? = null
        private var appliedTarget: String? = null
        private var overrideMode: OverrideMode = OverrideMode.TRUE
        private var sourceIsDir = false

        fun getSourcePath(): String? {
            return sourcePath
        }

        fun getAppliedTarget(): String? {
            return appliedTarget
        }

        fun getOverrideMode(): OverrideMode {
            return overrideMode
        }

        fun isSourceDir(): Boolean {
            return sourceIsDir
        }

        internal fun setSourcePath(value: String?) {
            sourcePath = value
        }

        internal fun setAppliedTarget(value: String?) {
            appliedTarget = value
        }

        internal fun setOverrideMode(value: OverrideMode) {
            overrideMode = value
        }

        internal fun setSourceIsDir(value: Boolean) {
            sourceIsDir = value
        }
    }

    class ModifyEntry {
        private var targetPath: String? = null
        private var find: String? = null
        private var normalizedFind: String? = null
        private var replace: String? = null
        private var which: WhichSpec = WhichSpec.all()

        fun getTargetPath(): String? {
            return targetPath
        }

        fun getFind(): String? {
            return find
        }

        fun getNormalizedFind(): String? {
            return normalizedFind
        }

        fun getReplace(): String? {
            return replace
        }

        fun getWhich(): WhichSpec {
            return which
        }

        internal fun setTargetPath(value: String?) {
            targetPath = value
        }

        internal fun setFind(value: String?) {
            find = value
        }

        internal fun setNormalizedFind(value: String?) {
            normalizedFind = value
        }

        internal fun setReplace(value: String?) {
            replace = value
        }

        internal fun setWhich(value: WhichSpec) {
            which = value
        }
    }

    enum class OverrideMode {
        TRUE,
        FALSE,
        ALL,
        NONE,
    }

    class WhichSpec private constructor(
        private val all: Boolean,
        private val last: Boolean,
        private val indexes: List<Int>,
    ) {
        fun isAll(): Boolean {
            return all
        }

        fun isLast(): Boolean {
            return last
        }

        fun getIndexes(): List<Int> {
            return indexes
        }

        companion object {
            @JvmStatic
            fun all(): WhichSpec {
                return WhichSpec(true, false, emptyList())
            }

            @JvmStatic
            fun last(): WhichSpec {
                return WhichSpec(false, true, emptyList())
            }

            @JvmStatic
            fun indexes(indexes: List<Int>?): WhichSpec {
                return WhichSpec(false, false, indexes ?: emptyList())
            }
        }
    }

    private class UpdateDLCMConfig {
        var enabled = false
        var version: String? = null
    }

    companion object {
        private val GSON: Gson = GsonBuilder().create()
        private val LAST_JSON_PAIR: Pattern = Pattern.compile("\".*?\"\\s*:\\s*\".*?\"")

        @JvmStatic
        @Suppress("DEPRECATION")
        fun load(updateJson: Path?): DlcUpdateManifest? {
            if (updateJson == null || !Files.exists(updateJson)) {
                return null
            }
            var obj: JsonObject? = null
            val fileName = updateJson.fileName?.toString()?.lowercase(Locale.ROOT) ?: ""
            if (fileName.endsWith(".dc")) {
                try {
                    val text = Files.readString(updateJson)
                    val trimmed = text?.trimStart() ?: ""
                    obj = if (trimmed.startsWith("{")) {
                        GSON.fromJson(trimmed, JsonObject::class.java)
                    } else {
                        DlcUpdateDcParser.parse(text)
                    }
                } catch (_: IOException) {
                    return null
                } catch (_: com.google.gson.JsonParseException) {
                    return null
                }
                return fromJsonObject(obj)
            }
            if (obj == null) {
                try {
                    Files.newBufferedReader(updateJson).use { reader ->
                        val jsonReader = JsonReader(reader)
                        jsonReader.isLenient = true
                        obj = GSON.fromJson(jsonReader, JsonObject::class.java)
                    }
                } catch (_: IOException) {
                    return null
                }
            }
            return fromJsonObject(obj)
        }

        @JvmStatic
        fun fromJsonObject(source: JsonObject?): DlcUpdateManifest? {
            if (source == null) {
                return null
            }
            val manifest = DlcUpdateManifest()
            val obj = DlcUpdateCompatibility.knownFields(source, manifest.unknownParameters)
            manifest.version = parseVersion(DlcDcConfigIO.getObjectIgnoreCase(obj, "version"))
            manifest.hidden = getBoolean(obj, "isHidden", false)
            val updateDLCMConfig = parseUpdateDLCM(DlcDcConfigIO.getIgnoreCase(obj, "UpdateDLCM"))
            manifest.updateDLCM = updateDLCMConfig.enabled
            manifest.updateDLCMVersion = updateDLCMConfig.version
            val fileObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "file")
            manifest.fileOps = parseFileOps(fileObj)
            manifest.updateOps = parseUpdateOps(DlcDcConfigIO.getIgnoreCase(obj, "update"))
            manifest.changelog = parseChangelog(DlcDcConfigIO.getIgnoreCase(obj, "changelog"))
            if (manifest.changelog.isNullOrEmpty() && fileObj != null && DlcDcConfigIO.hasIgnoreCase(fileObj, "changelog")) {
                manifest.changelog = parseChangelog(DlcDcConfigIO.getIgnoreCase(fileObj, "changelog"))
            }
            return manifest
        }

        private fun parseVersion(obj: JsonObject?): VersionInfo {
            val info = VersionInfo()
            if (obj == null) {
                return info
            }
            info.setPlatform(normalizePlatform(getString(obj, "platform")))
            info.setBase(getString(obj, "base"))
            info.setBaseNew(getString(obj, "base_new"))
            var dlc = getString(obj, "dlc")
            if (dlc.isNullOrBlank()) {
                dlc = getString(obj, "dlc_old")
            }
            info.setDlcOld(dlc)
            info.setDlcNew(getString(obj, "dlc_new"))
            return info
        }

        private fun parseChangelog(element: JsonElement?): Map<String, String> {
            val values: MutableMap<String, String> = LinkedHashMap()
            if (element == null || element.isJsonNull) {
                return values
            }
            if (element.isJsonPrimitive) {
                val raw = element.asString
                if (!raw.isNullOrBlank()) {
                    values["en_us"] = raw
                }
                return values
            }
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                for ((key, value) in obj.entrySet()) {
                    if (value != null && value.isJsonPrimitive) {
                        val raw = value.asString
                        val normalized = DlcDcConfigIO.normalizeLangKey(key)
                        if (!raw.isNullOrBlank() && normalized != null) {
                            values[normalized] = raw
                        }
                    }
                }
            }
            return values
        }

        private fun parseUpdateDLCM(element: JsonElement?): UpdateDLCMConfig {
            val config = UpdateDLCMConfig()
            if (element == null || element.isJsonNull || !element.isJsonPrimitive) {
                return config
            }
            if (element.asJsonPrimitive.isBoolean) {
                config.enabled = element.asBoolean
                return config
            }
            val raw = element.asString ?: return config
            val value = raw.trim()
            if (value.isBlank() || value.equals("false", ignoreCase = true)) {
                return config
            }
            if (value.equals("true", ignoreCase = true)) {
                config.enabled = true
                return config
            }
            config.enabled = true
            config.version = value
            return config
        }

        private fun parseFileOps(obj: JsonObject?): FileOps {
            val ops = FileOps()
            if (obj == null) {
                return ops
            }
            if (DlcDcConfigIO.hasIgnoreCase(obj, "del")) {
                val deletes = DlcDcConfigIO.getIgnoreCase(obj, "del")
                if (deletes != null && deletes.isJsonObject) {
                    for ((path, value) in deletes.asJsonObject.entrySet()) {
                        ops.getDel().add(path)
                        if (value.isJsonObject) {
                            DlcDcConfigIO.getStringIgnoreCase(value.asJsonObject, "hash")?.let {
                                ops.getDeleteHashes()[path] = it
                            }
                        }
                    }
                } else {
                    ops.getDel().addAll(readStringArray(deletes))
                }
            }
            if (DlcDcConfigIO.hasIgnoreCase(obj, "add")) {
                val addEl = DlcDcConfigIO.getIgnoreCase(obj, "add")
                if (addEl != null && addEl.isJsonObject) {
                    val addObj = addEl.asJsonObject
                    for ((key, value) in addObj.entrySet()) {
                        if (key.isNullOrBlank()) {
                            continue
                        }
                        val addEntry = parseAddEntry(key, value)
                        if (addEntry != null) {
                            ops.getAdd()[key] = addEntry
                        }
                    }
                }
            }
            if (DlcDcConfigIO.hasIgnoreCase(obj, "move")) {
                val moveEl = DlcDcConfigIO.getIgnoreCase(obj, "move")
                if (moveEl != null && moveEl.isJsonObject) {
                    val moveObj = moveEl.asJsonObject
                    for ((from, value) in moveObj.entrySet()) {
                        if (from.isNullOrBlank() || value == null || !value.isJsonPrimitive) {
                            continue
                        }
                        val to = value.asString
                        if (to.isNullOrBlank()) {
                            continue
                        }
                        ops.getMove()[from] = to
                    }
                }
            }
            if (DlcDcConfigIO.hasIgnoreCase(obj, "modify")) {
                val modEl = DlcDcConfigIO.getIgnoreCase(obj, "modify")
                if (modEl != null && modEl.isJsonObject) {
                    val modObj = modEl.asJsonObject
                    for ((key, value) in modObj.entrySet()) {
                        if (value != null && value.isJsonArray) {
                            for (child in value.asJsonArray) {
                                val mod = parseModifyEntry(key, child)
                                if (mod != null) {
                                    ops.getModify().add(mod)
                                }
                            }
                        } else {
                            val mod = parseModifyEntry(key, value)
                            if (mod != null) {
                                ops.getModify().add(mod)
                            }
                        }
                    }
                } else if (modEl != null && modEl.isJsonArray) {
                    for (element in modEl.asJsonArray) {
                        if (!element.isJsonObject) {
                            continue
                        }
                        val modObj = element.asJsonObject
                        val path = getString(modObj, "path")
                        if (path.isNullOrBlank()) {
                            continue
                        }
                        val mod = parseModifyEntry(path, modObj)
                        if (mod != null) {
                            ops.getModify().add(mod)
                        }
                    }
                }
            }
            return ops
        }

        private fun parseUpdateOps(element: JsonElement?): UpdateOps {
            val ops = UpdateOps()
            if (element == null || element.isJsonNull || !element.isJsonObject) {
                return ops
            }
            val obj = element.asJsonObject
            val infoObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "info")
            if (infoObj != null) {
                for ((key, value) in infoObj.entrySet()) {
                    if (key.isNullOrBlank()) {
                        continue
                    }
                    val updateEntry = parseUpdateEntry(value)
                    if (updateEntry != null) {
                        ops.getInfo()[key] = updateEntry
                    }
                }
            }
            val remoteObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "remote")
            if (remoteObj != null) {
                for ((key, value) in remoteObj.entrySet()) {
                    if (key.isNullOrBlank()) {
                        continue
                    }
                    val updateEntry = parseUpdateEntry(value)
                    if (updateEntry != null) {
                        ops.getRemote()[key] = updateEntry
                    }
                }
            }
            val requiredObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "required")
            if (requiredObj != null) {
                for ((key, value) in requiredObj.entrySet()) {
                    if (key.isNullOrBlank()) {
                        continue
                    }
                    val updateEntry = parseUpdateEntry(value)
                    if (updateEntry != null) {
                        ops.getRequired()[key] = updateEntry
                    }
                }
            }
            val presetObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "preset")
            if (presetObj != null) {
                for ((key, value) in presetObj.entrySet()) {
                    if (key.isNullOrBlank()) {
                        continue
                    }
                    val updateEntry = parseUpdateEntry(value)
                    if (updateEntry != null) {
                        ops.getPreset()[key] = updateEntry
                    }
                }
            }
            return ops
        }

        private fun parseUpdateEntry(element: JsonElement?): UpdateEntry? {
            if (element == null || element.isJsonNull || !element.isJsonObject) {
                return null
            }
            val obj = element.asJsonObject
            val curseforge = getString(obj, "curseforge")
            val modrinth = getString(obj, "modrinth")
            val directUrl = DlcDcConfigIO.getIgnoreCase(obj, "directUrl")
            val extraFields: MutableMap<String, JsonElement> = LinkedHashMap()
            for ((key, value) in obj.entrySet()) {
                if (key.isNullOrBlank()) {
                    continue
                }
                if (key.equals("curseforge", ignoreCase = true) ||
                    key.equals("modrinth", ignoreCase = true) ||
                    key.equals("directUrl", ignoreCase = true)
                ) {
                    continue
                }
                flattenUpdateField(key, value, extraFields)
            }
            if (directUrl != null && !extraFields.containsKey("download.directUrl")) {
                extraFields["download.directUrl"] = directUrl.deepCopy()
            }
            if (curseforge.isNullOrBlank() && modrinth.isNullOrBlank() && extraFields.isEmpty()) {
                return null
            }
            val entry = UpdateEntry()
            entry.setCurseforge(curseforge)
            entry.setModrinth(modrinth)
            entry.getFields().putAll(extraFields)
            return entry
        }

        private fun flattenUpdateField(key: String?, value: JsonElement?, out: MutableMap<String, JsonElement>?) {
            if (key.isNullOrBlank() || out == null) {
                return
            }
            if (value == null || value.isJsonNull) {
                out.putIfAbsent(key, JsonNull.INSTANCE)
                return
            }
            if (!value.isJsonObject) {
                out.putIfAbsent(key, value.deepCopy())
                return
            }
            val obj = value.asJsonObject
            if (obj.entrySet().isEmpty()) {
                out.putIfAbsent(key, JsonObject())
                return
            }
            for ((childKey, childValue) in obj.entrySet()) {
                if (childKey.isNullOrBlank()) {
                    continue
                }
                flattenUpdateField("$key.$childKey", childValue, out)
            }
        }

        private fun parseAddEntry(key: String?, element: JsonElement?): AddEntry? {
            if (element == null || !element.isJsonObject) {
                return null
            }
            val obj = element.asJsonObject
            val entry = AddEntry()
            entry.setSourcePath(key)
            entry.setAppliedTarget(getString(obj, "appliedTarget"))
            entry.setOverrideMode(parseOverride(DlcDcConfigIO.getIgnoreCase(obj, "override")))
            entry.setSourceIsDir(key != null && key.endsWith("/"))
            return entry
        }

        private fun parseModifyEntry(path: String?, element: JsonElement?): ModifyEntry? {
            if (path.isNullOrBlank() || element == null || !element.isJsonObject) {
                return null
            }
            val obj = element.asJsonObject
            val find = getString(obj, "find")
            val replace = getString(obj, "replace")
            if (find == null || replace == null) {
                return null
            }
            val entry = ModifyEntry()
            entry.setTargetPath(path)
            entry.setFind(find)
            entry.setReplace(replace)
            entry.setWhich(parseWhich(DlcDcConfigIO.getIgnoreCase(obj, "which")))
            entry.setNormalizedFind(normalizeFind(find))
            return entry
        }

        private fun normalizeFind(find: String?): String? {
            if (find == null) {
                return null
            }
            val matcher = LAST_JSON_PAIR.matcher(find)
            var last: String? = null
            while (matcher.find()) {
                last = matcher.group()
            }
            if (!last.isNullOrBlank()) {
                return last.trim()
            }
            return find.trim()
        }

        private fun parseOverride(element: JsonElement?): OverrideMode {
            if (element == null || element.isJsonNull) {
                return OverrideMode.TRUE
            }
            if (element.isJsonPrimitive) {
                if (element.asJsonPrimitive.isBoolean) {
                    return if (element.asBoolean) OverrideMode.TRUE else OverrideMode.FALSE
                }
                val raw = element.asString ?: return OverrideMode.TRUE
                return when (raw.trim().lowercase(Locale.ROOT)) {
                    "all" -> OverrideMode.ALL
                    "none" -> OverrideMode.NONE
                    "false" -> OverrideMode.FALSE
                    else -> OverrideMode.TRUE
                }
            }
            return OverrideMode.TRUE
        }

        private fun parseWhich(element: JsonElement?): WhichSpec {
            if (element == null || element.isJsonNull) {
                return WhichSpec.all()
            }
            if (element.isJsonPrimitive) {
                if (element.asJsonPrimitive.isNumber) {
                    val value = element.asInt
                    return if (value == -1) WhichSpec.last() else WhichSpec.indexes(listOf(value))
                }
                val raw = element.asString ?: return WhichSpec.all()
                return parseWhichString(raw)
            }
            if (element.isJsonArray) {
                val indexes: MutableList<Int> = ArrayList()
                var last = false
                for (child in element.asJsonArray) {
                    if (child == null || child.isJsonNull) {
                        continue
                    }
                    if (child.isJsonPrimitive) {
                        if (child.asJsonPrimitive.isNumber) {
                            val value = child.asInt
                            if (value == -1) {
                                last = true
                            } else {
                                indexes.add(value)
                            }
                        } else {
                            val spec = parseWhichString(child.asString)
                            if (spec.isAll()) {
                                return WhichSpec.all()
                            }
                            if (spec.isLast()) {
                                last = true
                            }
                            indexes.addAll(spec.getIndexes())
                        }
                    }
                }
                if (last) {
                    return WhichSpec.last()
                }
                if (indexes.isEmpty()) {
                    return WhichSpec.all()
                }
                return WhichSpec.indexes(indexes)
            }
            return WhichSpec.all()
        }

        private fun parseWhichString(raw: String?): WhichSpec {
            if (raw == null) {
                return WhichSpec.all()
            }
            val value = raw.trim().lowercase(Locale.ROOT)
            if (value.isBlank() || value == "all") {
                return WhichSpec.all()
            }
            if (value == "-1" || value == "last") {
                return WhichSpec.last()
            }
            val indexes: MutableList<Int> = ArrayList()
            for (part in value.split(",")) {
                val trimmed = part.trim()
                if (trimmed.isEmpty()) {
                    continue
                }
                try {
                    val num = trimmed.toInt()
                    if (num == -1) {
                        return WhichSpec.last()
                    }
                    indexes.add(num)
                } catch (_: NumberFormatException) {
                }
            }
            if (indexes.isEmpty()) {
                return WhichSpec.all()
            }
            return WhichSpec.indexes(indexes)
        }

        private fun readStringArray(element: JsonElement?): List<String> {
            val values: MutableList<String> = ArrayList()
            if (element == null || element.isJsonNull) {
                return values
            }
            if (element.isJsonPrimitive) {
                values.add(element.asString)
                return values
            }
            if (element.isJsonArray) {
                for (child in element.asJsonArray) {
                    if (child == null || child.isJsonNull) {
                        continue
                    }
                    if (child.isJsonPrimitive) {
                        values.add(child.asString)
                    }
                }
            }
            return values
        }

        private fun getString(obj: JsonObject, key: String): String? {
            return DlcDcConfigIO.getStringIgnoreCase(obj, key)
        }

        private fun getBoolean(obj: JsonObject, key: String, fallback: Boolean): Boolean {
            return DlcDcConfigIO.getBooleanIgnoreCase(obj, key, fallback)
        }

        private fun normalizePlatform(value: String?): String {
            if (value == null) {
                return "universal"
            }
            val normalized = value.trim().lowercase(Locale.ROOT)
            if (normalized == "client" || normalized == "server" || normalized == "universal") {
                return normalized
            }
            return "universal"
        }
    }
}
