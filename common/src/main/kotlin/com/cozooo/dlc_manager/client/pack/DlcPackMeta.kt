package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.core.DcConfigIO
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
            val obj = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC) ?: return null
            val basic = DcConfigIO.getObjectIgnoreCase(obj, "basic")
            val identifier = DcConfigIO.getStringIgnoreCase(basic, "identifier")?.trim()
            val version = DcConfigIO.getStringIgnoreCase(basic, "version")?.trim()
            if (!DlcPackMetadata.isValidIdentifier(identifier) || !DlcPackMetadata.isValidVersion(version)) {
                return null
            }
            val format = DcConfigIO.getStringIgnoreCase(basic, "format")?.trim().orEmpty()
            val timestamp = DcConfigIO.getStringIgnoreCase(basic, "timestamp")?.trim().orEmpty()
            val installed = ArrayList<String>()
            val array = DcConfigIO.getArrayIgnoreCase(obj, "installed")
            array?.forEach { element ->
                if (element != null && element.isJsonPrimitive) {
                    normalizeRelativePath(element.asString)?.let { installed.add(it) }
                }
            }
            return DlcPackMeta(identifier!!, version!!, format, timestamp, installed)
        }

        @JvmStatic
        fun normalizeRelativePath(path: String?): String? {
            if (path.isNullOrBlank()) {
                return null
            }
            val value = path.replace('\\', '/').trim().trim('/')
            if (value.isBlank() || value.startsWith("../") || value == ".." || value.contains("/../")) {
                return null
            }
            return value
        }
    }
}
