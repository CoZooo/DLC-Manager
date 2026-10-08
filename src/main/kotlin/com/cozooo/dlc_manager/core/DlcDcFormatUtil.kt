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
import java.util.ArrayDeque
import java.util.Locale

object DlcDcFormatUtil {
    const val DC_EXTENSION: String = ".dc"
    private val compactGson: Gson = GsonBuilder().disableHtmlEscaping().create()

    @JvmStatic
    fun isDcName(name: String?): Boolean {
        return name != null && name.lowercase(Locale.ROOT).endsWith(DC_EXTENSION)
    }

    @JvmStatic
    fun toDcPath(path: Path?): Path? {
        if (path == null) {
            return null
        }
        val fileName = path.fileName?.toString() ?: path.toString()
        if (isDcName(fileName)) {
            return path
        }
        val base = stripConfigExtension(fileName)
        val parent = path.parent
        return if (parent == null) Path.of(base + DC_EXTENSION) else parent.resolve(base + DC_EXTENSION)
    }

    @JvmStatic
    fun resolveConfigPath(dir: Path?, baseName: String?): Path? {
        if (dir == null || baseName.isNullOrBlank()) {
            return null
        }
        return dir.resolve(baseName + DC_EXTENSION)
    }

    @JvmStatic
    fun stripConfigExtension(name: String?): String {
        if (name == null) {
            return ""
        }
        return if (name.lowercase(Locale.ROOT).endsWith(DC_EXTENSION)) {
            name.substring(0, name.length - DC_EXTENSION.length)
        } else {
            name
        }
    }

    @JvmStatic
    fun read(path: Path?): JsonObject? {
        if (path == null || !Files.exists(path)) {
            return null
        }
        val lines = try {
            Files.readAllLines(path)
        } catch (_: IOException) {
            return null
        }
        val root = DlcNode()
        val stack = ArrayDeque<Frame>()
        stack.push(Frame.root(root))
        var index = 0
        while (index < lines.size) {
            var raw = lines[index]
            if (index == 0 && raw.startsWith("\uFEFF")) {
                raw = raw.substring(1)
            }
            val noComment = stripComment(raw)
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
            if (content.startsWith("[") && content.endsWith("]") && content.length >= 3) {
                var header = content.substring(1, content.length - 1).trim()
                while (stack.size > 1 && stack.peek().indent >= indent) {
                    stack.pop()
                }
                if (indent == 0 && (header == "dependencies" || header == "conflicts" || (!isQuotedKey(header) && header.startsWith("dependencies.")))) {
                    while (stack.size > 1) {
                        stack.pop()
                    }
                }
                if (!isQuotedKey(header) && header.startsWith("dependencies.")) {
                    val node = findNearestNode(stack)
                    val depKey = unquoteKey(header.substringAfter("dependencies."))
                    if (node != null && depKey.isNotBlank()) {
                        val child = node.dependencies.computeIfAbsent(depKey) { DlcNode() }
                        stack.push(Frame.node(indent, child))
                    }
                } else if (header == "dependencies") {
                    val node = findNearestNode(stack)
                    if (node != null) {
                        stack.push(Frame.relationContainer(indent, node, "dependencies"))
                    }
                } else if (header == "conflicts") {
                    val node = findNearestNode(stack)
                    if (node != null) {
                        stack.push(Frame.relationContainer(indent, node, "conflicts"))
                    }
                } else if (isSectionHeader(header)) {
                    val node = findNearestNode(stack)
                    if (node != null) {
                        stack.push(Frame.section(indent, node, header))
                    }
                } else if (stack.peek().type == FrameType.RELATION_CONTAINER) {
                    val relation = stack.peek().section
                    header = unquoteKey(header)
                    val parent = stack.peek().node
                    if (relation == "dependencies") {
                        val child = parent.dependencies.computeIfAbsent(header) { DlcNode() }
                        stack.push(Frame.node(indent, child))
                    } else if (relation == "conflicts") {
                        parent.conflictEntries.add(header)
                    }
                }
                index++
                continue
            }
            if (content.endsWith(":") && content.length >= 2 && !content.contains("=")) {
                while (stack.size > 1 && stack.peek().indent >= indent) {
                    stack.pop()
                }
                if (stack.peek().type == FrameType.RELATION_CONTAINER) {
                    val depKey = unquoteKey(content.substring(0, content.length - 1).trim())
                    if (depKey.isNotBlank()) {
                        val parent = stack.peek().node
                        if (stack.peek().section == "dependencies") {
                            val child = parent.dependencies.computeIfAbsent(depKey) { DlcNode() }
                            stack.push(Frame.node(indent, child))
                        } else if (stack.peek().section == "conflicts") {
                            parent.conflictEntries.add(depKey)
                        }
                    }
                }
                index++
                continue
            }
            if (stack.peek().type == FrameType.RELATION_CONTAINER && !content.contains("=")) {
                val relation = stack.peek().section
                val node = stack.peek().node
                val token = unquoteKey(content)
                if (token.isNotBlank()) {
                    if (relation == "dependencies") {
                        node.dependencyEntries.add(token)
                    } else if (relation == "conflicts") {
                        node.conflictEntries.add(token)
                    }
                }
                index++
                continue
            }
            val eq = content.indexOf('=')
            if (eq < 0) {
                index++
                continue
            }
            val key = content.substring(0, eq).trim()
            val valueText = content.substring(eq + 1).trim()
            val parsed = parseValue(lines, index, valueText)
            index = parsed.nextIndex + 1
            assignValue(stack, key, parsed.value)
        }
        return root.toJsonObject()
    }

