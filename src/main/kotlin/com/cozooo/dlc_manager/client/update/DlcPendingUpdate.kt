package com.cozooo.dlc_manager.client.update

import com.cozooo.dclanguage.DcDocuments
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator
import com.cozooo.dlc_manager.client.pack.DlcPackRecovery
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcFileMatch
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcStateConfig
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.function.Consumer

object DlcPendingUpdate {
    @JvmStatic
    @Synchronized
    fun schedule(packages: List<DlcUpdatePackage>, forceMissing: Boolean) {
        val configuredRoot: Path? = DlcPaths.dlcRoot()
        val root = configuredRoot ?: throw IOException("Missing DLC root")
        val update = root.resolve("tmp/update")
        val previous = locateUpdate(root)
        if (Files.exists(previous.resolve("pending_update.dc")) || Files.exists(previous.resolve("pending_recovery.dc"))) {
            throw IOException("A pending update already exists")
        }
        if (Files.exists(update.resolve("pending_update.dc")) || Files.exists(update.resolve("pending_recovery.dc"))) {
            throw IOException("A pending update already exists")
        }
        val id = UUID.randomUUID().toString()
        val directory = update.resolve("jobs").resolve(id)
        Files.createDirectories(directory)
        val archives = JsonArray()
        var uninstall = false
        for ((index, pkg) in packages.withIndex()) {
            val name = "archive-$index.zip"
            val copy = directory.resolve(name)
            Files.copy(pkg.getArchivePath(), copy, StandardCopyOption.COPY_ATTRIBUTES)
            val archive = JsonObject()
            archive.addProperty("file", name)
            archive.addProperty("hash", DlcFileMatch.hash(copy))
            archive.addProperty("ignore_unknown", pkg.getCompatibilityAction() == DlcUpdatePackage.CompatibilityAction.IGNORE)
            val worlds = JsonArray()
            pkg.getBackupWorlds().forEach { worlds.add(it) }
            archive.add("backup_worlds", worlds)
            archive.addProperty("restore_worlds", pkg.isRestoreWorldsAfterReapply())
            archives.add(archive)
            uninstall = uninstall || pkg.isAllowUninstallPacks()
        }
        val request = JsonObject()
        request.addProperty("format", 1)
        request.addProperty("job", id)
        request.addProperty("force_missing", forceMissing)
        request.addProperty("uninstall_incompatible_packs", uninstall)
        request.add("archives", archives)
        write(update.resolve("pending_update.dc"), request)
        try { locate(update) }
        catch (exception: IOException) {
            Files.deleteIfExists(update.resolve("pending_update.dc"))
            throw exception
        }
    }

