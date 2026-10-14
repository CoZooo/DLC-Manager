package com.cozooo.dlc_manager.client.core

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.function.Predicate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object DlcZipUtil {
    @JvmStatic
    @Throws(IOException::class)
    fun createUniqueDir(baseDir: Path?, prefix: String?): Path {
        if (baseDir == null) {
            throw IOException("Base directory is not available")
        }
        val normalizedBase = baseDir.toAbsolutePath().normalize()
        Files.createDirectories(normalizedBase)
        val safePrefix = sanitizePrefix(prefix)
        while (true) {
            val candidate = normalizedBase.resolve("$safePrefix-${System.nanoTime()}").normalize()
            if (!candidate.startsWith(normalizedBase)) {
                throw IOException("Invalid zip staging directory")
            }
            try {
                return Files.createDirectory(candidate)
            } catch (_: FileAlreadyExistsException) {
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun extractZip(zipPath: Path?, targetRoot: Path?) {
        extractZip(zipPath, targetRoot, Predicate { true })
    }

    @JvmStatic
    @Throws(IOException::class)
    fun extractZip(zipPath: Path?, targetRoot: Path?, include: Predicate<String>) {
        if (zipPath == null || !Files.isRegularFile(zipPath)) {
            throw IOException("Archive is not a file")
        }
        if (targetRoot == null) {
            throw IOException("Target directory is not available")
        }
        val normalizedRoot = targetRoot.toAbsolutePath().normalize()
        Files.createDirectories(normalizedRoot)
        ZipFile(zipPath.toFile(), StandardCharsets.UTF_8).use { zip ->
            val entries = zip.entries().asSequence().filter { include.test(it.name.replace('\\', '/')) }.sortedBy { it.name }.toList()
            for (entry in entries) {
                extractEntry(zip, entry, normalizedRoot)
            }
        }
    }

    @JvmStatic
    fun deleteRecursive(path: Path?) {
        if (path == null || !Files.exists(path)) {
            return
        }
        try {
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach {
                    try {
                        Files.deleteIfExists(it)
                    } catch (_: IOException) {
                    }
                }
            }
        } catch (_: IOException) {
        }
    }

    private fun extractEntry(zip: ZipFile, entry: ZipEntry, targetRoot: Path) {
        val name = entry.name ?: return
        val normalizedName = name.replace('\\', '/')
        if (normalizedName.isBlank() ||
            normalizedName.startsWith("/") ||
            normalizedName.contains('\u0000') ||
            normalizedName.startsWith("__MACOSX/") ||
            normalizedName.startsWith("._") ||
            isDsStoreEntry(normalizedName)
        ) {
            return
        }
        val target = targetRoot.resolve(normalizedName).normalize()
        if (!target.startsWith(targetRoot)) {
            return
        }
        if (entry.isDirectory) {
            Files.createDirectories(target)
            return
        }
        Files.createDirectories(target.parent)
        zip.getInputStream(entry).use { input ->
            Files.newOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun sanitizePrefix(prefix: String?): String {
        val value = prefix?.trim().orEmpty()
        if (value.isBlank()) {
            return "archive"
        }
        val sanitized = value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (sanitized.isBlank()) "archive" else sanitized
    }

    private fun isDsStoreEntry(name: String): Boolean {
        return name == ".DS_Store" || name.endsWith("/.DS_Store")
    }
}
