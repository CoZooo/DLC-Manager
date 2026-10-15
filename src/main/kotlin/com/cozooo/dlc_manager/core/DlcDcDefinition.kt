package com.cozooo.dlc_manager.core

import com.cozooo.dclanguage.DcLanguage
import com.cozooo.dclanguage.DcSchemaCatalog
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object DlcDcDefinition {
    private val CATALOG = load()

    @JvmStatic
    fun catalog(): DcSchemaCatalog = CATALOG

    @JvmStatic
    fun register(profile: String): DcLanguage = register(profile, null)

    @JvmStatic
    fun register(profile: String, values: JsonObject?): DcLanguage {
        val selected = CATALOG.profile(profile)
            ?: throw IllegalArgumentException("Unknown DLC Manager profile: $profile")
        return CATALOG.language(selected, values)
    }

    private fun load(): DcSchemaCatalog {
        try {
            DlcDcDefinition::class.java.getResourceAsStream("/META-INF/dlc-manager/dlc-definition.json").use { input ->
                if (input == null) throw IllegalStateException("Missing DLC Manager DC definition")
                return DcSchemaCatalog.fromJson(JsonParser.parseReader(InputStreamReader(input, StandardCharsets.UTF_8)).asJsonObject)
            }
        } catch (exception: IOException) {
            throw IllegalStateException("Cannot load DLC Manager DC definition", exception)
        }
    }
}
