package com.cozooo.dlc_manager.client.server

import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcUserPaths
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DlcServerList {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private const val STORE_FILE = "servers.dc"
    private const val PACKS_KEY = "packs"
    private const val ID_KEY = "id"
    private var entries: MutableList<DlcServerEntry> = ArrayList()
    private var lastSelectedKey: String? = null
    private var activeKey: String? = null

    @JvmStatic
    fun storePath(): Path = DlcUserPaths.configFile(STORE_FILE)

    @JvmStatic
    fun load() {
        entries = ArrayList()
        lastSelectedKey = null
        val key = identityKey()
        activeKey = key
        val data = findProfile(key)
        if (data != null) {
            apply(data)
            return
        }
        migrateLegacy()
    }

    @JvmStatic
    fun save() {
        activeKey = identityKey()
        writeCurrent()
    }

    private fun findProfile(key: String): JsonObject? {
        val packs = DlcDcConfigIO.getArrayIgnoreCase(
            DlcDcConfigIO.read(storePath(), DlcDcConfigIO.Layout.GENERIC), PACKS_KEY
        ) ?: return null
        for (element in packs) {
            if (element != null && element.isJsonObject) {
                val profile = element.asJsonObject
                if (key == DlcDcConfigIO.getStringIgnoreCase(profile, ID_KEY)) {
                    return profile
                }
            }
        }
        return null
    }

    private fun identityKey(): String {
        val modpack = DlcConfig.getModpack()
        if (!modpack.isNullOrBlank()) {
            return "pack:" + modpack.trim()
        }
        val root = DlcConfig.getDlcRoot()
        return "path:" + root.toAbsolutePath().normalize()
    }

    private fun migrateLegacy() {
        val legacyTmp = DlcPaths.tmpRoot().resolve("servers.dc")
        val legacyConfig = DlcPaths.managerConfigDir().resolve("servers.dc")
        val source = when {
            Files.exists(legacyTmp) -> legacyTmp
            Files.exists(legacyConfig) -> legacyConfig
            else -> return
        }
        val legacy = DlcDcConfigIO.read(source, DlcDcConfigIO.Layout.GENERIC) ?: return
        apply(legacy)
        if (writeCurrent()) {
            try {
                Files.deleteIfExists(source)
            } catch (_: IOException) {
            }
        }
    }

    private fun apply(data: JsonObject) {
        val parsed = gson.fromJson(data, DlcServerListData::class.java) ?: return
        entries = ArrayList()
        parsed.servers?.let { entries.addAll(it.filterNotNull()) }
        lastSelectedKey = parsed.lastSelected
    }

    private fun writeCurrent(): Boolean {
        val key = activeKey
        if (key.isNullOrBlank()) {
            return false
        }
        var root = DlcDcConfigIO.read(storePath(), DlcDcConfigIO.Layout.GENERIC)
        if (root == null) {
            root = JsonObject()
        }
        var packs = DlcDcConfigIO.getArrayIgnoreCase(root, PACKS_KEY)
        if (packs == null) {
            packs = JsonArray()
            root.add(PACKS_KEY, packs)
        }
        val profile = JsonObject()
        profile.addProperty(ID_KEY, key)
        val serialized = gson.toJsonTree(data()).asJsonObject
        for (entry in serialized.entrySet()) {
            profile.add(entry.key, entry.value)
        }
        var replaced = false
        for (i in 0 until packs.size()) {
            val element = packs[i]
            if (element != null && element.isJsonObject
                && key == DlcDcConfigIO.getStringIgnoreCase(element.asJsonObject, ID_KEY)
            ) {
                packs.set(i, profile)
                replaced = true
                break
            }
        }
        if (!replaced) {
            packs.add(profile)
        }
        return write(root)
    }

    private fun data(): DlcServerListData {
        val data = DlcServerListData()
        data.servers = entries
        data.lastSelected = lastSelectedKey
        return data
    }

    private fun write(root: JsonObject): Boolean {
        return try {
            storePath().parent?.let { Files.createDirectories(it) }
            DlcDcConfigIO.write(storePath(), root, DlcDcConfigIO.Layout.GENERIC)
        } catch (_: IOException) {
            false
        }
    }

    @JvmStatic
    fun getEntries(): List<DlcServerEntry> = entries.toList()

    @JvmStatic
    fun setEntries(newEntries: List<DlcServerEntry>?) {
        entries = ArrayList(newEntries?.filterNotNull() ?: emptyList())
    }

    @JvmStatic
    fun addEntry(entry: DlcServerEntry?) {
        if (entry == null) {
            return
        }
        entries.add(entry)
    }

    @JvmStatic
    fun removeEntry(key: String?) {
        if (key.isNullOrBlank()) {
            return
        }
        entries.removeIf { key == it.getKey() }
    }

    @JvmStatic
    fun getLastSelectedKey(): String? = lastSelectedKey

    @JvmStatic
    fun setLastSelectedKey(key: String?) {
        lastSelectedKey = key
    }

    private class DlcServerListData {
        var servers: List<DlcServerEntry?>? = null
        var lastSelected: String? = null
    }
}
