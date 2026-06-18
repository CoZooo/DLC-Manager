package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.Locale

class DlcPackMetadata(
    @JvmField val identifier: String,
    @JvmField val version: String,
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

    fun displayNameComponent(lang: String?): Component {
        return DlcTextFormatter.parse(resolveDisplayName(lang))
    }

    fun descriptionComponent(lang: String?): Component {
        return DlcMarkdownRenderer.renderMarkdownComponent(resolveDescription(lang))
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
            val modpackValues = readStringList(DlcDcConfigIO.getIgnoreCase(modpack, "modpack"))
            val metadata = DlcPackMetadata(
                identifier!!,
                version!!,
                DlcDcConfigIO.getStringIgnoreCase(basic, "gameVersion")?.trim().orEmpty(),
                DlcDcConfigIO.getStringIgnoreCase(basic, "modLoader")?.trim().orEmpty(),
                readStringList(DlcDcConfigIO.getIgnoreCase(basic, "author")).ifEmpty { listOf("Unknown Author") },
                modpackValues.ifEmpty { listOf("any") },
                resolveModpackAnyWildcard(modpackValues, sourceText),
                readStringList(DlcDcConfigIO.getIgnoreCase(modpack, "base")).ifEmpty { listOf("any") },
                readStringList(DlcDcConfigIO.getIgnoreCase(modpack, "dlc")).ifEmpty { listOf("any") },
                readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "DisplayName")),
                readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "description")),
                readLinks(DlcDcConfigIO.getObjectIgnoreCase(obj, "links")),
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
        fun versionMatches(rule: String?, current: String?): Boolean {
            val normalized = rule?.trim()
            if (normalized.isNullOrEmpty() || normalized.equals("any", ignoreCase = true)) {
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
                return allowAnyWildcard && rules.any { it.trim().equals("any", ignoreCase = true) }
            }
            return rules.any {
                val normalized = it.trim()
                normalized.isEmpty() ||
                    (allowAnyWildcard && normalized.equals("any", ignoreCase = true)) ||
                    currentValue.equals(normalized, ignoreCase = true)
            }
        }

        @JvmStatic
        fun stringRuleMatches(rule: String?, current: String?): Boolean {
            val normalized = rule?.trim().orEmpty()
            if (normalized.isEmpty() || normalized.equals("any", ignoreCase = true)) {
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

        private fun readLinks(obj: JsonObject?): Map<String, String> {
            if (obj == null) {
                return emptyMap()
            }
            val out = LinkedHashMap<String, String>()
            for (key in LINK_KEYS) {
                val value = DlcDcConfigIO.getStringIgnoreCase(obj, key)?.trim()
                if (!value.isNullOrEmpty()) {
                    out[key] = value
                }
            }
            return out
        }

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

        private fun resolveModpackAnyWildcard(values: List<String>, sourceText: String?): Boolean {
            if (values.isEmpty()) {
                return true
            }
            if (values.none { it.trim().equals("any", ignoreCase = true) }) {
                return false
            }
            val rawValues = readRawModpackValues(sourceText)
            if (rawValues.isEmpty()) {
                return true
            }
            return rawValues.any { isBareAnyToken(it) }
        }

        private fun readRawModpackValues(sourceText: String?): List<String> {
            if (sourceText.isNullOrBlank()) {
                return emptyList()
            }
            val out = ArrayList<String>()
            var inModpack = false
            val lines = sourceText.lines()
            var index = 0
            while (index < lines.size) {
                val stripped = stripComment(lines[index]).trim()
                if (stripped.startsWith("[") && stripped.endsWith("]")) {
                    val section = stripped.substring(1, stripped.length - 1).trim().trim('"', '\'')
                    inModpack = section.equals("modpack", ignoreCase = true)
                    index++
                    continue
                }
                if (inModpack) {
                    val eq = stripped.indexOf('=')
                    if (eq > 0 && stripped.substring(0, eq).trim().equals("modpack", ignoreCase = true)) {
                        val valueText = StringBuilder(stripped.substring(eq + 1).trim())
                        while (valueText.toString().trimStart().startsWith("[") && !valueText.toString().trimEnd().endsWith("]") && index + 1 < lines.size) {
                            index++
                            valueText.append('\n').append(stripComment(lines[index]).trim())
                        }
                        out.addAll(splitRawValues(valueText.toString()))
                    }
                }
                index++
            }
            return out
        }

        private fun splitRawValues(raw: String): List<String> {
            val value = raw.trim()
            if (!value.startsWith("[") || !value.endsWith("]")) {
                return listOf(value)
            }
            val inner = value.substring(1, value.length - 1)
            val out = ArrayList<String>()
            val current = StringBuilder()
            var quote = '\u0000'
            var escaped = false
            for (ch in inner) {
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
                    val token = current.toString().trim()
                    if (token.isNotEmpty()) {
                        out.add(token)
                    }
                    current.setLength(0)
                    continue
                }
                current.append(ch)
            }
            val token = current.toString().trim()
            if (token.isNotEmpty()) {
                out.add(token)
            }
            return out
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

        private fun isBareAnyToken(raw: String): Boolean {
            return raw.trim().equals("any", ignoreCase = true)
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
}
