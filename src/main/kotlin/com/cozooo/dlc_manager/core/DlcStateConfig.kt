package com.cozooo.dlc_manager.core

import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

object DlcStateConfig {
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private var configPath: Path? = null
    private var stateById: MutableMap<String, DlcState> = HashMap()

    @JvmStatic
    fun load() {
        val configDir = DlcPlatformServices.configDir().resolve("dlc_manager")
        configPath = DlcDcFormatUtil.resolveConfigPath(configDir, "state")
        stateById = HashMap()
        var migrateLegacyStateSection = false
        val path = configPath
        if (path != null && Files.exists(path)) {
            try {
                val root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC)
                if (root != null) {
                    val data = GSON.fromJson(root, DlcStateConfigData::class.java)
                    if (data != null && data.DLC == null && data.stateById != null) {
                        migrateLegacyStateSection = true
                    }
                    val loaded = selectStateMap(data)
                    if (!loaded.isNullOrEmpty()) {
                        stateById = HashMap(loaded)
                    } else {
                        val recovered = recoverFlattenedStateMap(root)
                        stateById = when {
                            recovered.isNotEmpty() -> recovered
                            loaded != null -> HashMap(loaded)
                            else -> stateById
                        }
                    }
                }
            } catch (_: Exception) {
                stateById = HashMap()
            }
        }
        var migrated = migrateLegacyStateSection
        migrated = migrateStoredPaths() || migrated
        migrated = migrateStateIds() || migrated
        migrated = normalizeEnabledStates() || migrated
        if (migrated) {
            save()
        }
    }

    @JvmStatic
    fun save() {
        val path = configPath ?: return
        try {
            Files.createDirectories(path.parent)
            val data = DlcStateConfigData()
            data.DLC = stateById
            val root = GSON.toJsonTree(data).asJsonObject
            DlcDcConfigIO.write(path, root, DlcDcConfigIO.Layout.GENERIC)
        } catch (_: IOException) {
        }
    }

    @JvmStatic
    fun getState(id: String?): DlcState? {
        return stateById[id]
    }

    @JvmStatic
    fun setState(id: String?, state: DlcState?) {
        if (id == null || state == null) {
            return
        }
        stateById[id] = state
    }

    @JvmStatic
    fun getAllStates(): Map<String, DlcState> {
        return Collections.unmodifiableMap(stateById)
    }

    class DlcState {
        @JvmField
        var enabled: Boolean = false

        @JvmField
        var appliedTargets: List<String>? = null
    }

    private class DlcStateConfigData {
        @JvmField
        var DLC: Map<String, DlcState>? = null

        @JvmField
        var stateById: Map<String, DlcState>? = null
    }

    private fun selectStateMap(data: DlcStateConfigData?): Map<String, DlcState>? {
        if (data == null) {
            return null
        }
        return data.DLC ?: data.stateById
    }

    private fun migrateStoredPaths(): Boolean {
        if (stateById.isEmpty()) {
            return false
        }
        val gameDir = DlcPaths.gameDir().toAbsolutePath().normalize()
        val gameReal = try {
            gameDir.toRealPath()
        } catch (_: Exception) {
            null
        }
        var changed = false
        for (state in stateById.values) {
            val applied = state.appliedTargets
            if (applied.isNullOrEmpty()) {
                continue
            }
            val migrated = ArrayList<String>()
            var localChanged = false
            for (raw in applied) {
                if (raw.isBlank()) {
                    continue
                }
                val path = DlcPaths.parsePathLenient(raw)
                if (path == null) {
                    localChanged = true
                    continue
                }
                if (path.isAbsolute) {
                    val normalized = path.toAbsolutePath().normalize()
                    var converted = false
                    if (gameReal != null) {
                        try {
                            val pathReal = normalized.toRealPath()
                            if (pathReal.startsWith(gameReal)) {
                                migrated.add(gameReal.relativize(pathReal).toString())
                                localChanged = true
                                converted = true
                            }
                        } catch (_: Exception) {
                        }
                    }
                    if (!converted && normalized.startsWith(gameDir)) {
                        migrated.add(gameDir.relativize(normalized).toString())
                        localChanged = true
                        converted = true
                    }
                    if (!converted) {
                        migrated.add(normalized.toString())
                    }
                } else {
                    migrated.add(path.toString())
                    if (raw != path.toString()) {
                        localChanged = true
                    }
                }
            }
            if (localChanged || migrated.size != applied.size) {
                state.appliedTargets = migrated
                changed = true
            }
        }
        return changed
    }

    private fun migrateStateIds(): Boolean {
        if (stateById.isEmpty()) {
            return false
        }
        val gameDir = DlcPaths.gameDir().toAbsolutePath().normalize()
        val gameReal = try {
            gameDir.toRealPath()
        } catch (_: Exception) {
            null
        }
        var changed = false
        val migrated = HashMap<String, DlcState>()
        for ((key, state) in stateById) {
            val newKey = migrateIdKey(key, gameDir, gameReal)
            if (newKey != key) {
                changed = true
            }
            val existing = migrated[newKey]
            if (existing == null) {
                migrated[newKey] = state
            } else if (state.enabled && !existing.enabled) {
                migrated[newKey] = state
            }
        }
        if (changed) {
            stateById = migrated
        }
        return changed
    }

    private fun normalizeEnabledStates(): Boolean {
        if (stateById.isEmpty()) {
            return false
        }
        var changed = false
        for (state in stateById.values) {
            if (!state.enabled) {
                continue
            }
            val applied = state.appliedTargets
            if (applied.isNullOrEmpty()) {
                state.enabled = false
                changed = true
                continue
            }
            var allExist = true
            for (raw in applied) {
                if (raw.isBlank()) {
                    allExist = false
                    break
                }
                val path = resolveStoredPath(raw)
                if (!Files.exists(path)) {
                    allExist = false
                    break
                }
            }
            if (!allExist) {
                state.enabled = false
                changed = true
            }
        }
        return changed
    }

    private fun resolveStoredPath(raw: String?): Path {
        if (raw == null) {
            return DlcPaths.gameDir()
        }
        val path = DlcPaths.parsePathLenient(raw)
        if (path == null) {
            return DlcPaths.gameDir().resolve("__invalid_path__")
        }
        return if (path.isAbsolute) path else DlcPaths.gameDir().resolve(path)
    }

    private fun migrateIdKey(key: String?, gameDir: Path, gameReal: Path?): String {
        if (key == null) {
            return ""
        }
        val colon = key.indexOf(':')
        val pipe = key.lastIndexOf('|')
        if (colon < 0 || pipe < 0 || pipe <= colon) {
            return key
        }
        val prefix = key.substring(0, colon + 1)
        val rootPart = key.substring(colon + 1, pipe)
        val suffix = key.substring(pipe)
        if (rootPart.isBlank()) {
            return key
        }
        try {
            val rootPath = Path.of(rootPart)
            if (!rootPath.isAbsolute) {
                return key
            }
            val normalized = rootPath.toAbsolutePath().normalize()
            if (gameReal != null) {
                try {
                    val rootReal = normalized.toRealPath()
                    if (rootReal.startsWith(gameReal)) {
                        val relative = gameReal.relativize(rootReal).toString()
                        return prefix + relative + suffix
                    }
                } catch (_: Exception) {
                }
            }
            if (normalized.startsWith(gameDir)) {
                val relative = gameDir.relativize(normalized).toString()
                return prefix + relative + suffix
            }
        } catch (_: Exception) {
        }
        return key
    }

    private fun recoverFlattenedStateMap(root: JsonObject?): MutableMap<String, DlcState> {
        val recovered = HashMap<String, DlcState>()
        if (root == null || root.entrySet().isEmpty()) {
            return recovered
        }
        for (entry in root.entrySet()) {
            val key = entry.key
            val value: JsonElement = entry.value
            if (key.isBlank() || key.equals("DLC", ignoreCase = true) || key.equals("stateById", ignoreCase = true)) {
                continue
            }
            if (!value.isJsonObject) {
                continue
            }
            val obj = value.asJsonObject
            if (!obj.has("enabled") && !obj.has("appliedTargets")) {
                continue
            }
            try {
                val state = GSON.fromJson(obj, DlcState::class.java)
                if (state != null) {
                    recovered[key] = state
                }
            } catch (_: Exception) {
            }
        }
        return recovered
    }
}
