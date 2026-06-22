package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.download.DlcDownloadService;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.update.DlcUpdateVersion;
import com.cozooo.dlc_manager.core.DlcApplier;
import com.cozooo.dlc_manager.core.DlcApplyMode;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPathUtil;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class DlcPackInstaller {
    private final Path instanceRoot;
    private final Path dlcRoot;

    public DlcPackInstaller(Path instanceRoot, Path dlcRoot) {
        this.instanceRoot = instanceRoot;
        this.dlcRoot = dlcRoot;
    }

    public InstallReport install(DlcPackPackage pack) throws IOException {
        return install(pack, false);
    }

    public InstallReport install(DlcPackPackage pack, boolean replaceExisting) throws IOException {
        return install(pack, replaceExisting, InstallOptions.strict());
    }

    public InstallReport install(DlcPackPackage pack, InstallOptions options) throws IOException {
        return install(pack, false, options);
    }

    public InstallReport install(DlcPackPackage pack, boolean replaceExisting, InstallOptions options) throws IOException {
        InstallOptions effectiveOptions = options == null ? InstallOptions.strict() : options;
        DlcPackMetadata metadata = pack.metadata;
        String identifier = metadata.identifier;
        Path packDir = packDir(identifier);
        Path metaPath = packDir.resolve("meta.dc");
        if (Files.exists(metaPath)) {
            if (!replaceExisting) {
                throw new IOException("DLC Pack already installed: " + identifier);
            }
            return replaceInstalledPack(pack, effectiveOptions, packDir, metaPath);
        }
        return installFresh(pack, effectiveOptions);
    }

    private InstallReport replaceInstalledPack(DlcPackPackage pack, InstallOptions options, Path packDir, Path metaPath) throws IOException {
        DlcPackMeta oldMeta = DlcPackMeta.load(metaPath);
        if (oldMeta == null) {
            throw new IOException("DLC Pack is not installed: " + pack.metadata.identifier);
        }
        PrecheckResult precheck = precheck(pack);
        validatePrecheck(precheck, options);
        Path replacementStaging = stagingDir(pack.metadata.identifier);
        Path backupDir = replacementStaging.resolve("replacement_backup");
        Path txnPath = replacementStaging.resolve("txn.dc");
        DlcPackTransaction txn = DlcPackTransaction.running(pack.metadata.identifier, replacementStaging.getFileName().toString());
        txn.replacement = true;
        try {
            if (options.progressListener != null) {
                options.progressListener.onProgress("Backing up " + pack.metadata.identifier, -1f);
            }
            Files.createDirectories(replacementStaging);
            txn.save(txnPath);
            backupExistingInstallation(oldMeta, packDir, backupDir);
            if (options.progressListener != null) {
                options.progressListener.onProgress("Uninstalling " + oldMeta.identifier, -1f);
            }
            uninstall(oldMeta.identifier, options.progressListener);
            InstallReport report = installFresh(pack, options);
            txn.status = DlcPackTransaction.Status.COMMITTED;
            txn.save(txnPath);
            return report;
        } catch (Throwable error) {
            try {
                restoreExistingInstallation(oldMeta, packDir, backupDir);
            } catch (Throwable restoreError) {
                error.addSuppressed(restoreError);
            }
            if (error instanceof IOException io) {
                throw io;
            }
            throw new IOException("Failed to replace DLC Pack: " + pack.metadata.identifier, error);
        } finally {
            deleteRecursive(replacementStaging);
        }
    }

    private InstallReport installFresh(DlcPackPackage pack, InstallOptions effectiveOptions) throws IOException {
        DlcPackMetadata metadata = pack.metadata;
        String identifier = metadata.identifier;
        Path packDir = packDir(identifier);
        Path metaPath = packDir.resolve("meta.dc");
        if (Files.exists(metaPath)) {
            throw new IOException("DLC Pack already installed: " + identifier);
        }
        DlcPackInstallPlan plan = DlcPackInstallPlan.build(pack, instanceRoot, dlcRoot);
        CompatibilityResolution compatibility = resolveCompatibility(metadata);
        DependencyResolution dependencies = resolveDependencies(metadata.dependencies);
        ConflictResolution conflicts = resolveIdentifierConflicts(metadata.conflicts);
        PrecheckResult precheck = new PrecheckResult(plan, compatibility, dependencies, conflicts);
        validatePrecheck(precheck, effectiveOptions);
        Path staging = stagingDir(identifier);
        Path txnPath = staging.resolve("txn.dc");
        DlcPackTransaction txn = DlcPackTransaction.running(identifier, staging.getFileName().toString());
        DlcPackMeta meta = new DlcPackMeta(identifier, metadata.version, pack.format.name(), Instant.now().toString(), java.util.List.of());
        try {
            checkCanceled(effectiveOptions.cancelChecker);
            if (effectiveOptions.progressListener != null) {
                effectiveOptions.progressListener.onProgress("Preparing " + identifier, 0f);
            }
            Files.createDirectories(staging);
            Files.createDirectories(packDir);
            archiveMetadata(pack, packDir);
            if (effectiveOptions.progressListener != null) {
                effectiveOptions.progressListener.onProgress("Applying runtime changes", 0.05f);
            }
            applyRuntimeChanges(dependencies, conflicts, txn, txnPath);
            moveConfiguredConflicts(metadata.conflicts, packDir, txn, txnPath);
            java.util.List<DlcPackInstallPlan.FileEntry> manifestFiles = DlcPackCFResolver.downloadManifestFiles(
                pack,
                staging,
                packDir,
                effectiveOptions.cancelChecker,
                (status, progress) -> {
                    if (effectiveOptions.progressListener != null) {
                        float normalized = progress < 0f ? -1f : 0.05f + Math.max(0f, Math.min(1f, progress)) * 0.9f;
                        effectiveOptions.progressListener.onProgress(status, normalized);
                    }
                }
            );
            DlcPackInstallPlan planWithManifest = DlcPackInstallPlan.merge(plan, manifestFiles, instanceRoot, dlcRoot);
            int totalFiles = Math.max(1, planWithManifest.files.size());
            for (int index = 0; index < planWithManifest.files.size(); index++) {
                DlcPackInstallPlan.FileEntry entry = planWithManifest.files.get(index);
                checkCanceled(effectiveOptions.cancelChecker);
                String relative = entry.relativePath;
                if (effectiveOptions.progressListener != null) {
                    effectiveOptions.progressListener.onProgress("Installing " + relative, 0.95f + index / (float) totalFiles * 0.03f);
                }
                Path target = DlcPathUtil.resolveInside(instanceRoot, relative);
                if (Files.exists(target)) {
                    Path displaced = DlcPathUtil.resolveInside(packDir, relative);
                    txn.addMovedPath(relative);
                    txn.save(txnPath);
                    Files.createDirectories(displaced.getParent());
                    Files.move(target, displaced, StandardCopyOption.REPLACE_EXISTING);
                }
                txn.addWrittenPath(relative);
                txn.save(txnPath);
                Files.createDirectories(target.getParent());
                Files.copy(entry.source, target, StandardCopyOption.REPLACE_EXISTING);
                meta.addInstalledPath(relative);
            }
            checkCanceled(effectiveOptions.cancelChecker);
            if (effectiveOptions.progressListener != null) {
                effectiveOptions.progressListener.onProgress("Writing metadata", 0.98f);
            }
            if (!meta.save(metaPath)) {
                throw new IOException("Failed to write DLC Pack meta: " + metaPath);
            }
            txn.status = DlcPackTransaction.Status.COMMITTED;
            txn.save(txnPath);
            deleteRecursive(staging);
            if (effectiveOptions.progressListener != null) {
                effectiveOptions.progressListener.onProgress("Installed " + identifier, 1f);
            }
            return new InstallReport(identifier, meta.installedPaths.size(), txn.movedPaths.size(), txn.enabledDlc.size(), txn.disabledDlc.size(), txn.enabledPresets.size(), txn.disabledPresets.size());
        } catch (Throwable error) {
            rollback(txn, packDir);
            txn.status = error instanceof DlcDownloadService.DownloadCanceledException
                ? DlcPackTransaction.Status.CANCELLED
                : DlcPackTransaction.Status.ROLLED_BACK;
            txn.save(txnPath);
            deleteRecursive(staging);
            deletePackInstallState(packDir);
            if (error instanceof IOException io) {
                throw io;
            }
            throw new IOException("Failed to install DLC Pack: " + identifier, error);
        }
    }

    private void backupExistingInstallation(DlcPackMeta oldMeta, Path packDir, Path backupDir) throws IOException {
        Path backupPackDir = backupDir.resolve("pack_dir");
        Path backupInstalledDir = backupDir.resolve("installed");
        copyRecursive(packDir, backupPackDir);
        for (String relative : oldMeta.installedPaths) {
            Path source = DlcPathUtil.resolveInside(instanceRoot, relative);
            if (!Files.isRegularFile(source)) {
                continue;
            }
            Path target = DlcPathUtil.resolveInside(backupInstalledDir, relative);
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void restoreExistingInstallation(DlcPackMeta oldMeta, Path packDir, Path backupDir) throws IOException {
        Path backupPackDir = backupDir.resolve("pack_dir");
        Path backupInstalledDir = backupDir.resolve("installed");
        Path currentCacheBackup = backupDir.resolve("current_cf_cache");
        copyRecursive(packDir.resolve("cf_cache"), currentCacheBackup);
        deleteArchivedTargetsFromInstance(backupPackDir);
        deleteRecursive(packDir);
        copyRecursive(backupPackDir, packDir);
        copyRecursive(currentCacheBackup, packDir.resolve("cf_cache"));
        for (String relative : oldMeta.installedPaths) {
            Path source = DlcPathUtil.resolveInside(backupInstalledDir, relative);
            if (!Files.isRegularFile(source)) {
                continue;
            }
            Path target = DlcPathUtil.resolveInside(instanceRoot, relative);
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteArchivedTargetsFromInstance(Path backupPackDir) throws IOException {
        if (!Files.isDirectory(backupPackDir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(backupPackDir)) {
            for (Path archived : stream.filter(Files::isRegularFile).sorted(Comparator.reverseOrder()).toList()) {
                String relative = DlcPathUtil.normalizeRelativePath(backupPackDir.relativize(archived).toString());
                if (relative == null || isPackRecordPath(relative)) {
                    continue;
                }
                Path target = DlcPathUtil.resolveInside(instanceRoot, relative);
                Files.deleteIfExists(target);
                pruneEmptyParents(target.getParent(), instanceRoot);
            }
        }
    }

    public PrecheckResult precheck(DlcPackPackage pack) throws IOException {
        if (pack == null || pack.metadata == null) {
            throw new IOException("DLC Pack is not available");
        }
        DlcPackInstallPlan plan = DlcPackInstallPlan.build(pack, instanceRoot, dlcRoot);
        CompatibilityResolution compatibility = resolveCompatibility(pack.metadata);
        DependencyResolution dependencies = resolveDependencies(pack.metadata.dependencies);
        ConflictResolution conflicts = resolveIdentifierConflicts(pack.metadata.conflicts);
        return new PrecheckResult(plan, compatibility, dependencies, conflicts);
    }

    private void validatePrecheck(PrecheckResult precheck, InstallOptions options) throws IOException {
        if (!precheck.getIncompatibleVersions().isEmpty()) {
            throw new IOException("DLC Pack is incompatible with current modpack version: " + String.join(", ", precheck.getIncompatibleVersions()));
        }
        if (!precheck.getMissingDependencies().isEmpty()) {
            throw new IOException("DLC Pack dependencies are missing: " + String.join(", ", precheck.getMissingDependencies()));
        }
        if (!precheck.getDlcToEnable().isEmpty() && !options.enableMissingDlcDependencies) {
            throw new IOException("DLC Pack dependencies must be enabled: " + String.join(", ", precheck.getDlcToEnable()));
        }
        if (!precheck.getPresetsToEnable().isEmpty() && !options.enableMissingPresetDependencies) {
            throw new IOException("DLC Pack preset dependencies must be enabled: " + String.join(", ", precheck.getPresetsToEnable()));
        }
        if (!precheck.getDlcToDisable().isEmpty() && !options.disableDlcConflicts) {
            throw new IOException("DLC Pack conflicts require disabling DLC: " + String.join(", ", precheck.getDlcToDisable()));
        }
        if (!precheck.getPresetsToDisable().isEmpty() && !options.disablePresetConflicts) {
            throw new IOException("DLC Pack conflicts require disabling presets: " + String.join(", ", precheck.getPresetsToDisable()));
        }
    }

    public UninstallReport uninstall(String identifier) throws IOException {
        return uninstall(identifier, null);
    }

    public UninstallReport uninstall(String identifier, ProgressListener progressListener) throws IOException {
        if (!DlcPackMetadata.isValidIdentifier(identifier)) {
            throw new IOException("Invalid DLC Pack identifier: " + identifier);
        }
        Path packDir = packDir(identifier);
        DlcPackMeta meta = DlcPackMeta.load(packDir.resolve("meta.dc"));
        if (meta == null) {
            throw new IOException("DLC Pack is not installed: " + identifier);
        }
        uninstallInstalledDlcItems(meta, progressListener);
        int deleted = 0;
        java.util.List<String> paths = new java.util.ArrayList<>(meta.installedPaths);
        java.util.Collections.reverse(paths);
        int deleteTotal = Math.max(1, paths.size());
        for (int index = 0; index < paths.size(); index++) {
            String relative = paths.get(index);
            if (progressListener != null) {
                progressListener.onProgress("Uninstalling " + relative, index / (float) deleteTotal * 0.5f);
            }
            Path target = DlcPathUtil.resolveInside(instanceRoot, relative);
            if (Files.deleteIfExists(target)) {
                deleted++;
                pruneEmptyParents(target.getParent(), instanceRoot);
            }
        }
        int restored = 0;
        if (Files.isDirectory(packDir)) {
            try (Stream<Path> stream = Files.walk(packDir)) {
                java.util.List<Path> archivedFiles = stream.filter(Files::isRegularFile).sorted(Comparator.reverseOrder()).toList();
                int restoreTotal = Math.max(1, archivedFiles.size());
                for (int index = 0; index < archivedFiles.size(); index++) {
                    Path archived = archivedFiles.get(index);
                    String relative = DlcPathUtil.normalizeRelativePath(packDir.relativize(archived).toString());
                    if (relative == null || isPackRecordPath(relative)) {
                        continue;
                    }
                    if (progressListener != null) {
                        progressListener.onProgress("Restoring " + relative, 0.5f + index / (float) restoreTotal * 0.45f);
                    }
                    Path target = DlcPathUtil.resolveInside(instanceRoot, relative);
                    Files.createDirectories(target.getParent());
                    Files.move(archived, target, StandardCopyOption.REPLACE_EXISTING);
                    restored++;
                }
            }
        }
        deletePackInstallState(packDir);
        if (progressListener != null) {
            progressListener.onProgress("Uninstalled " + identifier, 1f);
        }
        return new UninstallReport(identifier, deleted, restored);
    }

    private void uninstallInstalledDlcItems(DlcPackMeta meta, ProgressListener progressListener) throws IOException {
        Set<String> installedPaths = new LinkedHashSet<>();
        if (meta != null && meta.installedPaths != null) {
            for (String path : meta.installedPaths) {
                String relative = DlcPathUtil.normalizeRelativePath(path);
                if (relative != null) {
                    installedPaths.add(relative);
                }
            }
        }
        if (installedPaths.isEmpty()) {
            return;
        }
        Set<String> remoteTargetPaths = collectRemoteTargetPaths(installedPaths);
        java.util.List<DlcItem> allItems = collectCurrentDlcItems(true);
        if (allItems.isEmpty()) {
            deleteRemoteTargets(remoteTargetPaths);
            return;
        }
        boolean changed = false;
        for (DlcItem item : allItems) {
            if (item == null || !item.isEnabled()) {
                continue;
            }
            if (isPackInstalledDlcItem(item, installedPaths, remoteTargetPaths)) {
                item.setEnabled(false);
                DlcRuntimeState.setOverride(item.getId(), false);
                changed = true;
            }
        }
        if (changed) {
            if (progressListener != null) {
                progressListener.onProgress("Uninstalling installed DLC items", -1f);
            }
            DlcApplier.ApplyResult result = DlcApplier.apply(allItems, DlcApplyMode.RESTART_ONLY);
            java.util.List<String> failures = collectApplyFailures(result);
            if (!failures.isEmpty()) {
                throw new IOException("Failed to uninstall DLC Pack DLC items: " + String.join(", ", failures));
            }
        }
        deleteRemoteTargets(remoteTargetPaths);
    }

    private java.util.List<String> collectApplyFailures(DlcApplier.ApplyResult result) {
        if (result == null || result.messages() == null || result.messages().isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (String message : result.messages()) {
            if (message != null && message.startsWith("Failed to ")) {
                failures.add(message);
            }
        }
        return failures;
    }

    private Set<String> collectRemoteTargetPaths(Set<String> installedPaths) {
        Set<String> targets = new LinkedHashSet<>();
        for (DlcRemoteEntry entry : DlcRemoteLoader.load(dlcRoot)) {
            if (entry == null || entry.getConfigPath() == null || entry.getType() == DlcType.DLC_PACK) {
                continue;
            }
            String configRelative = toInstanceRelativePath(entry.getConfigPath());
            if (configRelative == null || !installedPaths.contains(configRelative)) {
                continue;
            }
            Path target = entry.getTargetPath();
            String targetRelative = toInstanceRelativePath(target);
            if (targetRelative != null) {
                targets.add(targetRelative);
            }
        }
        return targets;
    }

    private java.util.List<DlcItem> collectCurrentDlcItems(boolean detectInstalledState) {
        java.util.List<DlcItem> items = new java.util.ArrayList<>();
        java.util.Map<DlcType, java.util.List<DlcItem>> grouped = new DlcScanner().scan(detectInstalledState);
        for (java.util.List<DlcItem> list : grouped.values()) {
            if (list != null) {
                items.addAll(list);
            }
        }
        DlcRuntimeState.updateItemIndex(items);
        return items;
    }

    private boolean isPackInstalledDlcItem(DlcItem item, Set<String> installedPaths, Set<String> remoteTargetPaths) {
        for (Path source : item.getSourcePaths()) {
            String relative = toInstanceRelativePath(source);
            if (relative != null && installedPaths.contains(relative)) {
                return true;
            }
        }
        String sourceRelative = toInstanceRelativePath(item.getSourcePath());
        if (sourceRelative != null && (installedPaths.contains(sourceRelative) || remoteTargetPaths.contains(sourceRelative))) {
            return true;
        }
        for (Path source : item.getSourcePaths()) {
            String relative = toInstanceRelativePath(source);
            if (relative != null && remoteTargetPaths.contains(relative)) {
                return true;
            }
        }
        return false;
    }

    private void deleteRemoteTargets(Set<String> remoteTargetPaths) throws IOException {
        if (remoteTargetPaths == null || remoteTargetPaths.isEmpty()) {
            return;
        }
        for (String relative : remoteTargetPaths) {
            Path target = DlcPathUtil.resolveInside(instanceRoot, relative);
            if (Files.deleteIfExists(target)) {
                pruneEmptyParents(target.getParent(), instanceRoot);
            }
        }
    }

    private String toInstanceRelativePath(Path path) {
        if (path == null) {
            return null;
        }
        Path normalizedInstance = instanceRoot.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedInstance)) {
            return null;
        }
        return DlcPathUtil.normalizeRelativePath(normalizedInstance.relativize(normalizedPath).toString());
    }

    public Path packDir(String identifier) {
        if (!DlcPackMetadata.isValidIdentifier(identifier)) {
            throw new IllegalArgumentException("Invalid DLC Pack identifier: " + identifier);
        }
        return dlcRoot.resolve("tmp").resolve("pack").resolve(identifier).toAbsolutePath().normalize();
    }

    private Path stagingDir(String identifier) {
        return dlcRoot.resolve("tmp").resolve("_staging").resolve(identifier + "-" + System.nanoTime()).toAbsolutePath().normalize();
    }

    private void archiveMetadata(DlcPackPackage pack, Path packDir) throws IOException {
        Path metadataDir = packDir.resolve("metadata");
        Files.createDirectories(metadataDir);
        Files.copy(pack.packFile, metadataDir.resolve("pack.dc"), StandardCopyOption.REPLACE_EXISTING);
        if (pack.iconFile != null && Files.isRegularFile(pack.iconFile)) {
            Files.copy(pack.iconFile, metadataDir.resolve(pack.iconFile.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void rollback(DlcPackTransaction txn, Path packDir) {
        java.util.List<String> written = new java.util.ArrayList<>(txn.writtenPaths);
        java.util.Collections.reverse(written);
        for (String relative : written) {
            try {
                Path target = DlcPathUtil.resolveInside(instanceRoot, relative);
                Files.deleteIfExists(target);
                pruneEmptyParents(target.getParent(), instanceRoot);
            } catch (Exception ignored) {
            }
        }
        java.util.List<String> moved = new java.util.ArrayList<>(txn.movedPaths);
        java.util.Collections.reverse(moved);
        for (String relative : moved) {
            try {
                Path archived = DlcPathUtil.resolveInside(packDir, relative);
                if (!Files.exists(archived)) {
                    continue;
                }
                Path target = DlcPathUtil.resolveInside(instanceRoot, relative);
                Files.createDirectories(target.getParent());
                Files.move(archived, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
        }
        for (String id : txn.enabledDlc) {
            try {
                DlcItem item = DlcRuntimeState.getItemByIdentifier(id);
                if (item != null) {
                    item.setEnabled(false);
                }
            } catch (Exception ignored) {
            }
        }
        for (String id : txn.disabledDlc) {
            try {
                DlcItem item = DlcRuntimeState.getItemByIdentifier(id);
                if (item != null) {
                    item.setEnabled(true);
                }
            } catch (Exception ignored) {
            }
        }
        for (String id : txn.enabledPresets) {
            try {
                DlcPresetRuntimeState.setPresetEnabled(id, false);
            } catch (Exception ignored) {
            }
        }
        for (String id : txn.disabledPresets) {
            try {
                DlcPresetRuntimeState.setPresetEnabled(id, true);
            } catch (Exception ignored) {
            }
        }
    }

    private CompatibilityResolution resolveCompatibility(DlcPackMetadata metadata) {
        CompatibilityResolution result = new CompatibilityResolution();
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);
        String currentModpack = version == null ? null : version.getModpack();
        String currentBase = version == null ? null : version.getBase();
        String currentDlc = version == null ? null : version.getDlc();
        String currentPlatform = DlcPackMetadata.currentPlatform();
        String currentGameVersion = CrossPlatformRuntime.mcVersion();
        String currentModLoader = CrossPlatformRuntime.loaderName();
        if (!DlcPackMetadata.anyStringMatches(metadata.modpacks, currentModpack, metadata.modpackAnyWildcard)) {
            result.incompatible.add("modpack " + safeText(currentModpack) + " not in " + String.join(", ", metadata.modpacks));
        }
        if (!DlcPackMetadata.platformMatches(metadata.platform, currentPlatform)) {
            result.incompatible.add("platform " + safeText(currentPlatform) + " not in " + metadata.platform);
        }
        if (!DlcPackMetadata.stringRuleMatches(metadata.gameVersion, currentGameVersion)) {
            result.incompatible.add("gameVersion " + safeText(currentGameVersion) + " not in " + metadata.gameVersion);
        }
        if (!DlcPackMetadata.stringRuleMatches(metadata.modLoader, currentModLoader)) {
            result.incompatible.add("modLoader " + safeText(currentModLoader) + " not in " + metadata.modLoader);
        }
        if (!DlcPackMetadata.anyVersionMatches(metadata.baseVersions, currentBase)) {
            result.incompatible.add("base " + safeVersion(currentBase) + " not in " + String.join(", ", metadata.baseVersions));
        }
        if (!DlcPackMetadata.anyVersionMatches(metadata.dlcVersions, currentDlc)) {
            result.incompatible.add("dlc " + safeVersion(currentDlc) + " not in " + String.join(", ", metadata.dlcVersions));
        }
        return result;
    }

    private DependencyResolution resolveDependencies(java.util.List<String> dependencies) throws IOException {
        DependencyResolution result = new DependencyResolution();
        for (String dependency : dependencies) {
            String token = normalizeToken(dependency);
            if (token == null) {
                continue;
            }
            DlcItem item = DlcRuntimeState.getItemByIdentifier(token);
            if (item != null) {
                collectDlcDependency(item, result);
                continue;
            }
            String presetId = DlcPresetRuntimeState.findPresetId(token);
            if (presetId != null) {
                if (!DlcPresetRuntimeState.isPresetEnabled(presetId)) {
                    result.presetsToEnable.add(presetId);
                }
                continue;
            }
            String relative = DlcPathUtil.normalizeRelativePath(dependency);
            if (relative == null) {
                continue;
            }
            if (DlcPackInstallPlan.isForbiddenPath(relative, instanceRoot, dlcRoot)) {
                result.missing.add(relative);
                continue;
            }
            if (DlcPathUtil.findMatches(instanceRoot, relative).isEmpty()) {
                result.missing.add(relative);
            }
        }
        return result;
    }

    private void collectDlcDependency(DlcItem item, DependencyResolution result) {
        DlcRuntimeState.DependencyBatchResult batch = DlcRuntimeState.collectMissingDependencies(java.util.List.of(item));
        if (item.getIdentifier() != null && !item.isEnabled()) {
            result.dlcToEnable.add(item.getIdentifier());
        }
        for (DlcItem toEnable : batch.getItemsToEnable()) {
            if (toEnable != null && toEnable.getIdentifier() != null && !toEnable.getIdentifier().isBlank()) {
                result.dlcToEnable.add(toEnable.getIdentifier());
            }
        }
        for (DlcRuntimeState.DependencyLine line : batch.getLines()) {
            String dependency = line.getDependencyName();
            if (DlcRuntimeState.getItemByIdentifier(dependency) == null) {
                result.missing.add(dependency);
            }
        }
    }

    private ConflictResolution resolveIdentifierConflicts(java.util.List<String> conflicts) {
        ConflictResolution result = new ConflictResolution();
        for (String conflict : conflicts) {
            String token = normalizeToken(conflict);
            if (token == null) {
                continue;
            }
            DlcItem item = DlcRuntimeState.getItemByIdentifier(token);
            if (item != null) {
                if (item.isEnabled()) {
                    result.dlcToDisable.add(item.getIdentifier());
                }
                continue;
            }
            String presetId = DlcPresetRuntimeState.findPresetId(token);
            if (presetId != null && DlcPresetRuntimeState.isPresetEnabled(presetId)) {
                result.presetsToDisable.add(presetId);
            }
        }
        return result;
    }

    private void applyRuntimeChanges(DependencyResolution dependencies, ConflictResolution conflicts, DlcPackTransaction txn, Path txnPath) {
        for (String id : dependencies.dlcToEnable) {
            DlcItem item = DlcRuntimeState.getItemByIdentifier(id);
            if (item != null && !item.isEnabled()) {
                txn.addEnabledDlc(id);
                txn.save(txnPath);
                item.setEnabled(true);
            }
        }
        for (String id : dependencies.presetsToEnable) {
            if (!DlcPresetRuntimeState.isPresetEnabled(id)) {
                txn.addEnabledPreset(id);
                txn.save(txnPath);
                DlcPresetRuntimeState.setPresetEnabled(id, true);
            }
        }
        for (String id : conflicts.dlcToDisable) {
            DlcItem item = DlcRuntimeState.getItemByIdentifier(id);
            if (item != null && item.isEnabled()) {
                txn.addDisabledDlc(id);
                txn.save(txnPath);
                item.setEnabled(false);
            }
        }
        for (String id : conflicts.presetsToDisable) {
            if (DlcPresetRuntimeState.isPresetEnabled(id)) {
                txn.addDisabledPreset(id);
                txn.save(txnPath);
                DlcPresetRuntimeState.setPresetEnabled(id, false);
            }
        }
    }

    private void moveConfiguredConflicts(java.util.List<String> conflicts, Path packDir, DlcPackTransaction txn, Path txnPath) throws IOException {
        for (String conflict : conflicts) {
            if (isRuntimeIdentifier(conflict)) {
                continue;
            }
            String relative = DlcPathUtil.normalizeRelativePath(conflict);
            if (relative == null) {
                continue;
            }
            if (DlcPackInstallPlan.isForbiddenPath(relative, instanceRoot, dlcRoot)) {
                continue;
            }
            List<Path> targets = DlcPathUtil.findMatches(instanceRoot, relative);
            if (targets.isEmpty()) {
                continue;
            }
            for (Path target : targets) {
                String targetRelative = DlcPathUtil.normalizeRelativePath(
                    instanceRoot.toAbsolutePath().normalize()
                        .relativize(target.toAbsolutePath().normalize())
                        .toString()
                );
                if (targetRelative == null) {
                    continue;
                }
                Path displaced = DlcPathUtil.resolveInside(packDir, targetRelative);
                txn.addMovedPath(targetRelative);
                txn.save(txnPath);
                Files.createDirectories(displaced.getParent());
                Files.move(target, displaced, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private boolean isRuntimeIdentifier(String value) {
        String token = normalizeToken(value);
        if (token == null) {
            return false;
        }
        return DlcRuntimeState.getItemByIdentifier(token) != null
            || DlcPresetRuntimeState.findPresetId(token) != null;
    }

    private boolean isPackRecordPath(String relative) {
        String normalized = relative.toLowerCase(Locale.ROOT);
        return normalized.equals("meta.dc")
            || normalized.startsWith("metadata/")
            || normalized.startsWith("cf_cache/");
    }

    private void pruneEmptyParents(Path start, Path stop) {
        Path dir = start;
        Path normalizedStop = stop.toAbsolutePath().normalize();
        while (dir != null) {
            Path current = dir.toAbsolutePath().normalize();
            if (current.equals(normalizedStop) || !current.startsWith(normalizedStop)) {
                break;
            }
            try (Stream<Path> stream = Files.list(current)) {
                if (stream.findAny().isPresent()) {
                    return;
                }
                Files.deleteIfExists(current);
            } catch (IOException ex) {
                break;
            }
            dir = current.getParent();
        }
    }

    private void deleteRecursive(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void deletePackInstallState(Path packDir) {
        if (packDir == null || !Files.exists(packDir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(packDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                if (path.equals(packDir) || isCfCachePath(packDir, path)) {
                    return;
                }
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
        try {
            Path cache = packDir.resolve("cf_cache");
            if (!Files.exists(cache)) {
                Files.deleteIfExists(packDir);
            }
        } catch (IOException ignored) {
        }
    }

    private boolean isCfCachePath(Path packDir, Path path) {
        Path relative = packDir.relativize(path);
        return relative.getNameCount() > 0 && "cf_cache".equals(relative.getName(0).toString());
    }

    private void copyRecursive(Path source, Path target) throws IOException {
        if (source == null || target == null || !Files.exists(source)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.sorted(Comparator.naturalOrder()).toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim();
        return value.isBlank() ? null : value;
    }

    private String safeVersion(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<empty>" : value;
    }

    private void checkCanceled(DlcDownloadService.CancelChecker cancelChecker) throws DlcDownloadService.DownloadCanceledException {
        if (cancelChecker != null && cancelChecker.isCanceled()) {
            throw new DlcDownloadService.DownloadCanceledException("canceled");
        }
    }

    public static class InstallOptions {
        public final boolean enableMissingDlcDependencies;
        public final boolean enableMissingPresetDependencies;
        public final boolean disableDlcConflicts;
        public final boolean disablePresetConflicts;
        public final DlcDownloadService.CancelChecker cancelChecker;
        public final ProgressListener progressListener;

        public InstallOptions(boolean enableMissingDlcDependencies, boolean enableMissingPresetDependencies, boolean disableDlcConflicts, boolean disablePresetConflicts) {
            this(enableMissingDlcDependencies, enableMissingPresetDependencies, disableDlcConflicts, disablePresetConflicts, null, null);
        }

        public InstallOptions(boolean enableMissingDlcDependencies, boolean enableMissingPresetDependencies, boolean disableDlcConflicts, boolean disablePresetConflicts,
                              DlcDownloadService.CancelChecker cancelChecker) {
            this(enableMissingDlcDependencies, enableMissingPresetDependencies, disableDlcConflicts, disablePresetConflicts, cancelChecker, null);
        }

        public InstallOptions(boolean enableMissingDlcDependencies, boolean enableMissingPresetDependencies, boolean disableDlcConflicts, boolean disablePresetConflicts,
                              DlcDownloadService.CancelChecker cancelChecker, ProgressListener progressListener) {
            this.enableMissingDlcDependencies = enableMissingDlcDependencies;
            this.enableMissingPresetDependencies = enableMissingPresetDependencies;
            this.disableDlcConflicts = disableDlcConflicts;
            this.disablePresetConflicts = disablePresetConflicts;
            this.cancelChecker = cancelChecker;
            this.progressListener = progressListener;
        }

        public static InstallOptions strict() {
            return new InstallOptions(false, false, false, false);
        }

        public static InstallOptions applyConfirmedRuntimeChanges() {
            return new InstallOptions(true, true, true, true);
        }

        public InstallOptions withCancelChecker(DlcDownloadService.CancelChecker cancelChecker) {
            return new InstallOptions(enableMissingDlcDependencies, enableMissingPresetDependencies, disableDlcConflicts, disablePresetConflicts, cancelChecker, progressListener);
        }

        public InstallOptions withProgressListener(ProgressListener progressListener) {
            return new InstallOptions(enableMissingDlcDependencies, enableMissingPresetDependencies, disableDlcConflicts, disablePresetConflicts, cancelChecker, progressListener);
        }
    }

    public interface ProgressListener {
        void onProgress(String status, float progress);
    }

    public static class InstallReport {
        public final String identifier;
        public final int filesWritten;
        public final int displacedFiles;
        public final int enabledDlc;
        public final int disabledDlc;
        public final int enabledPresets;
        public final int disabledPresets;

        public InstallReport(String identifier, int filesWritten, int displacedFiles) {
            this(identifier, filesWritten, displacedFiles, 0, 0, 0, 0);
        }

        public InstallReport(String identifier, int filesWritten, int displacedFiles, int enabledDlc, int disabledDlc, int enabledPresets, int disabledPresets) {
            this.identifier = identifier;
            this.filesWritten = filesWritten;
            this.displacedFiles = displacedFiles;
            this.enabledDlc = enabledDlc;
            this.disabledDlc = disabledDlc;
            this.enabledPresets = enabledPresets;
            this.disabledPresets = disabledPresets;
        }
    }

    public static class UninstallReport {
        public final String identifier;
        public final int filesDeleted;
        public final int filesRestored;

        public UninstallReport(String identifier, int filesDeleted, int filesRestored) {
            this.identifier = identifier;
            this.filesDeleted = filesDeleted;
            this.filesRestored = filesRestored;
        }
    }

    public static class PrecheckResult {
        private final DlcPackInstallPlan plan;
        public final java.util.List<String> incompatibleVersions;
        public final java.util.List<String> missingDependencies;
        public final java.util.List<String> dlcToEnable;
        public final java.util.List<String> presetsToEnable;
        public final java.util.List<String> dlcToDisable;
        public final java.util.List<String> presetsToDisable;

        private PrecheckResult(DlcPackInstallPlan plan, CompatibilityResolution compatibility, DependencyResolution dependencies, ConflictResolution conflicts) {
            this.plan = plan;
            this.incompatibleVersions = java.util.List.copyOf(compatibility.incompatible);
            this.missingDependencies = java.util.List.copyOf(dependencies.missing);
            this.dlcToEnable = java.util.List.copyOf(dependencies.dlcToEnable);
            this.presetsToEnable = java.util.List.copyOf(dependencies.presetsToEnable);
            this.dlcToDisable = java.util.List.copyOf(conflicts.dlcToDisable);
            this.presetsToDisable = java.util.List.copyOf(conflicts.presetsToDisable);
        }

        public java.util.List<String> getForbiddenPaths() {
            return plan.forbiddenPaths;
        }

        public java.util.List<String> getIncompatibleVersions() {
            return incompatibleVersions;
        }

        public java.util.List<String> getMissingDependencies() {
            return missingDependencies;
        }

        public java.util.List<String> getDlcToEnable() {
            return dlcToEnable;
        }

        public java.util.List<String> getPresetsToEnable() {
            return presetsToEnable;
        }

        public java.util.List<String> getDlcToDisable() {
            return dlcToDisable;
        }

        public java.util.List<String> getPresetsToDisable() {
            return presetsToDisable;
        }

        public boolean hasBlockingIssues() {
            return !getIncompatibleVersions().isEmpty()
                || !getMissingDependencies().isEmpty();
        }

        public boolean requiresRuntimeConfirmation() {
            return !getDlcToEnable().isEmpty()
                || !getPresetsToEnable().isEmpty()
                || !getDlcToDisable().isEmpty()
                || !getPresetsToDisable().isEmpty();
        }
    }

    private static class DependencyResolution {
        private final Set<String> missing = new LinkedHashSet<>();
        private final Set<String> dlcToEnable = new LinkedHashSet<>();
        private final Set<String> presetsToEnable = new LinkedHashSet<>();
    }

    private static class CompatibilityResolution {
        private final Set<String> incompatible = new LinkedHashSet<>();
    }

    private static class ConflictResolution {
        private final Set<String> dlcToDisable = new LinkedHashSet<>();
        private final Set<String> presetsToDisable = new LinkedHashSet<>();
    }
}
