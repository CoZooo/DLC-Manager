package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object DlcPackOrder {
    @JvmStatic
    fun load(path: Path?): List<String> {
        if (path == null || !Files.exists(path)) {
            return emptyList()
        }
        val out = ArrayList<String>()
        try {
            Files.readAllLines(path).forEach { line ->
                normalize(line)?.let { value ->
                    if (!out.contains(value)) {
                        out.add(value)
                    }
                }
            }
        } catch (_: IOException) {
        }
        return out
    }

    @JvmStatic
    fun save(path: Path?, order: Collection<String>?): Boolean {
        if (path == null) {
            return false
        }
        val lines = ArrayList<String>()
        order?.forEach { key ->
            normalize(key)?.let { lines.add(it) }
        }
        return writeAtomicLines(path, lines)
    }

    @JvmStatic
    fun normalizeOrder(savedOrder: Collection<String>?, currentKeys: Collection<String>?): List<String> {
        val out = ArrayList<String>()
        if (savedOrder != null) {
            for (key in savedOrder) {
                val value = normalize(key) ?: continue
                if (currentKeys != null && currentKeys.contains(value) && !out.contains(value)) {
                    out.add(value)
                }
            }
        }
        if (currentKeys != null) {
            for (key in currentKeys) {
                val value = normalize(key) ?: continue
                if (!out.contains(value)) {
                    out.add(value)
                }
            }
        }
        return out
    }

    private fun normalize(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }
        val normalized = value.trim().replace('\\', '/').trim('/')
        if (normalized.isBlank() || normalized.startsWith("../") || normalized == ".." || normalized.contains("/../")) {
            return null
        }
        return normalized
    }

    private fun writeAtomicLines(path: Path, lines: List<String>): Boolean {
        return try {
            val target = DlcDcConfigIO.toDcPath(path) ?: return false
            val parent = target.parent
            val temp = if (parent != null) {
                Files.createDirectories(parent)
                Files.createTempFile(parent, target.fileName.toString() + ".", ".tmp")
            } else {
                Files.createTempFile(target.fileName.toString() + ".", ".tmp")
            }
            try {
                Files.write(temp, lines)
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: IOException) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                }
                if (target != path) {
                    Files.deleteIfExists(path)
                }
                true
            } finally {
                Files.deleteIfExists(temp)
            }
        } catch (_: IOException) {
            false
        }
    }
}
