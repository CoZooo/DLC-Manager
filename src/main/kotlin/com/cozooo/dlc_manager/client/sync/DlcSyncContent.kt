package com.cozooo.dlc_manager.client.sync

import com.cozooo.dlc_manager.core.*
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.sync.SyncFailure
import com.cozooo.dlc_manager.sync.SyncModel.*
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.*
import java.util.Locale

class DlcSyncContent(private val roots: List<Path>) {
    private val manager = DlcPlatformServices.configDir().resolve("dlc_manager").toAbsolutePath().normalize()
    fun managerPath(key: String): Path {
        if (key != "manager:config.dc") throw SyncFailure("not_settings")
        val path = manager.resolve(key.removePrefix("manager:"))
        var part: Path? = path
        while (part != null && part.startsWith(manager)) {
            if (Files.isSymbolicLink(part)) throw SyncFailure("symbolic_settings")
            part = part.parent
        }
        return path
    }
    private fun configKey(key: String) = key.startsWith("manager:") || key.substringAfter(':').startsWith("preset/")
    fun manifest(): List<Item> {
        val result = mutableListOf<Item>()
        result.add(item("manager:config.dc", managerPath("manager:config.dc")))
        for ((index, root) in roots.withIndex()) {
            val presets = root.resolve("preset")
            val updates = root.resolve("update")
            if (Files.isDirectory(presets)) Files.walk(presets).use { paths ->
                for (path in paths.filter { Files.isRegularFile(it) }.filter { DlcDcFormatUtil.isDcName(it.fileName.toString()) }.sorted().toList()) {
                    result.add(item("$index:" + root.relativize(path).toString().replace('\\', '/'), path))
                }
            }
            val version = DlcDcFormatUtil.resolveConfigPath(root.resolve("info"), "version")
            if (version != null && Files.isRegularFile(version)) {
                val value = DlcDcConfigIO.read(version, DlcDcConfigIO.Layout.GENERIC)
                val base = DlcDcConfigIO.getStringIgnoreCase(value, "base")
                val dlc = DlcDcConfigIO.getStringIgnoreCase(value, "dlc")
                if (!base.isNullOrBlank() && !dlc.isNullOrBlank()) {
                    val key = "$index:info/version.dc"
                    result.add(Item(key, "version", file(key, version), emptyList(), null, emptyMap(), emptyMap(), Update(base, dlc, base, dlc, if (DlcPlatformServices.isDedicatedServer()) "server" else "client")))
                }
            }
            if (Files.isDirectory(updates)) Files.list(updates).use { paths ->
                for (path in paths.filter { Files.isRegularFile(it) }.filter { it.fileName.toString().lowercase(Locale.ROOT).endsWith(".zip") }.sorted().toList()) {
                    if (DlcUpdatePackage.fromZip(path) != null) try { result.add(item("$index:update/${path.fileName}", path)) } catch (_: IOException) {}
                }
            }
        }
        return result
    }
    private fun file(key: String, path: Path): File {
        val anchor = if (path.startsWith(manager)) manager else roots.firstOrNull { path.startsWith(it) } ?: throw SyncFailure("outside_roots")
        var part: Path? = path
        while (part != null && part.startsWith(anchor)) {
            if (Files.isSymbolicLink(part)) throw SyncFailure("symbolic_file")
            part = part.parent
        }
        return File(key, Files.size(path), DlcFileMatch.hash(path))
    }
    fun item(key: String, path: Path): Item {
        if (key.startsWith("manager:")) return DlcSyncSettings.item(key, path)
        if (!Files.isRegularFile(path)) throw SyncFailure("file_missing", "$key")
        if (configKey(key)) {
            com.cozooo.dclanguage.DcDocuments.read(path)
            val obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC)
            val identifier = DlcDcConfigIO.getStringIgnoreCase(DlcDcConfigIO.getObjectIgnoreCase(obj, "basic"), "identifier")?.takeIf { it.isNotBlank() }
                ?: DlcDcConfigIO.getStringIgnoreCase(obj, "identifier")
            val fallback = identifier?.trim()?.takeIf { it.isNotBlank() } ?: DlcDcFormatUtil.stripConfigExtension(path.fileName.toString())
            val names = localized(obj, "DisplayName")
            val enabled = com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.loadPresetStatus().getOrDefault(fallback, com.cozooo.dlc_manager.client.preset.DlcPresetStatus.OFF).isEnabled()
            return Item(key, fallback, file(key, path), emptyList(), enabled, names, localized(obj, "tip"))
        }
        val pkg = DlcUpdatePackage.fromZip(path) ?: throw SyncFailure("invalid_update", "$key")
        val manifest = pkg.getManifest()
        val version = manifest.getVersion() ?: throw SyncFailure("invalid_update", "$key")
        val base = version.getBase()
        val old = version.getDlcOld()
        if (base.isNullOrBlank() || old.isNullOrBlank()) throw SyncFailure("update_versions_missing", "$key")
        val updated = version.getDlcNew()?.takeIf { it.isNotBlank() } ?: old
        val next = version.getBaseNew()?.takeIf { it.isNotBlank() } ?: base
        return Item(key, pkg.getName(), file(key, path), emptyList(), null, emptyMap(), manifest.getChangelog(), Update(base, old, next, updated, version.getPlatform()))
    }
    private fun localized(obj: JsonObject?, category: String): Map<String, String> {
        val values = DlcDcConfigIO.getObjectIgnoreCase(obj, category)
        val result = linkedMapOf<String, String>()
        if (values != null) for ((key, value) in values.entrySet()) {
            val language = DlcDcConfigIO.normalizeLangKey(key) ?: continue
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString) result[language] = value.asString
        }
        return result
    }
}
