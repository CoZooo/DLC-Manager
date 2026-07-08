package com.cozooo.dlc_manager.client.remote

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DlcRemoteMarker {
    const val SKIP_MARKER: String = "SKIP"
    const val PART_DLC: String = "dlc"
    const val PART_DLC_PACK: String = "dlcpack"

    @JvmStatic
    fun hasPartMarker(dlcRoot: Path?): Boolean {
        return hasPartMarker(dlcRoot, PART_DLC)
    }

    @JvmStatic
    fun hasPartMarker(dlcRoot: Path?, key: String?): Boolean {
        if (dlcRoot == null) {
            return false
        }
        val marker = resolveRemoteRoot(dlcRoot).resolve(SKIP_MARKER)
        if (!Files.exists(marker)) {
            return false
        }
        val normalized = normalizeKey(key) ?: return false
        return readPartKeys(marker).contains(normalized)
    }

    @JvmStatic
    fun writePartMarker(dlcRoot: Path?) {
        writePartMarker(dlcRoot, PART_DLC)
    }

    @JvmStatic
    fun writePartMarker(dlcRoot: Path?, key: String?) {
        if (dlcRoot == null) {
            return
        }
        val normalized = normalizeKey(key) ?: return
        val remoteRoot = resolveRemoteRoot(dlcRoot)
        try {
            Files.createDirectories(remoteRoot)
            val marker = remoteRoot.resolve(SKIP_MARKER)
            val keys = readPartKeys(marker)
            keys.add(normalized)
            writePartKeys(marker, keys)
        } catch (_: IOException) {
        }
    }

    @JvmStatic
    fun clearPartMarker(dlcRoot: Path?) {
        if (dlcRoot == null) {
            return
        }
        val marker = resolveRemoteRoot(dlcRoot).resolve(SKIP_MARKER)
        try {
            Files.deleteIfExists(marker)
        } catch (_: IOException) {
        }
    }

    @JvmStatic
    fun clearPartMarker(dlcRoot: Path?, key: String?) {
        if (dlcRoot == null) {
            return
        }
        val normalized = normalizeKey(key) ?: return
        val marker = resolveRemoteRoot(dlcRoot).resolve(SKIP_MARKER)
        try {
            val keys = readPartKeys(marker)
            keys.remove(normalized)
            if (keys.isEmpty()) {
                Files.deleteIfExists(marker)
            } else {
                writePartKeys(marker, keys)
            }
        } catch (_: IOException) {
        }
    }

    @JvmStatic
    fun resolveRemoteRoot(dlcRoot: Path): Path {
        return dlcRoot.resolve("remote")
    }

    private fun normalizeKey(key: String?): String? {
        val normalized = key?.trim()?.lowercase()
        if (normalized.isNullOrBlank()) {
            return null
        }
        return normalized
    }

    private fun readPartKeys(marker: Path): MutableSet<String> {
        if (!Files.exists(marker)) {
            return LinkedHashSet()
        }
        return try {
            val lines = Files.readAllLines(marker)
            val keys = LinkedHashSet<String>()
            for (line in lines) {
                val normalized = normalizeKey(line)
                if (normalized != null) {
                    keys.add(normalized)
                }
            }
            keys
        } catch (_: IOException) {
            LinkedHashSet()
        }
    }

    @Throws(IOException::class)
    private fun writePartKeys(marker: Path, keys: Set<String>) {
        Files.createDirectories(marker.parent)
        Files.write(marker, keys.sorted().map { it })
    }
}
