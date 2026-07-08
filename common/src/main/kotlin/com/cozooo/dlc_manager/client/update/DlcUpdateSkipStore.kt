package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

object DlcUpdateSkipStore {
    const val SECTION_SELF_UPDATE: String = "self_update"
    const val SECTION_HOT_UPDATE: String = "hot_update"
    const val SECTION_PACK_UPDATE: String = "pack_update"

    private const val STORE_BASENAME = "update_skip"
    private val lock = Any()

    @JvmStatic
    fun readSection(sectionKey: String?): JsonObject? {
        synchronized(lock) {
            val root = readStoreLocked()
            if (sectionKey == null || !root.has(sectionKey) || !root.get(sectionKey).isJsonObject) {
                return null
            }
            return root.getAsJsonObject(sectionKey).deepCopy()
        }
    }

    @JvmStatic
    fun writeSection(sectionKey: String?, section: JsonObject?) {
        if (sectionKey == null) {
            return
        }
        synchronized(lock) {
            val root = readStoreLocked()
            if (section == null || section.entrySet().isEmpty()) {
                root.remove(sectionKey)
            } else {
                root.add(sectionKey, section.deepCopy())
            }
            writeStoreLocked(root)
        }
    }

    @JvmStatic
    fun removeSection(sectionKey: String?) {
        writeSection(sectionKey, null)
    }

    private fun readStoreLocked(): JsonObject {
        val storePath = resolveStorePath(STORE_BASENAME)
        var root: JsonObject? = null
        if (storePath != null && Files.exists(storePath)) {
            root = DlcDcConfigIO.read(storePath, DlcDcConfigIO.Layout.GENERIC)
        }
        return root ?: JsonObject()
    }

    private fun writeStoreLocked(root: JsonObject?) {
        val storePath = resolveStorePath(STORE_BASENAME) ?: return
        DlcDcConfigIO.write(storePath, root ?: JsonObject(), DlcDcConfigIO.Layout.GENERIC)
    }

    private fun resolveStorePath(baseName: String): Path? {
        val dir = DlcPlatformServices.configDir().resolve("dlc_manager")
        return DlcDcConfigIO.toDcPath(dir.resolve(baseName))
    }
}
