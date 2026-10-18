package com.cozooo.dlc_manager.bootstrap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

object DlcBootstrapI18n {
    private val english = load("en_us")
    private val chinese = load("zh_cn")

    @JvmStatic
    fun languageCode(): String = if (Locale.getDefault().language == "zh") "zh_cn" else "en_us"
    @JvmStatic
    fun text(key: String, vararg arguments: Any?): String {
        val languageKey = "dlc_manager.bootstrap." + key.lowercase(Locale.ROOT)
        val messages = if (languageCode() == "zh_cn") chinese else english
        val value = if (messages.has(languageKey)) messages.get(languageKey) else english.get(languageKey)
        return String.format(Locale.ROOT, value?.asString ?: languageKey, *arguments)
    }

    private fun load(language: String): JsonObject {
        try {
            DlcBootstrapI18n::class.java.getResourceAsStream("/assets/dlc_manager/lang/$language.json").use { input ->
                if (input == null) throw IOException("Missing bootstrap language: $language")
                return JsonParser.parseReader(InputStreamReader(input, StandardCharsets.UTF_8)).asJsonObject
            }
        } catch (ex: IOException) {
            throw IllegalStateException(ex)
        }
    }
}
