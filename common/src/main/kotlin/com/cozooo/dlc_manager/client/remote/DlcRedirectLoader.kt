package com.cozooo.dlc_manager.client.remote

import com.cozooo.dlc_manager.core.DcConfigIO
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DlcRedirectLoader {
    @JvmStatic
    fun load(dlcRoot: Path?): List<DlcRedirectEntry> {
        if (dlcRoot == null) {
            return emptyList()
        }
        val redirectRoot = dlcRoot.resolve("remote").resolve("redirect")
        if (!Files.isDirectory(redirectRoot)) {
            return emptyList()
        }
        val entries = ArrayList<DlcRedirectEntry>()
        try {
            Files.walk(redirectRoot).use { stream ->
                stream.filter { path -> path.fileName != null && DcConfigIO.isDc(path) }
                    .forEach { path ->
                        val entry = readEntry(path)
                        if (entry != null) {
                            entries.add(entry)
                        }
                    }
            }
        } catch (_: IOException) {
        }
        return entries
    }

    private fun readEntry(path: Path): DlcRedirectEntry? {
        val obj = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC) ?: return null
        val redirectUrl = DcConfigIO.getStringIgnoreCase(obj, "RedirectUrl")
        if (redirectUrl.isNullOrBlank()) {
            return null
        }
        val entry = DlcRedirectEntry()
        entry.setConfigPath(path)
        entry.setRedirectUrl(redirectUrl)
        entry.setAvailable(DcConfigIO.getIgnoreCase(obj, "available"))

        val fileName = path.fileName?.toString() ?: ""
        entry.setFallbackName(stripConfigExtension(fileName))

        entry.getDisplayNames().putAll(readLocalized(DcConfigIO.getObjectIgnoreCase(obj, "DisplayName")))
        entry.getNotices().putAll(readLocalized(DcConfigIO.getObjectIgnoreCase(obj, "notice")))
        entry.getTips().putAll(readLocalized(DcConfigIO.getObjectIgnoreCase(obj, "tip")))
        return entry
    }

    private fun readLocalized(obj: JsonObject?): Map<String, String> {
        val values = HashMap<String, String>()
        if (obj == null) {
            return values
        }
        for ((key, value) in obj.entrySet()) {
            if (value.isJsonPrimitive) {
                val normalized = DcConfigIO.normalizeLangKey(key)
                if (normalized != null) {
                    values[normalized] = value.asString
                }
            }
        }
        return values
    }

    private fun stripConfigExtension(name: String?): String {
        if (name == null) {
            return ""
        }
        return if (name.lowercase().endsWith(".dc")) name.substring(0, name.length - 3) else name
    }
}
