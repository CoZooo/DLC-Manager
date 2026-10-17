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

    private var stateById: MutableMap<String, DlcState> = HashMap()

    @JvmStatic
    fun load() {
        reloadFromDisk()
        migrateStoredPaths()
        migrateStateIds()
        normalizeEnabledStates()
        for (items in DlcScanner().scan(true).values) {
            for (item in items ?: emptyList()) {
                if (item == null) continue
                val state = DlcState()
                state.enabled = item.isEnabled()
                state.mode = item.getMatchMode()
                state.root = DlcResourceHistory.relativePath(item.getDlcRoot())
                state.appliedTargets = item.getAppliedTargets().map { DlcResourceHistory.relativePath(it) }
                item.getId()?.let { stateById[it] = state }
            }
        }
        save()
    }

    @JvmStatic
    fun reloadFromDisk() {
        stateById = HashMap()
        val legacy = DlcPlatformServices.configDir().resolve("dlc_manager/state.dc")
        val oldStates = readStates(legacy)
        for (root in DlcConfig.getDlcRoots()) {
            val path = root.resolve("tmp/state.dc")
            val loaded = if (Files.exists(path)) readStates(path) else oldStates
            for ((id, state) in loaded) if (belongsToRoot(id, state, root)) stateById[id] = state
        }
    }

    @JvmStatic
    fun save() {
        for (root in DlcConfig.getDlcRoots()) {
            val path = root.resolve("tmp/state.dc")
            try {
                Files.createDirectories(path.parent)
                val data = DlcStateConfigData()
                data.DLC = stateById.filter { (id, state) -> belongsToRoot(id, state, root) }
                DlcDcConfigIO.write(path, GSON.toJsonTree(data).asJsonObject, DlcDcConfigIO.Layout.GENERIC)
            } catch (_: IOException) {}
        }
    }

    private fun readStates(path: Path): Map<String, DlcState> {
        return try {
            val obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC) ?: return emptyMap()
            val loaded = selectStateMap(GSON.fromJson(obj, DlcStateConfigData::class.java))
            if (!loaded.isNullOrEmpty()) loaded else recoverFlattenedStateMap(obj)
        } catch (_: RuntimeException) { emptyMap() }
    }

    private fun belongsToRoot(id: String, state: DlcState?, root: Path): Boolean {
        val relative = DlcResourceHistory.relativePath(root)
        if (state?.root != null) return state.root == relative
        val colon = id.indexOf(':')
        val separator = id.indexOf('|')
        if (colon >= 0 && separator > colon) return id.substring(colon + 1, separator) == relative
        return DlcConfig.getDlcRoots().firstOrNull() == root
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

        @JvmField var root: String? = null
        @JvmField var mode: String = "hash"

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
            if (state.root != null) {
                val storedRoot = DlcPaths.parsePathLenient(state.root)
                if (storedRoot != null && storedRoot.isAbsolute) {
                    state.root = DlcResourceHistory.relativePath(storedRoot)
                    changed = true
                }
            }
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
                        migrated.add(DlcResourceHistory.relativePath(normalized))
                        localChanged = true
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
            val rootPath = DlcPaths.parsePathLenient(rootPart) ?: return key
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