    @JvmStatic
    fun write(ordered: JsonObject?): String {
        if (ordered == null) {
            return ""
        }
        val out = StringBuilder(512)
        writeNode(out, ordered, 0, true)
        return out.toString()
    }

    @JvmStatic
    fun write(path: Path?, ordered: JsonObject?): Boolean {
        if (path == null || ordered == null) {
            return false
        }
        return try {
            if (path.parent != null) {
                Files.createDirectories(path.parent)
            }
            Files.writeString(path, write(ordered))
            true
        } catch (_: IOException) {
            false
        }
    }

    @JvmStatic
    fun readGeneric(path: Path?): JsonObject? {
        if (path == null || !Files.exists(path)) {
            return null
        }
        val lines = try {
            Files.readAllLines(path)
        } catch (_: IOException) {
            return null
        }
        return readGenericLines(lines)
    }

    @JvmStatic
    fun readElement(path: Path?): JsonElement? {
        if (path == null || !Files.exists(path) || !isDcName(path.fileName?.toString())) {
            return null
        }
        return readGeneric(path)
    }

    @JvmStatic
    fun readObject(path: Path?): JsonObject? {
        val element = readElement(path)
        return if (element != null && element.isJsonObject) element.asJsonObject else null
    }

    @JvmStatic
    fun readGenericText(text: String?): JsonObject? {
        if (text == null) {
            return null
        }
        return readGenericLines(text.split(Regex("\\R"), 0))
    }

    @JvmStatic
    fun writeGeneric(obj: JsonObject?): String {
        if (obj == null) {
            return ""
        }
        val out = StringBuilder(512)
        writeGenericRoot(out, obj)
        return out.toString()
    }

