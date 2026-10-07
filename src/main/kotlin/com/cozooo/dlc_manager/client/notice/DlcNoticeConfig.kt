package com.cozooo.dlc_manager.client.notice

import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.HashMap
import java.util.HashSet

class DlcNoticeConfig {
    enum class ShowFrequency(private val key: String) {
        PER_LAUNCH_GAME("perlaunchgame"),
        PER_GAME_SESSION("pergamesession"),
        PER_LAUNCH_MANAGER("perlaunchmanager"),
        ONCE("once");

        fun getKey(): String {
            return key
        }

        companion object {
            @JvmStatic
            fun fromString(raw: String?): ShowFrequency {
                if (raw == null) {
                    return PER_LAUNCH_MANAGER
                }
                for (value in entries) {
                    if (value.key.equals(raw, ignoreCase = true)) {
                        return value
                    }
                }
                return PER_LAUNCH_MANAGER
            }
        }
    }

    class LocalizedText {
        private val values: MutableMap<String, String> = HashMap()

        fun put(key: String?, value: String?) {
            if (key == null || value == null) {
                return
            }
            values[key] = value
        }

        fun getValues(): MutableMap<String, String> {
            return values
        }

        fun resolve(lang: String?): String {
            if (values.isEmpty()) {
                return ""
            }
            val key = DlcDcConfigIO.normalizeLangKey(lang)
            if (key != null && values.containsKey(key)) {
                return values[key] ?: ""
            }
            if (values.containsKey("en_us")) {
                return values["en_us"] ?: ""
            }
            return values.values.firstOrNull() ?: ""
        }
    }

    class Subhead {
        @JvmField
        var enabled: Boolean = true

        @JvmField
        val text: LocalizedText = LocalizedText()
    }

    private var path: Path? = null
    private var exists = false
    private var noticeEnabled = true
    private var neverShow = false
    private var showFrequency = ShowFrequency.PER_LAUNCH_MANAGER
    private val title = LocalizedText()
    private val subhead = Subhead()
    private val contents = LocalizedText()
    private val contentUrls = LocalizedText()
    private val contentUrlLoaded: MutableSet<String> = HashSet()
    private val contentUrlText: MutableMap<String, String?> = HashMap()

    fun save() {
        val noticePath = path
        if (noticePath == null || !exists || !Files.exists(noticePath)) {
            return
        }
        val obj = JsonObject()
        obj.addProperty("isNoticeEnabled", noticeEnabled)
        obj.addProperty("neverShow", neverShow)
        obj.addProperty("ShowFrequency", showFrequency.getKey())
        obj.add("Title", writeLocalized(title))
        val subheadObj = JsonObject()
        subheadObj.addProperty("enabled", subhead.enabled)
        val subheadText = writeLocalized(subhead.text)
        for ((key, value) in subheadText.entrySet()) {
            subheadObj.add(key, value)
        }
        obj.add("Subhead", subheadObj)
        if (contentUrls.getValues().isNotEmpty()) {
            obj.add("ContentUrl", writeLocalized(contentUrls))
        }
        obj.add("Contents", writeLocalized(contents))
        DlcDcConfigIO.write(noticePath, obj, DlcDcConfigIO.Layout.GENERIC)
    }

    fun isNoticeEnabled(): Boolean {
        return noticeEnabled
    }

    fun isNeverShow(): Boolean {
        return neverShow
    }

    fun setNeverShow(neverShow: Boolean) {
        this.neverShow = neverShow
    }

    fun getShowFrequency(): ShowFrequency {
        return showFrequency
    }

    fun getTitle(): LocalizedText {
        return title
    }

    fun getSubhead(): Subhead {
        return subhead
    }

    fun getContents(): LocalizedText {
        return contents
    }

    fun getContentUrl(): String {
        return getContentUrl(null)
    }

    fun getContentUrl(lang: String?): String {
        return normalizeUrl(contentUrls.resolve(lang))
    }

    fun hasContent(lang: String?): Boolean = contents.resolve(lang).isNotBlank() || getContentUrl(lang).isNotBlank()

    fun resolveContents(lang: String?): String {
        val contentUrl = getContentUrl(lang)
        if (contentUrl.isNotBlank()) {
            val remote = fetchContentUrlText(contentUrl)
            if (!remote.isNullOrBlank()) {
                return remote
            }
        }
        return contents.resolve(lang)
    }

