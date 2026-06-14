package com.cozooo.dlc_manager.client.remote

import com.cozooo.dlc_manager.core.DcConfigIO
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import java.nio.file.Path
import java.util.LinkedHashSet

open class DlcRedirectEntry {
    private var configPath: Path? = null
    private var available: String? = null
    private val availableLangs: MutableSet<String> = LinkedHashSet()
    private var hidden = false
    private var redirectUrl: String? = null
    private var fallbackName: String? = null
    private val displayNames: MutableMap<String, String> = HashMap()
    private val notices: MutableMap<String, String> = HashMap()
    private val tips: MutableMap<String, String> = HashMap()

    fun getConfigPath(): Path? = configPath

    fun setConfigPath(configPath: Path?) {
        this.configPath = configPath
    }

    fun getAvailable(): String? = available

    fun setAvailable(available: JsonElement?) {
        availableLangs.clear()
        hidden = false
        this.available = stringifyAvailable(available)
        if (available == null || available.isJsonNull) {
            return
        }
        if (available.isJsonArray) {
            readAvailableArray(available.asJsonArray)
            return
        }
        if (!available.isJsonPrimitive) {
            return
        }
        readAvailableString(available.asString)
    }

    fun getRedirectUrl(): String? = redirectUrl

    fun setRedirectUrl(redirectUrl: String?) {
        this.redirectUrl = redirectUrl
    }

    fun setFallbackName(fallbackName: String?) {
        this.fallbackName = fallbackName
    }

    fun getDisplayNames(): MutableMap<String, String> = displayNames

    fun getNotices(): MutableMap<String, String> = notices

    fun getTips(): MutableMap<String, String> = tips

    fun isHidden(): Boolean = hidden

    fun isAvailableFor(lang: String?): Boolean {
        if ((available == null || available!!.isBlank()) && availableLangs.isEmpty()) {
            return true
        }
        if (isHidden()) {
            return false
        }
        if (availableLangs.isEmpty()) {
            return true
        }
        val current = DcConfigIO.normalizeLangKey(lang)
        return current != null && availableLangs.contains(current)
    }

    fun resolveDisplayName(lang: String?): String {
        return resolveLocalized(displayNames, lang, fallbackName)
    }

    fun resolveNotice(lang: String?): String {
        return resolveLocalized(notices, lang, "")
    }

    fun resolveTip(lang: String?): String {
        return resolveLocalized(tips, lang, "")
    }

    private fun resolveLocalized(values: Map<String, String>?, lang: String?, fallback: String?): String {
        if (values.isNullOrEmpty()) {
            return fallback ?: ""
        }
        val key = DcConfigIO.normalizeLangKey(lang)
        if (key != null && values.containsKey(key)) {
            return values[key] ?: ""
        }
        if (values.containsKey("en_us")) {
            return values["en_us"] ?: ""
        }
        return values.values.iterator().next()
    }

    private fun readAvailableArray(array: JsonArray) {
        for (element in array) {
            if (element == null || !element.isJsonPrimitive) {
                continue
            }
            readAvailableString(element.asString)
        }
    }

    private fun readAvailableString(value: String?) {
        if (value == null) {
            return
        }
        for (token in value.split(",")) {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) {
                continue
            }
            if ("hidden".equals(trimmed, ignoreCase = true)) {
                hidden = true
                availableLangs.clear()
                return
            }
            val wanted = DcConfigIO.normalizeLangKey(trimmed)
            if (wanted != null) {
                availableLangs.add(wanted)
            }
        }
    }

    companion object {
        private fun stringifyAvailable(available: JsonElement?): String? {
            if (available == null || available.isJsonNull) {
                return null
            }
            if (available.isJsonPrimitive) {
                return available.asString
            }
            if (!available.isJsonArray) {
                return null
            }
            val values = ArrayList<String>()
            for (element in available.asJsonArray) {
                if (element != null && element.isJsonPrimitive) {
                    values.add(element.asString)
                }
            }
            return values.joinToString(",")
        }
    }
}
