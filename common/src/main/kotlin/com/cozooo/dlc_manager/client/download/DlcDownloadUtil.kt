package com.cozooo.dlc_manager.client.download

import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64

object DlcDownloadUtil {
    @JvmStatic
    fun extractFileNameFromUrl(url: String?): String? {
        return try {
            val path = URI.create(url).rawPath
            val idx = path.lastIndexOf('/')
            if (idx >= 0 && idx + 1 < path.length) {
                val name = decodeUrlPathSegment(path.substring(idx + 1))
                sanitizeFileName(name)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun sanitizeFileName(name: String?): String? {
        val cleaned = stripQuery(name)
        return if (isLikelyFileName(cleaned)) cleaned else null
    }

    @JvmStatic
    fun isLikelyFileName(name: String?): Boolean {
        if (name == null) {
            return false
        }
        val trimmed = name.trim()
        if (trimmed.contains('/') || trimmed.contains('\\')) {
            return false
        }
        val dot = trimmed.lastIndexOf('.')
        if (dot <= 0 || dot >= trimmed.length - 1) {
            return false
        }
        val ext = trimmed.substring(dot + 1)
        return ext.length <= 10
    }

    @JvmStatic
    fun stripQuery(name: String?): String? {
        if (name == null) {
            return null
        }
        var cleaned = name
        val query = cleaned.indexOf('?')
        if (query >= 0) {
            cleaned = cleaned.substring(0, query)
        }
        val hash = cleaned.indexOf('#')
        if (hash >= 0) {
            cleaned = cleaned.substring(0, hash)
        }
        return cleaned
    }

    @JvmStatic
    fun decodeUrlPathSegment(segment: String?): String? {
        if (segment.isNullOrBlank()) {
            return segment
        }
        val out = java.io.ByteArrayOutputStream(segment.length)
        var i = 0
        while (i < segment.length) {
            val c = segment[i]
            if (c == '%' && i + 2 < segment.length) {
                val hi = hexValue(segment[i + 1])
                val lo = hexValue(segment[i + 2])
                if (hi >= 0 && lo >= 0) {
                    out.write((hi shl 4) + lo)
                    i += 3
                    continue
                }
            }
            val bytes = c.toString().toByteArray(Charsets.UTF_8)
            out.write(bytes, 0, bytes.size)
            i++
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    private fun hexValue(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> 10 + (c - 'a')
            in 'A'..'F' -> 10 + (c - 'A')
            else -> -1
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun computeHashHex(file: Path?, algorithm: String?): String? {
        val hash = computeHashBytes(file, algorithm) ?: return null
        val builder = StringBuilder(hash.size * 2)
        for (byte in hash) {
            builder.append(String.format("%02x", byte))
        }
        return builder.toString()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun computeMd5Base64(file: Path?): String? {
        val hash = computeHashBytes(file, "MD5") ?: return null
        return Base64.getEncoder().encodeToString(hash)
    }

    @Throws(IOException::class)
    private fun computeHashBytes(file: Path?, algorithm: String?): ByteArray? {
        if (file == null || algorithm.isNullOrBlank()) {
            return null
        }
        val digest = try {
            MessageDigest.getInstance(algorithm)
        } catch (_: NoSuchAlgorithmException) {
            return null
        }
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    @JvmStatic
    fun normalizeHash(value: String?): String? {
        return value?.trim()?.replace("\"", "")
    }
}
