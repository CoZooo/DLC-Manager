package com.cozooo.dlc_manager.client.preset

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.JsonElement
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap

object DlcPresetStateStore {
    private const val STATE_BASE_NAME = "state"
    private const val PRESETS_KEY = "presets"

    @JvmStatic
    fun getStatePath(): Path? {
        val configDir = DlcPlatformServices.configDir().resolve("dlc_manager")
        return DlcDcConfigIO.toDcPath(configDir.resolve(STATE_BASE_NAME))
    }

    @JvmStatic
    fun loadPresetStatus(): MutableMap<String, DlcPresetStatus> {
        val result = LinkedHashMap<String, DlcPresetStatus>()
        val path = getStatePath()
        if (path == null || !Files.exists(path)) {
            return result
        }
        val root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC) ?: return result
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
        val path = getStatePath() ?: return
        try {
            Files.createDirectories(path.parent)
        } catch (_: IOException) {
            return
        }
        val root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC) ?: com.google.gson.JsonObject()
        val presets = com.google.gson.JsonObject()
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
}
