package com.cozooo.dlc_manager.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

object DlcResourceHistory {
    @JvmStatic fun key(config: String, identifier: String?): String = UUID.nameUUIDFromBytes((config + "\u0000" + identifier).toByteArray(StandardCharsets.UTF_8)).toString()

    @JvmStatic fun relativePath(path: Path): String = DlcPaths.gameDir().toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/')

    private fun resolvePath(path: String): Path = DlcPaths.gameDir().toAbsolutePath().normalize().resolve(path).normalize()

    private fun read(root: Path, kind: String): JsonObject {
        val file = root.resolve("tmp").resolve(kind).resolve("history.dc")
        val data = DlcDcConfigIO.read(file, DlcDcConfigIO.Layout.GENERIC) ?: run {
            if (Files.exists(file)) throw IOException("Invalid resource history: $file")
            JsonObject().apply {
                addProperty("format", 1)
                add("resources", JsonObject())
                add("backups", JsonObject())
            }
        }
        if (!data.has("resources") || !data.get("resources").isJsonObject || !data.has("backups") || !data.get("backups").isJsonObject) throw IOException("Invalid resource history: $file")
        return data
    }

    private fun save(root: Path, kind: String, data: JsonObject) {
        val file = root.resolve("tmp").resolve(kind).resolve("history.dc")
        com.cozooo.dlc_manager.client.update.DlcUpdateJournal.beforeWrite(file)
        Files.createDirectories(file.parent)
        val staging = file.resolveSibling("history.pending.dc")
        if (!DlcDcConfigIO.write(staging, data, DlcDcConfigIO.Layout.GENERIC)) throw IOException("Cannot save resource history: $file")
        move(staging, file)
    }

    @JvmStatic @Synchronized @Throws(IOException::class)
    fun downloaded(root: Path, key: String, identifier: String?, config: Path?, source: Path) {
        val data = read(root, "required")
        resource(data, root, key, identifier, config, source)
        save(root, "required", data)
    }

