package com.cozooo.dlc_manager.client.update

import com.cozooo.dclanguage.DcDocuments
import com.cozooo.dclanguage.DcParseException
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

object DlcUpdateDcParser {
    @JvmStatic
    fun parse(text: String?): JsonObject? {
        if (text == null) return null
        return try {
            DcDocuments.readText(text).toJson().also(::adaptDeleteLists)
        } catch (_: DcParseException) {
            null
        }
    }

    private fun adaptDeleteLists(value: JsonElement) {
        if (value.isJsonArray) {
            value.asJsonArray.forEach(::adaptDeleteLists)
        } else if (value.isJsonObject) {
            for (entry in value.asJsonObject.entrySet()) {
                val child = entry.value
                if (entry.key.equals("del", ignoreCase = true) && child.isJsonObject &&
                    child.asJsonObject.asMap().values.all { it.isJsonObject && it.asJsonObject.entrySet().isEmpty() }
                ) {
                    val paths = JsonArray()
                    child.asJsonObject.keySet().forEach(paths::add)
                    entry.setValue(paths)
                } else adaptDeleteLists(child)
            }
        }
    }
}