    @JvmStatic
    @Synchronized
    fun process(log: Consumer<String>, restartRequired: Boolean): Boolean {
        val configuredRoot: Path? = DlcPaths.dlcRoot()
        val configured = configuredRoot ?: return false
        val update = locateUpdate(configured)
        val root = update.parent.parent
        val pending = update.resolve("pending_update.dc")
        val recovery = update.resolve("pending_recovery.dc")
        if (!Files.exists(pending) && !Files.exists(recovery)) {
            Files.deleteIfExists(locatorPath())
            return false
        }
        var directory: Path? = null
        var journal: DlcUpdateJournal? = null
        var packSession: DlcPackUpdateCoordinator.UpdateSession? = null
        var recovered = true
        try {
            locate(update)
            if (Files.exists(recovery)) {
                val previous = DcDocuments.read(recovery).toJson()
                directory = job(update, previous.get("job").asString)
                if (!previous.has("committed") || !previous.get("committed").asBoolean) {
                    journal = DlcUpdateJournal.open(DlcPaths.gameDir(), directory)
                    DlcPackRecovery.recover(DlcPaths.gameDir(), root)
                    journal.rollback()
                    throw IOException("The previous update was interrupted and has been rolled back")
                }
                saveResult(true, "OK", restartRequired)
                return true
            }
            val request = DcDocuments.read(pending).toJson()
            if (!request.has("format") || request.get("format").asInt != 1) throw IOException("Unsupported pending update format")
            directory = job(update, request.get("job").asString)
            val packages = mutableListOf<DlcUpdatePackage>()
            for (value in request.getAsJsonArray("archives")) {
                val archive = value.asJsonObject
                val path = directory.resolve(archive.get("file").asString).normalize()
                if (!path.startsWith(directory) || !Files.isRegularFile(path) || Files.isSymbolicLink(path)) throw IOException("Invalid pending update archive")
                if (DlcFileMatch.hash(path) != archive.get("hash").asString) throw IOException("Pending update archive changed: ${path.fileName}")
                val pkg = DlcUpdatePackage.fromZip(path) ?: throw IOException("Invalid pending update package")
                val ignore = archive.has("ignore_unknown") && archive.get("ignore_unknown").asBoolean
                if (!ignore && pkg.getManifest().getUnknownParameters().isNotEmpty()) {
                    throw IOException("Unknown update parameters: " + pkg.getManifest().getUnknownParameters().joinToString(", "))
                }
                pkg.setCompatibilityAction(if (ignore) DlcUpdatePackage.CompatibilityAction.IGNORE else DlcUpdatePackage.CompatibilityAction.RESUME)
                if (archive.has("backup_worlds")) pkg.setBackupWorlds(archive.getAsJsonArray("backup_worlds").map { it.asString })
                pkg.setRestoreWorldsAfterReapply(archive.has("restore_worlds") && archive.get("restore_worlds").asBoolean)
                packages.add(pkg)
            }
            if (packages.isEmpty()) throw IOException("Pending update contains no packages")
            val marker = JsonObject()
            marker.addProperty("job", request.get("job").asString)
            marker.addProperty("committed", false)
            write(recovery, marker)
            journal = DlcUpdateJournal.open(DlcPaths.gameDir(), directory)
            journal.capture(DlcPaths.gameDir().resolve("config/dlc_manager"))
            for (dlcRoot in DlcConfig.getDlcRoots()) {
                for (folder in listOf("info", "remote", "required", "preset", "tmp/pack", "tmp/history", "tmp/state.dc")) journal.capture(dlcRoot.resolve(folder))
            }
            DlcStateConfig.reloadFromDisk()
            for (state in DlcStateConfig.getAllStates().values) {
                state.appliedTargets?.forEach { journal.capture(DlcPaths.gameDir().resolve(it)) }
            }
            val coordinator = DlcPackUpdateCoordinator(DlcPaths.gameDir(), root)
            val version = packages.last().getManifest().getVersion() ?: throw IOException("Missing version info")
            packSession = coordinator.prepareForUpdate(version.getBaseNew() ?: version.getBase(), version.getDlcNew() ?: version.getDlcOld(),
                request.has("uninstall_incompatible_packs") && request.get("uninstall_incompatible_packs").asBoolean)
            val result = DlcUpdateService.resumeUpdates(packages,
                request.has("force_missing") && request.get("force_missing").asBoolean) { status, _ -> log.accept(status.orEmpty()) }
            if (!result.isSuccess || result.isHelperScheduled) throw IOException(result.message)
            packSession.finishAfterSuccessfulUpdate()
            DlcConfig.reloadFromDisk(com.cozooo.dlc_manager.platform.DlcPlatformServices.isDedicatedServer())
            marker.addProperty("committed", true)
            write(recovery, marker)
            saveResult(true, "OK", restartRequired)
            log.accept(if (restartRequired) "Pending hot update completed; restart required to reload mods" else "Pending hot update completed")
            return true
        } catch (failure: Exception) {
            recovered = false
            try {
                packSession?.restoreAfterFailedUpdate()
                if (journal != null) {
                    journal.rollback()
                    DlcUpdateHashIndex.invalidate(DlcPaths.gameDir(), root, listOf("mods"))
                    DlcConfig.reloadFromDisk(com.cozooo.dlc_manager.platform.DlcPlatformServices.isDedicatedServer())
                    DlcStateConfig.reloadFromDisk()
                    com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.clearAll()
                    com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState.reload()
                }
                recovered = true
            } catch (restore: Exception) {
                failure.addSuppressed(restore)
            }
            val message = failure.message ?: failure.toString()
            saveResult(false, message, false, recovered)
            log.accept("Pending hot update failed: $message")
            if (!recovered) throw IOException("Pending update rollback could not complete", failure)
            return true
        } finally {
            journal?.close()
            Files.deleteIfExists(pending)
            if (recovered) {
                Files.deleteIfExists(recovery)
                Files.deleteIfExists(locatorPath())
                directory?.let { DlcUpdateService.deleteRecursive(it) }
            }
        }
    }

    @JvmStatic
    fun hasPending(): Boolean {
        val root: Path? = DlcPaths.dlcRoot()
        if (root == null) return false
        val directory = locateUpdate(root)
        return Files.exists(directory.resolve("pending_update.dc")) || Files.exists(directory.resolve("pending_recovery.dc"))
    }

    @JvmStatic
    fun readResult(): JsonObject? {
        val path = resultPath()
        return if (Files.exists(path)) DcDocuments.read(path).toJson() else null
    }

    @JvmStatic
    fun acknowledgeResult() { Files.deleteIfExists(resultPath()) }

    private fun resultPath(): Path = DlcPaths.gameDir().resolve(".dlc-manager/pending_update_result.dc")

    private fun locatorPath(): Path = DlcPaths.gameDir().resolve(".dlc-manager/pending_update_location.dc")

    private fun locate(update: Path) {
        val locator = JsonObject()
        locator.addProperty("directory", DlcUpdateJournal.relativePath(DlcPaths.gameDir(), update))
        write(locatorPath(), locator)
    }

    private fun locateUpdate(root: Path): Path {
        if (!Files.exists(locatorPath())) return root.resolve("tmp/update").toAbsolutePath().normalize()
        val path = DlcUpdateJournal.resolveRelativePath(DlcPaths.gameDir(), DcDocuments.read(locatorPath()).toJson().get("directory").asString)
        if (!path.endsWith(Path.of("tmp/update"))) throw IOException("Invalid pending update location")
        return path
    }

    private fun job(update: Path, id: String): Path {
        if (!id.matches(Regex("[0-9a-fA-F-]{36}"))) throw IOException("Invalid pending update job")
        val directory = update.resolve("jobs").resolve(id).toAbsolutePath().normalize()
        var part: Path? = directory
        while (part != null && part.startsWith(update.toAbsolutePath().normalize())) {
            if (Files.isSymbolicLink(part)) throw IOException("Invalid pending update job link")
            part = part.parent
        }
        return directory
    }

    private fun saveResult(success: Boolean, message: String, restart: Boolean, rollbackComplete: Boolean = true) {
        val result = JsonObject()
        result.addProperty("success", success)
        result.addProperty("rollback_complete", rollbackComplete)
        result.addProperty("message", message)
        result.addProperty("restart_required", restart)
        write(resultPath(), result)
    }

    private fun write(path: Path, value: JsonObject) { DcDocuments.write(path, DcDocuments.fromJson(value)) }
}