    @JvmStatic
    fun writeGeneric(path: Path?, obj: JsonObject?): Boolean {
        if (path == null || obj == null) {
            return false
        }
        return try {
            if (path.parent != null) {
                Files.createDirectories(path.parent)
            }
            Files.writeString(path, writeGeneric(obj))
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun readGenericLines(lines: List<String>?): JsonObject? {
        if (lines == null) {
            return null
        }
        val root = JsonObject()
        val stack = ArrayDeque<GenericFrame>()
        stack.push(GenericFrame(-1, root, null))
        var index = 0
        while (index < lines.size) {
            var raw = lines[index]
            if (index == 0 && raw.startsWith("\uFEFF")) {
                raw = raw.substring(1)
            }
            val noComment = stripComment(raw)
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
            if (content.startsWith("[") && content.endsWith("]") && content.length >= 3) {
                val rawHeader = content.substring(1, content.length - 1).trim()
                val header = unquoteKey(rawHeader)
                if (!isQuotedKey(rawHeader) && header.contains(".")) {
                    val child = ensureGenericPath(root, header)
                    stack.clear()
                    stack.push(GenericFrame(-1, root, null))
                    stack.push(GenericFrame(indent, child, header.substringAfterLast('.')))
                    index++
                    continue
                }
                while (stack.size > 1 && stack.peek().indent >= indent) {
                    stack.pop()
                }
                val parent = stack.peek().obj
                val existing = parent.get(header)
                val child = if (existing != null && existing.isJsonObject) {
                    existing.asJsonObject
                } else {
                    JsonObject().also { parent.add(header, it) }
                }
                stack.push(GenericFrame(indent, child, header))
                index++
                continue
            }
            if (isGenericRelationSection(stack.peek().name) && !content.contains("=")) {
                val token = unquoteKey(content)
                if (token.isNotBlank()) {
                    stack.peek().obj.add(token, JsonObject())
                }
                index++
                continue
            }
            val eq = content.indexOf('=')
            if (eq < 0) {
                index++
                continue
            }
            val key = unquoteKey(content.substring(0, eq).trim())
            val valueText = content.substring(eq + 1).trim()
            val parsed = parseValue(lines, index, valueText)
            index = parsed.nextIndex + 1
            if (key.isNotBlank()) {
                stack.peek().obj.add(key, parsed.value)
            }
        }
        return root
    }

    private fun writeGenericRoot(out: StringBuilder, obj: JsonObject) {
        var wroteAny = false
        for ((key, value) in obj.entrySet()) {
            if (value != null && value.isJsonObject) {
                continue
            }
            out.append(formatGenericKey(key)).append(" = ").append(formatGenericValue(key, value)).append('\n')
            wroteAny = true
        }
        for ((key, value) in obj.entrySet()) {
            if (value == null || !value.isJsonObject) {
                continue
            }
            if (wroteAny) {
                out.append('\n')
            }
            writeGenericSection(out, key, value.asJsonObject, 0)
            wroteAny = true
        }
    }

    private fun writeGenericSection(out: StringBuilder, name: String, obj: JsonObject, sectionIndent: Int) {
        indentLine(out, sectionIndent)
        out.append('[').append(formatGenericKey(name)).append(']').append('\n')
        val relationSection = isGenericRelationSection(name)
        val hasNestedSections = hasNestedGenericSections(obj)
        val bodyIndent = if (sectionIndent > 0 || hasNestedSections) sectionIndent + 2 else 0
        var wroteAny = false
        for ((key, value) in obj.entrySet()) {
            if (value != null && value.isJsonObject) {
                continue
            }
            indentLine(out, bodyIndent)
            if (relationSection && isGenericRelationEntry(key, value)) {
                out.append(formatStringValue(key)).append('\n')
            } else {
                out.append(formatGenericKey(key)).append(" = ").append(formatGenericValue(key, value)).append('\n')
            }
            wroteAny = true
        }
        for ((key, value) in obj.entrySet()) {
            if (value == null || !value.isJsonObject) {
                continue
            }
            if (relationSection && value.asJsonObject.entrySet().isEmpty()) {
                indentLine(out, bodyIndent)
                out.append(formatStringValue(key)).append('\n')
                wroteAny = true
                continue
            }
            if (wroteAny) {
                out.append('\n')
            }
            writeGenericSection(out, key, value.asJsonObject, sectionIndent + 2)
            wroteAny = true
        }
    }

    private fun hasNestedGenericSections(obj: JsonObject?): Boolean {
        if (obj == null || obj.entrySet().isEmpty()) {
            return false
        }
        for ((_, value) in obj.entrySet()) {
            if (value != null && value.isJsonObject) {
                return true
            }
        }
        return false
    }

    private fun isGenericRelationSection(name: String?): Boolean {
        return name.equals("dependencies", ignoreCase = true) || name.equals("conflicts", ignoreCase = true)
    }

    private fun isGenericRelationEntry(key: String?, value: JsonElement?): Boolean {
        if (key.isNullOrBlank() || value == null || value.isJsonNull || !value.isJsonPrimitive) {
            return false
        }
        val primitive = value.asJsonPrimitive
        return primitive.isString && primitive.asString.trim() == key.trim()
    }

    private fun ensureGenericPath(root: JsonObject, header: String): JsonObject {
        var current = root
        for (rawPart in header.split(".")) {
            val part = rawPart.trim()
            if (part.isBlank()) {
                continue
            }
            val existing = current.get(part)
            current = if (existing != null && existing.isJsonObject) {
                existing.asJsonObject
            } else {
                JsonObject().also {
                    current.add(part, it)
                }
            }
        }
        return current
    }

    private fun isQuotedKey(text: String?): Boolean {
        if (text == null) {
            return false
        }
        val key = text.trim()
        return key.length >= 2 &&
            ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'")))
    }

    private fun formatGenericKey(key: String?): String {
        if (key == null) {
            return "\"\""
        }
        if (key.matches(Regex("[A-Za-z_][A-Za-z0-9_-]*"))) {
            return key
        }
        return "\"" + escapeString(key) + "\""
    }

    private fun formatGenericValue(key: String?, value: JsonElement?): String {
        if (value == null || value.isJsonNull) {
            return "null"
        }
        if (value.isJsonPrimitive) {
            val primitive = value.asJsonPrimitive
            if (primitive.isBoolean) {
                return if (primitive.asBoolean) "true" else "false"
            }
            if (primitive.isNumber) {
                return primitive.asNumber.toString()
            }
            return formatStringValue(primitive.asString)
        }
        if (value.isJsonArray && isVerticalArrayKey(key)) {
            return formatValue(value)
        }
        return compactGson.toJson(value)
    }

    private fun isVerticalArrayKey(key: String?): Boolean {
        return key != null && (key.equals("enable", ignoreCase = true) || key.equals("conflicts", ignoreCase = true))
    }

    private fun writeNode(out: StringBuilder, node: JsonObject, indent: Int, root: Boolean) {
        val depsElement = node.get("dependencies")
        val dependencyEntries = collectRelationEntries(depsElement)
        val hasNestedDependencies = depsElement != null && depsElement.isJsonObject && depsElement.asJsonObject.entrySet().isNotEmpty()
        val conflictEntries = collectRelationEntries(node.get("conflicts"))
        val hasConflicts = conflictEntries.isNotEmpty()
        val hasDownloadSection = hasDownloadSection(node)
        if (hasDownloadSection) {
            if (!root) {
                indentLine(out, indent)
            }
            out.append("[download]\n")
            writeDownload(out, node, indent)
        }
        if (hasDownloadSection) {
            indentLine(out, indent)
            out.append('\n')
        }
        indentLine(out, indent)
        out.append("[basic]\n")
        writeBasic(out, node, indent)

        if (dependencyEntries.isNotEmpty() || hasNestedDependencies) {
            indentLine(out, indent)
            out.append('\n')
            indentLine(out, indent)
            out.append("[dependencies]\n")
            for (entry in dependencyEntries) {
                indentLine(out, indent)
                out.append(formatStringValue(entry)).append('\n')
            }
            if (depsElement != null && depsElement.isJsonObject) {
                for ((key, value) in depsElement.asJsonObject.entrySet()) {
                    if (!value.isJsonObject) {
                        continue
                    }
                    indentLine(out, indent)
                    out.append("[dependencies.").append(key).append("]\n")
                    writeNode(out, value.asJsonObject, indent + 2, false)
                }
            }
        }

        if (hasConflicts) {
            indentLine(out, indent)
            out.append('\n')
            indentLine(out, indent)
            out.append("[conflicts]\n")
            for (entry in conflictEntries) {
                indentLine(out, indent)
                out.append(formatStringValue(entry)).append('\n')
            }
        }

        val display = asObject(node.get("DisplayName"))
        if (display.entrySet().isNotEmpty()) {
            indentLine(out, indent)
            out.append('\n')
            indentLine(out, indent)
            out.append("[DisplayName]\n")
            for ((key, value) in display.entrySet()) {
                out.append(formatKeyValue(key, value)).append('\n')
            }
        }

        val tip = asObject(node.get("tip"))
        if (tip.entrySet().isNotEmpty()) {
            indentLine(out, indent)
            out.append('\n')
            indentLine(out, indent)
            out.append("[tip]\n")
            for ((key, value) in tip.entrySet()) {
                out.append(formatKeyValue(key, value)).append('\n')
            }
        }
    }

    private fun writeDownload(out: StringBuilder, node: JsonObject, indent: Int) {
        val keys = arrayOf("modrinth_projectid", "modrinth_versionid", "curseforge_projectid", "curseforge_fileid", "directUrl", "priority")
        for (key in keys) {
            val value = node.get(key) ?: continue
            indentLine(out, indent)
            out.append(formatKeyValue(key, value)).append('\n')
        }
    }

    private fun hasDownloadSection(node: JsonObject?): Boolean {
        if (node == null) {
            return false
        }
        return hasMeaningfulDownloadValue(node.get("modrinth_projectid")) ||
            hasMeaningfulDownloadValue(node.get("modrinth_versionid")) ||
            hasMeaningfulDownloadValue(node.get("curseforge_projectid")) ||
            hasMeaningfulDownloadValue(node.get("curseforge_fileid")) ||
            hasMeaningfulDownloadValue(node.get("directUrl")) ||
            hasMeaningfulDownloadValue(node.get("priority"))
    }

    private fun hasMeaningfulDownloadValue(value: JsonElement?): Boolean {
        if (value == null || value.isJsonNull) {
            return false
        }
        if (value.isJsonPrimitive) {
            val primitive = value.asJsonPrimitive
            if (primitive.isString) {
                val text = primitive.asString
                return !text.isNullOrBlank()
            }
            return true
        }
        if (value.isJsonArray) {
            return !value.asJsonArray.isEmpty
        }
        if (value.isJsonObject) {
            return value.asJsonObject.entrySet().isNotEmpty()
        }
        return false
    }

    private fun writeBasic(out: StringBuilder, node: JsonObject, indent: Int) {
        val keys = arrayOf("identifier", "type", "file_name", "appliedTarget", "isHidden", "AllowEnableByAll", "alarm")
        for (key in keys) {
            if (!node.has(key)) {
                continue
            }
            indentLine(out, indent)
            out.append(formatKeyValue(key, node.get(key))).append('\n')
        }
        for ((key, value) in node.entrySet()) {
            if (key.isNullOrBlank()) {
                continue
            }
            if (key == "dependencies" || key == "conflicts" || key == "DisplayName" || key == "tip" || isKnownBasicKey(key) || isDownloadKey(key)) {
                continue
            }
            indentLine(out, indent)
            out.append(formatKeyValue(key, value)).append('\n')
        }
    }

    private fun isKnownBasicKey(key: String): Boolean {
        return key == "identifier" || key == "type" || key == "file_name" || key == "appliedTarget" ||
            key == "isHidden" || key == "AllowEnableByAll" || key == "alarm"
    }

    private fun isDownloadKey(key: String): Boolean {
        return key == "modrinth_projectid" || key == "modrinth_versionid" || key == "curseforge_projectid" ||
            key == "curseforge_fileid" || key == "directUrl" || key == "priority"
    }

    private fun collectRelationEntries(element: JsonElement?): List<String> {
        val names = LinkedHashSet<String>()
        collectRelationEntries(element, names)
        return ArrayList(names)
    }

    private fun collectRelationEntries(element: JsonElement?, names: MutableSet<String>) {
        if (element == null || element.isJsonNull) {
            return
        }
        if (element.isJsonPrimitive || element.isJsonArray) {
            collectPrimitiveNames(element, names)
            return
        }
        if (!element.isJsonObject) {
            return
        }
        for ((key, value) in element.asJsonObject.entrySet()) {
            if (value == null || value.isJsonNull) {
                if (!key.isNullOrBlank()) {
                    names.add(key.trim())
                }
                continue
            }
            if (value.isJsonPrimitive || value.isJsonArray) {
                collectPrimitiveNames(value, names)
                continue
            }
        }
    }

    private fun collectPrimitiveNames(element: JsonElement?, names: MutableSet<String>?) {
        if (element == null || names == null) {
            return
        }
        if (element.isJsonPrimitive) {
            val raw = element.asString ?: return
            for (part in raw.split(Regex("[,\\n\\r]+"))) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty()) {
                    names.add(trimmed)
                }
            }
            return
        }
        if (element.isJsonArray) {
            for (child in element.asJsonArray) {
                collectPrimitiveNames(child, names)
            }
        }
    }

