package com.cozooo.dlc_manager.core

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import java.util.HexFormat

object DlcFileMatch {
    private val scanHashes = ThreadLocal<MutableMap<Path, String>>()
    @JvmStatic fun mode(value: String?): String = if (value.equals("name", true)) "name" else "hash"
    @JvmStatic fun beginScan() { scanHashes.set(HashMap()) }
    @JvmStatic fun endScan() { scanHashes.remove() }

    @JvmStatic @Throws(IOException::class)
    fun hash(path: Path): String {
        val normalized = path.toAbsolutePath().normalize()
        val cache = scanHashes.get()
        cache?.get(normalized)?.let { return it }
        val digest = MessageDigest.getInstance("SHA-256")
        if (Files.isDirectory(normalized)) {
            Files.walk(normalized).use { paths ->
                for (child in paths.sorted(Comparator.comparing { p: Path -> normalized.relativize(p).toString() }).toList()) {
                    if (Files.isSymbolicLink(child)) throw IOException("Cannot hash symbolic link: $child")
                    digest.update((normalized.relativize(child).toString().replace('\\', '/') + "\u0000" + (if (Files.isDirectory(child)) "directory" else "file") + "\u0000").toByteArray(StandardCharsets.UTF_8))
                    if (Files.isRegularFile(child)) {
                        digest.update((Files.size(child).toString() + "\u0000").toByteArray(StandardCharsets.UTF_8))
                        update(digest, child)
                    }
                }
            }
        } else {
            if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) throw IOException("Not a regular resource: $normalized")
            update(digest, normalized)
        }
        val result = HexFormat.of().formatHex(digest.digest())
        cache?.put(normalized, result)
        return result
    }

    @JvmStatic @Throws(IOException::class)
    fun hash(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            digest.update(buffer, 0, count)
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun update(digest: MessageDigest, file: Path) {
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
    }

    @JvmStatic @Throws(IOException::class)
    fun matches(source: Path?, target: Path?, mode: String?): Boolean {
        if (source == null || target == null || !Files.exists(target)) return false
        if (mode(mode) == "name") return true
        if (!Files.exists(source) || Files.isDirectory(source) != Files.isDirectory(target)) return false
        if (Files.isRegularFile(source) && Files.size(source) != Files.size(target)) return false
        return hash(source) == hash(target)
    }

    @JvmStatic @Throws(IOException::class)
    fun find(source: Path?, directory: Path?, mode: String?): Path? {
        if (source == null || directory == null || !Files.isDirectory(directory)) return null
        val named = directory.resolve(source.fileName)
        if (matches(source, named, mode)) return named
        if (mode(mode) == "name") return null
        Files.list(directory).use { paths ->
            for (path in paths.sorted().toList()) if (path != named && matches(source, path, mode)) return path
        }
        return null
    }
    @JvmStatic @Throws(IOException::class)
    fun findByHash(expectedHash: String, directory: Path?): Path? {
        if (directory == null || !Files.isDirectory(directory)) return null
        Files.list(directory).use { paths ->
            for (path in paths.sorted().toList()) {
                if (!Files.isSymbolicLink(path) && (Files.isRegularFile(path) || Files.isDirectory(path)) && expectedHash == hash(path)) return path
            }
        }
        return null
    }

}
