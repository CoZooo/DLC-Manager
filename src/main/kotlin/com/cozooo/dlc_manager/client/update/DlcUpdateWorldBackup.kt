package com.cozooo.dlc_manager.client.update

import com.cozooo.dclanguage.DcDocuments
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

object DlcUpdateWorldBackup {
    data class World(val name: String, val modified: Long) {
        fun name(): String = name
        fun modified(): Long = modified
    }
    data class Snapshot(val id: String, val kind: String, val timestamp: Long, val worlds: List<String>) {
        fun id(): String = id
        fun kind(): String = kind
        fun timestamp(): Long = timestamp
        fun worlds(): List<String> = worlds
    }

    @JvmStatic fun listWorlds(rawGame: Path): List<World> {
        val game = canonical(rawGame)
        val saves = game.resolve("saves")
        if (!Files.exists(saves)) return emptyList()
        safe(saves)
        val result = mutableListOf<World>()
        Files.list(saves).use { paths ->
            for (path in paths.toList()) {
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) continue
                val level = path.resolve("level.dat")
                if (Files.isRegularFile(level, LinkOption.NOFOLLOW_LINKS)) {
                    result.add(World(path.fileName.toString(), Files.getLastModifiedTime(level).toMillis()))
                }
            }
        }
        return result.sortedWith(compareByDescending<World> { it.modified }.thenBy { it.name })
    }

    @JvmStatic fun list(rawRoot: Path): List<Snapshot> {
        val root = canonical(rawRoot)
        val sets = root.resolve("worlds/sets")
        if (!Files.exists(sets)) return emptyList()
        safe(sets)
        val result = mutableListOf<Snapshot>()
        Files.list(sets).use { paths ->
            for (file in paths.filter { it.toString().endsWith(".dc") }.toList()) {
                safe(file)
                val data = DcDocuments.read(file).toJson()
                val names = data.getAsJsonArray("worlds").map { validName(it.asJsonObject.get("name").asString) }
                if (names.isEmpty()) continue
                result.add(Snapshot(data.get("id").asString, data.get("kind").asString, data.get("timestamp").asLong, names))
            }
        }
        return result.sortedWith(compareByDescending<Snapshot> { it.timestamp }.thenBy { it.id })
    }

    @JvmStatic fun pointer(rawRoot: Path, kind: String): String? {
        val root = canonical(rawRoot)
        val file = root.resolve("worlds/$kind.dc")
        if (!Files.exists(file)) return null
        safe(file)
        val id = validName(DcDocuments.read(file).toJson().get("id").asString)
        return id.takeIf { available(root, it) }
    }

    @JvmStatic fun worldNames(root: Path, id: String): List<String> = read(canonical(root), id)
        .getAsJsonArray("worlds").map { validName(it.asJsonObject.get("name").asString) }

    @JvmStatic fun point(rawRoot: Path, kind: String, id: String) {
        val root = canonical(rawRoot)
        val data = JsonObject()
        data.addProperty("id", validName(id))
        val file = root.resolve("worlds/$kind.dc")
        safe(file)
        DlcUpdateJournal.beforeWriteCanonical(file)
        write(file, data)
    }

    @JvmStatic fun capture(rawGame: Path, rawRoot: Path, names: Collection<String>, kind: String): String {
        val game = canonical(rawGame)
        val root = canonical(rawRoot)
        val id = UUID.randomUUID().toString()
        val directory = root.resolve("worlds/data/$id")
        safe(directory)
        val data = JsonObject()
        data.addProperty("id", id)
        data.addProperty("kind", kind)
        data.addProperty("timestamp", System.currentTimeMillis())
        val worlds = JsonArray()
        for (raw in names.toSet()) {
            val name = validName(raw)
            val source = game.resolve("saves").resolve(name)
            safe(source)
            if (directory.startsWith(source) || source.startsWith(directory)) throw IOException("World backup overlaps source world: $name")
            val exists = Files.exists(source)
            val entry = JsonObject()
            entry.addProperty("name", name)
            entry.addProperty("exists", exists)
            if (exists) lock(source).use { copy(source, directory.resolve(name)) }
            worlds.add(entry)
        }
        data.add("worlds", worlds)
        val manifest = root.resolve("worlds/sets/$id.dc")
        safe(manifest)
        write(manifest, data)
        return id
    }

    @JvmStatic fun restore(rawGame: Path, rawRoot: Path, id: String, selection: Collection<String>?) {
        val game = canonical(rawGame)
        val root = canonical(rawRoot)
        restoreWithRecovery(game, root, id, selection, "recovery")
    }

    private fun restoreWithRecovery(game: Path, root: Path, id: String, selection: Collection<String>?, kind: String): String? {
        val data = read(root, id)
        val names = data.getAsJsonArray("worlds").map { validName(it.asJsonObject.get("name").asString) }
            .filter { selection == null || selection.contains(it) }
        if (names.isEmpty()) return null
        val recovery = capture(game, root, names, kind)
        if (kind == "rollback") point(root, "after", recovery)
        try {
            restoreRaw(game, root, id, names)
            if (kind != "return") {
                val targets = returnTargets(root).toMutableMap()
                names.forEach { targets[it] = recovery }
                saveReturns(root, targets)
            }
        }
        catch (failure: Exception) {
            try { restoreRaw(game, root, recovery, names) }
            catch (recoveryFailure: Exception) { failure.addSuppressed(recoveryFailure) }
            throw failure
        }
        return recovery
    }

    @JvmStatic fun rollback(rawGame: Path, rawRoot: Path) {
        val game = canonical(rawGame)
        val root = canonical(rawRoot)
        val before = pointer(root, "before") ?: return
        restoreWithRecovery(game, root, before, null, "rollback")
    }

    @JvmStatic fun returnTargets(rawRoot: Path): MutableMap<String, String> {
        val root = canonical(rawRoot)
        val file = root.resolve("worlds/returns.dc")
        safe(file)
        val result = linkedMapOf<String, String>()
        if (!Files.exists(file)) return result
        val data = DcDocuments.read(file).toJson()
        if (!data.has("worlds")) return result
        for ((rawName, value) in data.getAsJsonObject("worlds").entrySet()) {
            val name = validName(rawName)
            val id = validName(value.asString)
            if (available(root, id) && worldNames(root, id).contains(name)) result[name] = id
        }
        return result
    }

    @JvmStatic fun returnBeforeRestore(rawGame: Path, rawRoot: Path, name: String) {
        val root = canonical(rawRoot)
        val game = canonical(rawGame)
        validName(name)
        val targets = returnTargets(root)
        val id = targets[name] ?: throw IOException("No state before world rollback: $name")
        val temporary = restoreWithRecovery(game, root, id, listOf(name), "return")
        targets.remove(name)
        saveReturns(root, targets)
        if (read(root, id).get("kind").asString != "update") delete(root, id, listOf(name))
        if (temporary != null) delete(root, temporary, null)
    }

    @JvmStatic fun delete(rawRoot: Path, id: String, selection: Collection<String>?) {
        val root = canonical(rawRoot)
        val data = read(root, id)
        val remaining = JsonArray()
        val removed = mutableListOf<String>()
        for (value in data.getAsJsonArray("worlds")) {
            val name = validName(value.asJsonObject.get("name").asString)
            if (selection == null || selection.contains(name)) removed.add(name) else remaining.add(value)
        }
        if (removed.isEmpty() && selection != null) return
        val manifest = root.resolve("worlds/sets/${validName(id)}.dc")
        val directory = root.resolve("worlds/data/$id")
        val trash = root.resolve("worlds/trash/${UUID.randomUUID()}")
        safe(trash)
        removed.forEach { checkTree(directory.resolve(it)) }
        val moved = mutableListOf<String>()
        try {
            Files.createDirectories(trash)
            for (name in removed) {
                val source = directory.resolve(name)
                if (Files.exists(source)) {
                    Files.move(source, trash.resolve(name))
                    moved.add(name)
                }
            }
            if (remaining.isEmpty) Files.move(manifest, trash.resolve("meta.dc"))
            else {
                data.add("worlds", remaining)
                write(manifest, data)
            }
        } catch (failure: Exception) {
            for (name in moved) {
                try { Files.move(trash.resolve(name), directory.resolve(name)) }
                catch (restoreFailure: Exception) { failure.addSuppressed(restoreFailure) }
            }
            throw failure
        }
        if (remaining.isEmpty) {
            for (kind in listOf("before", "after")) {
                val file = root.resolve("worlds/$kind.dc")
                safe(file)
                if (Files.exists(file) && id == DcDocuments.read(file).toJson().get("id").asString) Files.delete(file)
            }
        }
        saveReturns(root, returnTargets(root))
        removeTree(trash)
        if (Files.isDirectory(directory)) {
            Files.list(directory).use { children -> if (children.findAny().isEmpty) Files.delete(directory) }
        }
    }

    @JvmStatic fun deleteAll(root: Path) {
        val worlds = canonical(root).resolve("worlds")
        checkTree(worlds)
        removeTree(worlds)
    }

    private fun saveReturns(root: Path, targets: Map<String, String>) {
        val worlds = JsonObject()
        for ((name, id) in targets) worlds.addProperty(validName(name), validName(id))
        val data = JsonObject()
        data.add("worlds", worlds)
        val file = root.resolve("worlds/returns.dc")
        safe(file)
        DlcUpdateJournal.beforeWriteCanonical(file)
        write(file, data)
    }

    private fun available(root: Path, id: String): Boolean {
        val file = root.resolve("worlds/sets/${validName(id)}.dc")
        safe(file)
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
    }

    private fun checkTree(path: Path) {
        safe(path)
        if (!Files.exists(path)) return
        Files.walk(path).use { paths -> paths.iterator().forEachRemaining { safe(it) } }
    }

    private fun removeTree(path: Path) {
        checkTree(path)
        if (!Files.exists(path)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                safe(file)
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun write(file: Path, data: JsonObject) {
        Files.createDirectories(file.parent)
        val document = if (Files.exists(file)) DcDocuments.read(file) else DcDocuments.create()
        val current = document.root()
        for ((key, value) in data.entrySet()) current.add(key, value)
        DcDocuments.write(file, document)
    }

    private fun read(root: Path, id: String): JsonObject {
        val file = root.resolve("worlds/sets/${validName(id)}.dc")
        safe(file)
        val data = DcDocuments.read(file).toJson()
        if (id != data.get("id").asString) throw IOException("Invalid world backup")
        return data
    }

    private fun restoreRaw(game: Path, root: Path, id: String, names: Collection<String>) {
        val data = read(root, id)
        for (value in data.getAsJsonArray("worlds")) {
            val entry = value.asJsonObject
            val name = validName(entry.get("name").asString)
            if (!names.contains(name)) continue
            val target = game.resolve("saves").resolve(name)
            val source = root.resolve("worlds/data/${validName(id)}").resolve(name)
            safe(target)
            safe(source)
            val exists = entry.get("exists").asBoolean
            if (exists && !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) throw IOException("Missing world backup: $name")
            DlcUpdateJournal.beforeWriteCanonical(target)
            lock(target).use {
                clear(target)
                if (exists) copy(source, target)
            }
            if (!exists && Files.exists(target)) {
                Files.deleteIfExists(target.resolve("session.lock"))
                Files.delete(target)
            }
        }
    }

    private fun validName(name: String): String {
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\') || Path.of(name).isAbsolute) {
            throw IOException("Invalid world backup name")
        }
        return name
    }

    private fun canonical(path: Path): Path {
        val absolute = path.toAbsolutePath().normalize()
        var ancestor = absolute
        while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.parent
        return ancestor.toRealPath().resolve(ancestor.relativize(absolute))
    }

    private fun safe(path: Path) {
        var part: Path? = path.toAbsolutePath().normalize()
        while (part != null) {
            if (Files.isSymbolicLink(part)) throw IOException("World backups do not support symbolic links: $part")
            part = part.parent
        }
    }

    private fun copy(source: Path, target: Path) {
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                safe(dir)
                Files.createDirectories(target.resolve(source.relativize(dir)))
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (file == source.resolve("session.lock")) return FileVisitResult.CONTINUE
                safe(file)
                if (!attrs.isRegularFile) throw IOException("Unsupported world file: $file")
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun clear(target: Path) {
        if (!Files.exists(target)) return
        Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                safe(file)
                if (file != target.resolve("session.lock")) Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                if (dir != target) Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun lock(world: Path): WorldLock {
        val file = world.resolve("session.lock")
        safe(file)
        if (!Files.exists(file)) return WorldLock(null, null)
        val channel = FileChannel.open(file, StandardOpenOption.WRITE)
        try {
            val lock = channel.tryLock() ?: throw IOException("World is open. Leave the world before backing up or restoring: ${world.fileName}")
            return WorldLock(channel, lock)
        } catch (failure: Exception) {
            channel.close()
            throw IOException("World is open or locked: ${world.fileName}", failure)
        }
    }

    private class WorldLock(val channel: FileChannel?, val lock: FileLock?) : AutoCloseable {
        override fun close() {
            try { lock?.release() }
            finally { channel?.close() }
        }
    }
}
