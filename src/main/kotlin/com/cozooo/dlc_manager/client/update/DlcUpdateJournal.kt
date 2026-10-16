package com.cozooo.dlc_manager.client.update

import com.cozooo.dclanguage.DcDocuments
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator

class DlcUpdateJournal private constructor(game: Path, directory: Path, private val entries: JsonArray, private val roots: List<Path>) : AutoCloseable {
    private val game = game.toAbsolutePath().normalize()
    private val directory = directory.toAbsolutePath().normalize()

    fun capture(target: Path) {
        var normalized = target.toAbsolutePath().normalize()
        val internal = directory.parent.parent
        if (normalized.startsWith(internal)) return
        if (internal.startsWith(normalized)) throw IOException("Update target contains pending task: $target")
        while (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) && normalized.parent != null && normalized.parent != game &&
            !Files.exists(normalized.parent, LinkOption.NOFOLLOW_LINKS)) normalized = normalized.parent
        if (!allowed(normalized)) throw IOException("Invalid pending update target: $target")
        checkLinks(normalized)
        for (value in entries) if (normalized.startsWith(resolve(value.asJsonObject.get("path").asString))) return
        val relative = relativePath(game, normalized)
        val trusted = JsonArray()
        roots.forEach { trusted.add(relativePath(game, it)) }
        val name = entries.size().toString()
        val exists = Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)
        if (exists) copy(normalized, directory.resolve("backups").resolve(name))
        val entry = JsonObject()
        entry.addProperty("path", relative)
        entry.addProperty("backup", name)
        entry.addProperty("existed", exists)
        entries.add(entry)
        val root = JsonObject()
        root.add("entries", entries)
        root.add("roots", trusted)
        DcDocuments.write(directory.resolve("recovery.dc"), DcDocuments.fromJson(root))
    }

    fun rollback() {
        ACTIVE.remove()
        var failure: IOException? = null
        for (index in entries.size() - 1 downTo 0) {
            val entry = entries[index].asJsonObject
            try {
                val target = resolve(entry.get("path").asString)
                val backup = directory.resolve("backups").resolve(entry.get("backup").asString).normalize()
                if (!backup.startsWith(directory.resolve("backups"))) throw IOException("Invalid recovery backup")
                if (entry.get("existed").asBoolean && !Files.exists(backup)) throw IOException("Missing recovery backup: $backup")
                remove(target)
                if (entry.get("existed").asBoolean) copy(backup, target)
            } catch (exception: IOException) {
                if (failure == null) failure = exception else failure.addSuppressed(exception)
            }
        }
        failure?.let { throw it }
    }

    private fun allowed(path: Path): Boolean = path != game && (path.startsWith(game) ||
        roots.any { path.startsWith(it.toAbsolutePath().normalize()) })

    private fun resolve(relative: String): Path {
        val target = resolveRelativePath(game, relative)
        if (!allowed(target) || target.startsWith(directory)) throw IOException("Invalid recovery path")
        checkLinks(target)
        return target
    }

    private fun checkLinks(target: Path) {
        var part: Path? = target
        while (part != null && (part == game || allowed(part))) {
            if (Files.isSymbolicLink(part)) throw IOException("Cannot update symbolic link: $target")
            part = part.parent
        }
    }

    override fun close() {
        ACTIVE.remove()
        com.cozooo.dlc_manager.client.helper.DlcFileOpsHelperClient.requireImmediateOperations(false)
    }

    companion object {
        private val ACTIVE = ThreadLocal<DlcUpdateJournal>()

        @JvmStatic
        fun open(game: Path, directory: Path): DlcUpdateJournal {
            val log = directory.resolve("recovery.dc")
            val saved = if (Files.exists(log)) DcDocuments.read(log).toJson() else JsonObject()
            val entries = if (saved.has("entries")) saved.getAsJsonArray("entries") else JsonArray()
            val roots = if (saved.has("roots")) saved.getAsJsonArray("roots").map { resolveRelativePath(game, it.asString) }
                else com.cozooo.dlc_manager.core.DlcConfig.getDlcRoots()
            if (entries == null) throw IOException("Invalid pending update recovery journal")
            return DlcUpdateJournal(game, directory, entries, roots.toList()).also {
                ACTIVE.set(it)
                com.cozooo.dlc_manager.client.helper.DlcFileOpsHelperClient.requireImmediateOperations(true)
            }
        }

        @JvmStatic
        fun relativePath(base: Path, path: Path): String = try {
            base.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/')
        } catch (exception: IllegalArgumentException) {
            throw IOException("Cannot store a relative path across filesystem roots", exception)
        }

        @JvmStatic
        fun resolveRelativePath(base: Path, value: String): Path {
            val relative = Path.of(value)
            if (relative.isAbsolute || value.startsWith("\\") || value.matches(Regex("^[A-Za-z]:.*"))) throw IOException("Expected a relative path")
            return base.toAbsolutePath().normalize().resolve(relative).normalize()
        }

        @JvmStatic
        fun isActive(): Boolean = ACTIVE.get() != null

        @JvmStatic
        fun beforeOperation(target: Path?) {
            val journal = ACTIVE.get() ?: return
            if (target == null) return
            val path = target.toAbsolutePath().normalize()
            val internal = journal.directory.parent.parent
            val runtime = journal.game.resolve(".dlc-manager")
            check(journal.allowed(path) && !path.startsWith(internal) && !internal.startsWith(path) && !path.startsWith(runtime) && !runtime.startsWith(path)) {
                "Invalid pending update operation target: $target"
            }
        }

        @JvmStatic
        fun beforeWriteCanonical(target: Path) {
            val journal = ACTIVE.get() ?: return
            for (anchor in journal.roots + listOf(journal.game)) {
                val original = anchor.toAbsolutePath().normalize()
                var ancestor = original
                while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.parent
                val canonical = ancestor.toRealPath().resolve(ancestor.relativize(original))
                if (target.startsWith(canonical)) {
                    journal.capture(original.resolve(canonical.relativize(target)))
                    return
                }
            }
            throw IOException("Invalid pending update target: $target")
        }

        @JvmStatic
        fun beforeWrite(target: Path?) {
            val journal = ACTIVE.get() ?: return
            if (target == null) return
            try { journal.capture(target) }
            catch (exception: IOException) { throw IllegalStateException("Cannot back up pending update target: $target", exception) }
        }

        private fun copy(source: Path, target: Path) {
            if (Files.isSymbolicLink(source)) throw IOException("Cannot back up symbolic link: $source")
            if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(target.parent)
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                return
            }
            Files.createDirectories(target)
            Files.list(source).use { stream -> for (child in stream.toList()) copy(child, target.resolve(child.fileName)) }
        }

        private fun remove(target: Path) {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return
            if (Files.isSymbolicLink(target)) throw IOException("Cannot restore symbolic link: $target")
            Files.walk(target).use { paths -> for (path in paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path) }
        }
    }
}