    private fun formatKeyValue(key: String, value: JsonElement?): String {
        return "$key = ${formatValue(value)}"
    }

    private fun formatValue(value: JsonElement?): String {
        if (value == null || value.isJsonNull) {
            return "\"\""
        }
        if (value.isJsonPrimitive) {
            val primitive = value.asJsonPrimitive
            if (primitive.isBoolean) {
                return if (primitive.asBoolean) "true" else "false"
            }
            if (primitive.isNumber) {
                return primitive.asNumber.toString()
            }
            return formatStringValue(primitive.asString)
        }
        if (value.isJsonArray) {
            val array = value.asJsonArray
            if (array.isEmpty) {
                return "[]"
            }
            val out = StringBuilder()
            out.append("[\n")
            for (i in 0 until array.size()) {
                out.append("  ").append(formatValue(array[i]))
                if (i + 1 < array.size()) {
                    out.append(',')
                }
                out.append('\n')
            }
            out.append("]")
            return out.toString()
        }
        return "{}"
    }

    private fun formatStringValue(value: String?): String {
        if (value == null) {
            return "\"\""
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
            var delimiter = "\"\"\""
            if (normalized.contains(delimiter)) {
                if (!normalized.contains("'''")) {
                    delimiter = "'''"
                } else {
                    return "\"" + escapeString(value) + "\""
                }
            }
            return delimiter + normalized + delimiter
        }
        return "\"" + escapeString(value) + "\""
    }

