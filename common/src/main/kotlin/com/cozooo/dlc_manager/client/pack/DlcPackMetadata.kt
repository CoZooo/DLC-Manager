package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.Locale

class DlcPackMetadata(
    @JvmField val identifier: String,
    @JvmField val version: String,
    @JvmField val platform: String,
    @JvmField val gameVersion: String,
    @JvmField val modLoader: String,
    @JvmField val authors: List<String>,
    @JvmField val modpacks: List<String>,
    @JvmField val modpackAnyWildcard: Boolean,
    @JvmField val baseVersions: List<String>,
    @JvmField val dlcVersions: List<String>,
    @JvmField val displayNames: Map<String, String>,
    @JvmField val descriptions: Map<String, String>,
    @JvmField val links: Map<String, String>,
    @JvmField val dependencies: List<String>,
    @JvmField val conflicts: List<String>,
) {
    fun resolveDisplayName(lang: String?): String {
        return resolveLocalized(displayNames, lang, identifier)
    }

    fun resolveDescription(lang: String?): String {
        return resolveLocalized(descriptions, lang, "")
    }

    companion object {
        private val LINK_KEYS = setOf("homepage", "issue", "sponsor")
        private val VERSION_PATTERN = Regex("""^\d+(?:\.\d+)*(?:[-+][A-Za-z0-9_.-]+)?$""")
        private val FORBIDDEN_IDENTIFIER_CHARS = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

        @JvmStatic
        fun read(obj: JsonObject?): Result {
            return read(obj, null)
        }

        @JvmStatic
        fun read(path: Path?): Result {
            if (path == null) {
                return Result.invalid("missing pack.dc")
            }
            val text = try {
                Files.readString(path)
            } catch (_: Exception) {
                null
            }
            return read(DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC), text)
        }

        @JvmStatic
        fun read(obj: JsonObject?, sourceText: String?): Result {
            if (obj == null) {
                return Result.invalid("missing pack.dc")
            }
            val basic = DlcDcConfigIO.getObjectIgnoreCase(obj, "basic")
            val identifier = DlcDcConfigIO.getStringIgnoreCase(basic, "identifier")?.trim()
            if (!isValidIdentifier(identifier)) {
                return Result.invalid("invalid identifier")
            }
            val version = DlcDcConfigIO.getStringIgnoreCase(basic, "version")?.trim()
            if (!isValidVersion(version)) {
                return Result.invalid("invalid version")
            }
            val modpack = DlcDcConfigIO.getObjectIgnoreCase(obj, "modpack")
            val sourceRules = readSourceRules(sourceText)
            val metadata = DlcPackMetadata(
                identifier!!,
                version!!,
                normalizePlatform(DlcDcConfigIO.getStringIgnoreCase(basic, "platform")),
                sourceRules?.basicGameVersion ?: readRuleString(DlcDcConfigIO.getStringIgnoreCase(basic, "gameVersion")),
                sourceRules?.basicModLoader ?: readRuleString(DlcDcConfigIO.getStringIgnoreCase(basic, "modLoader")),
                readStringList(DlcDcConfigIO.getIgnoreCase(basic, "author")).ifEmpty { listOf("Unknown Author") },
                sourceRules?.modpack ?: readRuleList(DlcDcConfigIO.getIgnoreCase(modpack, "modpack")),
                false,
                sourceRules?.base ?: readRuleList(DlcDcConfigIO.getIgnoreCase(modpack, "base")),
                sourceRules?.dlc ?: readRuleList(DlcDcConfigIO.getIgnoreCase(modpack, "dlc")),
                readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "DisplayName")),
                readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "description")),
                readLinks(obj),
                readStringSection(DlcDcConfigIO.getIgnoreCase(obj, "dependencies")),
                readStringSection(DlcDcConfigIO.getIgnoreCase(obj, "conflicts")),
            )
            return Result.valid(metadata)
        }

        @JvmStatic
        fun isValidIdentifier(value: String?): Boolean {
            if (value.isNullOrBlank() || value != value.trim()) {
                return false
            }
            for (ch in value) {
                if (ch.code < 32 || FORBIDDEN_IDENTIFIER_CHARS.contains(ch)) {
                    return false
                }
            }
            return try {
                Path.of(value)
                true
            } catch (_: InvalidPathException) {
                false
            }
        }

        @JvmStatic
        fun isValidVersion(value: String?): Boolean {
            return !value.isNullOrBlank() && VERSION_PATTERN.matches(value.trim())
        }

        @JvmStatic
        fun normalizePlatform(value: String?): String {
            val trimmed = value?.trim().orEmpty()
            return if (trimmed.isEmpty()) "universal" else trimmed.lowercase(Locale.ROOT)
        }

        @JvmStatic
        fun currentPlatform(): String {
            return if (CrossPlatformRuntime.isDedicatedServer()) "server" else "client"
        }

        @JvmStatic
        fun platformMatches(rule: String?, current: String?): Boolean {
            val normalized = normalizePlatform(rule)
            if (normalized == "universal") {
                return true
            }
            return normalized == current?.trim()?.lowercase(Locale.ROOT)
        }

        @JvmStatic
        fun versionMatches(rule: String?, current: String?): Boolean {
            val normalized = rule?.trim()
            if (normalized.isNullOrEmpty()) {
                return true
            }
            val currentValue = current?.trim()
            if (currentValue.isNullOrEmpty()) {
                return false
            }
            if (normalized.endsWith(".x", ignoreCase = true)) {
                return currentValue.startsWith(normalized.substring(0, normalized.length - 1))
            }
            val split = normalized.split("~", limit = 2)
            if (split.size == 2) {
                val lower = split[0].trim()
                val upper = split[1].trim()
                if (lower.isNotEmpty() && compareVersions(currentValue, lower) < 0) {
                    return false
                }
                if (upper.isNotEmpty() && compareVersions(currentValue, upper) > 0) {
                    return false
                }
                return true
            }
            return currentValue == normalized
        }

        @JvmStatic
        fun anyVersionMatches(rules: List<String>?, current: String?): Boolean {
            if (rules.isNullOrEmpty()) {
                return true
            }
            for (rule in rules) {
                if (versionMatches(rule, current)) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun anyStringMatches(rules: List<String>?, current: String?): Boolean {
            return anyStringMatches(rules, current, true)
        }

        @JvmStatic
        fun anyStringMatches(rules: List<String>?, current: String?, allowAnyWildcard: Boolean): Boolean {
            if (rules.isNullOrEmpty()) {
                return true
            }
            val currentValue = current?.trim().orEmpty()
            if (currentValue.isEmpty()) {
                return false
            }
            return rules.any {
                val normalized = it.trim()
                normalized.isEmpty() ||
                    currentValue.equals(normalized, ignoreCase = true)
            }
        }

        @JvmStatic
        fun stringRuleMatches(rule: String?, current: String?): Boolean {
            val normalized = rule?.trim().orEmpty()
            if (normalized.isEmpty()) {
                return true
            }
            val currentValue = current?.trim().orEmpty()
            return currentValue.isNotEmpty() && currentValue.equals(normalized, ignoreCase = true)
        }

        @JvmStatic
        fun compareVersionStrings(left: String?, right: String?): Int {
            return compareVersions(left?.trim().orEmpty(), right?.trim().orEmpty())
        }

        private fun resolveLocalized(values: Map<String, String>, lang: String?, fallback: String): String {
            if (values.isEmpty()) {
                return fallback
            }
            val current = DlcDcConfigIO.normalizeLangKey(lang)
            if (!current.isNullOrBlank()) {
                val exact = values[current]
                if (!exact.isNullOrBlank()) {
                    return exact
                }
            }
            val english = values["en_us"]
            if (!english.isNullOrBlank()) {
                return english
            }
            return values.values.firstOrNull { it.isNotBlank() } ?: fallback
        }

        private fun readLocalized(obj: JsonObject?): Map<String, String> {
            if (obj == null) {
                return emptyMap()
            }
            val out = LinkedHashMap<String, String>()
            for ((key, value) in obj.entrySet()) {
                val lang = DlcDcConfigIO.normalizeLangKey(key) ?: continue
                val text = primitiveString(value)?.trimEnd() ?: continue
                out[lang] = text
            }
            return out
        }

        private fun readLinks(root: JsonObject?): Map<String, String> {
            if (root == null) {
                return emptyMap()
            }
            val out = LinkedHashMap<String, String>()
            val links = DlcDcConfigIO.getObjectIgnoreCase(root, "links")
            readLinksInto(out, links)
            val lang = DlcDcConfigIO.normalizeLangKey(CrossPlatformRuntime.languageCode())
            if (!lang.isNullOrBlank()) {
                readLinksInto(out, DlcDcConfigIO.getObjectIgnoreCase(links, lang))
            }
            return out
        }

        private fun readLinksInto(out: MutableMap<String, String>?, obj: JsonObject?) {
            if (out == null || obj == null) {
                return
            }
            for (key in LINK_KEYS) {
                val value = DlcDcConfigIO.getStringIgnoreCase(obj, key)?.trim()
                if (!value.isNullOrEmpty()) {
                    out[key] = value
                }
            }
        }

        private fun readRuleString(value: String?): String = value?.trim().orEmpty()

        private fun readRuleList(element: JsonElement?): List<String> = readStringList(element)

        private fun readStringList(element: JsonElement?): List<String> {
            if (element == null || element.isJsonNull) {
                return emptyList()
            }
            if (element.isJsonArray) {
                return readStringArray(element.asJsonArray)
            }
            val value = primitiveString(element)?.trim()
            return if (value.isNullOrEmpty()) emptyList() else listOf(value)
        }

        private fun readStringSection(element: JsonElement?): List<String> {
            if (element == null || element.isJsonNull) {
                return emptyList()
            }
            if (!element.isJsonObject) {
                return readStringList(element)
            }
            val out = ArrayList<String>()
            for ((key, value) in element.asJsonObject.entrySet()) {
                val values = readStringList(value)
                if (values.isNotEmpty()) {
                    out.addAll(values)
                    continue
                }
                if (value != null && value.isJsonObject) {
                    val identifier = DlcDcConfigIO.getStringIgnoreCase(value.asJsonObject, "identifier")?.trim()
                    if (!identifier.isNullOrEmpty()) {
                        out.add(identifier)
                    } else if (!key.isNullOrBlank()) {
                        out.add(key.trim())
                    }
                } else if (!key.isNullOrBlank()) {
                    out.add(key.trim())
                }
            }
            return out
        }

        private fun readStringArray(array: JsonArray): List<String> {
            val out = ArrayList<String>()
            for (item in array) {
                val value = primitiveString(item)?.trim()
                if (!value.isNullOrEmpty()) {
                    out.add(value)
                }
            }
            return out
        }

        private fun readSourceRules(sourceText: String?): SourceRules? {
            if (sourceText.isNullOrBlank()) {
                return null
            }
            val rules = SourceRules()
            var section = ""
            for (line in sourceText.lines()) {
                val stripped = stripComment(line).trim()
                if (stripped.isEmpty()) {
                    continue
                }
                if (stripped.startsWith("[") && stripped.endsWith("]")) {
                    section = stripped.substring(1, stripped.length - 1).trim().trim('"', '\'')
                    continue
                }
                val eq = stripped.indexOf('=')
                if (eq <= 0) {
                    continue
                }
                val key = stripped.substring(0, eq).trim()
                val value = stripped.substring(eq + 1).trim()
                if (section.equals("basic", ignoreCase = true)) {
                    if (key.equals("gameVersion", ignoreCase = true)) {
                        rules.basicGameVersion = parseQuotedRule(value)
                    } else if (key.equals("modLoader", ignoreCase = true)) {
                        rules.basicModLoader = parseQuotedRule(value)
                    }
                } else if (section.equals("modpack", ignoreCase = true)) {
                    if (key.equals("modpack", ignoreCase = true)) {
                        rules.modpack = parseQuotedRuleList(value)
                    } else if (key.equals("base", ignoreCase = true)) {
                        rules.base = parseQuotedRuleList(value)
                    } else if (key.equals("dlc", ignoreCase = true)) {
                        rules.dlc = parseQuotedRuleList(value)
                    }
                }
            }
            return rules
        }

        private fun parseQuotedRule(raw: String): String {
            val value = raw.trim()
            return if (isQuoted(value)) unquote(value).trim() else ""
        }

        private fun parseQuotedRuleList(raw: String): List<String> {
            val value = raw.trim()
            if (value.isEmpty()) {
                return emptyList()
            }
            if (!value.startsWith("[") || !value.endsWith("]")) {
                val item = parseQuotedRule(value)
                return if (item.isEmpty()) emptyList() else listOf(item)
            }
            return splitArrayTokens(value.substring(1, value.length - 1))
                .map { parseQuotedRule(it) }
                .filter { it.isNotEmpty() }
        }

        private fun splitArrayTokens(text: String): List<String> {
            val out = ArrayList<String>()
            val current = StringBuilder()
            var quote = '\u0000'
            var escaped = false
            for (ch in text) {
                if (escaped) {
                    current.append(ch)
                    escaped = false
                    continue
                }
                if (quote != '\u0000') {
                    current.append(ch)
                    if (quote == '"' && ch == '\\') {
                        escaped = true
                    } else if (ch == quote) {
                        quote = '\u0000'
                    }
                    continue
                }
                if (ch == '"' || ch == '\'') {
                    quote = ch
                    current.append(ch)
                    continue
                }
                if (ch == ',') {
                    out.add(current.toString().trim())
                    current.setLength(0)
                    continue
                }
                current.append(ch)
            }
            out.add(current.toString().trim())
            return out
        }

        private fun isQuoted(value: String): Boolean {
            return value.length >= 2 &&
                ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))
        }

        private fun unquote(value: String): String {
            val inner = value.substring(1, value.length - 1)
            if (!value.startsWith("\"")) {
                return inner
            }
            val out = StringBuilder()
            var escaped = false
            for (ch in inner) {
                if (escaped) {
                    out.append(
                        when (ch) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> ch
                        }
                    )
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else {
                    out.append(ch)
                }
            }
            if (escaped) {
                out.append('\\')
            }
            return out.toString()
        }

        private fun stripComment(line: String): String {
            var quote = '\u0000'
            var escaped = false
            for (i in line.indices) {
                val ch = line[i]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (quote != '\u0000') {
                    if (quote == '"' && ch == '\\') {
                        escaped = true
                    } else if (ch == quote) {
                        quote = '\u0000'
                    }
                    continue
                }
                if (ch == '"' || ch == '\'') {
                    quote = ch
                    continue
                }
                if (ch == '#') {
                    return line.substring(0, i)
                }
            }
            return line
        }

        private fun primitiveString(element: JsonElement?): String? {
            if (element == null || !element.isJsonPrimitive) {
                return null
            }
            return try {
                element.asString
            } catch (_: Exception) {
                null
            }
        }

        private fun compareVersions(left: String, right: String): Int {
            val leftParts = left.lowercase(Locale.ROOT).split(Regex("[.+_-]"))
            val rightParts = right.lowercase(Locale.ROOT).split(Regex("[.+_-]"))
            val count = maxOf(leftParts.size, rightParts.size)
            for (i in 0 until count) {
                val l = leftParts.getOrNull(i) ?: "0"
                val r = rightParts.getOrNull(i) ?: "0"
                val ln = l.toLongOrNull()
                val rn = r.toLongOrNull()
                val cmp = if (ln != null && rn != null) {
                    ln.compareTo(rn)
                } else {
                    l.compareTo(r)
                }
                if (cmp != 0) {
                    return cmp
                }
            }
            return 0
        }
    }

    class Result private constructor(
        @JvmField val valid: Boolean,
        @JvmField val metadata: DlcPackMetadata?,
        @JvmField val error: String?,
    ) {
        companion object {
            @JvmStatic
            fun valid(metadata: DlcPackMetadata): Result = Result(true, metadata, null)

            @JvmStatic
            fun invalid(error: String): Result = Result(false, null, error)
            }
        }

        private class SourceRules {
            var basicGameVersion: String? = null
            var basicModLoader: String? = null
            var modpack: List<String>? = null
            var base: List<String>? = null
            var dlc: List<String>? = null
        }
    }
