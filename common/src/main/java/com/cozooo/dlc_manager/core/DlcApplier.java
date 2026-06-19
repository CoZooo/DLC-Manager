package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.client.helper.DlcFileOpsHelperClient;
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState;
import com.cozooo.dlc_manager.client.preset.DlcPresetStateStore;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DlcApplier {
    private static final String CONFIG_MISSING_MARKER = "__dlc_config_missing__";
    private static final ThreadLocal<ApplyContext> CURRENT_APPLY_CONTEXT = new ThreadLocal<>();

    public static ApplyResult apply(List<DlcItem> items, DlcApplyMode mode) {
        List<String> messages = new ArrayList<>();
        boolean requiresRestart = false;
        boolean attemptedHotReload = false;
        ApplyContext context = new ApplyContext(messages);
        CURRENT_APPLY_CONTEXT.set(context);

        try {
            DlcConflictPathResult conflictPathResult = applyDlcConflictPaths(items);
            if (!conflictPathResult.messages().isEmpty()) {
                messages.addAll(conflictPathResult.messages());
            }
            if (conflictPathResult.changed()) {
                requiresRestart = true;
            }
            for (DlcItem item : items) {
                DlcStateConfig.DlcState previous = DlcStateConfig.getState(item.getId());
                boolean wasEnabled = previous != null && previous.enabled;
                if (requiresRestartOnToggle(item)) {
                    Boolean baseline = DlcRuntimeState.getBaselineEnabled(item.getId());
                    if (baseline != null) {
                        if (baseline != item.isEnabled()) {
                            requiresRestart = true;
                        }
                    } else if (item.isEnabled() != wasEnabled) {
                        requiresRestart = true;
                    }
                }

                if (item.isEnabled()) {
                    if (!wasEnabled) {
                        messages.add("Enabled " + item.getName());
                    }
                List<Path> statePaths = applyItem(item, wasEnabled);
                    if (!statePaths.isEmpty()) {
                        DlcStateConfig.DlcState state = new DlcStateConfig.DlcState();
                        state.enabled = true;
                        state.appliedTargets = statePaths.stream()
                            .map(DlcPathUtil::toGameRelativePath)
                            .toList();
                        DlcStateConfig.setState(item.getId(), state);
                    }
                } else {
                    if (wasEnabled) {
                        messages.add("Disabled " + item.getName());
                        RemovalResult removal = removeItem(item, previous);
                        messages.addAll(removal.messages());
                    }
                    DlcStateConfig.DlcState state = new DlcStateConfig.DlcState();
                    state.enabled = false;
                    state.appliedTargets = List.of();
                    DlcStateConfig.setState(item.getId(), state);
                }
            }
            PresetConflictPathResult presetPathResult = applyPresetConflictPaths();
            if (!presetPathResult.messages().isEmpty()) {
                messages.addAll(presetPathResult.messages());
            }
            if (presetPathResult.changed() && DlcPresetRuntimeState.hasAppliedFileConflictChangesFromBaseline()) {
                requiresRestart = true;
            }
        } finally {
            CURRENT_APPLY_CONTEXT.remove();
        }

        requiresRestart = requiresRestart || context.helperScheduled;
        DlcStateConfig.save();

        return new ApplyResult(messages, requiresRestart, attemptedHotReload, context.helperScheduled);
    }

    private static DlcConflictPathResult applyDlcConflictPaths(Collection<DlcItem> items) {
        List<String> messages = new ArrayList<>();
        Set<String> desiredPatterns = collectEnabledDlcConflictPathPatterns(items);
        Set<String> desiredPaths = resolveExistingConflictTargets(desiredPatterns);
        Set<String> currentPaths = collectConflictBackupRelativePaths();
        boolean changed = false;

        Set<String> toDisable = new LinkedHashSet<>(desiredPaths);
        toDisable.removeAll(currentPaths);
        for (String relative : toDisable) {
            if (disableDlcConflictPath(relative, messages)) {
                changed = true;
                currentPaths.add(relative);
            }
        }

        Set<String> toRestore = new LinkedHashSet<>(currentPaths);
        for (String relative : toRestore) {
            if (matchesAnyConflictPattern(desiredPatterns, relative)) {
                continue;
            }
            if (restoreDlcConflictPath(relative, messages)) {
                changed = true;
            }
        }
        return new DlcConflictPathResult(messages, changed);
    }

    private static Set<String> collectEnabledDlcConflictPathPatterns(Collection<DlcItem> items) {
        Set<String> result = new LinkedHashSet<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        for (DlcItem item : items) {
            if (item == null || !item.isEnabled()) {
                continue;
            }
            List<String> conflicts = item.getConflicts();
            if (conflicts == null || conflicts.isEmpty()) {
                continue;
            }
            for (String conflict : conflicts) {
                if (conflict == null || conflict.isBlank() || getItemByIdentifier(conflict) != null) {
                    continue;
                }
                String relative = DlcPathUtil.normalizeRelativePath(conflict);
                if (relative != null) {
                    result.add(relative);
                }
            }
        }
        return result;
    }

    private static DlcItem getItemByIdentifier(String identifier) {
        return DlcRuntimeState.getItemByIdentifier(identifier);
    }

    private static Set<String> resolveExistingConflictTargets(Set<String> patterns) {
        Set<String> result = new LinkedHashSet<>();
        Path gameDir = DlcPaths.gameDir();
        if (gameDir == null || patterns == null || patterns.isEmpty()) {
            return result;
        }
        Path normalizedGameDir = gameDir.toAbsolutePath().normalize();
        for (String pattern : patterns) {
            for (Path target : DlcPathUtil.findMatches(normalizedGameDir, pattern)) {
                String relative = DlcPathUtil.normalizeRelativePath(normalizedGameDir.relativize(target.toAbsolutePath().normalize()).toString());
                if (relative != null) {
                    result.add(relative);
                }
            }
        }
        return result;
    }

    private static Set<String> collectConflictBackupRelativePaths() {
        Set<String> result = new LinkedHashSet<>();
        Path root = resolveDlcConflictBackupRoot();
        if (root == null || !Files.exists(root)) {
            return result;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        try (var stream = Files.walk(normalizedRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String relative = DlcPathUtil.normalizeRelativePath(normalizedRoot.relativize(path.toAbsolutePath().normalize()).toString());
                if (relative != null) {
                    result.add(relative);
                }
            });
        } catch (IOException ignored) {
            recordApplyFailure(ignored);
        }
        return result;
    }

    private static boolean matchesAnyConflictPattern(Set<String> patterns, String relative) {
        if (patterns == null || patterns.isEmpty() || relative == null || relative.isBlank()) {
            return false;
        }
        for (String pattern : patterns) {
            if (DlcPathUtil.matches(pattern, relative)) {
                return true;
            }
        }
        return false;
    }

    private static boolean disableDlcConflictPath(String relative, List<String> messages) {
        Path target = resolveDlcConflictTarget(relative);
        Path backup = resolveDlcConflictBackup(relative);
        if (target == null || backup == null) {
            return false;
        }
        if (!Files.exists(target)) {
            return Files.exists(backup) || DlcFileOpsHelperClient.cancelPendingOperation(target, "move") && Files.exists(backup);
        }
        try {
            deleteRecursiveIfExists(backup);
            if (backup.getParent() != null) {
                Files.createDirectories(backup.getParent());
            }
            moveWithHelperFallback(target, backup, true);
            cleanupEmptyParents(target.getParent(), DlcPaths.gameDir());
            cleanupEmptyParents(backup.getParent(), resolveDlcConflictBackupRoot());
            return true;
        } catch (IOException exception) {
            addPathConflictMessage(messages, exception, "Failed to disable DLC conflict path: " + relative);
            recordApplyFailure(exception);
            return false;
        }
    }

    private static boolean restoreDlcConflictPath(String relative, List<String> messages) {
        Path target = resolveDlcConflictTarget(relative);
        Path backup = resolveDlcConflictBackup(relative);
        if (target == null || backup == null) {
            return false;
        }
        if (!Files.exists(backup)) {
            return Files.exists(target) || DlcFileOpsHelperClient.cancelPendingOperation(backup, "move") && Files.exists(target);
        }
        try {
            deleteRecursiveIfExists(target);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            moveWithHelperFallback(backup, target, true);
            cleanupEmptyParents(backup.getParent(), resolveDlcConflictBackupRoot());
            return true;
        } catch (IOException exception) {
            addPathConflictMessage(messages, exception, "Failed to restore DLC conflict path: " + relative);
            recordApplyFailure(exception);
            return false;
        }
    }

    private static void addPathConflictMessage(List<String> messages, IOException exception, String fallback) {
        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            messages.add(message);
        } else {
            messages.add(fallback);
        }
    }

    private static Path resolveDlcConflictTarget(String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        Path gameDir = DlcPaths.gameDir();
        if (gameDir == null) {
            return null;
        }
        Path resolved = DlcPaths.resolveAgainstGameDir(relative);
        if (resolved == null) {
            return null;
        }
        Path normalizedGameDir = gameDir.toAbsolutePath().normalize();
        return resolved.startsWith(normalizedGameDir) ? resolved : null;
    }

    private static Path resolveDlcConflictBackup(String relative) {
        Path root = resolveDlcConflictBackupRoot();
        if (root == null || relative == null || relative.isBlank()) {
            return null;
        }
        String normalized = DlcPathUtil.normalizeRelativePath(relative);
        return normalized == null ? null : root.resolve(normalized).normalize();
    }

    private static Path resolveDlcConflictBackupRoot() {
        Path dlcRoot = DlcPaths.dlcRoot();
        return dlcRoot == null ? null : DlcTmpPaths.resolve(dlcRoot, "conflicts");
    }

    private static PresetConflictPathResult applyPresetConflictPaths() {
        java.util.Map<String, java.util.Set<String>> desiredByPreset = DlcConfig.isPresetFuncOn()
            ? DlcPresetRuntimeState.getDesiredDisabledRelativePathsByPreset()
            : java.util.Map.of();
        java.util.Map<String, java.util.Set<String>> currentByPreset = DlcPresetRuntimeState.getAppliedDisabledRelativePathsByPreset();
        if (currentByPreset.equals(desiredByPreset)) {
            return new PresetConflictPathResult(List.of(), false);
        }

        List<String> messages = new ArrayList<>();
        boolean changed = false;
        Set<String> current = unionRelativePaths(currentByPreset);
        Set<String> desired = unionRelativePaths(desiredByPreset);
        Set<String> restored = new LinkedHashSet<>();
        Set<String> disabled = new LinkedHashSet<>();

        Set<String> toRestore = new LinkedHashSet<>(current);
        toRestore.removeAll(desired);
        for (String relative : toRestore) {
            if (restorePresetConflictPath(relative, messages)) {
                restored.add(relative);
                changed = true;
            }
        }

        Set<String> toDisable = new LinkedHashSet<>(desired);
        toDisable.removeAll(current);
        for (String relative : toDisable) {
            if (disablePresetConflictPath(relative, messages)) {
                disabled.add(relative);
                changed = true;
            }
        }
        DlcPresetRuntimeState.setAppliedDisabledRelativePathsByPreset(
            updateAppliedRelativePathsByPreset(currentByPreset, desiredByPreset, restored, disabled));
        return new PresetConflictPathResult(messages, changed);
    }

    private static boolean disablePresetConflictPath(String relative, List<String> messages) {
        Path target = resolvePresetConflictTarget(relative);
        Path backup = resolvePresetConflictBackup(relative);
        if (target == null || backup == null) {
            return false;
        }
        if (!Files.exists(target)) {
            if (Files.exists(backup)) {
                return true;
            }
            if (DlcFileOpsHelperClient.cancelPendingOperation(target, "move") && Files.exists(backup)) {
                return true;
            }
            return false;
        }
        try {
            deleteRecursiveIfExists(backup);
            if (backup.getParent() != null) {
                Files.createDirectories(backup.getParent());
            }
            moveWithHelperFallback(target, backup, true);
            cleanupEmptyParents(target.getParent(), DlcPaths.gameDir());
            cleanupEmptyParents(backup.getParent(), DlcTmpPaths.resolve(DlcPaths.dlcRoot(), "disabled"));
            return true;
        } catch (IOException ignored) {
            String message = ignored.getMessage();
            if (message != null && !message.isBlank()) {
                messages.add(message);
            } else {
                messages.add("Failed to disable preset conflict path: " + relative);
            }
            recordApplyFailure(ignored);
            return false;
        }
    }

    private static boolean restorePresetConflictPath(String relative, List<String> messages) {
        Path target = resolvePresetConflictTarget(relative);
        Path backup = resolvePresetConflictBackup(relative);
        if (target == null || backup == null) {
            return false;
        }
        if (!Files.exists(backup)) {
            if (Files.exists(target)) {
                return true;
            }
            if (DlcFileOpsHelperClient.cancelPendingOperation(backup, "move") && Files.exists(target)) {
                return true;
            }
            return false;
        }
        try {
            deleteRecursiveIfExists(target);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            moveWithHelperFallback(backup, target, true);
            cleanupEmptyParents(backup.getParent(), DlcTmpPaths.resolve(DlcPaths.dlcRoot(), "disabled"));
            return true;
        } catch (IOException ignored) {
            String message = ignored.getMessage();
            if (message != null && !message.isBlank()) {
                messages.add(message);
            } else {
                messages.add("Failed to restore preset conflict path: " + relative);
            }
            recordApplyFailure(ignored);
            return false;
        }
    }

    private static Path resolvePresetConflictTarget(String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        Path gameDir = DlcPaths.gameDir();
        if (gameDir == null) {
            return null;
        }
        Path resolved = DlcPaths.resolveAgainstGameDir(relative);
        if (resolved == null) {
            return null;
        }
        Path normalizedGameDir = gameDir.toAbsolutePath().normalize();
        if (!resolved.startsWith(normalizedGameDir)) {
            return null;
        }
        return resolved;
    }

    private static Path resolvePresetConflictBackup(String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        Path dlcRoot = DlcPaths.dlcRoot();
        if (dlcRoot == null) {
            return null;
        }
        Path disabledRoot = DlcTmpPaths.resolve(dlcRoot, "disabled");
        if (disabledRoot == null) {
            return null;
        }
        return disabledRoot.resolve(relative).normalize();
    }

    private static Set<String> unionRelativePaths(java.util.Map<String, java.util.Set<String>> byPreset) {
        Set<String> result = new LinkedHashSet<>();
        if (byPreset == null || byPreset.isEmpty()) {
            return result;
        }
        for (java.util.Set<String> paths : byPreset.values()) {
            if (paths != null) {
                result.addAll(paths);
            }
        }
        return result;
    }

    private static java.util.Map<String, java.util.Set<String>> updateAppliedRelativePathsByPreset(
        java.util.Map<String, java.util.Set<String>> currentByPreset,
        java.util.Map<String, java.util.Set<String>> desiredByPreset,
        java.util.Set<String> restored,
        java.util.Set<String> disabled
    ) {
        java.util.Map<String, java.util.Set<String>> next = new java.util.LinkedHashMap<>();
        Set<String> current = unionRelativePaths(currentByPreset);
        Set<String> desired = unionRelativePaths(desiredByPreset);
        Set<String> all = new LinkedHashSet<>(current);
        all.addAll(desired);
        for (String relative : all) {
            boolean currentHas = current.contains(relative);
            boolean desiredHas = desired.contains(relative);
            if (currentHas && desiredHas) {
                assignRelativePathOwners(next, desiredByPreset, relative);
            } else if (!currentHas && desiredHas) {
                if (disabled.contains(relative)) {
                    assignRelativePathOwners(next, desiredByPreset, relative);
                }
            } else if (currentHas && !desiredHas) {
                if (!restored.contains(relative)) {
                    assignRelativePathOwners(next, currentByPreset, relative);
                }
            }
        }
        return next;
    }

    private static void assignRelativePathOwners(
        java.util.Map<String, java.util.Set<String>> target,
        java.util.Map<String, java.util.Set<String>> ownersByPreset,
        String relative
    ) {
        if (relative == null || relative.isBlank() || ownersByPreset == null || ownersByPreset.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<String, java.util.Set<String>> entry : ownersByPreset.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || !entry.getValue().contains(relative)) {
                continue;
            }
            target.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).add(relative);
        }
    }

    private static void cleanupEmptyParents(Path start, Path stopExclusive) {
        if (start == null) {
            return;
        }
        Path stop = stopExclusive == null ? null : stopExclusive.toAbsolutePath().normalize();
        Path current = start.toAbsolutePath().normalize();
        while (current != null && !current.equals(stop)) {
            try {
                if (!Files.isDirectory(current) || !isDirectoryEmpty(current)) {
                    break;
                }
                Files.deleteIfExists(current);
            } catch (IOException ignored) {
                break;
            }
            current = current.getParent();
        }
    }

    private static boolean isDirectoryEmpty(Path path) throws IOException {
        try (var stream = Files.list(path)) {
            return stream.findAny().isEmpty();
        }
    }

    private static boolean requiresRestartOnToggle(DlcItem item) {
        return item.getType() == DlcType.MOD
            || item.getType() == DlcType.TACZ_PACK
            || DlcConfigTypeSupport.isConfigType(item);
    }

    private static List<Path> applyItem(DlcItem item, boolean wasEnabled) {
        List<Path> statePaths = new ArrayList<>();
        try {
            if (item.getType() == DlcType.UNKNOWN) {
                Path target = DlcPaths.resolveAppliedTarget(item.getAppliedTargetRaw());
                if (target == null) {
                    return statePaths;
                }
                List<Path> sources = item.getSourcePaths();
                if (sources.isEmpty()) {
                    return statePaths;
                }
                boolean dirTarget = DlcPaths.isAppliedTargetDirectory(item.getAppliedTargetRaw(), target, sources.get(0));
                if (!dirTarget && sources.size() > 1) {
                    return statePaths;
                }
                if (DlcConfigTypeSupport.isConfigType(item)) {
                    return applyConfigItem(item, sources, target, dirTarget, wasEnabled);
                }
                if (dirTarget) {
                    Files.createDirectories(target);
                    for (Path source : sources) {
                        if (source == null) {
                            continue;
                        }
                        Path resolved = target.resolve(source.getFileName().toString());
                        if (!wasEnabled && Files.exists(resolved)) {
                            backupExisting(resolved);
                        }
                        copyRecursive(source, resolved);
                        statePaths.add(resolved);
                    }
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Path source = sources.get(0);
                    if (!wasEnabled && Files.exists(target)) {
                        backupExisting(target);
                    }
                    copyRecursive(source, target);
                    statePaths.add(target);
                }
                return statePaths;
            }
            List<Path> targetDirs = switch (item.getType()) {
                case MOD -> List.of(DlcPaths.modsDir());
                case RESOURCE_PACK -> DlcConfig.getResourcepackRoots();
                case DATA_PACK -> DlcConfig.getDatapackRoots();
                case TACZ_PACK -> List.of(DlcPaths.taczDir());
                default -> List.of();
            };
            for (Path targetDir : targetDirs) {
                Files.createDirectories(targetDir);
                Path target = targetDir.resolve(item.getSourcePath().getFileName().toString());
                if (!wasEnabled && Files.exists(target)) {
                    backupExisting(target);
                }
                copyRecursive(item.getSourcePath(), target);
                statePaths.add(target);
            }
        } catch (IOException ignored) {
            recordApplyFailure(ignored);
        }

        return statePaths;
    }

    private static List<Path> applyConfigItem(DlcItem item, List<Path> sources, Path targetRoot, boolean dirTarget, boolean wasEnabled) throws IOException {
        List<Path> statePaths = new ArrayList<>();
        if (dirTarget) {
            Files.createDirectories(targetRoot);
            for (Path source : sources) {
                if (source == null) {
                    continue;
                }
                Path target = targetRoot.resolve(source.getFileName().toString());
                Path statePath = backupConfigTarget(item.getId(), target, !wasEnabled);
                copyRecursive(source, target);
                statePaths.add(statePath);
            }
            return statePaths;
        }
        Path parent = targetRoot.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path source = sources.get(0);
        Path statePath = backupConfigTarget(item.getId(), targetRoot, !wasEnabled);
        copyRecursive(source, targetRoot);
        statePaths.add(statePath);
        return statePaths;
    }

    private static Path backupConfigTarget(String itemId, Path target, boolean shouldCreate) throws IOException {
        Path backupPath = DlcConfigTypeSupport.backupPathForItemTarget(itemId, target);
        Path markerPath = DlcConfigTypeSupport.missingMarkerPath(backupPath);
        if (shouldCreate) {
            deleteRecursiveIfExists(backupPath);
            deleteRecursiveIfExists(markerPath);
            Files.createDirectories(DlcConfigTypeSupport.backupRootForItem(itemId));
            if (Files.exists(target)) {
                copyRecursive(target, backupPath);
                return backupPath;
            }
            Files.writeString(markerPath, CONFIG_MISSING_MARKER);
            return markerPath;
        }
        if (Files.exists(markerPath)) {
            return markerPath;
        }
        return backupPath;
    }

    private static RemovalResult removeItem(DlcItem item, DlcStateConfig.DlcState previous) {
        List<String> messages = new ArrayList<>();
        if (previous == null || previous.appliedTargets == null) {
            return new RemovalResult(messages);
        }
        if (DlcConfigTypeSupport.isConfigType(item)) {
            restoreConfigItem(item, previous, messages);
            return new RemovalResult(messages);
        }
        List<String> failedPaths = new ArrayList<>();
        for (String pathString : previous.appliedTargets) {
            Path resolved = resolveStoredPath(pathString);
            try {
                deleteRecursive(resolved);
            } catch (IOException ignored) {
                String message = ignored.getMessage();
                if (message != null && message.startsWith("Failed to ")) {
                    messages.add(message);
                    continue;
                }
                if (resolved != null) {
                    failedPaths.add(DlcPathUtil.toGameRelativePath(resolved));
                }
            }
        }
        if (!failedPaths.isEmpty()) {
            messages.add("Failed to remove some files for " + item.getName() + ": " + String.join(", ", failedPaths));
        }
        return new RemovalResult(messages);
    }

    private static void restoreConfigItem(DlcItem item, DlcStateConfig.DlcState previous, List<String> messages) {
        List<Path> targets = resolveConfigTargets(item);
        List<String> stored = previous.appliedTargets == null ? List.of() : previous.appliedTargets;
        for (int i = 0; i < targets.size(); i++) {
            Path target = targets.get(i);
            Path fallbackBackup = DlcConfigTypeSupport.backupPathForItemTarget(item.getId(), target);
            Path fallbackMarker = DlcConfigTypeSupport.missingMarkerPath(fallbackBackup);
            Path statePath = i < stored.size() ? resolveStoredPath(stored.get(i)) : null;
            if (statePath == null || statePath.toString().isBlank()) {
                if (Files.exists(fallbackMarker)) {
                    statePath = fallbackMarker;
                } else if (Files.exists(fallbackBackup)) {
                    statePath = fallbackBackup;
                } else {
                    Path legacyBackup = DlcConfigTypeSupport.backupPathForTarget(target);
                    Path legacyMarker = DlcConfigTypeSupport.missingMarkerPath(legacyBackup);
                    statePath = Files.exists(legacyMarker) ? legacyMarker : legacyBackup;
                }
            }
            Path backupPath = statePath;
            Path markerPath = statePath;
            if (Files.exists(statePath) && statePath.getFileName() != null
                && statePath.getFileName().toString().endsWith(".missing")) {
                backupPath = fallbackBackup;
                if (!Files.exists(backupPath)) {
                    backupPath = DlcConfigTypeSupport.backupPathForTarget(target);
                }
            } else if (statePath.equals(fallbackBackup)) {
                markerPath = fallbackMarker;
            } else if (statePath.equals(DlcConfigTypeSupport.backupPathForTarget(target))) {
                markerPath = DlcConfigTypeSupport.missingMarkerPath(statePath);
            } else {
                markerPath = DlcConfigTypeSupport.missingMarkerPath(statePath);
            }
            try {
                if (isMissingMarker(statePath) || isMissingMarker(markerPath)) {
                    deleteRecursiveIfExists(target);
                } else if (Files.exists(backupPath)) {
                    copyRecursive(backupPath, target);
                } else {
                    deleteRecursiveIfExists(target);
                }
                deleteRecursiveIfExists(backupPath);
                deleteRecursiveIfExists(markerPath);
            } catch (IOException ignored) {
                String message = ignored.getMessage();
                if (message != null && message.startsWith("Failed to ")) {
                    messages.add(message);
                } else if (target != null) {
                    messages.add("Failed to restore: " + DlcPathUtil.toGameRelativePath(target));
                }
            }
        }
    }

    private static boolean isMissingMarker(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (name.endsWith(".missing")) {
            return true;
        }
        try {
            return CONFIG_MISSING_MARKER.equals(Files.readString(path).trim());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static List<Path> resolveConfigTargets(DlcItem item) {
        List<Path> targets = new ArrayList<>();
        if (item == null) {
            return targets;
        }
        Path targetRoot = DlcPaths.resolveAppliedTarget(item.getAppliedTargetRaw());
        if (targetRoot == null) {
            return targets;
        }
        List<Path> sources = item.getSourcePaths();
        if (sources.isEmpty()) {
            return targets;
        }
        boolean dirTarget = DlcPaths.isAppliedTargetDirectory(item.getAppliedTargetRaw(), targetRoot, sources.get(0));
        if (!dirTarget && sources.size() > 1) {
            return targets;
        }
        if (dirTarget) {
            for (Path source : sources) {
                if (source == null) {
                    continue;
                }
                targets.add(targetRoot.resolve(source.getFileName().toString()));
            }
        } else {
            targets.add(targetRoot);
        }
        return targets;
    }

    private static void copyRecursive(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(dir);
                    Files.createDirectories(target.resolve(relative));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(file);
                    copyWithHelperFallback(file, target.resolve(relative));
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            copyWithHelperFallback(source, target);
        }
    }

    private static void backupExisting(Path target) {
        if (target == null || !Files.exists(target)) {
            return;
        }
        Path backupRoot = null;
        if (!DlcConfig.getDlcRoots().isEmpty()) {
            backupRoot = DlcTmpPaths.resolve(DlcConfig.getDlcRoots().get(0), "backup");
        }
        if (backupRoot == null) {
            backupRoot = DlcTmpPaths.resolve(DlcPaths.gameDir(), "backup");
        }
        try {
            Files.createDirectories(backupRoot);
            String name = target.getFileName().toString() + "." + System.currentTimeMillis() + ".bak";
            Path backupTarget = backupRoot.resolve(name);
            copyRecursive(target, backupTarget);
        } catch (IOException ignored) {
        }
    }

    private static void deleteRecursiveIfExists(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        deleteRecursive(path);
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                deleteWithHelperFallback(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                deleteWithHelperFallback(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyWithHelperFallback(Path source, Path target) throws IOException {
        if (source == null || target == null) {
            return;
        }
        if (DlcFileOpsHelperClient.cancelPendingDelete(target) && Files.exists(target)) {
            return;
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            DlcFileOpsHelperClient.FileOpResult result = DlcFileOpsHelperClient.scheduleCopy(source, target, true);
            if (result.ok()) {
                markHelperScheduled();
                return;
            }
            throw new IOException(result.message(), exception);
        }
    }

    private static void moveWithHelperFallback(Path source, Path target, boolean replaceExisting) throws IOException {
        if (source == null || target == null) {
            return;
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try {
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
        } catch (IOException exception) {
            DlcFileOpsHelperClient.FileOpResult result = DlcFileOpsHelperClient.scheduleMove(source, target, replaceExisting);
            if (result.ok()) {
                markHelperScheduled();
                return;
            }
            throw new IOException(result.message(), exception);
        }
    }

    private static void deleteWithHelperFallback(Path path) throws IOException {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            DlcFileOpsHelperClient.FileOpResult result = DlcFileOpsHelperClient.scheduleDelete(path);
            if (result.ok()) {
                markHelperScheduled();
                return;
            }
            throw new IOException(result.message(), exception);
        }
    }

    private static void recordApplyFailure(IOException exception) {
        if (exception == null) {
            return;
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        ApplyContext context = CURRENT_APPLY_CONTEXT.get();
        if (context == null || context.messages.contains(message)) {
            return;
        }
        context.messages.add(message);
    }

    private static void markHelperScheduled() {
        ApplyContext context = CURRENT_APPLY_CONTEXT.get();
        if (context != null) {
            context.helperScheduled = true;
        }
    }

    private static Path resolveStoredPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Path.of("");
        }
        Path path = Path.of(rawPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return DlcPaths.gameDir().resolve(path).normalize();
    }

    private record RemovalResult(List<String> messages) {}

    private static final class ApplyContext {
        private final List<String> messages;
        private boolean helperScheduled;

        private ApplyContext(List<String> messages) {
            this.messages = messages;
        }
    }

    private record PresetConflictPathResult(List<String> messages, boolean changed) {}

    private record DlcConflictPathResult(List<String> messages, boolean changed) {}

    public record ApplyResult(List<String> messages, boolean requiresRestart, boolean hotReloaded, boolean helperScheduled) {}
}
