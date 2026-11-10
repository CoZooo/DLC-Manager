package com.cozooo.dlc_manager.client.preset

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap

object DlcPresetStateStore {
    private const val PRESETS_KEY = "presets"

    @JvmStatic
    fun getStatePath(): Path {
        return DlcConfig.getDlcRoot().resolve("tmp/state.dc")
    }

    @JvmStatic
    fun loadPresetStatus(): MutableMap<String, DlcPresetStatus> {
        val result = LinkedHashMap<String, DlcPresetStatus>()
        var root = readStateFile(getStatePath())
        if (root == null || DlcDcConfigIO.getObjectIgnoreCase(root, PRESETS_KEY) == null) {
            root = readStateFile(legacyStatePath())
        }
        if (root == null) {
            return result
        }
        val presets = DlcDcConfigIO.getObjectIgnoreCase(root, PRESETS_KEY) ?: return result
        for ((key, value) in presets.entrySet()) {
            if (key == null || value == null || value.isJsonNull) {
                continue
            }
            try {
                val raw = value.asString
                val status = DlcPresetStatus.fromString(raw)
                result[key] = status
            } catch (_: Exception) {
            }
        }
        return result
    }

    @JvmStatic
    fun savePresetStatus(statuses: Map<String, DlcPresetStatus?>?) {
        val safeStatuses = statuses ?: emptyMap()
        val path = getStatePath()
        try {
            Files.createDirectories(path.parent)
        } catch (_: IOException) {
            return
        }
        var root = readStateFile(path)
        if (root == null) {
            root = readStateFile(legacyStatePath())
        }
        if (root == null) {
            root = JsonObject()
        }
        val presets = JsonObject()
        for ((key, value) in safeStatuses) {
            if (key.isBlank() || value == null) {
                continue
            }
            presets.addProperty(key, value.toConfigValue())
        }
        DlcDcConfigIO.removeIgnoreCase(root, PRESETS_KEY)
        root.add(PRESETS_KEY, presets)
        DlcDcConfigIO.write(path, root, DlcDcConfigIO.Layout.GENERIC)
    }

    private fun readStateFile(path: Path?): JsonObject? {
        return if (path != null && Files.exists(path)) DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC) else null
    }

    private fun legacyStatePath(): Path {
        return DlcPlatformServices.configDir().resolve("dlc_manager/state.dc")
    }
}
