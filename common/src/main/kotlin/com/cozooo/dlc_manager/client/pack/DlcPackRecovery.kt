package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.client.core.DlcZipUtil
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.core.DlcRuntimeState
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.Locale

object DlcPackRecovery {
    @JvmStatic
    fun recover(instanceRoot: Path?, dlcRoot: Path?): RecoveryReport {
        val report = RecoveryReport()
        if (instanceRoot == null || dlcRoot == null) {
            return report
        }
        val stagingRoot = dlcRoot.resolve("tmp").resolve("_staging").toAbsolutePath().normalize()
        if (!Files.isDirectory(stagingRoot)) {
            return report
        }
        try {
            Files.list(stagingRoot).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .toList()
                    .mapNotNull { staging ->
                        val txnPath = staging.resolve("txn.dc")
                        if (!Files.exists(txnPath)) {
                            report.unknown++
                            return@mapNotNull null
                        }
                        val txn = DlcPackTransaction.load(txnPath)
                        if (txn == null) {
                            report.unknown++
                            return@mapNotNull null
                        }
                        StagingTxn(staging, txn)
                    }
                    .sortedBy { it.txn.replacement }
                    .forEach { recoverStaging(instanceRoot, dlcRoot, it.staging, it.txn, report) }
            }
        } catch (_: IOException) {
        }
        return report
    }

    private fun recoverStaging(instanceRoot: Path, dlcRoot: Path, staging: Path, txn: DlcPackTransaction, report: RecoveryReport) {
        val txnPath = staging.resolve("txn.dc")
        report.transactions++
        if (txn.status == DlcPackTransaction.Status.RUNNING) {
            val packDir = packDir(dlcRoot, txn.identifier)
            if (Files.exists(packDir.resolve("meta.dc"))) {
                report.cleaned++
                DlcZipUtil.deleteRecursive(staging)
                return
            }
            if (txn.replacement) {
                restoreReplacement(instanceRoot, dlcRoot, staging, report)
            } else {
                rollback(instanceRoot, packDir, txn, report)
                DlcZipUtil.deleteRecursive(packDir)
            }
            txn.status = DlcPackTransaction.Status.ROLLED_BACK
            txn.save(txnPath)
            report.rolledBack++
        } else {
            report.cleaned++
        }
        DlcZipUtil.deleteRecursive(staging)
    }

    private fun restoreReplacement(instanceRoot: Path, dlcRoot: Path, staging: Path, report: RecoveryReport) {
        val backupDir = staging.resolve("replacement_backup")
        var backupPackDir = backupDir.resolve("pack_dir")
        var installedSnapshot = backupDir.resolve("installed")
        if (!Files.isDirectory(backupPackDir)) {
            backupPackDir = staging.resolve("pack_dir")
            installedSnapshot = staging.resolve("installed")
        }
        val oldMeta = DlcPackMeta.load(backupPackDir.resolve("meta.dc")) ?: return
        val packDir = packDir(dlcRoot, oldMeta.identifier)
        try {
            deleteArchivedTargetsFromInstance(instanceRoot, backupPackDir)
            DlcZipUtil.deleteRecursive(packDir)
            copyRecursive(backupPackDir, packDir)
            for (relative in oldMeta.installedPaths) {
                val source = resolveInside(installedSnapshot, relative)
                if (!Files.exists(source)) {
                    continue
                }
                val target = resolveInside(instanceRoot, relative)
                copyRecursive(source, target)
                report.restored++
            }
        } catch (_: IOException) {
        }
    }

    private fun rollback(instanceRoot: Path, packDir: Path, txn: DlcPackTransaction, report: RecoveryReport) {
        for (relative in txn.writtenPaths.asReversed()) {
            try {
                val target = resolveInside(instanceRoot, relative)
                if (Files.deleteIfExists(target)) {
                    report.deletedWritten++
                }
                pruneEmptyParents(target.parent, instanceRoot)
            } catch (_: Exception) {
            }
        }
        val archivedPaths = archivedRelativePaths(packDir)
        archivedPaths.addAll(txn.movedPaths)
        val toRestore = archivedPaths.toMutableList()
        toRestore.sortBy { it.length }
        for (relative in toRestore) {
            try {
                val archived = resolveInside(packDir, relative)
                if (!Files.exists(archived)) {
                    continue
                }
                val target = resolveInside(instanceRoot, relative)
                Files.createDirectories(target.parent)
                Files.move(archived, target, StandardCopyOption.REPLACE_EXISTING)
                report.restored++
            } catch (_: Exception) {
            }
        }
        restoreRuntimeChanges(txn)
    }

    private fun archivedRelativePaths(packDir: Path): MutableSet<String> {
        val paths = LinkedHashSet<String>()
        if (!Files.isDirectory(packDir)) {
            return paths
        }
        try {
            Files.walk(packDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .sorted(Comparator.reverseOrder())
                    .forEach { archived ->
                        val relative = DlcPackMeta.normalizeRelativePath(packDir.relativize(archived).toString())
                        if (relative != null && !isPackRecordPath(relative)) {
                            paths.add(relative)
                        }
                    }
            }
        } catch (_: IOException) {
        }
        return paths
    }

    private fun deleteArchivedTargetsFromInstance(instanceRoot: Path, backupPackDir: Path) {
        if (!Files.isDirectory(backupPackDir)) {
            return
        }
        Files.walk(backupPackDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .sorted(Comparator.reverseOrder())
                .forEach { archived ->
                    val relative = DlcPackMeta.normalizeRelativePath(backupPackDir.relativize(archived).toString())
                    if (relative == null || isPackRecordPath(relative)) {
                        return@forEach
                    }
                    val target = resolveInside(instanceRoot, relative)
                    Files.deleteIfExists(target)
                    pruneEmptyParents(target.parent, instanceRoot)
                }
        }
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

    private fun restoreRuntimeChanges(txn: DlcPackTransaction) {
        for (id in txn.enabledDlc) {
            try {
                DlcRuntimeState.getItemByIdentifier(id)?.setEnabled(false)
            } catch (_: Exception) {
            }
        }
        for (id in txn.disabledDlc) {
            try {
                DlcRuntimeState.getItemByIdentifier(id)?.setEnabled(true)
            } catch (_: Exception) {
            }
        }
        for (id in txn.enabledPresets) {
            try {
                DlcPresetRuntimeState.setPresetEnabled(id, false)
            } catch (_: Exception) {
            }
        }
        for (id in txn.disabledPresets) {
            try {
                DlcPresetRuntimeState.setPresetEnabled(id, true)
            } catch (_: Exception) {
            }
        }
    }

    private fun packDir(dlcRoot: Path, identifier: String): Path {
        return dlcRoot.resolve("tmp").resolve("pack").resolve(identifier).toAbsolutePath().normalize()
    }

    private fun resolveInside(root: Path, relative: String): Path {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedRelative = DlcPackMeta.normalizeRelativePath(relative) ?: throw IOException("Invalid relative path: $relative")
        val target = normalizedRoot.resolve(normalizedRelative).normalize()
        if (!target.startsWith(normalizedRoot)) {
            throw IOException("Path escapes root: $relative")
        }
        return target
    }

    private fun isPackRecordPath(relative: String): Boolean {
        val normalized = relative.lowercase(Locale.ROOT)
        return normalized == "meta.dc" ||
            normalized.startsWith("metadata/")
    }

    private fun pruneEmptyParents(start: Path?, stop: Path) {
        var dir = start
        val normalizedStop = stop.toAbsolutePath().normalize()
        while (dir != null) {
            val current = dir.toAbsolutePath().normalize()
            if (current == normalizedStop || !current.startsWith(normalizedStop)) {
                break
            }
            try {
                Files.list(current).use { stream ->
                    if (stream.findAny().isPresent) {
                        return
                    }
                }
                Files.deleteIfExists(current)
            } catch (_: IOException) {
                break
            }
            dir = current.parent
        }
    }

    class RecoveryReport {
        @JvmField var transactions = 0
        @JvmField var rolledBack = 0
        @JvmField var cleaned = 0
        @JvmField var deletedWritten = 0
        @JvmField var restored = 0
        @JvmField var unknown = 0
    }

    private data class StagingTxn(val staging: Path, val txn: DlcPackTransaction)
}
