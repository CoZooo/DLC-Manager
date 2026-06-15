package com.cozooo.dlc_manager.client.pack

import com.cozooo.dlc_manager.client.download.DlcDownloadService
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcRuntimeState
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Comparator
import java.util.Locale
import kotlin.io.path.name

class DlcPackInstaller(
    private val instanceRoot: Path,
    private val dlcRoot: Path,
) {
    fun install(pack: DlcPackPackage): InstallReport {
        return install(pack, false)
    }

    fun install(pack: DlcPackPackage, replaceExisting: Boolean): InstallReport {
        return install(pack, replaceExisting, InstallOptions.strict())
    }

    fun install(pack: DlcPackPackage, options: InstallOptions): InstallReport {
        return install(pack, false, options)
    }

    fun install(pack: DlcPackPackage, replaceExisting: Boolean, options: InstallOptions?): InstallReport {
        val effectiveOptions = options ?: InstallOptions.strict()
        val metadata = pack.metadata
        val identifier = metadata.identifier
        val packDir = packDir(identifier)
        val metaPath = packDir.resolve("meta.dc")
        if (Files.exists(metaPath)) {
            if (!replaceExisting) {
                throw IOException("DLC Pack already installed: $identifier")
            }
            return replaceInstalledPack(pack, effectiveOptions, packDir, metaPath)
        }
        return installFresh(pack, effectiveOptions)
    }

    private fun replaceInstalledPack(
        pack: DlcPackPackage,
        options: InstallOptions,
        packDir: Path,
        metaPath: Path,
    ): InstallReport {
        val oldMeta = DlcPackMeta.load(metaPath) ?: throw IOException("DLC Pack is not installed: ${pack.metadata.identifier}")
        val precheck = precheck(pack)
        validatePrecheck(precheck, options)
        val replacementStaging = stagingDir(pack.metadata.identifier)
        val backupDir = replacementStaging.resolve("replacement_backup")
        val txnPath = replacementStaging.resolve("txn.dc")
        val txn = DlcPackTransaction.running(pack.metadata.identifier, replacementStaging.name)
        txn.replacement = true
        try {
            Files.createDirectories(replacementStaging)
            txn.save(txnPath)
            backupExistingInstallation(oldMeta, packDir, backupDir)
            uninstall(oldMeta.identifier)
            val report = installFresh(pack, options)
            txn.status = DlcPackTransaction.Status.COMMITTED
            txn.save(txnPath)
            return report
        } catch (error: Throwable) {
            try {
                restoreExistingInstallation(oldMeta, packDir, backupDir)
            } catch (restoreError: Throwable) {
                error.addSuppressed(restoreError)
            }
            if (error is IOException) {
                throw error
            }
            throw IOException("Failed to replace DLC Pack: ${pack.metadata.identifier}", error)
        } finally {
            deleteRecursive(replacementStaging)
        }
    }

    private fun installFresh(pack: DlcPackPackage, effectiveOptions: InstallOptions): InstallReport {
        val metadata = pack.metadata
        val identifier = metadata.identifier
        val packDir = packDir(identifier)
        val metaPath = packDir.resolve("meta.dc")
        if (Files.exists(metaPath)) {
            throw IOException("DLC Pack already installed: $identifier")
        }
        val plan = DlcPackInstallPlan.build(pack, instanceRoot, dlcRoot)
        val compatibility = resolveCompatibility(metadata)
        val dependencies = resolveDependencies(metadata.dependencies)
        val conflicts = resolveIdentifierConflicts(metadata.conflicts)
        val precheck = PrecheckResult(
            plan,
            compatibility.incompatible.toList(),
            dependencies.missing.toList(),
            dependencies.dlcToEnable.toList(),
            dependencies.presetsToEnable.toList(),
            conflicts.dlcToDisable.toList(),
            conflicts.presetsToDisable.toList(),
        )
        validatePrecheck(precheck, effectiveOptions)
        val staging = stagingDir(identifier)
        val txnPath = staging.resolve("txn.dc")
        val txn = DlcPackTransaction.running(identifier, staging.name)
        val meta = DlcPackMeta(identifier, metadata.version, pack.format.name, Instant.now().toString(), emptyList())
        try {
            checkCanceled(effectiveOptions.cancelChecker)
            Files.createDirectories(staging)
            Files.createDirectories(packDir)
            archiveMetadata(pack, packDir)
            applyRuntimeChanges(dependencies, conflicts, txn, txnPath)
            moveConfiguredConflicts(metadata.conflicts, packDir, txn, txnPath)
            val manifestFiles = DlcPackCFResolver.downloadManifestFiles(pack, staging, effectiveOptions.cancelChecker)
            val planWithManifest = DlcPackInstallPlan.merge(plan, manifestFiles, instanceRoot, dlcRoot)
            for (entry in planWithManifest.files) {
                checkCanceled(effectiveOptions.cancelChecker)
                val relative = entry.relativePath
                val target = resolveInside(instanceRoot, relative)
                if (Files.exists(target)) {
                    val displaced = resolveInside(packDir, relative)
                    txn.addMovedPath(relative)
                    txn.save(txnPath)
                    Files.createDirectories(displaced.parent)
                    Files.move(target, displaced, StandardCopyOption.REPLACE_EXISTING)
                }
                txn.addWrittenPath(relative)
                txn.save(txnPath)
                Files.createDirectories(target.parent)
                Files.copy(entry.source, target, StandardCopyOption.REPLACE_EXISTING)
                meta.addInstalledPath(relative)
            }
            checkCanceled(effectiveOptions.cancelChecker)
            if (!meta.save(metaPath)) {
                throw IOException("Failed to write DLC Pack meta: $metaPath")
            }
            txn.status = DlcPackTransaction.Status.COMMITTED
            txn.save(txnPath)
            deleteRecursive(staging)
            return InstallReport(
                identifier,
                meta.installedPaths.size,
                txn.movedPaths.size,
                txn.enabledDlc.size,
                txn.disabledDlc.size,
                txn.enabledPresets.size,
                txn.disabledPresets.size,
            )
        } catch (error: Throwable) {
            rollback(txn, packDir)
            txn.status = if (error is DlcDownloadService.DownloadCanceledException) {
                DlcPackTransaction.Status.CANCELLED
            } else {
                DlcPackTransaction.Status.ROLLED_BACK
            }
            txn.save(txnPath)
            deleteRecursive(staging)
            deleteRecursive(packDir)
            if (error is IOException) {
                throw error
            }
            throw IOException("Failed to install DLC Pack: $identifier", error)
        }
    }

    private fun backupExistingInstallation(oldMeta: DlcPackMeta, packDir: Path, backupDir: Path) {
        val backupPackDir = backupDir.resolve("pack_dir")
        val backupInstalledDir = backupDir.resolve("installed")
        copyRecursive(packDir, backupPackDir)
        for (relative in oldMeta.installedPaths) {
            val source = resolveInside(instanceRoot, relative)
            if (!Files.isRegularFile(source)) {
                continue
            }
            val target = resolveInside(backupInstalledDir, relative)
            Files.createDirectories(target.parent)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun restoreExistingInstallation(oldMeta: DlcPackMeta, packDir: Path, backupDir: Path) {
        val backupPackDir = backupDir.resolve("pack_dir")
        val backupInstalledDir = backupDir.resolve("installed")
        deleteArchivedTargetsFromInstance(backupPackDir)
        deleteRecursive(packDir)
        copyRecursive(backupPackDir, packDir)
        for (relative in oldMeta.installedPaths) {
            val source = resolveInside(backupInstalledDir, relative)
            if (!Files.isRegularFile(source)) {
                continue
            }
            val target = resolveInside(instanceRoot, relative)
            Files.createDirectories(target.parent)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteArchivedTargetsFromInstance(backupPackDir: Path) {
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

    fun precheck(pack: DlcPackPackage?): PrecheckResult {
        if (pack == null) {
            throw IOException("DLC Pack is not available")
        }
        val plan = DlcPackInstallPlan.build(pack, instanceRoot, dlcRoot)
        val compatibility = resolveCompatibility(pack.metadata)
        val dependencies = resolveDependencies(pack.metadata.dependencies)
        val conflicts = resolveIdentifierConflicts(pack.metadata.conflicts)
        return PrecheckResult(
            plan,
            compatibility.incompatible.toList(),
            dependencies.missing.toList(),
            dependencies.dlcToEnable.toList(),
            dependencies.presetsToEnable.toList(),
            conflicts.dlcToDisable.toList(),
            conflicts.presetsToDisable.toList(),
        )
    }

    private fun validatePrecheck(precheck: PrecheckResult, options: InstallOptions) {
        if (!precheck.plan.valid) {
            throw IOException("DLC Pack contains forbidden paths: ${precheck.forbiddenPaths.joinToString(", ")}")
        }
        if (precheck.incompatibleVersions.isNotEmpty()) {
            throw IOException("DLC Pack is incompatible with current modpack version: ${precheck.incompatibleVersions.joinToString(", ")}")
        }
        if (precheck.missingDependencies.isNotEmpty()) {
            throw IOException("DLC Pack dependencies are missing: ${precheck.missingDependencies.joinToString(", ")}")
        }
        if (precheck.dlcToEnable.isNotEmpty() && !options.enableMissingDlcDependencies) {
            throw IOException("DLC Pack dependencies must be enabled: ${precheck.dlcToEnable.joinToString(", ")}")
        }
        if (precheck.presetsToEnable.isNotEmpty() && !options.enableMissingPresetDependencies) {
            throw IOException("DLC Pack preset dependencies must be enabled: ${precheck.presetsToEnable.joinToString(", ")}")
        }
        if (precheck.dlcToDisable.isNotEmpty() && !options.disableDlcConflicts) {
            throw IOException("DLC Pack conflicts require disabling DLC: ${precheck.dlcToDisable.joinToString(", ")}")
        }
        if (precheck.presetsToDisable.isNotEmpty() && !options.disablePresetConflicts) {
            throw IOException("DLC Pack conflicts require disabling presets: ${precheck.presetsToDisable.joinToString(", ")}")
        }
    }

    fun uninstall(identifier: String): UninstallReport {
        if (!DlcPackMetadata.isValidIdentifier(identifier)) {
            throw IOException("Invalid DLC Pack identifier: $identifier")
        }
        val packDir = packDir(identifier)
        val meta = DlcPackMeta.load(packDir.resolve("meta.dc")) ?: throw IOException("DLC Pack is not installed: $identifier")
        var deleted = 0
        for (relative in meta.installedPaths.asReversed()) {
            val target = resolveInside(instanceRoot, relative)
            if (Files.deleteIfExists(target)) {
                deleted++
                pruneEmptyParents(target.parent, instanceRoot)
            }
        }
        var restored = 0
        if (Files.isDirectory(packDir)) {
            Files.walk(packDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .sorted(Comparator.reverseOrder())
                    .forEach { archived ->
                        val relative = DlcPackMeta.normalizeRelativePath(packDir.relativize(archived).toString()) ?: return@forEach
                        if (isPackRecordPath(relative)) {
                            return@forEach
                        }
                        val target = resolveInside(instanceRoot, relative)
                        Files.createDirectories(target.parent)
                        Files.move(archived, target, StandardCopyOption.REPLACE_EXISTING)
                        restored++
                    }
            }
        }
        deleteRecursive(packDir)
        return UninstallReport(identifier, deleted, restored)
    }

    fun packDir(identifier: String): Path {
        if (!DlcPackMetadata.isValidIdentifier(identifier)) {
            throw IllegalArgumentException("Invalid DLC Pack identifier: $identifier")
        }
        return dlcRoot.resolve("tmp").resolve("pack").resolve(identifier).toAbsolutePath().normalize()
    }

    private fun stagingDir(identifier: String): Path {
        return dlcRoot.resolve("tmp").resolve("_staging").resolve(identifier + "-" + System.nanoTime()).toAbsolutePath().normalize()
    }

    private fun archiveMetadata(pack: DlcPackPackage, packDir: Path) {
        val metadataDir = packDir.resolve("metadata")
        Files.createDirectories(metadataDir)
        Files.copy(pack.packFile, metadataDir.resolve("pack.dc"), StandardCopyOption.REPLACE_EXISTING)
        val icon = pack.iconFile
        if (icon != null && Files.isRegularFile(icon)) {
            Files.copy(icon, metadataDir.resolve(icon.fileName.toString()), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun rollback(txn: DlcPackTransaction, packDir: Path) {
        for (relative in txn.writtenPaths.asReversed()) {
            try {
                val target = resolveInside(instanceRoot, relative)
                Files.deleteIfExists(target)
                pruneEmptyParents(target.parent, instanceRoot)
            } catch (_: Exception) {
            }
        }
        for (relative in txn.movedPaths.asReversed()) {
            try {
                val archived = resolveInside(packDir, relative)
                if (!Files.exists(archived)) {
                    continue
                }
                val target = resolveInside(instanceRoot, relative)
                Files.createDirectories(target.parent)
                Files.move(archived, target, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
            }
        }
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

    private fun resolveCompatibility(metadata: DlcPackMetadata): CompatibilityResolution {
        val result = CompatibilityResolution()
        val version = DlcUpdateVersion.load(dlcRoot)
        val currentBase = version?.getBase()
        val currentDlc = version?.getDlc()
        if (!DlcPackMetadata.anyVersionMatches(metadata.baseVersions, currentBase)) {
            result.incompatible.add("base ${safeVersion(currentBase)} not in ${metadata.baseVersions.joinToString(", ")}")
        }
        if (!DlcPackMetadata.anyVersionMatches(metadata.dlcVersions, currentDlc)) {
            result.incompatible.add("dlc ${safeVersion(currentDlc)} not in ${metadata.dlcVersions.joinToString(", ")}")
        }
        return result
    }

    private fun resolveDependencies(dependencies: List<String>): DependencyResolution {
        val result = DependencyResolution()
        for (dependency in dependencies) {
            val token = normalizeToken(dependency) ?: continue
            val item = DlcRuntimeState.getItemByIdentifier(token)
            if (item != null) {
                collectDlcDependency(item, result)
                continue
            }
            val presetId = DlcPresetRuntimeState.findPresetId(token)
            if (presetId != null) {
                if (!DlcPresetRuntimeState.isPresetEnabled(presetId)) {
                    result.presetsToEnable.add(presetId)
                }
                continue
            }
            val relative = DlcPackMeta.normalizeRelativePath(dependency) ?: continue
            if (DlcPackInstallPlan.isForbiddenPath(relative, instanceRoot, dlcRoot)) {
                result.missing.add(relative)
                continue
            }
            val target = resolveInside(instanceRoot, relative)
            if (!Files.exists(target)) {
                result.missing.add(relative)
            }
        }
        return result
    }

    private fun collectDlcDependency(item: DlcItem, result: DependencyResolution) {
        val batch = DlcRuntimeState.collectMissingDependencies(listOf(item))
        val identifier = item.getIdentifier()
        if (!identifier.isNullOrBlank() && !item.isEnabled()) {
            result.dlcToEnable.add(identifier)
        }
        for (toEnable in batch.getItemsToEnable()) {
            val id = toEnable.getIdentifier()
            if (!id.isNullOrBlank()) {
                result.dlcToEnable.add(id)
            }
        }
        for (line in batch.getLines()) {
            val dependency = line.getDependencyName()
            if (DlcRuntimeState.getItemByIdentifier(dependency) == null) {
                result.missing.add(dependency)
            }
        }
    }

    private fun resolveIdentifierConflicts(conflicts: List<String>): ConflictResolution {
        val result = ConflictResolution()
        for (conflict in conflicts) {
            val token = normalizeToken(conflict) ?: continue
            val item = DlcRuntimeState.getItemByIdentifier(token)
            if (item != null) {
                if (item.isEnabled()) {
                    item.getIdentifier()?.let { result.dlcToDisable.add(it) }
                }
                continue
            }
            val presetId = DlcPresetRuntimeState.findPresetId(token)
            if (presetId != null && DlcPresetRuntimeState.isPresetEnabled(presetId)) {
                result.presetsToDisable.add(presetId)
            }
        }
        return result
    }

    private fun applyRuntimeChanges(
        dependencies: DependencyResolution,
        conflicts: ConflictResolution,
        txn: DlcPackTransaction,
        txnPath: Path,
    ) {
        for (id in dependencies.dlcToEnable) {
            val item = DlcRuntimeState.getItemByIdentifier(id)
            if (item != null && !item.isEnabled()) {
                txn.addEnabledDlc(id)
                txn.save(txnPath)
                item.setEnabled(true)
            }
        }
        for (id in dependencies.presetsToEnable) {
            if (!DlcPresetRuntimeState.isPresetEnabled(id)) {
                txn.addEnabledPreset(id)
                txn.save(txnPath)
                DlcPresetRuntimeState.setPresetEnabled(id, true)
            }
        }
        for (id in conflicts.dlcToDisable) {
            val item = DlcRuntimeState.getItemByIdentifier(id)
            if (item != null && item.isEnabled()) {
                txn.addDisabledDlc(id)
                txn.save(txnPath)
                item.setEnabled(false)
            }
        }
        for (id in conflicts.presetsToDisable) {
            if (DlcPresetRuntimeState.isPresetEnabled(id)) {
                txn.addDisabledPreset(id)
                txn.save(txnPath)
                DlcPresetRuntimeState.setPresetEnabled(id, false)
            }
        }
    }

    private fun moveConfiguredConflicts(conflicts: List<String>, packDir: Path, txn: DlcPackTransaction, txnPath: Path) {
        for (conflict in conflicts) {
            if (isRuntimeIdentifier(conflict)) {
                continue
            }
            val relative = DlcPackMeta.normalizeRelativePath(conflict) ?: continue
            if (DlcPackInstallPlan.isForbiddenPath(relative, instanceRoot, dlcRoot)) {
                throw IOException("DLC Pack conflict path is forbidden: $relative")
            }
            val target = resolveInside(instanceRoot, relative)
            if (!Files.exists(target)) {
                continue
            }
            val displaced = resolveInside(packDir, relative)
            txn.addMovedPath(relative)
            txn.save(txnPath)
            Files.createDirectories(displaced.parent)
            Files.move(target, displaced, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun isRuntimeIdentifier(value: String?): Boolean {
        val token = normalizeToken(value) ?: return false
        return DlcRuntimeState.getItemByIdentifier(token) != null ||
            DlcPresetRuntimeState.findPresetId(token) != null
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

    private fun deleteRecursive(path: Path?) {
        if (path == null || !Files.exists(path)) {
            return
        }
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach {
                try {
                    Files.deleteIfExists(it)
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun copyRecursive(source: Path?, target: Path?) {
        if (source == null || target == null || !Files.exists(source)) {
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

    private fun normalizeToken(token: String?): String? {
        val value = token?.trim()
        return if (value.isNullOrBlank()) null else value
    }

    private fun safeVersion(value: String?): String {
        return if (value.isNullOrBlank()) "unknown" else value
    }

    private fun checkCanceled(cancelChecker: DlcDownloadService.CancelChecker?) {
        if (cancelChecker?.isCanceled() == true) {
            throw DlcDownloadService.DownloadCanceledException("canceled")
        }
    }

    class InstallOptions(
        @JvmField val enableMissingDlcDependencies: Boolean,
        @JvmField val enableMissingPresetDependencies: Boolean,
        @JvmField val disableDlcConflicts: Boolean,
        @JvmField val disablePresetConflicts: Boolean,
        @JvmField val cancelChecker: DlcDownloadService.CancelChecker? = null,
    ) {
        fun withCancelChecker(cancelChecker: DlcDownloadService.CancelChecker?): InstallOptions {
            return InstallOptions(
                enableMissingDlcDependencies,
                enableMissingPresetDependencies,
                disableDlcConflicts,
                disablePresetConflicts,
                cancelChecker,
            )
        }

        companion object {
            @JvmStatic
            fun strict(): InstallOptions {
                return InstallOptions(false, false, false, false)
            }

            @JvmStatic
            fun applyConfirmedRuntimeChanges(): InstallOptions {
                return InstallOptions(true, true, true, true)
            }
        }
    }

    class InstallReport(
        @JvmField val identifier: String,
        @JvmField val filesWritten: Int,
        @JvmField val displacedFiles: Int,
        @JvmField val enabledDlc: Int = 0,
        @JvmField val disabledDlc: Int = 0,
        @JvmField val enabledPresets: Int = 0,
        @JvmField val disabledPresets: Int = 0,
    )

    class UninstallReport(
        @JvmField val identifier: String,
        @JvmField val filesDeleted: Int,
        @JvmField val filesRestored: Int,
    )

    class PrecheckResult internal constructor(
        @JvmField val plan: DlcPackInstallPlan,
        @JvmField val incompatibleVersions: List<String>,
        @JvmField val missingDependencies: List<String>,
        @JvmField val dlcToEnable: List<String>,
        @JvmField val presetsToEnable: List<String>,
        @JvmField val dlcToDisable: List<String>,
        @JvmField val presetsToDisable: List<String>,
    ) {
        @JvmField val forbiddenPaths: List<String> = plan.forbiddenPaths

        fun hasBlockingIssues(): Boolean {
            return forbiddenPaths.isNotEmpty() ||
                incompatibleVersions.isNotEmpty() ||
                missingDependencies.isNotEmpty()
        }

        fun requiresRuntimeConfirmation(): Boolean {
            return dlcToEnable.isNotEmpty() ||
                presetsToEnable.isNotEmpty() ||
                dlcToDisable.isNotEmpty() ||
                presetsToDisable.isNotEmpty()
        }
    }

    private class DependencyResolution {
        val missing: MutableSet<String> = LinkedHashSet()
        val dlcToEnable: MutableSet<String> = LinkedHashSet()
        val presetsToEnable: MutableSet<String> = LinkedHashSet()
    }

    private class CompatibilityResolution {
        val incompatible: MutableSet<String> = LinkedHashSet()
    }

    private class ConflictResolution {
        val dlcToDisable: MutableSet<String> = LinkedHashSet()
        val presetsToDisable: MutableSet<String> = LinkedHashSet()
    }
}
