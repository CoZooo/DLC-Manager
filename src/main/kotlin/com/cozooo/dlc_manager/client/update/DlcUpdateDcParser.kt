package com.cozooo.dlc_manager.client.update

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.ArrayDeque

object DlcUpdateDcParser {
    private val gson = GsonBuilder().create()

    @JvmStatic
    fun parse(text: String?): JsonObject? {
        if (text == null) {
            return null
        }
        val lines = text.split(Regex("\\R"), 0).toMutableList()
        val root = JsonObject()
        val stack = ArrayDeque<Frame>()
        stack.push(Frame.root(root))

        var index = 0
        while (index < lines.size) {
            var rawLine = lines[index]
            if (index == 0 && rawLine.startsWith("\uFEFF")) {
                rawLine = rawLine.substring(1)
            }
            val noComment = stripComment(rawLine)
            if (noComment.isBlank()) {
                index++
                continue
            }
            val indent = countLeadingSpaces(noComment)
            val content = noComment.substring(indent).trim()
            if (content.isBlank()) {
                index++
                continue
            }

            val isSectionHeader = content.startsWith("[") && content.endsWith("]") && content.length >= 3
            while (stack.size > 1) {
                val top = stack.peek()
                var shouldPop = if (isSectionHeader) indent <= top.indent else indent < top.indent
                if (!shouldPop && !isSectionHeader && indent == top.indent && top.obj != null && !top.sectionObject) {
                    shouldPop = true
                }
                if (!shouldPop) {
                    break
                }
                stack.pop()
            }
            val current = stack.peek()

            if (isSectionHeader) {
                val header = content.substring(1, content.length - 1).trim()
                if (header.isBlank() || current.obj == null) {
                    index++
                    continue
                }
                if (header.equals("del", ignoreCase = true)) {
                    val arr = getOrCreateArray(current.obj, header)
                    stack.push(Frame.array(indent, arr))
                } else {
                    val child = getOrCreateObject(current.obj, header)
                    stack.push(Frame.sectionObject(indent, child, current.obj, header))
                }
                index++
                continue
            }

            if (current.array != null) {
                var item = content
                if (item.endsWith(",")) {
                    item = item.substring(0, item.length - 1).trim()
                }
                if (item.isNotBlank()) {
                    val value = parseValue(lines, index, item)
                    current.array.add(value.value)
                    index = value.nextIndex + 1
                } else {
                    index++
                }
                continue
            }

            if (content.endsWith(":") && !content.contains("=") && current.obj != null) {
                val key = unquoteKey(content.substring(0, content.length - 1).trim())
                if (key.isNotBlank()) {
                    val child = getOrCreateObject(current.obj, key)
                    stack.push(Frame.obj(indent, child, current.obj, key))
                }
                index++
                continue
            }

            val eq = content.indexOf('=')
            if (eq >= 0 && current.obj != null) {
                val key = unquoteKey(content.substring(0, eq).trim())
                if (key.isNotBlank()) {
                    val valueText = content.substring(eq + 1).trim()
                    val value = parseValue(lines, index, valueText)
                    current.obj.add(key, value.value)
                    index = value.nextIndex + 1
                    continue
                }
            }

            index++
        }

        return root
    }

    private fun getOrCreateObject(parent: JsonObject, key: String): JsonObject {
        val existing = parent.get(key)
        if (existing != null && existing.isJsonObject) {
            return existing.asJsonObject
        }
        val child = JsonObject()
        parent.add(key, child)
        return child
    }

    private fun getOrCreateArray(parent: JsonObject, key: String): JsonArray {
        val existing = parent.get(key)
        if (existing != null && existing.isJsonArray) {
            return existing.asJsonArray
        }
        val arr = JsonArray()
        parent.add(key, arr)
        return arr
    }

    private fun unquoteKey(key: String?): String {
        if (key == null) {
            return ""
        }
        val trimmed = key.trim()
        if (trimmed.length >= 2 &&
            ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'")))
        ) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        return trimmed
    }

