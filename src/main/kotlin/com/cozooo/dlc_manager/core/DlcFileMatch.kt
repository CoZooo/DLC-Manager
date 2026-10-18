package com.cozooo.dlc_manager.core

import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.Comparator
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

object DlcFileMatch {
    private const val LIGHT_LIMIT = 128 * DlcFileSize.KIB
    private val SAMPLE_BYTES = (64 * DlcFileSize.KIB).toInt()
    private data class CachedHash(val stamp: String, val hash: String)
    private val hashes = ConcurrentHashMap<Path, CachedHash>()
    private val scanHashes = ThreadLocal<MutableMap<Path, CachedHash>>()
    @JvmStatic fun mode(value: String?): String = if (value.equals("name", true)) "name" else "hash"
    @JvmStatic fun beginScan() { scanHashes.set(HashMap()) }
    @JvmStatic fun endScan() { scanHashes.remove() }

    @JvmStatic @Throws(IOException::class)
    fun hash(path: Path): String {
        val normalized = path.toAbsolutePath().normalize()
        val stamp = stamp(normalized)
        val scan = scanHashes.get()
        var cached = scan?.get(normalized)
        if (cached == null || cached.stamp != stamp) cached = hashes[normalized]
        if (cached != null && cached.stamp == stamp) return cached.hash
        val result = fullHash(normalized)
        if (stamp != stamp(normalized)) throw IOException("Resource changed while hashing: $path")
        val updated = CachedHash(stamp, result)
        if (hashes.size >= 8192) hashes.clear()
        hashes[normalized] = updated
        scan?.put(normalized, updated)
        return result
    }

    private fun stamp(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val attributes = attributes(path)
        if (attributes.isDirectory) Files.walk(path).use { paths ->
            for (child in paths.sorted(Comparator.comparing { p: Path -> path.relativize(p).toString() }).toList()) {
                checkInterrupted()
                addStamp(digest, path.relativize(child).toString().replace('\\', '/'), child)
            }
        } else addStamp(digest, "", path)
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun addStamp(digest: MessageDigest, relative: String, path: Path) {
        val attributes = attributes(path)
        val value = relative + "\u0000" + (if (attributes.isDirectory) "directory" else "file") + "\u0000" +
            attributes.size() + "\u0000" + attributes.lastModifiedTime() + "\u0000" + attributes.fileKey() + "\u0000"
        digest.update(value.toByteArray(StandardCharsets.UTF_8))
        if (attributes.isRegularFile) { digest.update(lightHash(path).toByteArray(StandardCharsets.US_ASCII)); unchanged(path, attributes) }
    }

    @JvmStatic @Throws(IOException::class)
    fun lightHash(path: Path): String {
        val before = attributes(path)
        if (!before.isRegularFile) throw IOException("Not a regular resource: $path")
        if (before.size() <= LIGHT_LIMIT) return fullHash(path)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ByteBuffer.allocate(java.lang.Long.BYTES).putLong(before.size()).array())
        Files.newByteChannel(path).use { channel ->
            val buffer = ByteBuffer.allocate(SAMPLE_BYTES)
            for (offset in longArrayOf(0, before.size() - buffer.capacity())) {
                checkInterrupted(); buffer.clear(); channel.position(offset)
                while (buffer.hasRemaining()) {
                    checkInterrupted()
                    if (channel.read(buffer) < 0) throw EOFException("Resource changed while hashing: $path")
                }
                digest.update(buffer.array())
            }
        }
        unchanged(path, before)
        return "light-" + HexFormat.of().formatHex(digest.digest())
    }

    @JvmStatic @Throws(IOException::class)
    fun fullHash(path: Path): String {
        val normalized = path.toAbsolutePath().normalize()
        val before = attributes(normalized)
        val digest = MessageDigest.getInstance("SHA-256")
        if (before.isDirectory) Files.walk(normalized).use { paths ->
            for (child in paths.sorted(Comparator.comparing { p: Path -> normalized.relativize(p).toString() }).toList()) {
                checkInterrupted()
                val childAttributes = attributes(child)
                digest.update((normalized.relativize(child).toString().replace('\\', '/') + "\u0000" + (if (childAttributes.isDirectory) "directory" else "file") + "\u0000").toByteArray(StandardCharsets.UTF_8))
                if (childAttributes.isRegularFile) {
                    digest.update((childAttributes.size().toString() + "\u0000").toByteArray(StandardCharsets.UTF_8))
                    update(digest, child)
                }
            }
        } else update(digest, normalized)
        unchanged(normalized, before)
        return HexFormat.of().formatHex(digest.digest())
    }

    @JvmStatic @Throws(IOException::class)
    fun hash(input: java.io.InputStream): String {
        checkInterrupted()
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(SAMPLE_BYTES)
        while (true) {
            val count = input.read(buffer)
            checkInterrupted()
            if (count == -1) break
            digest.update(buffer, 0, count)
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun update(digest: MessageDigest, file: Path) {
        val before = attributes(file)
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(SAMPLE_BYTES)
            while (true) {
                val count = input.read(buffer)
                checkInterrupted()
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        unchanged(file, before)
    }

    private fun attributes(path: Path): BasicFileAttributes {
        checkInterrupted()
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (!attributes.isRegularFile && !attributes.isDirectory) throw IOException("Not a regular resource or directory: $path")
        return attributes
    }

    private fun unchanged(path: Path, before: BasicFileAttributes) {
        val after = attributes(path)
        if (before.isDirectory != after.isDirectory || before.size() != after.size() ||
            before.lastModifiedTime() != after.lastModifiedTime() || before.fileKey() != after.fileKey()) throw IOException("Resource changed while hashing: $path")
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw IOException("Resource validation canceled")
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
