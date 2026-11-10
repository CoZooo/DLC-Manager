package com.cozooo.dlc_manager.client.sync

import com.cozooo.dlc_manager.sync.DlcSyncFailure
import com.cozooo.dlc_manager.sync.DlcSyncModel.*
import com.cozooo.dlc_manager.core.*
import com.cozooo.dlc_manager.client.remote.*
import com.cozooo.dlc_manager.client.required.*
import com.cozooo.dlc_manager.client.update.DlcUpdateJournal
import java.io.IOException
import java.nio.file.*
import java.util.function.BooleanSupplier

class DlcSyncAdapter @JvmOverloads constructor(private val quiesce: Quiesce = Quiesce {}) : Adapter {
    private val configuredRoots = DlcConfig.getDlcRoots().map { it.toAbsolutePath().normalize() }
    private val content = DlcSyncContent(configuredRoots)
    private val game = DlcPaths.gameDir().toAbsolutePath().normalize()
    private val aliases = mutableMapOf<String, Path>()
    private val previous = mutableMapOf<String, DlcItem>()
    override fun quiesce(canceled: BooleanSupplier) = quiesce.await(canceled)
    override fun workDirectory(): Path = configuredRoots.first().resolve("tmp/sync")
    private fun roots() = configuredRoots
    private fun id(root: Path, path: Path?): String {
        val normalized = path?.toAbsolutePath()?.normalize() ?: throw DlcSyncFailure("invalid_path")
        if (!normalized.startsWith(root) || normalized == root) throw DlcSyncFailure("outside_root")
        return "${roots().indexOf(root)}:" + root.relativize(normalized).toString().replace('\\', '/')
    }
    override fun resolve(id: String): Path {
        if (id.startsWith("manager:")) return content.managerPath(id)
        val colon = id.indexOf(':')
        if (colon <= 0) throw DlcSyncFailure("invalid_resource")
        val index = id.substring(0, colon).toIntOrNull() ?: throw DlcSyncFailure("invalid_root")
        if (index !in roots().indices) throw DlcSyncFailure("root_missing")
        val relative = id.substring(colon + 1)
        if (relative.isBlank() || relative.contains('\\') || relative.contains(':') || relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) throw DlcSyncFailure("invalid_path")
        val root = roots()[index]
        val path = aliases[id] ?: root.resolve(relative).normalize()
        if (!path.startsWith(root) || path == root) throw DlcSyncFailure("outside_root")
        var part: Path? = path
        while (part != null && part.startsWith(root)) { if (Files.isSymbolicLink(part)) throw DlcSyncFailure("symbolic_path"); part = part.parent }
        return path
    }
    private fun file(root: Path, path: Path?, config: Boolean): File = file(root, path, path, config)
    private fun file(root: Path, path: Path?, canonical: Path?, config: Boolean): File {
        if (path == null) throw DlcSyncFailure("filename_missing")
        val id = id(root, canonical)
        if (!config) aliases[id] = path.toAbsolutePath().normalize()
        resolve(id)
        return File(id, if (Files.exists(path)) com.cozooo.dlc_manager.sync.DlcSyncArchive.size(path) else -1,
            if (Files.exists(path)) DlcFileMatch.hash(path) else null)
    }
    private fun items() = DlcScanner().scan(false).values.filterNotNull().flatMap { it.filterNotNull() }
    private fun remoteItem(root: Path, entry: DlcRemoteEntry, scanned: List<DlcItem>): Item {
        val data = entry.getTargetPath()
        var enabled: Boolean? = if (entry.getType() == DlcType.DLC_PACK) null else false
        for (item in scanned) if (data != null && item.getSourcePath()?.toAbsolutePath()?.normalize() == data.toAbsolutePath().normalize()) enabled = item.isEnabled()
        val resources = mutableListOf<File>()
        if (data != null) resources.add(file(root, data, entry.resolveTargetPath(data.fileName.toString()), false))
        return Item(id(root, entry.getConfigPath()), entry.getDlcIdentifier(), file(root, entry.getConfigPath(), true), resources, enabled, entry.getDisplayNames(), entry.getTips())
    }
    private fun requiredItem(root: Path, entry: DlcRequiredEntry): Item {
        var name = DlcRequiredService.resolveExistingFileName(entry)
        if (name.isNullOrBlank()) name = entry.getRequiredFileName()
        val resources = mutableListOf<File>()
        if (!name.isNullOrBlank()) resources.add(file(root, entry.resolveRequiredPath(name), entry.resolveDownloadPath(name), false))
        return Item(id(root, entry.getConfigPath()), entry.getIdentifier(), file(root, entry.getConfigPath(), true), resources, null, localized(entry.getConfigPath(), "DisplayName"), localized(entry.getConfigPath(), "tip"))
    }
    private fun localized(config: Path?, category: String): Map<String, String> {
        if (config == null) return emptyMap()
        val obj = DlcDcConfigIO.getObjectIgnoreCase(DlcDcConfigIO.read(config, DlcDcConfigIO.Layout.GENERIC), category)
        val result = linkedMapOf<String, String>()
        if (obj != null) for ((key, value) in obj.entrySet()) {
            val language = DlcDcConfigIO.normalizeLangKey(key) ?: continue
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString)
                result[language] = value.asString
        }
        return result
    }
    private fun synchronizable(path: Path?): Boolean {
        if (path == null) return false
        val name = path.fileName.toString()
        return DlcDcFormatUtil.isDcName(name) && !name.equals("notice.dc", true) && !name.equals("_metadata.dc", true)
    }
    private fun validateConfigurations(root: Path, category: String, depth: Int) {
        val directory = root.resolve(category)
        if (!Files.exists(directory)) return
        resolve(id(root, directory))
        Files.walk(directory, depth).use { paths ->
            for (path in paths.filter { Files.isRegularFile(it) }.filter(::synchronizable).filter { !it.startsWith(root.resolve("remote/redirect")) }.toList()) {
                if (Thread.currentThread().isInterrupted) throw DlcSyncFailure("comparison_canceled")
                resolve(id(root, path)); com.cozooo.dclanguage.DcDocuments.read(path)
            }
        }
    }
    override fun manifest(): List<Item> {
        val result = mutableListOf<Item>()
        val scanned = items()
        for (root in roots()) {
            validateConfigurations(root, "remote", Int.MAX_VALUE); validateConfigurations(root, "required", 1)
            for (entry in DlcRemoteLoader.load(root)) { if (!synchronizable(entry.getConfigPath())) continue; if (Thread.currentThread().isInterrupted) throw DlcSyncFailure("comparison_canceled"); result.add(remoteItem(root, entry, scanned)) }
            for (entry in DlcRequiredLoader.load(root)) { if (!synchronizable(entry.getConfigPath())) continue; if (Thread.currentThread().isInterrupted) throw DlcSyncFailure("comparison_canceled"); result.add(requiredItem(root, entry)) }
        }
        result.addAll(content.manifest())
        return result
    }
    private fun auxiliary(key: String) = key.startsWith("manager:") || key.substringAfter(':').startsWith("preset/") || key.substringAfter(':').startsWith("update/")
    override fun item(key: String): Item {
        validateKey(key)
        if (auxiliary(key)) return content.item(key, resolve(key))
        val root = roots()[key.substringBefore(':').toInt()]
        if (key.substringAfter(':').startsWith("remote/")) {
            val entry = DlcRemoteLoader.loadSingle(resolve(key), root) ?: throw DlcSyncFailure("invalid_remote")
            return remoteItem(root, entry, items())
        }
        return requiredItem(root, required(key))
    }
    override fun validateKey(key: String) {
        val path = resolve(key)
        val relative = key.substringAfter(':')
        if (key.startsWith("manager:")) return
        if (relative.startsWith("preset/") && DlcDcFormatUtil.isDcName(relative)) return
        if (relative.startsWith("update/") && relative.indexOf('/', 7) < 0 && relative.lowercase(java.util.Locale.ROOT).endsWith(".zip")) return
        if ((!relative.startsWith("remote/") && !relative.startsWith("required/")) || relative.startsWith("remote/redirect/") ||
            !DlcDcFormatUtil.isDcName(relative) || path.fileName.toString().equals("notice.dc", true) || path.fileName.toString().equals("_metadata.dc", true)) throw DlcSyncFailure("not_config")
        if (!previous.containsKey(key) && relative.startsWith("remote/") && Files.isRegularFile(path)) {
            val entry = DlcRemoteLoader.loadSingle(path, roots()[key.substringBefore(':').toInt()])
            if (entry?.getTargetPath() != null) for (item in items()) {
                if (item.getSourcePath()?.toAbsolutePath()?.normalize() == entry.getTargetPath()?.toAbsolutePath()?.normalize()) previous[key] = item
            }
        }
    }
    override fun validateIncoming(item: Item) {
        if (com.cozooo.dlc_manager.sync.DlcSyncModel.category(item) == Category.UPDATE && item.key !in com.cozooo.dlc_manager.sync.DlcSyncModel.eligibleUpdates(listOf(item), content.manifest()))
            throw DlcSyncFailure("update_version")
    }
    override fun writeSettings(key: String, settings: com.google.gson.JsonObject, journal: Journal) {
        if (key != "manager:config.dc") throw DlcSyncFailure("not_settings")
        DlcSyncSettings.write(content.managerPath(key), settings, journal)
    }
    override fun journal(directory: Path): Journal {
        val presetPaths = com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.getAppliedDisabledRelativePathsByPreset()
        val journal = DlcUpdateJournal.openSync(game, directory)
        try {
            mark(directory, "pending")
            com.cozooo.dlc_manager.client.preset.DlcPresetStateStore.getStatePath()?.let(journal::capture)
            for (root in roots()) {
                journal.capture(root.resolve("tmp/state.dc"))
                journal.capture(root.resolve("tmp/dlc/history.dc"))
                journal.capture(root.resolve("tmp/required/history.dc"))
            }
        } catch (error: Exception) { journal.close(); throw error }
        return object : Journal {
            override fun capture(path: Path) = journal.capture(path)
            override fun rollback() { journal.rollback(); reload(); com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.setAppliedDisabledRelativePathsByPreset(presetPaths); mark(directory, "restored") }
            override fun commit() = mark(directory, "committed")
            override fun close() = journal.close()
        }
    }
    override fun prepare(key: String, force: Boolean, canceled: BooleanSupplier, progress: Progress) {
        validateKey(key)
        if (auxiliary(key)) return
        val config = resolve(key)
        val root = roots()[key.substringBefore(':').toInt()]
        DlcUpdateJournal.beforeWrite(config)
        if (key.substringAfter(':').startsWith("remote/")) {
            val entry = DlcRemoteLoader.loadSingle(config, root) ?: throw DlcSyncFailure("invalid_remote")
            if (force && entry.getTargetPath() != null && Files.exists(entry.getTargetPath())) {
                DlcUpdateJournal.beforeWrite(entry.getTargetPath()); DlcResourceHistory.delete(entry.getTargetPath() ?: throw DlcSyncFailure("remote_missing"))
            }
            if (entry.getTargetPath() == null || !Files.exists(entry.getTargetPath())) DlcRemoteService.downloadOne(entry,
                { done, total -> progress.update("downloading", key, done, total) }, { canceled.asBoolean })
        } else {
            val entry = required(key)
            val existing = DlcRequiredService.resolveExistingFileName(entry)
            if (force && existing != null) { val path = entry.resolveRequiredPath(existing) ?: throw DlcSyncFailure("resource_missing", key); DlcUpdateJournal.beforeWrite(path); DlcResourceHistory.delete(path) }
            val available = DlcRequiredService.resolveExistingFileName(entry)
            if (available == null || entry.resolveRequiredPath(available) == null || !Files.isRegularFile(entry.resolveRequiredPath(available))) DlcRequiredService.downloadOne(entry,
                { done, total -> progress.update("downloading", key, done, total) }, { canceled.asBoolean })
        }
    }
    private fun required(key: String): DlcRequiredEntry {
        val config = resolve(key)
        for (root in roots()) for (entry in DlcRequiredLoader.load(root)) if (entry.getConfigPath()?.toAbsolutePath()?.normalize() == config) return entry
        throw DlcSyncFailure("invalid_required")
    }
    override fun apply(key: String, enabled: Boolean?, journal: Journal, canceled: BooleanSupplier): Boolean {
        validateKey(key)
        if (canceled.asBoolean) throw DlcSyncFailure("canceled")
        if (auxiliary(key)) {
            val value = content.item(key, resolve(key))
            if (com.cozooo.dlc_manager.sync.DlcSyncModel.category(value) == Category.UPDATE) { validateIncoming(value); return false }
            if (com.cozooo.dlc_manager.sync.DlcSyncModel.category(value) == Category.PRESET) return enabled == null || DlcSyncPresets.apply(value.identifier ?: throw DlcSyncFailure("preset_missing", key), enabled, canceled)
            return true
        }
        if (key.substringAfter(':').startsWith("required/")) {
            val entry = required(key)
            DlcRequiredInstaller.install(entry) { path -> if (canceled.asBoolean) throw DlcSyncFailure("canceled"); journal.capture(path) }
            return entry.resolveInstallationTargets(DlcRequiredService.resolveExistingFileName(entry)).isNotEmpty()
        }
        val root = roots()[key.substringBefore(':').toInt()]
        val remote = DlcRemoteLoader.loadSingle(resolve(key), root) ?: throw DlcSyncFailure("remote_missing")
        val target = remote.getTargetPath() ?: throw DlcSyncFailure("remote_missing")
        if (!Files.exists(target)) throw DlcSyncFailure("remote_missing")
        if (remote.getType() == DlcType.DLC_PACK) return false
        for (item in items()) if (item.getSourcePath()?.toAbsolutePath()?.normalize() == target.toAbsolutePath().normalize()) {
            val old = previous[key]
            val restart = item.isEnabled() || enabled == true || old?.isEnabled() == true
            if (old != null && old.getId() != item.getId() && old.isEnabled()) { old.setEnabled(false); DlcApplier.applySynced(old) }
            if (enabled != null) item.setEnabled(enabled)
            DlcApplier.applySynced(item)
            return restart
        }
        throw DlcSyncFailure("apply")
    }
    private fun mark(directory: Path, status: String) {
        val data = com.google.gson.JsonObject(); data.addProperty("status", status)
        val temp = directory.resolve("sync.pending.dc")
        Files.createDirectories(directory)
        if (!DlcDcConfigIO.write(temp, data, DlcDcConfigIO.Layout.GENERIC)) throw DlcSyncFailure("recovery_save")
        try { Files.move(temp, directory.resolve("sync.dc"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temp, directory.resolve("sync.dc"), StandardCopyOption.REPLACE_EXISTING) }
    }
    override fun recoverOperation(operation: String) {
        java.util.UUID.fromString(operation)
        restore(workDirectory().resolve(operation), true)
    }
    override fun recover() = restore(workDirectory(), false)
    private fun restore(directory: Path, committed: Boolean) {
        if (!Files.isDirectory(directory)) return
        var restored = false
        Files.walk(directory).use { paths ->
            for (path in paths.filter { it.fileName.toString() == "sync.dc" }.sorted(Comparator.reverseOrder()).toList()) {
                val data = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC) ?: throw DlcSyncFailure("invalid_recovery", "$path")
                val status = DlcDcConfigIO.getStringIgnoreCase(data, "status")
                if (status != "pending" && !(committed && status == "committed")) continue
                DlcUpdateJournal.open(game, path.parent).use { it.rollback() }
                mark(path.parent, "restored")
                restored = true
            }
        }
        reload()
        if (restored) com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.setAppliedDisabledRelativePathsByPreset(com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.getDesiredDisabledRelativePathsByPreset())
    }
    override fun reload() { DlcStateConfig.reloadFromDisk(); DlcRuntimeState.updateItemIndex(items()); DlcSyncPresets.reload() }
}
