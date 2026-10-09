package com.cozooo.dlc_manager.core

import com.cozooo.dclanguage.DcDocument
import com.cozooo.dclanguage.DcDocuments
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
    private val downloadKeys = setOf(
        "modrinth_projectid", "modrinth_versionid", "curseforge_projectid", "curseforge_fileid", "directurl", "priority",
    )

    @JvmStatic
    fun isDcName(name: String?): Boolean = name != null && name.lowercase(Locale.ROOT).endsWith(DC_EXTENSION)

    @JvmStatic
    fun toDcPath(path: Path?): Path? {
        if (path == null) return null
        val fileName = path.fileName?.toString() ?: path.toString()
        if (isDcName(fileName)) return path
        val base = stripConfigExtension(fileName)
        return path.parent?.resolve(base + DC_EXTENSION) ?: Path.of(base + DC_EXTENSION)
    }

    @JvmStatic
    fun resolveConfigPath(dir: Path?, baseName: String?): Path? =
        if (dir == null || baseName.isNullOrBlank()) null else dir.resolve(baseName + DC_EXTENSION)

    @JvmStatic
    fun stripConfigExtension(name: String?): String =
        if (name == null) "" else if (isDcName(name)) name.dropLast(DC_EXTENSION.length) else name

    @JvmStatic
    fun read(path: Path?): JsonObject? {
        if (path == null || !Files.exists(path)) return null
        return try {
            readNode(readStructuredDocument(path).root())
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @JvmStatic
    fun write(ordered: JsonObject?): String {
        if (ordered == null) return ""
        val root = JsonObject()
        updateNode(root, ordered)
        return DcDocuments.writeText(DcDocuments.fromJson(root))
    }

    @JvmStatic
    fun write(path: Path?, ordered: JsonObject?): Boolean = writeDocument(path, ordered, true)

    @JvmStatic
    fun readGeneric(path: Path?): JsonObject? {
        if (path == null || !Files.exists(path)) return null
        return try {
            DcDocuments.read(path).toJson()
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @JvmStatic
    fun readElement(path: Path?): JsonElement? =
        if (path == null || !isDcName(path.fileName?.toString())) null else readGeneric(path)

    @JvmStatic
    fun readObject(path: Path?): JsonObject? = readElement(path)?.takeIf { it.isJsonObject }?.asJsonObject

    @JvmStatic
    fun readGenericText(text: String?): JsonObject? {
        if (text == null) return null
        return try {
            DcDocuments.readText(text).toJson()
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @JvmStatic
    fun writeGeneric(obj: JsonObject?): String =
        if (obj == null) "" else DcDocuments.writeText(DcDocuments.fromJson(obj))

    @JvmStatic
    fun writeGeneric(path: Path?, obj: JsonObject?): Boolean = writeDocument(path, obj, false)

    private fun writeDocument(path: Path?, value: JsonObject?, structured: Boolean): Boolean {
        if (path == null || value == null) return false
        return try {
            val document = if (Files.exists(path)) {
                if (structured) readStructuredDocument(path) else DcDocuments.read(path)
            } else DcDocuments.create()
            if (structured) {
                updateNode(document.root(), value)
            } else {
                document.root().entrySet().clear()
                value.entrySet().forEach { (key, child) -> document.root().add(key, child.deepCopy()) }
            }
            DcDocuments.write(path, document)
            true
        } catch (_: IOException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    private fun readStructuredDocument(path: Path): DcDocument {
        val text = Files.readString(path)
        val headers = DcDocuments.categoryHeaders(text)
        val frames = ArrayDeque<HeaderFrame>()
        val branches = HashSet<List<String>>()
        val rewritten = StringBuilder(text.length)
        var copied = 0
        for ((index, header) in headers.withIndex()) {
            while (!frames.isEmpty() && frames.peek().indentation >= header.indentation()) frames.pop()
            val parent = frames.peek()
            var node = parent?.node ?: emptyList()
            val names = header.names()
            val raw = text.substring(header.start(), header.end())
            val dependency = names.size > 1 && names[0].equals("dependencies", true)
            val next = headers.getOrNull(index + 1)
            val nestedHeader = next != null && next.indentation() > header.indentation() && next.names().size == 1 && isSection(next.names()[0])
            val quotedPath = '"' in raw || '\'' in raw
            val completePath = names.size > 2 && (quotedPath || names.drop(2).any(::isSection) ||
                branches.any { startsWith(names, it) && names.size > it.size })
            val relationChild = parent?.relation != null && !isSection(names[0]) && !dependency
            var replacement: String? = null
            var relation: String? = null
            if (dependency && (names.size == 2 || !completePath || nestedHeader && !quotedPath)) {
                node = node + listOf(names[0], names.drop(1).joinToString("."))
                branches.add(node)
                replacement = formatHeader(node)
            } else if (relationChild) {
                node = node + listOf(parent.relation!!, names.joinToString("."))
                branches.add(node)
                if (header.bracketed()) replacement = formatHeader(node)
            } else if (names.size > 1) {
                val last = names.last()
                if (isSection(last)) {
                    node = names.dropLast(1)
                    if (node.size > 1 && node[0].equals("dependencies", true)) branches.add(node)
                    if (last.equals("dependencies", true) || last.equals("conflicts", true)) relation = last
                } else if (names[names.lastIndex - 1].equals("dependencies", true)) {
                    node = names
                    branches.add(node)
                } else {
                    branches.filter { startsWith(names, it) }.maxByOrNull { it.size }?.let { node = it }
                }
            } else if (names[0].equals("dependencies", true) || names[0].equals("conflicts", true)) {
                relation = names[0]
            }
            frames.push(HeaderFrame(header.indentation(), node, relation))
            if (replacement != null && replacement != raw) {
                rewritten.append(text, copied, header.start()).append(replacement)
                copied = header.end()
            }
        }
        return DcDocuments.readText(if (copied == 0) text else rewritten.append(text, copied, text.length).toString())
    }

    private fun startsWith(path: List<String>, prefix: List<String>): Boolean =
        path.size >= prefix.size && path.subList(0, prefix.size) == prefix

    private fun formatHeader(path: List<String>): String = path.joinToString(".", "[", "]") {
        if (it.matches(Regex("[A-Za-z_][A-Za-z0-9_-]*"))) it else JsonPrimitive(it).toString()
    }

    private data class HeaderFrame(val indentation: Int, val node: List<String>, val relation: String?)

    private fun readNode(document: JsonObject): JsonObject {
        val out = JsonObject()
        document.entrySet().forEach { (key, value) ->
            if (!isSection(key)) out.add(key, value.deepCopy())
        }
        for (section in listOf("download", "basic")) {
            objectValue(document, section)?.entrySet()?.forEach { (key, value) -> put(out, key, value.deepCopy()) }
        }
        for (relation in listOf("dependencies", "conflicts")) {
            val entries = JsonObject()
            collectRelation(get(out, relation), entries)
            collectRelation(get(document, relation), entries)
            put(out, relation, if (entries.entrySet().isEmpty()) JsonPrimitive("") else entries)
        }
        for (section in listOf("DisplayName", "tip")) {
            val value = get(document, section)
            if (value != null) put(out, section, value.deepCopy())
            else if (get(out, section) == null) out.add(section, JsonObject())
        }
        return out
    }

    private fun collectRelation(value: JsonElement?, entries: JsonObject) {
        if (value == null || value.isJsonNull) return
        when {
            value.isJsonPrimitive -> value.asString.split(Regex("[,\\n\\r]+")).forEach {
                val trimmed = it.trim()
                if (trimmed.isNotEmpty()) entries.addProperty(trimmed, trimmed)
            }
            value.isJsonArray -> value.asJsonArray.forEach { collectRelation(it, entries) }
            else -> value.asJsonObject.entrySet().forEach { (key, child) ->
                when {
                    child.isJsonObject -> entries.add(key, if (child.asJsonObject.entrySet().isEmpty()) JsonPrimitive(key) else readNode(child.asJsonObject))
                    child.isJsonNull -> entries.addProperty(key, key)
                    else -> collectRelation(child, entries)
                }
            }
        }
    }

    private fun updateNode(document: JsonObject, value: JsonObject) {
        val previous = readNode(document)
        previous.entrySet().forEach { (key, _) -> if (get(value, key) == null) removeField(document, key) }
        for ((key, current) in value.entrySet()) {
            if (get(previous, key)?.toString() == current.toString()) continue
            if (key.equals("dependencies", true) || key.equals("conflicts", true)) {
                val relation = writeRelation(current, objectValue(document, key))
                val spelling = actualKey(document, key)
                removeField(document, key)
                if (!relation.entrySet().isEmpty()) document.add(spelling, relation)
                continue
            }
            var container = fieldContainer(document, key)
            if (container == null) {
                if (current.isJsonObject && !isSection(key)) {
                    container = document
                } else if (key.equals("DisplayName", true) || key.equals("tip", true)) {
                    if (current.isJsonObject && current.asJsonObject.entrySet().isEmpty()) continue
                    container = document
                } else {
                    val section = if (key.lowercase(Locale.ROOT) in downloadKeys) "download" else "basic"
                    container = objectValue(document, section) ?: JsonObject().also { document.add(actualKey(document, section), it) }
                }
            }
            put(container, key, current.deepCopy())
        }
    }

    private fun writeRelation(value: JsonElement, original: JsonObject?): JsonObject {
        val semantic = JsonObject()
        collectRelation(value, semantic)
        val out = JsonObject()
        for ((key, child) in semantic.entrySet()) {
            val before = original?.get(key)
            if (child.isJsonObject) {
                val branch = if (before?.isJsonObject == true) before.asJsonObject.deepCopy() else JsonObject()
                updateNode(branch, child.asJsonObject)
                out.add(key, branch)
            } else {
                out.add(key, if (before != null && (!before.isJsonObject || before.asJsonObject.entrySet().isEmpty())) before.deepCopy() else JsonObject())
            }
        }
        return out
    }

    private fun fieldContainer(document: JsonObject, key: String): JsonObject? {
        for (section in listOf("basic", "download")) {
            val fields = objectValue(document, section)
            if (fields != null && get(fields, key) != null) return fields
        }
        return document.takeIf { !key.equals("basic", true) && !key.equals("download", true) && get(document, key) != null }
    }

    private fun removeField(document: JsonObject, key: String) {
        for (section in listOf("basic", "download")) {
            objectValue(document, section)?.let { it.remove(actualKey(it, key)) }
        }
        if (!key.equals("basic", true) && !key.equals("download", true)) document.remove(actualKey(document, key))
    }

    private fun isSection(key: String): Boolean =
        listOf("basic", "download", "dependencies", "conflicts", "DisplayName", "tip").any { it.equals(key, true) }

    private fun objectValue(value: JsonObject, key: String): JsonObject? = get(value, key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun get(value: JsonObject, key: String): JsonElement? = value.get(actualKey(value, key))

    private fun put(value: JsonObject, key: String, element: JsonElement) = value.add(actualKey(value, key), element)

    private fun actualKey(value: JsonObject, key: String): String =
        if (value.has(key)) key else value.keySet().firstOrNull { it.equals(key, true) } ?: key
}
