package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.core.DcConfigIO
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

object DlcPackOrder {
    @JvmStatic
    fun load(path: Path?): List<String> {
        if (path == null || !Files.exists(path)) {
            return emptyList()
        }
        val obj = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC) ?: return emptyList()
        val array = DcConfigIO.getArrayIgnoreCase(obj, "archives") ?: return emptyList()
        val out = ArrayList<String>()
        array.forEach { element ->
            if (element != null && element.isJsonPrimitive) {
                normalize(element.asString)?.let { value ->
                    if (!out.contains(value)) {
                        out.add(value)
                    }
                }
            }
        }
        return out
    }

    @JvmStatic
    fun save(path: Path?, order: Collection<String>?): Boolean {
        if (path == null) {
            return false
        }
        val obj = JsonObject()
        val array = JsonArray()
        order?.forEach { key ->
            normalize(key)?.let { array.add(it) }
        }
        obj.add("archives", array)
        return DlcPackDcFiles.writeAtomic(path, obj)
    }

    @JvmStatic
    fun normalizeOrder(savedOrder: Collection<String>?, currentKeys: Collection<String>?): List<String> {
        val out = ArrayList<String>()
        if (savedOrder != null) {
            for (key in savedOrder) {
                val value = normalize(key) ?: continue
                if (currentKeys != null && currentKeys.contains(value) && !out.contains(value)) {
                    out.add(value)
                }
            }
        }
        if (currentKeys != null) {
            for (key in currentKeys) {
                val value = normalize(key) ?: continue
                if (!out.contains(value)) {
                    out.add(value)
                }
            }
        }
        return out
    }

    private fun normalize(value: String?): String? {
        return if (value.isNullOrBlank()) null else value.trim()
    }
}
