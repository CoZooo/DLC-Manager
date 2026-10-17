package com.cozooo.dlc_manager.client.server

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.core.DlcPaths
import com.google.gson.GsonBuilder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DlcServerList {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var configPath: Path? = null
    private var entries: MutableList<DlcServerEntry> = ArrayList()
    private var lastSelectedKey: String? = null

    @JvmStatic
    fun load() {
        configPath = DlcPaths.migrateLegacyConfigFile("servers.dc")
        entries = ArrayList()
        lastSelectedKey = null
        val path = configPath ?: return
        if (!Files.exists(path)) {
            return
        }
        try {
            val root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC)
            if (root != null) {
                val data = gson.fromJson(root, DlcServerListData::class.java)
                val servers = data?.servers
                if (servers != null) {
                    entries.addAll(servers.filterNotNull())
                    lastSelectedKey = data.lastSelected
                }
            }
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun save() {
        if (configPath == null) {
            configPath = DlcPaths.migrateLegacyConfigFile("servers.dc")
        }
        val path = configPath ?: return
        try {
            Files.createDirectories(path.parent)
            val data = DlcServerListData()
            data.servers = entries
            data.lastSelected = lastSelectedKey
            val root = gson.toJsonTree(data).asJsonObject
            DlcDcConfigIO.write(path, root, DlcDcConfigIO.Layout.GENERIC)
        } catch (_: IOException) {
        }
    }

    @JvmStatic
    fun getEntries(): List<DlcServerEntry> {
        return entries.toList()
    }

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
    fun getLastSelectedKey(): String? {
        return lastSelectedKey
    }

    @JvmStatic
    fun setLastSelectedKey(key: String?) {
        lastSelectedKey = key
    }

    private class DlcServerListData {
        var servers: List<DlcServerEntry?>? = null
        var lastSelected: String? = null
    }
}
