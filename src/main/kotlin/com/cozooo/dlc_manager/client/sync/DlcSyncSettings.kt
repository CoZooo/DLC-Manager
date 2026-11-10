package com.cozooo.dlc_manager.client.sync

import com.cozooo.dlc_manager.core.*
import com.cozooo.dlc_manager.sync.DlcSyncFailure
import com.cozooo.dlc_manager.sync.DlcSyncModel.*
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.*
import java.nio.charset.StandardCharsets

internal object DlcSyncSettings {
    @JvmStatic fun item(key: String, path: Path): Item {
        val values = DlcConfig.buildSyncConfigJson(read(path))
        val bytes = values.toString().toByteArray(StandardCharsets.UTF_8)
        return Item(key, "config", File(key, bytes.size.toLong(), DlcFileMatch.hash(java.io.ByteArrayInputStream(bytes))), emptyList(), null,
            emptyMap(), emptyMap(), null, values)
    }
    private fun read(path: Path): JsonObject = if (Files.exists(path)) com.cozooo.dclanguage.DcDocuments.read(path).toJson() else JsonObject()
    @JvmStatic fun write(path: Path, incoming: JsonObject, journal: Journal) {
        val values = DlcConfig.buildSyncConfigJson(incoming)
        val original = read(path)
        val previous = DlcConfig.buildSyncConfigJson(original)
        for ((section, entries) in values.entrySet()) {
            val category = existingKey(original, section)
            var target = DlcDcConfigIO.getObjectIgnoreCase(original, category)
            for ((key, value) in entries.asJsonObject.entrySet()) {
                if (DlcDcConfigIO.getIgnoreCase(DlcDcConfigIO.getObjectIgnoreCase(incoming, section), key) == null || value == previous.getAsJsonObject(section).get(key)) continue
                val destination = target ?: JsonObject().also { target = it; original.add(category, it) }
                if (section == "modpack" && key == "updateGitRepo" && DlcDcConfigIO.getIgnoreCase(destination, "updateGithubRepo") != null) destination.add(existingKey(destination, "updateGithubRepo"), value.deepCopy())
                destination.add(existingKey(destination, key), value.deepCopy())
            }
        }
        journal.capture(path)
        Files.createDirectories(path.parent)
        if (!DlcDcConfigIO.write(path, original, DlcDcConfigIO.Layout.GENERIC)) throw DlcSyncFailure("settings_save")
    }
    private fun existingKey(obj: JsonObject, key: String) = obj.keySet().firstOrNull { it.equals(key, true) } ?: key
}