    private fun escapeString(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
    }

    private fun assignValue(stack: ArrayDeque<Frame>, key: String?, value: JsonElement) {
        val node = findNearestNode(stack)
        if (node == null || key.isNullOrBlank()) {
            return
        }
        val section = findNearestSection(stack)
        when (section?.section ?: "basic") {
            "download" -> node.download.add(key, value)
            "DisplayName" -> node.displayName.add(key, value)
            "tip" -> node.tip.add(key, value)
            "dependencies" -> collectPrimitiveNames(value, node.dependencyEntries)
            "conflicts" -> collectPrimitiveNames(value, node.conflictEntries)
            "basic" -> node.basic.add(key, value)
            else -> node.basic.add(key, value)
        }
    }

    private fun isSectionHeader(header: String): Boolean {
        return header == "download" || header == "basic" || header == "DisplayName" || header == "tip"
    }

    private fun findNearestNode(stack: ArrayDeque<Frame>): DlcNode? {
        for (frame in stack) {
            if (frame.type == FrameType.NODE || frame.type == FrameType.ROOT) {
                return frame.node
            }
        }
        return null
    }

    private fun findNearestSection(stack: ArrayDeque<Frame>): Frame? {
        for (frame in stack) {
            if (frame.type == FrameType.SECTION) {
                return frame
            }
        }
        return null
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
        if (value == "{}") {
            return JsonObject()
        }
        if (value == "[]") {
            return JsonArray()
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            return parseArray(value.substring(1, value.length - 1))
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            try {
                return compactGson.fromJson(value, JsonObject::class.java)
            } catch (_: Exception) {
            }
        }
        try {
            if (value.contains(".")) {
                return JsonPrimitive(value.toDouble())
            }
            return JsonPrimitive(value.toLong())
        } catch (_: NumberFormatException) {
        }
        return JsonPrimitive(value)
    }

