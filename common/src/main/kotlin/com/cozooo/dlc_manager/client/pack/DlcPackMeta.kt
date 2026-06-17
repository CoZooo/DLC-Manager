package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcPathUtil
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

class DlcPackMeta(
    @JvmField var identifier: String,
    @JvmField var version: String,
    @JvmField var format: String,
    @JvmField var timestamp: String,
    installedPaths: Collection<String>?,
) {
    @JvmField val installedPaths: MutableList<String> = ArrayList()

    init {
        addInstalledPaths(installedPaths)
    }

    fun addInstalledPath(path: String?) {
        val normalized = normalizeRelativePath(path) ?: return
        if (!installedPaths.contains(normalized)) {
            installedPaths.add(normalized)
        }
    }

    fun addInstalledPaths(paths: Collection<String>?) {
        paths?.forEach { addInstalledPath(it) }
    }

    fun save(path: Path?): Boolean {
        if (path == null) {
            return false
        }
        val obj = JsonObject()
        val basic = JsonObject()
        basic.addProperty("identifier", identifier)
        basic.addProperty("version", version)
        basic.addProperty("format", format)
        basic.addProperty("timestamp", timestamp)
        obj.add("basic", basic)
        val files = JsonArray()
        for (installed in installedPaths) {
            files.add(installed)
        }
        obj.add("installed", files)
        return DlcPackDcFiles.writeAtomic(path, obj)
    }

    companion object {
        @JvmStatic
        fun load(path: Path?): DlcPackMeta? {
            if (path == null || !Files.exists(path)) {
                return null
            }
            val obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC) ?: return null
            val basic = DlcDcConfigIO.getObjectIgnoreCase(obj, "basic")
            val identifier = DlcDcConfigIO.getStringIgnoreCase(basic, "identifier")?.trim()
            val version = DlcDcConfigIO.getStringIgnoreCase(basic, "version")?.trim()
            if (!DlcPackMetadata.isValidIdentifier(identifier) || !DlcPackMetadata.isValidVersion(version)) {
                return null
            }
            val format = DlcDcConfigIO.getStringIgnoreCase(basic, "format")?.trim().orEmpty()
            val timestamp = DlcDcConfigIO.getStringIgnoreCase(basic, "timestamp")?.trim().orEmpty()
            val installed = ArrayList<String>()
            val array = DlcDcConfigIO.getArrayIgnoreCase(obj, "installed")
            array?.forEach { element ->
                if (element != null && element.isJsonPrimitive) {
                    normalizeRelativePath(element.asString)?.let { installed.add(it) }
                }
            }
            return DlcPackMeta(identifier!!, version!!, format, timestamp, installed)
        }

        @JvmStatic
        fun normalizeRelativePath(path: String?): String? {
            return DlcPathUtil.normalizeRelativePath(path)
        }
    }
}
