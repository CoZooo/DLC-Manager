package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.client.core.DlcZipUtil
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.Locale

class DlcPackUpdateCoordinator(
    private val instanceRoot: Path,
    private val dlcRoot: Path,
) {
    private val installer = DlcPackInstaller(instanceRoot, dlcRoot)

    fun check(targetBase: String?, targetDlc: String?): DlcPackUpdateGuard.CheckResult {
        return DlcPackUpdateGuard.check(dlcRoot, targetBase, targetDlc)
    }

    fun uninstallIncompatibleForCurrentVersion(): List<DlcPackInstaller.UninstallReport> {
        val version = DlcUpdateVersion.load(dlcRoot)
        val check = check(version?.getBase(), version?.getDlc())
        if (!check.hasIncompatiblePacks()) {
            return emptyList()
        }
        val reports = ArrayList<DlcPackInstaller.UninstallReport>()
        var failure: IOException? = null
        for (status in check.incompatiblePacks) {
            val meta = status.meta ?: continue
            try {
                reports.add(installer.uninstall(meta.identifier))
            } catch (ex: IOException) {
                if (failure == null) {
                    failure = ex
                } else {
                    failure.addSuppressed(ex)
                }
            }
        }
        failure?.let { throw it }
        return reports
    }

    fun prepareForUpdate(targetBase: String?, targetDlc: String?, uninstallIncompatible: Boolean): UpdateSession {
        return prepareForUpdate(check(targetBase, targetDlc), uninstallIncompatible)
    }

    fun prepareForUpdate(check: DlcPackUpdateGuard.CheckResult?, uninstallIncompatible: Boolean): UpdateSession {
        if (check == null || !check.hasInstalledPacks()) {
            return UpdateSession(emptyList())
        }
        if (check.hasIncompatiblePacks() && !uninstallIncompatible) {
            throw IOException("DLC Pack is incompatible with target modpack version")
        }
        val snapshots = ArrayList<Snapshot>()
        try {
            for (status in check.packs) {
                if (status.meta == null) {
                    throw IOException("DLC Pack meta is missing: ${status.identifier()}")
                }
                snapshots.add(snapshot(status))
            }
            for (snapshot in snapshots) {
                installer.uninstall(snapshot.meta.identifier)
                snapshot.uninstalled = true
            }
            return UpdateSession(snapshots)
        } catch (error: Throwable) {
            restoreSnapshots(snapshots.filter { it.uninstalled }, true)
            snapshots.forEach { DlcZipUtil.deleteRecursive(it.staging) }
            if (error is IOException) {
                throw error
            }
            throw IOException("Failed to prepare DLC Packs for update", error)
        }
    }

    private fun snapshot(status: DlcPackUpdateGuard.PackStatus): Snapshot {
        val meta = status.meta ?: throw IOException("DLC Pack meta is missing: ${status.identifier()}")
        val identifier = meta.identifier
        val stagingRoot = dlcRoot.resolve("tmp").resolve("_staging")
        val staging = DlcZipUtil.createUniqueDir(stagingRoot, identifier)
        val packDirSnapshot = staging.resolve("pack_dir")
        val installedSnapshot = staging.resolve("installed")
        copyRecursive(status.packDir, packDirSnapshot)
        for (relative in meta.installedPaths) {
            val source = resolveInside(instanceRoot, relative)
            if (!Files.exists(source)) {
                continue
            }
            val target = resolveInside(installedSnapshot, relative)
            copyRecursive(source, target)
        }
        val txn = DlcPackTransaction.running(identifier, staging.fileName.toString())
        txn.replacement = true
        txn.save(staging.resolve("txn.dc"))
        return Snapshot(meta, status.compatible, staging, packDirSnapshot, installedSnapshot)
    }

    private fun restoreSnapshots(snapshots: List<Snapshot>, includeIncompatible: Boolean) {
        var failure: IOException? = null
        for (snapshot in snapshots) {
            if (!includeIncompatible && !snapshot.compatible) {
                continue
            }
            try {
                restoreSnapshot(snapshot)
            } catch (ex: IOException) {
                if (failure == null) {
                    failure = ex
                } else {
                    failure.addSuppressed(ex)
                }
            }
        }
        failure?.let { throw it }
    }

    private fun restoreSnapshot(snapshot: Snapshot) {
        val packDir = installer.packDir(snapshot.meta.identifier)
        DlcZipUtil.deleteRecursive(packDir)
        copyRecursive(snapshot.packDirSnapshot, packDir)
        val metadata = readArchivedMetadata(packDir)
        if (metadata != null) {
            moveConfiguredConflicts(metadata.conflicts, packDir)
        }
        for (relative in snapshot.meta.installedPaths) {
            val source = resolveInside(snapshot.installedSnapshot, relative)
            if (!Files.exists(source)) {
                continue
            }
            val target = resolveInside(instanceRoot, relative)
            if (Files.exists(target)) {
                val displaced = resolveInside(packDir, relative)
                Files.createDirectories(displaced.parent)
                Files.move(target, displaced, StandardCopyOption.REPLACE_EXISTING)
            }
            copyRecursive(source, target)
        }
    }

    private fun readArchivedMetadata(packDir: Path): DlcPackMetadata? {
        val metadataPath = packDir.resolve("metadata").resolve("pack.dc")
        if (!Files.isRegularFile(metadataPath)) {
            return null
        }
        val result = DlcPackMetadata.read(metadataPath)
        return if (result.valid) result.metadata else null
    }

    private fun moveConfiguredConflicts(conflicts: List<String>?, packDir: Path) {
        if (conflicts.isNullOrEmpty()) {
            return
        }
        for (conflict in conflicts) {
            val relative = DlcPackMeta.normalizeRelativePath(conflict) ?: continue
            if (DlcPackInstallPlan.isForbiddenPath(relative, instanceRoot, dlcRoot)) {
                throw IOException("DLC Pack conflict path is forbidden: $relative")
            }
            if (isPackRecordPath(relative)) {
                continue
            }
            val target = resolveInside(instanceRoot, relative)
            if (!Files.exists(target)) {
                continue
            }
            val displaced = resolveInside(packDir, relative)
            Files.createDirectories(displaced.parent)
            Files.move(target, displaced, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun resolveInside(root: Path, relative: String?): Path {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedRelative = DlcPackMeta.normalizeRelativePath(relative)
            ?: throw IOException("Invalid relative path: $relative")
        val target = normalizedRoot.resolve(normalizedRelative).normalize()
        if (!target.startsWith(normalizedRoot)) {
            throw IOException("Path escapes root: $relative")
        }
        return target
    }

    private fun copyRecursive(source: Path?, target: Path?) {
        if (source == null || target == null || !Files.exists(source)) {
            return
        }
        if (Files.isRegularFile(source)) {
            Files.createDirectories(target.parent)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            return
        }
        Files.walk(source).use { stream ->
            stream.sorted(Comparator.naturalOrder()).forEach { path ->
                val relative = source.relativize(path)
                val destination = target.resolve(relative).normalize()
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination)
                } else if (Files.isRegularFile(path)) {
                    Files.createDirectories(destination.parent)
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun isPackRecordPath(relative: String): Boolean {
        val normalized = relative.lowercase(Locale.ROOT)
        return normalized == "meta.dc" ||
            normalized.startsWith("metadata/") ||
            normalized.startsWith("cf_cache/")
    }

    inner class UpdateSession internal constructor(
        private val snapshots: List<Snapshot>,
    ) {
        fun hasPacks(): Boolean = snapshots.isNotEmpty()

        fun hasIncompatiblePacks(): Boolean = snapshots.any { !it.compatible }

        fun identifiers(): List<String> = snapshots.map { it.meta.identifier }

        fun finishAfterSuccessfulUpdate() {
            markCompleted(snapshots.filter { !it.compatible })
            restoreSnapshots(snapshots, false)
            markCompleted(snapshots.filter { it.compatible })
            cleanup()
        }

        fun restoreAfterFailedUpdate() {
            restoreSnapshots(snapshots, true)
            markCompleted(snapshots)
            cleanup()
        }

        private fun markCompleted(completed: List<Snapshot>) {
            for (snapshot in completed) {
                val txnPath = snapshot.staging.resolve("txn.dc")
                val txn = DlcPackTransaction.load(txnPath) ?: continue
                txn.status = DlcPackTransaction.Status.COMMITTED
                txn.save(txnPath)
            }
        }

        fun cleanup() {
            snapshots.forEach { DlcZipUtil.deleteRecursive(it.staging) }
        }
    }

    data class Snapshot(
        val meta: DlcPackMeta,
        val compatible: Boolean,
        val staging: Path,
        val packDirSnapshot: Path,
        val installedSnapshot: Path,
        var uninstalled: Boolean = false,
    )
}