    private fun resource(data: JsonObject, root: Path, key: String, identifier: String?, config: Path?, source: Path): JsonObject {
        val resources = data.getAsJsonObject("resources")
        val item = if (resources.has(key)) resources.getAsJsonObject(key) else JsonObject()
        if (item.has("active") && !item.get("active").asBoolean) {
            item.add("targets", JsonObject())
            item.addProperty("status", "downloaded")
        }
        item.addProperty("identifier", identifier)
        item.addProperty("config", config?.let(::relativePath) ?: "")
        item.addProperty("source", relativePath(source))
        item.addProperty("downloaded", source.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize().resolve("downloaded/required")))
        val downloads = if (item.has("downloads")) item.getAsJsonArray("downloads") else JsonArray()
        val downloadedPath = relativePath(source)
        if (item.get("downloaded").asBoolean && downloads.none { it.asString == downloadedPath }) downloads.add(downloadedPath)
        item.add("downloads", downloads)
        item.addProperty("active", true)
        if (!item.has("targets")) item.add("targets", JsonObject())
        if (!item.has("status")) item.addProperty("status", "downloaded")
        resources.add(key, item)
        return item
    }

    @JvmStatic @Synchronized @Throws(IOException::class)
    fun prepare(root: Path, kind: String, key: String, identifier: String?, config: Path?, source: Path, target: Path, replace: Boolean) {
        prepare(root, kind, key, identifier, config, source, target, replace, "hash")
    }

    @JvmStatic @Throws(IOException::class)
    fun prepare(root: Path, kind: String, key: String, identifier: String?, config: Path?, source: Path, target: Path, replace: Boolean, mode: String?) {
        prepare(root, kind, key, identifier, config, source, source, target, replace, mode)
    }

    @JvmStatic @Synchronized @Throws(IOException::class)
    fun prepare(root: Path, kind: String, key: String, identifier: String?, config: Path?, source: Path, content: Path, target: Path, replace: Boolean, mode: String?) {
        if (replace) com.cozooo.dlc_manager.client.update.DlcUpdateJournal.beforeWrite(target)
        val data = read(root, kind)
        val item = resource(data, root, key, identifier, config, source)
        val targets = item.getAsJsonObject("targets")
        val targetKey = key(relativePath(target), "target")
        val previous = if (targets.has(targetKey)) targets.getAsJsonObject(targetKey) else null
        if (replace && Files.exists(target)) {
            val directory = root.resolve("tmp").resolve(kind)
            val safeId = if (identifier.isNullOrBlank()) key else identifier.replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
            var timestamp = System.currentTimeMillis()
            var backup: Path
            do { backup = directory.resolve("$safeId.${timestamp++}") } while (Files.exists(backup) || Files.exists(Path.of("$backup.old")))
            copy(target, backup)
            val record = JsonObject().apply {
                addProperty("resource", key)
                addProperty("target", relativePath(target))
                addProperty("path", root.toAbsolutePath().normalize().relativize(backup.toAbsolutePath().normalize()).toString().replace('\\', '/'))
                addProperty("hash", DlcFileMatch.hash(backup))
                addProperty("timestamp", timestamp - 1)
                addProperty("old", false)
            }
            data.getAsJsonObject("backups").add(UUID.randomUUID().toString(), record)
        }
        val installed = JsonObject().apply {
            addProperty("path", relativePath(target))
            addProperty("mode", DlcFileMatch.mode(mode))
            addProperty("hash", DlcFileMatch.hash(if (!replace && Files.exists(target)) target else content))
            addProperty("owned", replace || kind == "dlc" || previous != null && previous.get("owned").asBoolean)
        }
        targets.add(targetKey, installed)
        item.addProperty("status", if (replace) "installing" else "installed")
        save(root, kind, data)
    }

    @JvmStatic @Synchronized @Throws(IOException::class)
    fun installedPaths(root: Path, kind: String, key: String): List<Path> {
        val item = read(root, kind).getAsJsonObject("resources").getAsJsonObject(key) ?: return emptyList()
        if (!item.get("active").asBoolean) return emptyList()
        return item.getAsJsonObject("targets").entrySet().map { resolvePath(it.value.asJsonObject.get("path").asString) }
    }

    @JvmStatic @Synchronized @Throws(IOException::class)
    fun complete(root: Path, kind: String, key: String) {
        val data = read(root, kind)
        val item = data.getAsJsonObject("resources").getAsJsonObject(key)
        if (item != null) {
            item.addProperty("status", "installed")
            item.addProperty("installedAt", System.currentTimeMillis())
            save(root, kind, data)
        }
    }

    @JvmStatic @Synchronized @Throws(IOException::class)
    fun rekey(root: Path, kind: String, oldKey: String, newKey: String) {
        if (oldKey == newKey) return
        val data = read(root, kind)
        val resources = data.getAsJsonObject("resources")
        if (!resources.has(oldKey)) return
        val previous = resources.remove(oldKey).asJsonObject
        if (resources.has(newKey)) {
            val current = resources.getAsJsonObject(newKey)
            if (previous.get("active").asBoolean) {
                val targets = previous.getAsJsonObject("targets")
                current.getAsJsonObject("targets").entrySet().forEach { targets.add(it.key, it.value) }
                current.add("targets", targets)
            }
        } else resources.add(newKey, previous)
        for (value in data.getAsJsonObject("backups").asMap().values) {
            val backup = value.asJsonObject
            if (oldKey == backup.get("resource").asString) backup.addProperty("resource", newKey)
        }
        save(root, kind, data)
    }

    @JvmStatic @Synchronized @Throws(IOException::class)
    fun restore(root: Path, kind: String, key: String, earliest: Boolean): Boolean {
        val data = read(root, kind)
        val resources = data.getAsJsonObject("resources")
        if (!resources.has(key)) return false
        val item = resources.getAsJsonObject(key)
        if (!item.get("active").asBoolean) { archiveConsumed(root, kind, data); return true }
        val backups = data.getAsJsonObject("backups")
        val replacements = LinkedHashMap<Path, Path>()
        val removals = ArrayList<Path>()
        for (value in item.getAsJsonObject("targets").asMap().values) {
            val installed = value.asJsonObject
            val targetPath = installed.get("path").asString
            val target = resolvePath(targetPath)
            var selected: JsonObject? = null
            for (backupValue in backups.asMap().values) {
                val backup = backupValue.asJsonObject
                if (backup.get("resource").asString != key || backup.get("old").asBoolean || backup.get("target").asString != targetPath) continue
                val existing = selected
                if (existing == null || (if (earliest) backup.get("timestamp").asLong < existing.get("timestamp").asLong else backup.get("timestamp").asLong > existing.get("timestamp").asLong)) selected = backup
            }
            val backupRecord = selected
            val mode = if (installed.has("mode")) installed.get("mode").asString else "hash"
            val ownsTarget = installed.get("owned").asBoolean
            val matching = Files.exists(target) && (mode == "name" || installed.get("hash").asString == DlcFileMatch.hash(target))
            if (ownsTarget && !matching && kind == "dlc" && mode == "hash") {
                DlcFileMatch.findByHash(installed.get("hash").asString, target.parent)?.let { removals.add(it) }
            }
            if (backupRecord != null) {
                val backup = checkedBackup(root, kind, backupRecord.get("path").asString)
                if (backupRecord.get("hash").asString != DlcFileMatch.hash(backup)) throw IOException("Resource backup is damaged: $backup")
                replacements[target] = backup
            } else if (ownsTarget && matching) {
                removals.add(target)
            }
        }
        for (target in removals) {
            com.cozooo.dlc_manager.client.update.DlcUpdateJournal.beforeWrite(target)
            delete(target)
        }
        for ((target, backup) in replacements) {
            com.cozooo.dlc_manager.client.update.DlcUpdateJournal.beforeWrite(target)
            replace(backup, target)
        }
        for (backupValue in backups.asMap().values) {
            val backup = backupValue.asJsonObject
            if (backup.get("resource").asString == key) backup.addProperty("old", true)
        }
        item.addProperty("active", false)
        item.addProperty("status", "removed")
        save(root, kind, data)
        archiveConsumed(root, kind, data)
        return true
    }

    private fun archiveConsumed(root: Path, kind: String, data: JsonObject) {
        var changed = false
        for (value in data.getAsJsonObject("backups").asMap().values) {
            val backup = value.asJsonObject
            val stored = backup.get("path").asString
            if (!backup.get("old").asBoolean || stored.endsWith(".old")) continue
            val path = checkedBackup(root, kind, stored)
            val old = path.resolveSibling("${path.fileName}.old")
            if (Files.exists(path)) {
                com.cozooo.dlc_manager.client.update.DlcUpdateJournal.beforeWrite(path)
                com.cozooo.dlc_manager.client.update.DlcUpdateJournal.beforeWrite(old)
                move(path, old)
            }
            backup.addProperty("path", root.toAbsolutePath().normalize().relativize(old).toString().replace('\\', '/'))
            changed = true
        }
        if (changed) save(root, kind, data)
    }

    @JvmStatic @Synchronized @Throws(IOException::class)
    fun reconcileRequired(root: Path, currentKeys: Set<String>) {
        reconcileRequired(root, currentKeys, emptySet())
    }

    @JvmStatic @Synchronized @Throws(IOException::class)
    fun reconcileRequired(root: Path, currentKeys: Set<String>, referencedDownloads: Set<String>) {
        val data = read(root, "required")
        archiveConsumed(root, "required", data)
        val removed = data.getAsJsonObject("resources").entrySet().filter { (key, value) ->
            val config = value.asJsonObject.get("config").asString
            key !in currentKeys && config.isNotBlank() && !Files.exists(resolvePath(config))
        }.map { it.key }
        for (key in removed) {
            val item = data.getAsJsonObject("resources").getAsJsonObject(key)
            restore(root, "required", key, true)
            for (stored in downloadedPaths(item)) {
                val source = resolvePath(stored)
                if (!source.startsWith(root.toAbsolutePath().normalize().resolve("downloaded/required"))) throw IOException("Invalid required resource cache: $source")
                val shared = stored in referencedDownloads || data.getAsJsonObject("resources").entrySet().any { (resourceKey, value) -> resourceKey in currentKeys && stored in downloadedPaths(value.asJsonObject) }
                if (!shared) delete(source)
            }
        }
    }

    private fun downloadedPaths(item: JsonObject): Set<String> {
        val paths = HashSet<String>()
        if (item.has("downloads")) for (path in item.getAsJsonArray("downloads")) paths.add(path.asString)
        if (item.get("downloaded").asBoolean) paths.add(item.get("source").asString)
        return paths
    }

    private fun checkedBackup(root: Path, kind: String, value: String): Path {
        val base = root.toAbsolutePath().normalize().resolve("tmp").resolve(kind)
        val path = root.toAbsolutePath().normalize().resolve(value).normalize()
        if (!path.startsWith(base)) throw IOException("Invalid resource backup path: $value")
        return path
    }

    private fun move(source: Path, target: Path) {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING) }
    }

    @JvmStatic @Throws(IOException::class)
    fun replace(source: Path, target: Path) {
        val staging = target.resolveSibling("${target.fileName}.${UUID.randomUUID()}.partial")
        try {
            copy(source, staging)
            delete(target)
            move(staging, target)
        } finally { delete(staging) }
    }

    @JvmStatic @Throws(IOException::class)
    fun copy(source: Path, target: Path) {
        if (Files.isSymbolicLink(source)) throw IOException("Cannot copy symbolic link: $source")
        if (Files.isDirectory(source)) {
            Files.createDirectories(target)
            Files.list(source).use { paths -> for (child in paths.toList()) copy(child, target.resolve(child.fileName)) }
        } else {
            Files.createDirectories(target.parent)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @JvmStatic @Throws(IOException::class)
    fun delete(path: Path) {
        if (!Files.exists(path)) return
        if (Files.isSymbolicLink(path)) throw IOException("Cannot delete symbolic link: $path")
        if (Files.isDirectory(path)) Files.list(path).use { paths -> for (child in paths.toList()) delete(child) }
        Files.delete(path)
    }
}
