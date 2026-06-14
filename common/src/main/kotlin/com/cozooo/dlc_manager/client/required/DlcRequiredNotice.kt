package com.cozooo.dlc_manager.client.required

import com.cozooo.dlc_manager.core.DcConfigIO
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

class DlcRequiredNotice private constructor(
    private val downloadDesc: Map<String, String>,
    private val manageDesc: Map<String, String>,
) {
    fun resolveDownloadDesc(): String {
        return resolveLocalized(downloadDesc)
    }

    fun resolveManageDesc(): String {
        return resolveLocalized(manageDesc)
    }

    companion object {
        @Suppress("unused")
        private val GSON: Gson = GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()

        @JvmStatic
        fun load(): DlcRequiredNotice {
            val download = HashMap<String, String>()
            val manage = HashMap<String, String>()
            val dlcRoot = firstDlcRoot()
            if (dlcRoot == null) {
                return DlcRequiredNotice(download, manage)
            }
            val requiredDir = dlcRoot.resolve("required")
            val noticePath = requiredDir.resolve("notice.dc")
            ensureNoticeFile(noticePath)
            if (!Files.exists(noticePath)) {
                return DlcRequiredNotice(download, manage)
            }
            val obj = DcConfigIO.read(noticePath, DcConfigIO.Layout.GENERIC)
            if (obj == null) {
                return DlcRequiredNotice(download, manage)
            }
            readLocalized(DcConfigIO.getObjectIgnoreCase(obj, "download_desc"), download)
            readLocalized(DcConfigIO.getObjectIgnoreCase(obj, "manage_desc"), manage)
            return DlcRequiredNotice(download, manage)
        }

        private fun readLocalized(data: JsonObject?, out: MutableMap<String, String>) {
            if (data == null) {
                return
            }
            for ((key, value) in data.entrySet()) {
                if (value != null && value.isJsonPrimitive) {
                    val normalized = DcConfigIO.normalizeLangKey(key)
                    if (normalized != null) {
                        out[normalized] = value.asString
                    }
                }
            }
        }

        private fun resolveLocalized(values: Map<String, String>?): String {
            if (values.isNullOrEmpty()) {
                return ""
            }
            val lang = DcConfigIO.normalizeLangKey(CrossPlatformRuntime.languageCode())
            if (lang != null && values.containsKey(lang)) {
                return values[lang] ?: ""
            }
            if (values.containsKey("en_us")) {
                return values["en_us"] ?: ""
            }
            return values.values.firstOrNull() ?: ""
        }

        private fun ensureNoticeFile(noticePath: Path?) {
            if (noticePath == null || Files.exists(noticePath)) {
                return
            }
            val obj = JsonObject()
            val download = JsonObject()
            download.addProperty("en_us", "")
            obj.add("download_desc", download)
            val manage = JsonObject()
            manage.addProperty("en_us", "")
            obj.add("manage_desc", manage)
            try {
                Files.createDirectories(noticePath.parent)
                DcConfigIO.write(noticePath, obj, DcConfigIO.Layout.GENERIC)
            } catch (_: Exception) {
            }
        }

        private fun firstDlcRoot(): Path? {
            return DlcPathConfig.getDlcRoots().firstOrNull()
        }

    }
}
