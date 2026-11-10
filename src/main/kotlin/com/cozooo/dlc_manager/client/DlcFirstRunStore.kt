package com.cozooo.dlc_manager.client

import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcUserPaths
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DlcFirstRunStore {
    private const val STORE_FILE = "first-run.dc"
    private const val LEGACY_MARKER = "ShownOnFirstRun"
    private const val SHOWN_KEY = "shown"

    @JvmStatic
    fun defaultStorePath(): Path = DlcUserPaths.stateFile(STORE_FILE)

    @JvmStatic
    fun wasShown(dlcRoot: Path?): Boolean = wasShown(dlcRoot, defaultStorePath())

    @JvmStatic
    fun wasShown(dlcRoot: Path?, storePath: Path?): Boolean {
        if (dlcRoot == null || storePath == null) return false
        val key = keyFor(dlcRoot)
        if (clearLegacyMarker(dlcRoot)) {
            markShown(dlcRoot, storePath)
            return true
        }
        return readKeys(storePath).contains(key)
    }

    @JvmStatic
    fun markShown(dlcRoot: Path?): Boolean = markShown(dlcRoot, defaultStorePath())

    @JvmStatic
    fun markShown(dlcRoot: Path?, storePath: Path?): Boolean {
        if (dlcRoot == null || storePath == null) return false
        clearLegacyMarker(dlcRoot)
        val key = keyFor(dlcRoot)
        val keys = readKeys(storePath)
        if (!keys.add(key)) return true
        val objectValue = JsonObject()
        val shown = JsonArray()
        for (existing in keys) {
            shown.add(existing)
        }
        objectValue.add(SHOWN_KEY, shown)
        return try {
            storePath.parent?.let { Files.createDirectories(it) }
            DlcDcConfigIO.write(storePath, objectValue, DlcDcConfigIO.Layout.GENERIC)
        } catch (_: IOException) {
            false
        }
    }

    @JvmStatic
    fun keyFor(dlcRoot: Path): String {
        val modpack = DlcConfig.getModpack()
        if (modpack.isNullOrBlank()) {
            return "path:" + dlcRoot.toAbsolutePath().normalize()
        }
        return "pack:" + modpack.trim() + "@" + installedVersion(dlcRoot)
    }

    private fun installedVersion(dlcRoot: Path): String {
        val path = DlcPaths.resolveVersionConfigPath(dlcRoot)
        val objectValue = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC)
        var version = DlcDcConfigIO.getStringIgnoreCase(objectValue, "version")
        if (version.isNullOrBlank()) {
            version = DlcDcConfigIO.getStringIgnoreCase(objectValue, "base")
        }
        return version?.trim().orEmpty()
    }

    private fun readKeys(storePath: Path): LinkedHashSet<String> {
        val keys = LinkedHashSet<String>()
        val objectValue = DlcDcConfigIO.read(storePath, DlcDcConfigIO.Layout.GENERIC)
        val shown = DlcDcConfigIO.getArrayIgnoreCase(objectValue, SHOWN_KEY) ?: return keys
        for (element in shown) {
            if (element != null && element.isJsonPrimitive) {
                val value = element.asString
                if (value.isNotBlank()) keys.add(value)
            }
        }
        return keys
    }

    private fun clearLegacyMarker(dlcRoot: Path): Boolean {
        return try {
            Files.deleteIfExists(dlcRoot.resolve(LEGACY_MARKER))
        } catch (_: IOException) {
            false
        }
    }
}
