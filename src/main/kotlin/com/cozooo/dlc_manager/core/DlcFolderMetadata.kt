package com.cozooo.dlc_manager.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.file.Path
import java.util.Locale
import java.util.TreeMap

class DlcFolderMetadata(roots: List<Path>, gameDir: Path) {
    data class Entry(val priority: Int, val displayNames: Map<String, String>) {
        fun priority(): Int = priority
        fun displayNames(): Map<String, String> = displayNames
        fun displayName(folder: String, language: String?): String {
            val value = displayNames[language?.lowercase(Locale.ROOT).orEmpty()]?.takeIf { it.isNotBlank() }
                ?: displayNames["en_us"]
            return value?.takeIf { it.isNotBlank() } ?: folder
        }
    }
    private data class Segment(val name: String, val metadata: Entry)
    private data class Key(val path: String, val segments: List<Segment>)
    data class Group(val key: String, val path: String, val label: String, val items: List<DlcItem>) {
        fun isRoot(): Boolean = path.isEmpty()
        fun key(): String = key
        fun path(): String = path
        fun label(): String = label
        fun items(): List<DlcItem> = items
    }
    private val roots = roots.map { it.toAbsolutePath().normalize() }.sortedByDescending { it.nameCount }
    private val gameDir = gameDir.toAbsolutePath().normalize()
    private val cache = HashMap<Path, Map<String, Entry>>()

    fun group(items: List<DlcItem>, language: String?): List<Group> {
        val grouped = linkedMapOf<Key, MutableList<DlcItem>>()
        for (item in items) grouped.getOrPut(key(item)) { ArrayList() }.add(item)
        return grouped.keys.sortedWith { left, right -> compare(left, right) }.map { key ->
            val label = key.segments.joinToString("/") { it.metadata.displayName(it.name, language) }
            Group(identity(key), key.path, label, grouped.getValue(key).toList())
        }
    }

    fun load(directory: Path): Map<String, Entry> = cache.getOrPut(directory.toAbsolutePath().normalize()) {
        val obj = DlcDcConfigIO.read(directory.resolve(FILE_NAME), DlcDcConfigIO.Layout.GENERIC)
        if (obj == null) emptyMap() else {
            val entries = linkedMapOf<String, Entry>()
            for ((folder, value) in obj.entrySet()) {
                if (!value.isJsonObject) continue
                val values = value.asJsonObject
                var priority = 0
                val raw = DlcDcConfigIO.getIgnoreCase(values, "priority")
                if (raw != null && raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber) {
                    try { priority = raw.asBigDecimal.intValueExact() }
                    catch (_: ArithmeticException) { }
                    catch (_: NumberFormatException) { }
                }
                val names = TreeMap<String, String>()
                for ((field, name) in values.entrySet()) {
                    if (field.startsWith("DisplayName.", true) && name.isJsonPrimitive && name.asJsonPrimitive.isString)
                        names[field.substring(12).lowercase(Locale.ROOT)] = name.asString
                }
                entries[folder] = Entry(priority, names.toMap())
            }
            entries.toMap()
        }
    }

    private fun key(item: DlcItem): Key {
        val source = item.getSourcePath() ?: return Key("", emptyList())
        val normalized = source.toAbsolutePath().normalize()
        val root = roots.firstOrNull { normalized.startsWith(it) }
        val directory = item.getGroupDirectory()?.toAbsolutePath()?.normalize() ?: normalized.parent ?: return Key("", emptyList())
        if (root == null) {
            return try {
                val relative = gameDir.relativize(directory).toString().replace('\\', '/')
                Key(relative, if (relative.isEmpty()) emptyList() else relative.split('/').map { Segment(it, DEFAULT) })
            } catch (_: IllegalArgumentException) {
                Key("", emptyList())
            }
        }
        val remote = directory.startsWith(root.resolve("remote")) || directory.startsWith(root.resolve("downloaded/DLCs"))
        val origin = when {
            directory.startsWith(root.resolve("remote")) -> root.resolve("remote")
            directory.startsWith(root.resolve("downloaded/DLCs")) -> root.resolve("downloaded/DLCs")
            else -> root
        }
        if (!directory.startsWith(origin)) return Key("", emptyList())
        val relative = origin.relativize(directory)
        val segments = ArrayList<Segment>()
        var parent = Path.of("")
        if (relative.toString().isNotEmpty()) for (part in relative) {
            val name = part.toString()
            val local = load(root.resolve(parent))[name]
            val downloaded = load(root.resolve("remote").resolve(parent))[name]
            val selected = if (local != null && downloaded != null && local != downloaded) {
                if (remote) downloaded else local
            } else local ?: downloaded
            segments.add(Segment(name, selected ?: DEFAULT))
            parent = parent.resolve(part)
        }
        return Key(relative.toString().replace('\\', '/'), segments.toList())
    }

    companion object {
        const val FILE_NAME = DlcDcFormatUtil.FOLDER_METADATA_FILE_NAME
        private val DEFAULT = Entry(0, emptyMap())
        private fun compare(left: Key, right: Key): Int {
            for (index in 0 until minOf(left.segments.size, right.segments.size)) {
                val a = left.segments[index]
                val b = right.segments[index]
                val priority = a.metadata.priority.compareTo(b.metadata.priority)
                if (priority != 0) return priority
                val name = a.name.compareTo(b.name)
                if (name != 0) return name
            }
            val depth = left.segments.size.compareTo(right.segments.size)
            return if (depth != 0) depth else identity(left).compareTo(identity(right))
        }
        private fun identity(key: Key): String {
            val values = JsonArray()
            values.add(key.path)
            for (segment in key.segments) {
                val metadata = JsonObject()
                metadata.addProperty("priority", segment.metadata.priority)
                val names = JsonObject()
                TreeMap(segment.metadata.displayNames).forEach { (locale, name) -> names.addProperty(locale, name) }
                metadata.add("DisplayName", names)
                values.add(metadata)
            }
            return values.toString()
        }
    }
}
