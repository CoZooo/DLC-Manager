package com.cozooo.dlc_manager.client.update

import com.cozooo.dclanguage.DcSchemaCatalog
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object DlcUpdateCompatibility {
    private val CATALOG = loadCatalog()
    private val PROFILE = CATALOG.profile("update")

    @JvmStatic
    fun knownFields(source: JsonObject, unknown: MutableList<String>): JsonObject = filter(source, emptyList(), unknown).asJsonObject

    private fun filter(value: JsonElement, path: List<String>, unknown: MutableList<String>): JsonElement {
        if (value.isJsonArray) {
            val result = JsonArray()
            for (child in value.asJsonArray) {
                val entryPath = if (CATALOG.category(PROFILE, path + "*") != null) path + "*" else path
                result.add(if (child.isJsonObject || child.isJsonArray) filter(child, entryPath, unknown) else child.deepCopy())
            }
            return result
        }
        if (!value.isJsonObject) return value.deepCopy()
        val result = JsonObject()
        for ((key, child) in value.asJsonObject.entrySet()) {
            val childPath = path + key
            val field = CATALOG.field(PROFILE, path, key)
            val category = CATALOG.category(PROFILE, childPath)
            if (field != null) result.add(key, child.deepCopy())
            else if (category != null) result.add(key, filter(child, childPath, unknown))
            else unknown.add(childPath.joinToString("."))
        }
        return result
    }

    private fun loadCatalog(): DcSchemaCatalog {
        try {
            DlcUpdateCompatibility::class.java.getResourceAsStream("/META-INF/dlc-manager/update-language-definition.json").use { input ->
                checkNotNull(input) { "Missing update language definition" }
                return DcSchemaCatalog.fromJson(JsonParser.parseReader(InputStreamReader(input, StandardCharsets.UTF_8)).asJsonObject)
            }
        } catch (exception: java.io.IOException) {
            throw IllegalStateException("Cannot load update language definition", exception)
        }
    }
}
