package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.core.DlcDcConfigIO
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.Path

class DlcPackTransaction @JvmOverloads constructor(
    @JvmField var status: Status,
    @JvmField var identifier: String,
    @JvmField var stagingId: String,
    movedPaths: Collection<String>?,
    writtenPaths: Collection<String>?,
    enabledDlc: Collection<String>? = emptyList(),
    disabledDlc: Collection<String>? = emptyList(),
    enabledPresets: Collection<String>? = emptyList(),
    disabledPresets: Collection<String>? = emptyList(),
    @JvmField var replacement: Boolean = false,
) {
    enum class Status {
        RUNNING,
        CANCELLED,
        COMMITTED,
        ROLLED_BACK,
    }

    @JvmField val movedPaths: MutableList<String> = ArrayList()
    @JvmField val writtenPaths: MutableList<String> = ArrayList()
    @JvmField val enabledDlc: MutableList<String> = ArrayList()
    @JvmField val disabledDlc: MutableList<String> = ArrayList()
    @JvmField val enabledPresets: MutableList<String> = ArrayList()
    @JvmField val disabledPresets: MutableList<String> = ArrayList()

    init {
        addAll(this.movedPaths, movedPaths)
        addAll(this.writtenPaths, writtenPaths)
        addAllTokens(this.enabledDlc, enabledDlc)
        addAllTokens(this.disabledDlc, disabledDlc)
        addAllTokens(this.enabledPresets, enabledPresets)
        addAllTokens(this.disabledPresets, disabledPresets)
    }

    fun addMovedPath(path: String?) {
        addPath(movedPaths, path)
    }

    fun addWrittenPath(path: String?) {
        addPath(writtenPaths, path)
    }

    fun addEnabledDlc(id: String?) {
        addToken(enabledDlc, id)
    }

    fun addDisabledDlc(id: String?) {
        addToken(disabledDlc, id)
    }

    fun addEnabledPreset(id: String?) {
        addToken(enabledPresets, id)
    }

    fun addDisabledPreset(id: String?) {
        addToken(disabledPresets, id)
    }

    fun save(path: Path?): Boolean {
        if (path == null) {
            return false
        }
        val obj = JsonObject()
        obj.addProperty("status", status.name.lowercase())
        obj.addProperty("identifier", identifier)
        obj.addProperty("staging", stagingId)
        obj.add("moved", toArray(movedPaths))
        obj.add("written", toArray(writtenPaths))
        obj.add("enabled_dlc", toArray(enabledDlc))
        obj.add("disabled_dlc", toArray(disabledDlc))
        obj.add("enabled_presets", toArray(enabledPresets))
        obj.add("disabled_presets", toArray(disabledPresets))
        obj.addProperty("replacement", replacement)
        return DlcPackDcFiles.writeAtomic(path, obj)
    }

    companion object {
        @JvmStatic
        fun running(identifier: String, stagingId: String): DlcPackTransaction {
            return DlcPackTransaction(Status.RUNNING, identifier, stagingId, emptyList(), emptyList())
        }

        @JvmStatic
        fun load(path: Path?): DlcPackTransaction? {
            if (path == null || !Files.exists(path)) {
                return null
            }
            val obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC) ?: return null
            val status = parseStatus(DlcDcConfigIO.getStringIgnoreCase(obj, "status"))
            val identifier = DlcDcConfigIO.getStringIgnoreCase(obj, "identifier")?.trim()
            val staging = DlcDcConfigIO.getStringIgnoreCase(obj, "staging")?.trim().orEmpty()
            if (status == null || !DlcPackMetadata.isValidIdentifier(identifier)) {
                return null
            }
            return DlcPackTransaction(
                status,
                identifier!!,
                staging,
                readArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "moved")),
                readArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "written")),
                readTokenArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "enabled_dlc")),
                readTokenArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "disabled_dlc")),
                readTokenArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "enabled_presets")),
                readTokenArray(DlcDcConfigIO.getArrayIgnoreCase(obj, "disabled_presets")),
                getBoolean(obj, "replacement"),
            )
        }

        @JvmStatic
        fun parseStatus(value: String?): Status? {
            if (value.isNullOrBlank()) {
                return null
            }
            return Status.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
        }

        private fun addAll(target: MutableList<String>, paths: Collection<String>?) {
            paths?.forEach { addPath(target, it) }
        }

        private fun addAllTokens(target: MutableList<String>, tokens: Collection<String>?) {
            tokens?.forEach { addToken(target, it) }
        }

        private fun addPath(target: MutableList<String>, path: String?) {
            val normalized = DlcPackMeta.normalizeRelativePath(path) ?: return
            if (!target.contains(normalized)) {
                target.add(normalized)
            }
        }

        private fun addToken(target: MutableList<String>, token: String?) {
            val value = token?.trim()
            if (!value.isNullOrBlank() && !target.contains(value)) {
                target.add(value)
            }
        }

        private fun toArray(paths: List<String>): JsonArray {
            val array = JsonArray()
            for (path in paths) {
                array.add(path)
            }
            return array
        }

        private fun readArray(array: JsonArray?): List<String> {
            if (array == null) {
                return emptyList()
            }
            val out = ArrayList<String>()
            array.forEach { element ->
                if (element != null && element.isJsonPrimitive) {
                    DlcPackMeta.normalizeRelativePath(element.asString)?.let { out.add(it) }
                }
            }
            return out
        }

        private fun readTokenArray(array: JsonArray?): List<String> {
            if (array == null) {
                return emptyList()
            }
            val out = ArrayList<String>()
            array.forEach { element ->
                if (element != null && element.isJsonPrimitive) {
                    val value = try {
                        element.asString?.trim()
                    } catch (_: Exception) {
                        null
                    }
                    if (!value.isNullOrBlank() && !out.contains(value)) {
                        out.add(value)
                    }
                }
            }
            return out
        }

        private fun getBoolean(obj: JsonObject, key: String): Boolean {
            val value = DlcDcConfigIO.getIgnoreCase(obj, key)
            if (value == null || !value.isJsonPrimitive) {
                return false
            }
            return try {
                value.asBoolean
            } catch (_: Exception) {
                false
            }
        }
    }
}
