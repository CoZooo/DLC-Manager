package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.client.helper.DlcFileOpsHelperClient;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class DlcApplier {
    private static final String CONFIG_MISSING_MARKER = "__dlc_config_missing__";
    private static final ThreadLocal<ApplyContext> CURRENT_APPLY_CONTEXT = new ThreadLocal<>();

    public static ApplyResult apply(List<DlcItem> items, ApplyMode mode) {
        List<String> messages = new ArrayList<>();
        boolean requiresRestart = false;
        boolean attemptedHotReload = false;
        ApplyContext context = new ApplyContext(messages);
        CURRENT_APPLY_CONTEXT.set(context);

        try {
            for (DlcItem item : items) {
                DlcConfig.DlcState previous = DlcConfig.getState(item.getId());
                boolean wasEnabled = previous != null && previous.enabled;
                if (item.isEnabled() != wasEnabled && requiresRestartOnToggle(item)) {
                    requiresRestart = true;
                }

                if (item.isEnabled()) {
                    if (!wasEnabled) {
                        messages.add("Enabled " + item.getName());
                    }
                    List<Path> statePaths = applyItem(item, wasEnabled);
                    if (!statePaths.isEmpty()) {
                        DlcConfig.DlcState state = new DlcConfig.DlcState();
                        state.enabled = true;
                        state.appliedTargets = statePaths.stream()
                            .map(DlcApplier::toGameRelativePath)
                            .toList();
                        DlcConfig.setState(item.getId(), state);
                    }
                } else {
                    if (wasEnabled) {
                        messages.add("Disabled " + item.getName());
                        RemovalResult removal = removeItem(item, previous);
                        messages.addAll(removal.messages());
                    }
                    DlcConfig.DlcState state = new DlcConfig.DlcState();
                    state.enabled = false;
                    state.appliedTargets = List.of();
                    DlcConfig.setState(item.getId(), state);
                }
            }
        } finally {
            CURRENT_APPLY_CONTEXT.remove();
        }

        requiresRestart = requiresRestart || context.helperScheduled;
        DlcConfig.save();

        return new ApplyResult(messages, requiresRestart, attemptedHotReload, context.helperScheduled);
    }

    private static boolean requiresRestartOnToggle(DlcItem item) {
        return item.getType() == DlcType.MOD
            || item.getType() == DlcType.TACZ_PACK
            || DlcConfigDlcSupport.isConfigType(item);
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
                if (DlcConfigDlcSupport.isConfigType(item)) {
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
                case RESOURCE_PACK -> DlcPathConfig.getResourcepackRoots();
                case DATA_PACK -> DlcPathConfig.getDatapackRoots();
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
                Path statePath = backupConfigTarget(target, !wasEnabled);
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
        Path statePath = backupConfigTarget(targetRoot, !wasEnabled);
        copyRecursive(source, targetRoot);
        statePaths.add(statePath);
        return statePaths;
    }

    private static Path backupConfigTarget(Path target, boolean shouldCreate) throws IOException {
        Path backupPath = DlcConfigDlcSupport.backupPathForTarget(target);
        Path markerPath = DlcConfigDlcSupport.missingMarkerPath(backupPath);
        if (shouldCreate) {
            deleteRecursiveIfExists(backupPath);
            deleteRecursiveIfExists(markerPath);
            Files.createDirectories(DlcConfigDlcSupport.backupRoot());
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

    private static RemovalResult removeItem(DlcItem item, DlcConfig.DlcState previous) {
        List<String> messages = new ArrayList<>();
        if (previous == null || previous.appliedTargets == null) {
            return new RemovalResult(messages);
        }
        if (DlcConfigDlcSupport.isConfigType(item)) {
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
                    failedPaths.add(toGameRelativePath(resolved));
                }
            }
        }
        if (!failedPaths.isEmpty()) {
            messages.add("Failed to remove some files for " + item.getName() + ": " + String.join(", ", failedPaths));
        }
        return new RemovalResult(messages);
    }

    private static void restoreConfigItem(DlcItem item, DlcConfig.DlcState previous, List<String> messages) {
        List<Path> targets = resolveConfigTargets(item);
        List<String> stored = previous.appliedTargets == null ? List.of() : previous.appliedTargets;
        for (int i = 0; i < targets.size(); i++) {
            Path target = targets.get(i);
            Path fallbackBackup = DlcConfigDlcSupport.backupPathForTarget(target);
            Path fallbackMarker = DlcConfigDlcSupport.missingMarkerPath(fallbackBackup);
            Path statePath = i < stored.size() ? resolveStoredPath(stored.get(i)) : null;
            if (statePath == null || statePath.toString().isBlank()) {
                statePath = Files.exists(fallbackMarker) ? fallbackMarker : fallbackBackup;
            }
            Path backupPath = statePath;
            Path markerPath = statePath;
            if (Files.exists(statePath) && statePath.getFileName() != null
                && statePath.getFileName().toString().endsWith(".missing")) {
                backupPath = fallbackBackup;
            } else if (statePath.equals(fallbackBackup)) {
                markerPath = fallbackMarker;
            } else {
                markerPath = DlcConfigDlcSupport.missingMarkerPath(statePath);
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
                    messages.add("Failed to restore: " + toGameRelativePath(target));
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
        if (!DlcPathConfig.getDlcRoots().isEmpty()) {
            backupRoot = DlcTmpPaths.resolve(DlcPathConfig.getDlcRoots().get(0), "backup");
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

    private static String toGameRelativePath(Path path) {
        if (path == null) {
            return "";
        }
        Path gameDir = DlcPaths.gameDir().toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        try {
            Path gameReal = gameDir.toRealPath();
            Path pathReal = normalized.toRealPath();
            if (pathReal.startsWith(gameReal)) {
                return gameReal.relativize(pathReal).toString();
            }
        } catch (Exception ignored) {
        }
        try {
            if (normalized.startsWith(gameDir)) {
                return gameDir.relativize(normalized).toString();
            }
        } catch (Exception ignored) {
        }
        return normalized.toString();
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

    public record ApplyResult(List<String> messages, boolean requiresRestart, boolean hotReloaded, boolean helperScheduled) {}
}