    private fun parseArray(inner: String): JsonArray {
        val result = JsonArray()
        for (token in splitArrayTokens(inner)) {
            val trimmed = token.trim()
            if (trimmed.isNotEmpty()) {
                result.add(parseScalar(trimmed))
            }
        }
        return result
    }

    private fun splitArrayTokens(text: String): List<String> {
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        var inString = false
        var quote = '\u0000'
        var escaping = false
        var bracketDepth = 0
        var braceDepth = 0
        for (c in text) {
            if (inString) {
                current.append(c)
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
                current.append(c)
                continue
            }
            if (c == '[') {
                bracketDepth++
                current.append(c)
                continue
            }
            if (c == ']') {
                bracketDepth = maxOf(0, bracketDepth - 1)
                current.append(c)
                continue
            }
            if (c == '{') {
                braceDepth++
                current.append(c)
                continue
            }
            if (c == '}') {
                braceDepth = maxOf(0, braceDepth - 1)
                current.append(c)
                continue
            }
            if (bracketDepth == 0 && braceDepth == 0 && (c == ',' || c == '\n' || c == '\r')) {
                val token = current.toString().trim()
                if (token.isNotEmpty()) {
                    tokens.add(token)
                }
                current.setLength(0)
                continue
            }
            current.append(c)
        }
        val token = current.toString().trim()
        if (token.isNotEmpty()) {
            tokens.add(token)
        }
        return tokens
    }

