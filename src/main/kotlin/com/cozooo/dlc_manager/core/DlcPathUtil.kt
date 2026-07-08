package com.cozooo.dlc_manager.core

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DlcPathUtil {
    @JvmStatic
    fun hasWildcard(pattern: String?): Boolean {
        val normalized = normalizePattern(pattern) ?: return false
        return normalized.split('/').any { it.contains('*') }
    }

    @JvmStatic
    fun normalizePattern(pattern: String?): String? {
        return normalizeRelativePath(pattern)
    }

    @JvmStatic
    fun normalizeRelativePath(path: String?): String? {
        if (path.isNullOrBlank()) {
            return null
        }
        val raw = path.replace('\\', '/').trim()
        val value = raw.trim('/')
        if (value.isBlank() ||
            value.startsWith("../") ||
            value == ".." ||
            value.contains("/../") ||
            value.endsWith("/..") ||
            value.contains("//")
        ) {
            return null
        }
        return value
    }

    @JvmStatic
    fun matches(pattern: String?, relativePath: String?): Boolean {
        val normalizedPattern = normalizePattern(pattern) ?: return false
        val normalizedPath = normalizePattern(relativePath) ?: return false
        val patternParts = normalizedPattern.split('/')
        val pathParts = normalizedPath.split('/')
        if (patternParts.size != pathParts.size) {
            return false
        }
        for (index in patternParts.indices) {
            if (!matchesSegment(patternParts[index], pathParts[index])) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun hasFileNameWildcard(pattern: String?): Boolean {
        return normalizeFileNamePattern(pattern)?.contains('*') == true
    }

    @JvmStatic
    fun matchesFileName(pattern: String?, fileName: String?): Boolean {
        val normalizedPattern = normalizeFileNamePattern(pattern) ?: return false
        val normalizedFileName = normalizeFileNamePattern(fileName) ?: return false
        if (!normalizedPattern.contains('*')) {
            return normalizedPattern == normalizedFileName
        }
        val regex = StringBuilder(normalizedPattern.length + 8)
        for (ch in normalizedPattern) {
            if (ch == '*') {
                regex.append("[^/]*")
            } else {
                regex.append(Regex.escape(ch.toString()))
            }
        }
        return Regex(regex.toString()).matches(normalizedFileName)
    }

    @JvmStatic
    fun findMatches(root: Path?, pattern: String?): List<Path> {
        if (root == null) {
            return emptyList()
        }
        val normalizedPattern = normalizePattern(pattern) ?: return emptyList()
        val normalizedRoot = root.toAbsolutePath().normalize()
        if (!Files.exists(normalizedRoot)) {
            return emptyList()
        }
        if (!hasWildcard(normalizedPattern)) {
            val exact = normalizedRoot.resolve(normalizedPattern).normalize()
            return if (exact.startsWith(normalizedRoot) && Files.exists(exact)) listOf(exact) else emptyList()
        }
        val result = ArrayList<Path>()
        try {
            Files.walk(normalizedRoot, normalizedPattern.split('/').size).use { stream ->
                stream.filter { it != normalizedRoot }.forEach { path ->
                    val relative = normalizeRelative(normalizedRoot.relativize(path))
                    if (relative != null && matches(normalizedPattern, relative)) {
                        result.add(path)
                    }
                }
            }
        } catch (_: IOException) {
            return emptyList()
        }
        result.sortBy { normalizeRelative(normalizedRoot.relativize(it)) }
        return result.toList()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun resolveInside(root: Path?, relative: String?): Path {
        val normalizedRoot = root?.toAbsolutePath()?.normalize() ?: throw IOException("Root directory is not available")
        val normalizedRelative = normalizeRelativePath(relative) ?: throw IOException("Invalid relative path: $relative")
        val target = normalizedRoot.resolve(normalizedRelative).normalize()
        if (!target.startsWith(normalizedRoot)) {
            throw IOException("Path escapes root: $relative")
        }
        return target
    }

    @JvmStatic
    fun toGameRelativePath(path: Path?): String {
        return toRelativePath(DlcPaths.gameDir(), path)
    }

    @JvmStatic
    fun toRelativePath(root: Path?, path: Path?): String {
        if (path == null) {
            return ""
        }
        val normalized = path.toAbsolutePath().normalize()
        if (root == null) {
            return normalized.toString().replace('\\', '/')
        }
        val normalizedRoot = root.toAbsolutePath().normalize()
        try {
            val rootReal = normalizedRoot.toRealPath()
            val pathReal = normalized.toRealPath()
            if (pathReal.startsWith(rootReal)) {
                return rootReal.relativize(pathReal).toString().replace('\\', '/')
            }
        } catch (_: Exception) {
        }
        try {
            if (normalized.startsWith(normalizedRoot)) {
                return normalizedRoot.relativize(normalized).toString().replace('\\', '/')
            }
        } catch (_: Exception) {
        }
        return normalized.toString().replace('\\', '/')
    }

    private fun normalizeRelative(path: Path?): String? {
        return if (path == null) null else normalizePattern(path.toString())
    }

    private fun normalizeFileNamePattern(value: String?): String? {
        return normalizeRelativePath(value)
    }

    private fun matchesSegment(pattern: String, value: String): Boolean {
        if (pattern == value) {
            return true
        }
        if (!pattern.contains('*')) {
            return false
        }
        val regex = StringBuilder(pattern.length + 8)
        for (ch in pattern) {
            if (ch == '*') {
                regex.append(".*")
            } else {
                regex.append(Regex.escape(ch.toString()))
            }
        }
        return Regex("^$regex$").matches(value)
    }
}
