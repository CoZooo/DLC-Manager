package com.cozooo.dlc_manager.core;



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
    public static ApplyResult apply(List<DlcItem> items, ApplyMode mode) {
        List<String> messages = new ArrayList<>();
        boolean requiresRestart = false;
        boolean attemptedHotReload = false;

        for (DlcItem item : items) {
            DlcConfig.DlcState previous = DlcConfig.getState(item.getId());
            boolean wasEnabled = previous != null && previous.enabled;

            if (item.isEnabled()) {
                if (!wasEnabled) {
                    messages.add("Enabled " + item.getName());
                }
                List<Path> targets = applyItem(item);
                if (!targets.isEmpty()) {
                    DlcConfig.DlcState state = new DlcConfig.DlcState();
                    state.enabled = true;
                    state.appliedTargets = targets.stream().map(Path::toString).toList();
                    DlcConfig.setState(item.getId(), state);
                }
                if (item.getType() == DlcType.MOD || item.getType() == DlcType.TACZ_PACK) {
                    requiresRestart = true;
                }
            } else {
                if (wasEnabled) {
                    messages.add("Disabled " + item.getName());
                    removeItem(previous);
                }
                DlcConfig.DlcState state = new DlcConfig.DlcState();
                state.enabled = false;
                state.appliedTargets = List.of();
                DlcConfig.setState(item.getId(), state);
            }
        }

        DlcConfig.save();

        return new ApplyResult(messages, requiresRestart, attemptedHotReload);
    }

    private static List<Path> applyItem(DlcItem item) {
        List<Path> targets = new ArrayList<>();
        try {
            List<Path> targetDirs = switch (item.getType()) {
                case MOD -> List.of(DlcPaths.modsDir());
                case RESOURCE_PACK -> DlcPathConfig.getResourcepackRoots();
                case DATA_PACK -> DlcPathConfig.getDatapackRoots();
                case TACZ_PACK -> List.of(DlcPaths.taczDir());
                default -> DlcPathConfig.getOtherRoots();
            };
            for (Path targetDir : targetDirs) {
                Files.createDirectories(targetDir);
                Path target = targetDir.resolve(item.getSourcePath().getFileName().toString());
                copyRecursive(item.getSourcePath(), target);
                targets.add(target);
            }
        } catch (IOException ignored) {
            // Best-effort copy.
        }

        return targets;
    }

    private static void removeItem(DlcConfig.DlcState previous) {
        if (previous == null || previous.appliedTargets == null) {
            return;
        }
        for (String pathString : previous.appliedTargets) {
            try {
                deleteRecursive(Path.of(pathString));
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        }
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
                    Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public record ApplyResult(List<String> messages, boolean requiresRestart, boolean hotReloaded) {}
}
