package com.cozooo.dlc_manager.sync

import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.google.gson.*
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DlcSyncFailure(private val code: String, vararg arguments: Any?) : IOException(PREFIX + code) {
    private val arguments = arguments.map { it.toString() }.toTypedArray()
    fun causedBy(cause: Throwable): DlcSyncFailure { initCause(cause); return this }
    override val message: String
        get() {
            var language = DlcPlatformServices.languageCode().ifBlank { "en_us" }.lowercase(Locale.ROOT).replace('-', '_')
            if (!language.matches(Regex("[a-z]{2,3}_[a-z]{2,3}"))) language = "en_us"
            val key = PREFIX + code
            val value = load(language).get(key) ?: load("en_us").get(key)
            val pattern = value?.asString ?: key
            return try { String.format(Locale.ROOT, pattern, *arguments) } catch (_: IllegalFormatException) { pattern }
        }
    fun write(response: JsonObject) {
        response.addProperty("reasonKey", PREFIX + code)
        val values = JsonArray(); arguments.forEach { values.add(it) }; response.add("reasonArgs", values)
    }
    companion object {
        private const val serialVersionUID = 1L
        private const val PREFIX = "dlc_manager.sync.error."
        private val LANGUAGES = ConcurrentHashMap<String, JsonObject>()
        @JvmStatic fun `is`(error: Throwable?, code: String) = error is DlcSyncFailure && error.code == code
        @JvmStatic fun read(response: JsonObject?): IOException {
            if (response?.has("reasonKey") == true && response.get("reasonKey").isJsonPrimitive) {
                val key = response.get("reasonKey").asString
                if (key.startsWith(PREFIX) && key.removePrefix(PREFIX).matches(Regex("[a-z_]+"))) {
                    val arguments = if (response.has("reasonArgs")) response.getAsJsonArray("reasonArgs").map { it.asString }.toTypedArray() else emptyArray()
                    return DlcSyncFailure(key.removePrefix(PREFIX), *arguments)
                }
            }
            return if (response?.has("reason") == true) IOException(response.get("reason").asString) else DlcSyncFailure("unsupported")
        }
        private fun load(language: String): JsonObject = LANGUAGES.computeIfAbsent(language) { name ->
            try {
                DlcSyncFailure::class.java.getResourceAsStream("/assets/dlc_manager/lang/$name.json").use { input ->
                    if (input == null) JsonObject() else JsonParser.parseReader(InputStreamReader(input, StandardCharsets.UTF_8)).asJsonObject
                }
            } catch (_: IOException) { JsonObject() } catch (_: RuntimeException) { JsonObject() }
        }
    }
}
