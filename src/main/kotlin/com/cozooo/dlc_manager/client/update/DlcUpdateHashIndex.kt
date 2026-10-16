package com.cozooo.dlc_manager.client.update

import com.cozooo.dclanguage.DcDocument
import com.cozooo.dclanguage.DcDocuments
import com.cozooo.dlc_manager.core.DlcFileMatch
import com.cozooo.dlc_manager.core.DlcPathUtil
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class DlcUpdateHashIndex private constructor(private val hashes: Map<String, String>) {
    fun findDeleteMatches(gameDir: Path, raw: String, expectedHash: String?): List<Path> {
        val named = DlcPathUtil.findMatches(gameDir, raw)
        if (named.isNotEmpty()) return named
        val pattern = DlcPathUtil.normalizePattern(raw) ?: return emptyList()
        if (!pattern.startsWith("mods/") || expectedHash == null) return emptyList()
        val expected = expectedHash.trim().lowercase(Locale.ROOT)
        if (!expected.matches(Regex("[0-9a-f]{64}"))) return emptyList()
        val parentPattern = pattern.substringBeforeLast('/')
        val mods = gameDir.resolve("mods").toAbsolutePath().normalize()
        return hashes.entries.mapNotNull { (name, hash) ->
            val file = mods.resolve(name).normalize()
            val parent = "mods" + if (name.contains('/')) "/" + name.substringBeforeLast('/') else ""
            file.takeIf { expected.equals(hash, true) && DlcPathUtil.matches(parentPattern, parent) &&
                file.startsWith(mods) && isSafeFile(mods, file) }
        }.sorted()
    }

    companion object {
        private val WORKER = Executors.newSingleThreadExecutor { task ->
            Thread(task, "dlc-update-hashes").apply { isDaemon = true }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun prepare(gameDir: Path, dlcRoot: Path): DlcUpdateHashIndex = onWorker { synchronize(gameDir, dlcRoot) }

        @JvmStatic
        @Throws(IOException::class)
        fun invalidate(gameDir: Path, dlcRoot: Path, changed: List<String>) {
            val mods = gameDir.resolve("mods").toAbsolutePath().normalize()
            val targets = changed.map { gameDir.resolve(it).toAbsolutePath().normalize() }
                .filter { it.startsWith(mods) || mods.startsWith(it) }
            if (targets.isEmpty()) return
            onWorker {
                val indexPath = indexPath(dlcRoot)
                if (Files.exists(indexPath)) {
                    val document = DcDocuments.read(indexPath)
                    val files = files(document)
                    var dirty = false
                    for (name in files.keySet().toList()) {
                        val file = gameDir.resolve("mods").resolve(name).toAbsolutePath().normalize()
                        if (targets.any { file.startsWith(it) }) {
                            files.addProperty(name, "")
                            dirty = true
                        }
                    }
                    if (dirty) DcDocuments.write(indexPath, document)
                }
            }
        }

        private fun synchronize(gameDir: Path, dlcRoot: Path): DlcUpdateHashIndex {
            val path = indexPath(dlcRoot)
            val document = if (Files.exists(path)) DcDocuments.read(path) else DcDocuments.create()
            val files = files(document)
            val current = linkedMapOf<String, String>()
            val mods = gameDir.resolve("mods").toAbsolutePath().normalize()
            if (Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)) {
                Files.walk(mods).use { stream ->
                    for (file in stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.sorted().toList()) {
                        if (!isSafeFile(mods, file)) continue
                        val name = mods.relativize(file).toString().replace('\\', '/')
                        val saved = files.get(name)
                        var hash = if (saved != null && saved.isJsonPrimitive && saved.asJsonPrimitive.isString) saved.asString else ""
                        if (hash.isBlank()) hash = DlcFileMatch.hash(file)
                        current[name] = hash
                    }
                }
            }
            for (name in files.keySet().toList()) if (!current.containsKey(name)) files.remove(name)
            current.forEach { (name, hash) -> files.addProperty(name, hash) }
            DcDocuments.write(path, document)
            return DlcUpdateHashIndex(current.toMap())
        }

        private fun isSafeFile(mods: Path, file: Path): Boolean {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return false
            var part: Path? = file
            while (part != null && part.startsWith(mods)) {
                if (Files.isSymbolicLink(part)) return false
                part = part.parent
            }
            return true
        }

        private fun files(document: DcDocument): JsonObject {
            val value = document.root().get("files")
            if (value != null && !value.isJsonObject) throw IOException("Invalid update hash list: files must be an object")
            if (value == null) document.root().add("files", JsonObject())
            return document.root().getAsJsonObject("files")
        }

        private fun indexPath(dlcRoot: Path): Path = dlcRoot.resolve("tmp/update/hash_list.dc")

        private fun <T> onWorker(work: Callable<T>): T {
            try {
                return WORKER.submit(work).get()
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while organizing update resources", exception)
            } catch (exception: ExecutionException) {
                val cause = exception.cause
                if (cause is IOException) throw cause
                throw IOException("Failed to organize update resources", cause)
            }
        }
    }
}
