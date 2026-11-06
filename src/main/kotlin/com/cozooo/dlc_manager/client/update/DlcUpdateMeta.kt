package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap

class DlcUpdateMeta {
    private var dlcOld: String? = null
    private var dlcNew: String? = null
    private var baseOld: String? = null
    private var baseNew: String? = null
    private var timestamp: String? = null
    private var hidden = false
    private val changelog: MutableMap<String, String> = LinkedHashMap()
    private val restorePaths: MutableList<String> = ArrayList()
    private val createdPaths: MutableList<String> = ArrayList()

    fun save(metaPath: Path?) {
        if (metaPath == null) {
            return
        }
        try {
            Files.createDirectories(metaPath.parent)
            val obj = JsonObject()
            obj.addProperty("dlc_old", dlcOld ?: "")
            obj.addProperty("dlc_new", dlcNew ?: "")
            if (!baseOld.isNullOrBlank()) {
                obj.addProperty("version_old", baseOld)
            }
            if (!baseNew.isNullOrBlank()) {
                obj.addProperty("version_new", baseNew)
            }
            obj.addProperty("timestamp", timestamp ?: "")
            obj.addProperty("hidden", hidden)
            if (changelog.isNotEmpty()) {
                val logObj = JsonObject()
                for ((key, value) in changelog) {
                    logObj.addProperty(key, value)
                }
                obj.add("changelog", logObj)
            }
            val restore = JsonArray()
            for (path in restorePaths) {
                restore.add(path)
            }
            val created = JsonArray()
            for (path in createdPaths) {
                created.add(path)
            }
            obj.add("restore", restore)
            obj.add("created", created)
            DlcDcConfigIO.write(metaPath, obj, DlcDcConfigIO.Layout.GENERIC)
        } catch (_: IOException) {
        }
    }

    fun getDlcOld(): String? = dlcOld

    fun setDlcOld(dlcOld: String?) {
        this.dlcOld = dlcOld
    }

    fun getDlcNew(): String? = dlcNew

    fun setDlcNew(dlcNew: String?) {
        this.dlcNew = dlcNew
    }

    fun getBaseOld(): String? = baseOld

    fun getVersionOld(): String? = baseOld

    fun setBaseOld(baseOld: String?) {
        this.baseOld = baseOld
    }

    fun setVersionOld(versionOld: String?) {
        this.baseOld = versionOld
    }

    fun getBaseNew(): String? = baseNew

    fun getVersionNew(): String? = baseNew

    fun setBaseNew(baseNew: String?) {
        this.baseNew = baseNew
    }

    fun setVersionNew(versionNew: String?) {
        this.baseNew = versionNew
    }

    fun getTimestamp(): String? = timestamp

    fun setTimestamp(timestamp: String?) {
        this.timestamp = timestamp
    }

    fun isHidden(): Boolean = hidden

    fun setHidden(hidden: Boolean) {
        this.hidden = hidden
    }

    fun getChangelog(): MutableMap<String, String> = changelog

    fun setChangelog(values: Map<String, String>?) {
        changelog.clear()
        if (values == null) {
            return
        }
        for ((key, value) in values) {
            if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                val normalizedKey = DlcDcConfigIO.normalizeLangKey(key)
                if (normalizedKey != null) {
                    changelog[normalizedKey] = value
                }
            }
        }
    }

    fun resolveChangelog(lang: String?): String {
        if (changelog.isEmpty()) {
            return ""
        }
        val key = DlcDcConfigIO.normalizeLangKey(lang)
        if (key != null && changelog.containsKey(key)) {
            return changelog[key] ?: ""
        }
        if (changelog.containsKey("en_us")) {
            return changelog["en_us"] ?: ""
        }
        return changelog.values.firstOrNull() ?: ""
    }

    fun getRestorePaths(): MutableList<String> = restorePaths

    fun getCreatedPaths(): MutableList<String> = createdPaths

    companion object {
        @JvmStatic
        fun load(metaPath: Path?): DlcUpdateMeta? {
            if (metaPath == null || !Files.exists(metaPath)) {
                return null
            }
            val obj = DlcDcConfigIO.read(metaPath, DlcDcConfigIO.Layout.GENERIC) ?: return null
            val meta = DlcUpdateMeta()
            meta.dlcOld = DlcDcConfigIO.getStringIgnoreCase(obj, "dlc_old")
            meta.dlcNew = DlcDcConfigIO.getStringIgnoreCase(obj, "dlc_new")
            meta.baseOld = firstNonBlank(DlcDcConfigIO.getStringIgnoreCase(obj, "version_old"), DlcDcConfigIO.getStringIgnoreCase(obj, "base_old"))
            meta.baseNew = firstNonBlank(DlcDcConfigIO.getStringIgnoreCase(obj, "version_new"), DlcDcConfigIO.getStringIgnoreCase(obj, "base_new"))
            meta.timestamp = DlcDcConfigIO.getStringIgnoreCase(obj, "timestamp")
            meta.hidden = DlcDcConfigIO.getBooleanIgnoreCase(obj, "hidden", false)
            val logObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "changelog")
            if (logObj != null) {
                for ((key, value) in logObj.entrySet()) {
                    if (value != null && value.isJsonPrimitive) {
                        val raw = value.asString
                        if (!raw.isNullOrBlank()) {
                            val normalizedKey = DlcDcConfigIO.normalizeLangKey(key)
                            if (normalizedKey != null) {
                                meta.changelog[normalizedKey] = raw
                            }
                        }
                    }
                }
            }
            val restoreArray = DlcDcConfigIO.getArrayIgnoreCase(obj, "restore")
            restoreArray?.forEach { element ->
                if (element != null && element.isJsonPrimitive) {
                    meta.restorePaths.add(element.asString)
                }
            }
            val createdArray = DlcDcConfigIO.getArrayIgnoreCase(obj, "created")
            createdArray?.forEach { element ->
                if (element != null && element.isJsonPrimitive) {
                    meta.createdPaths.add(element.asString)
                }
            }
            return meta
        }

        private fun firstNonBlank(primary: String?, fallback: String?): String? {
            return if (primary.isNullOrBlank()) fallback else primary
        }
    }
}