    @Synchronized
    private fun fetchContentUrlText(contentUrl: String?): String? {
        val normalizedUrl = normalizeUrl(contentUrl)
        if (normalizedUrl.isBlank()) {
            return null
        }
        if (contentUrlLoaded.contains(normalizedUrl)) {
            return contentUrlText[normalizedUrl]
        }
        contentUrlLoaded.add(normalizedUrl)
        var connection: HttpURLConnection? = null
        try {
            val url = URI(normalizedUrl).toURL()
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "DLC-Manager-Notice/1.0")
            val code = connection.responseCode
            if (code >= 200 && code < 300) {
                connection.inputStream.use { input ->
                    val bytes = input.readAllBytes()
                    var text = String(bytes, StandardCharsets.UTF_8)
                    if (text.startsWith("\uFEFF")) {
                        text = text.substring(1)
                    }
                    contentUrlText[normalizedUrl] = text
                    return text
                }
            }
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
        contentUrlText[normalizedUrl] = null
        return null
    }

    companion object {
        @JvmStatic
        fun load(): DlcNoticeConfig = load(resolveNoticePath())

        @JvmStatic
        fun load(path: Path?): DlcNoticeConfig {
            val config = DlcNoticeConfig()
            config.path = path
            if (path == null || !Files.exists(path)) {
                config.noticeEnabled = false
                config.exists = false
                return config
            }
            config.exists = true
            val obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC)
            if (obj == null) {
                config.noticeEnabled = false
                return config
            }
            config.noticeEnabled = getBoolean(obj, "isNoticeEnabled", true)
            config.neverShow = getBoolean(obj, "neverShow", false)
            config.showFrequency = ShowFrequency.fromString(getString(obj, "ShowFrequency"))
            config.title.getValues().putAll(readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "Title")))
            val subheadObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "Subhead")
            if (subheadObj != null) {
                config.subhead.enabled = getBoolean(subheadObj, "enabled", true)
                config.subhead.text.getValues().putAll(readLocalized(subheadObj))
            }
            config.contentUrls.getValues().putAll(readLocalizedUrlValues(DlcDcConfigIO.getIgnoreCase(obj, "ContentUrl")))
            config.contents.getValues().putAll(readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "Contents")))
            return config
        }

        private fun resolveNoticePath(): Path? {
            val root = firstDlcRoot() ?: return null
            return root.resolve("info").resolve("notice.dc")
        }

        private fun firstDlcRoot(): Path? {
            return DlcConfig.getDlcRoots().firstOrNull()
        }

        private fun readLocalized(obj: JsonObject?): Map<String, String> {
            val values: MutableMap<String, String> = HashMap()
            if (obj == null) {
                return values
            }
            for ((key, value) in obj.entrySet()) {
                if (value.isJsonPrimitive && !key.equals("enabled", ignoreCase = true)) {
                    val normalized = DlcDcConfigIO.normalizeLangKey(key)
                    if (normalized != null) {
                        values[normalized] = value.asString
                    }
                }
            }
            return values
        }

        private fun readLocalizedUrlValues(element: JsonElement?): Map<String, String> {
            val values: MutableMap<String, String> = HashMap()
            if (element == null) {
                return values
            }
            if (element.isJsonPrimitive) {
                val url = normalizeUrl(element.asString)
                if (url.isNotBlank()) {
                    values["en_us"] = url
                }
                return values
            }
            if (!element.isJsonObject) {
                return values
            }
            val obj = element.asJsonObject
            for ((key, value) in obj.entrySet()) {
                if (value.isJsonPrimitive) {
                    val url = normalizeUrl(value.asString)
                    val normalized = DlcDcConfigIO.normalizeLangKey(key)
                    if (url.isNotBlank() && normalized != null) {
                        values[normalized] = url
                    }
                }
            }
            return values
        }

        private fun writeLocalized(text: LocalizedText): JsonObject {
            val obj = JsonObject()
            for ((key, value) in text.getValues()) {
                obj.addProperty(key, value)
            }
            return obj
        }

        private fun getBoolean(obj: JsonObject, key: String, fallback: Boolean): Boolean {
            return DlcDcConfigIO.getBooleanIgnoreCase(obj, key, fallback)
        }

        private fun getString(obj: JsonObject, key: String): String? {
            return DlcDcConfigIO.getStringIgnoreCase(obj, key)
        }

        private fun normalizeUrl(raw: String?): String {
            return raw?.trim() ?: ""
        }

    }
}