    private fun parseValue(lines: List<String>, startIndex: Int, first: String?): ParseValue {
        val value = first?.trim() ?: ""
        var index = startIndex
        if (value.startsWith("\"\"\"") || value.startsWith("'''")) {
            val delimiter = if (value.startsWith("\"\"\"")) "\"\"\"" else "'''"
            val text = StringBuilder()
            var remaining = value.substring(3)
            while (true) {
                val end = remaining.indexOf(delimiter)
                if (end >= 0) {
                    text.append(remaining, 0, end)
                    return ParseValue(JsonPrimitive(text.toString()), index)
                }
                text.append(remaining)
                if (index + 1 >= lines.size) {
                    return ParseValue(JsonPrimitive(text.toString()), index)
                }
                text.append('\n')
                index++
                remaining = lines[index]
            }
        }

        if (value.startsWith("[") || value.startsWith("{")) {
            val raw = StringBuilder(value)
            var delta = containerDelta(value)
            while (delta > 0 && index + 1 < lines.size) {
                index++
                val next = stripComment(lines[index]).trim()
                if (next.isEmpty()) {
                    continue
                }
                raw.append('\n').append(next)
                delta += containerDelta(next)
            }
            return ParseValue(parseScalar(raw.toString().trim()), index)
        }

        return ParseValue(parseScalar(value), index)
    }

    private fun containerDelta(text: String): Int {
        var delta = 0
        var inString = false
        var quote = '\u0000'
        var escaping = false
        for (c in text) {
            if (inString) {
                if (escaping) {
                    escaping = false
                    continue
                }
                if (c == '\\') {
                    escaping = true
                    continue
                }
                if (c == quote) {
                    inString = false
                }
                continue
            }
            if (c == '"' || c == '\'') {
                inString = true
                quote = c
                continue
            }
            if (c == '[' || c == '{') {
                delta++
            } else if (c == ']' || c == '}') {
                delta--
            }
        }
        return delta
    }

    private fun parseScalar(raw: String?): JsonElement {
        val value = raw?.trim() ?: ""
        if (value.isEmpty()) {
            return JsonPrimitive("")
        }
        if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
            return JsonPrimitive(unescapeQuoted(value.substring(1, value.length - 1)))
        }
        if (value.startsWith("'") && value.endsWith("'") && value.length >= 2) {
            return JsonPrimitive(value.substring(1, value.length - 1))
        }
        if (value.equals("true", ignoreCase = true)) {
            return JsonPrimitive(true)
        }
        if (value.equals("false", ignoreCase = true)) {
            return JsonPrimitive(false)
        }
        if (value.equals("null", ignoreCase = true)) {
            return JsonNull.INSTANCE
        }
        if (value == "{}") {
            return JsonObject()
        }
        if (value == "[]") {
            return JsonArray()
        }
        if ((value.startsWith("[") && value.endsWith("]")) || (value.startsWith("{") && value.endsWith("}"))) {
            try {
                val parsed = gson.fromJson(value, JsonElement::class.java)
                if (parsed != null) {
                    return parsed
                }
            } catch (_: Exception) {
            }
        }
        try {
            return if (value.contains(".")) {
                JsonPrimitive(value.toDouble())
            } else {
                JsonPrimitive(value.toLong())
            }
        } catch (_: NumberFormatException) {
        }
        return JsonPrimitive(value)
    }

    private fun unescapeQuoted(text: String): String {
        return text
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun stripComment(line: String?): String {
        if (line.isNullOrEmpty()) {
            return ""
        }
        val out = StringBuilder(line.length)
        var inString = false
        var quote = '\u0000'
        var escaping = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inString) {
                out.append(c)
                if (escaping) {
                    escaping = false
                    i++
                    continue
                }
                if (c == '\\') {
                    escaping = true
                    i++
                    continue
                }
                if (c == quote) {
                    inString = false
                }
                i++
                continue
            }
            if (c == '"' || c == '\'') {
                inString = true
                quote = c
                out.append(c)
                i++
                continue
            }
            if (c == '#') {
                break
            }
            if (c == '/' && i + 1 < line.length && line[i + 1] == '/') {
                break
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun countLeadingSpaces(text: String): Int {
        var count = 0
        while (count < text.length && text[count] == ' ') {
            count++
        }
        return count
    }

    private data class ParseValue(val value: JsonElement, val nextIndex: Int)

    private data class Frame(
        val indent: Int,
        val obj: JsonObject?,
        val array: JsonArray?,
        val parentObject: JsonObject?,
        val keyInParent: String?,
        val sectionObject: Boolean,
    ) {
        companion object {
            fun root(root: JsonObject): Frame = Frame(-1, root, null, null, null, true)

            fun obj(indent: Int, obj: JsonObject, parent: JsonObject, keyInParent: String): Frame =
                Frame(indent, obj, null, parent, keyInParent, false)

            fun sectionObject(indent: Int, obj: JsonObject, parent: JsonObject, keyInParent: String): Frame =
                Frame(indent, obj, null, parent, keyInParent, true)

            fun array(indent: Int, array: JsonArray): Frame = Frame(indent, null, array, null, null, false)
        }
    }
}
