package com.cozooo.dlc_manager.client.remote

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DlcRemoteMarker {
    const val PART_MARKER: String = "PART"

    @JvmStatic
    fun hasPartMarker(dlcRoot: Path?): Boolean {
        if (dlcRoot == null) {
            return false
        }
        val marker = resolveRemoteRoot(dlcRoot).resolve(PART_MARKER)
        return Files.exists(marker)
    }

    @JvmStatic
    fun writePartMarker(dlcRoot: Path?) {
        if (dlcRoot == null) {
            return
        }
        val remoteRoot = resolveRemoteRoot(dlcRoot)
        try {
            Files.createDirectories(remoteRoot)
            val marker = remoteRoot.resolve(PART_MARKER)
            if (!Files.exists(marker)) {
                Files.createFile(marker)
            }
        } catch (_: IOException) {
        }
    }

    @JvmStatic
    fun clearPartMarker(dlcRoot: Path?) {
        if (dlcRoot == null) {
            return
        }
        val marker = resolveRemoteRoot(dlcRoot).resolve(PART_MARKER)
        try {
            Files.deleteIfExists(marker)
        } catch (_: IOException) {
        }
    }

    @JvmStatic
    fun resolveRemoteRoot(dlcRoot: Path): Path {
        return dlcRoot.resolve("remote")
    }
}