    private fun unescapeQuoted(text: String): String {
        return text.replace("\\n", "\n")
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
        for (i in line.indices) {
            val c = line[i]
            if (inString) {
                out.append(c)
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
                out.append(c)
                continue
            }
            if (c == '#') {
                break
            }
            if (c == '/' && i + 1 < line.length && line[i + 1] == '/') {
                break
            }
            out.append(c)
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

    private fun indentLine(out: StringBuilder, indent: Int) {
        repeat(indent) {
            out.append(' ')
        }
    }

    private fun asObject(element: JsonElement?): JsonObject {
        return if (element != null && element.isJsonObject) element.asJsonObject else JsonObject()
    }

    private fun unquoteKey(text: String?): String {
        if (text == null) {
            return ""
        }
        val key = text.trim()
        return if (isQuotedKey(key)) unescapeQuoted(key.substring(1, key.length - 1)) else key
    }

    private data class ParseValue(val value: JsonElement, val nextIndex: Int)

    private data class GenericFrame(val indent: Int, val obj: JsonObject, val name: String?)

    private enum class FrameType {
        ROOT,
        NODE,
        RELATION_CONTAINER,
        SECTION,
    }

    private data class Frame(
        val indent: Int,
        val type: FrameType,
        val node: DlcNode,
        val section: String?,
    ) {
        companion object {
            fun root(node: DlcNode): Frame = Frame(-1, FrameType.ROOT, node, null)
            fun node(indent: Int, node: DlcNode): Frame = Frame(indent, FrameType.NODE, node, null)
            fun relationContainer(indent: Int, node: DlcNode, section: String): Frame = Frame(indent, FrameType.RELATION_CONTAINER, node, section)
            fun section(indent: Int, node: DlcNode, section: String): Frame = Frame(indent, FrameType.SECTION, node, section)
        }
    }

    private class DlcNode {
        val download = JsonObject()
        val basic = JsonObject()
        val displayName = JsonObject()
        val tip = JsonObject()
        val dependencyEntries: MutableSet<String> = LinkedHashSet()
        val conflictEntries: MutableSet<String> = LinkedHashSet()
        val dependencies: MutableMap<String, DlcNode> = LinkedHashMap()

        fun toJsonObject(): JsonObject {
            val out = JsonObject()
            for ((key, value) in download.entrySet()) {
                out.add(key, value)
            }
            for ((key, value) in basic.entrySet()) {
                out.add(key, value)
            }
            val basicDeps = out.get("dependencies")
            if (basicDeps != null && !basicDeps.isJsonNull) {
                collectPrimitiveNames(basicDeps, dependencyEntries)
                out.remove("dependencies")
            }
            val basicConflicts = out.get("conflicts")
            if (basicConflicts != null && !basicConflicts.isJsonNull) {
                collectPrimitiveNames(basicConflicts, conflictEntries)
                out.remove("conflicts")
            }
            if (dependencyEntries.isNotEmpty() || dependencies.isNotEmpty()) {
                val deps = JsonObject()
                for (name in dependencyEntries) {
                    if (name.isNotBlank()) {
                        deps.add(name, JsonPrimitive(name))
                    }
                }
                for ((key, value) in dependencies) {
                    deps.add(key, value.toJsonObject())
                }
                out.add("dependencies", deps)
            } else if (!out.has("dependencies")) {
                out.addProperty("dependencies", "")
            }
            if (conflictEntries.isNotEmpty()) {
                val conflicts = JsonObject()
                for (name in conflictEntries) {
                    if (name.isNotBlank()) {
                        conflicts.add(name, JsonPrimitive(name))
                    }
                }
                out.add("conflicts", conflicts)
            } else if (!out.has("conflicts")) {
                out.addProperty("conflicts", "")
            }
            if (displayName.entrySet().isNotEmpty()) {
                out.add("DisplayName", displayName)
            } else if (!out.has("DisplayName")) {
                out.add("DisplayName", JsonObject())
            }
            if (tip.entrySet().isNotEmpty()) {
                out.add("tip", tip)
            } else if (!out.has("tip")) {
                out.add("tip", JsonObject())
            }
            return out
        }
    }
}
