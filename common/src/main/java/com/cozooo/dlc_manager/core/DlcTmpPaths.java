package com.cozooo.dlc_manager.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DlcTmpPaths {
    public static final String TMP_DIR = "tmp";
    public static final String LEGACY_TMP_DIR = "update_tmp";

    private static final Set<Path> MIGRATED_BASES = ConcurrentHashMap.newKeySet();
    private static final Object LOCK = new Object();

    private DlcTmpPaths() {
    }

    public static Path root(Path base) {
        if (base == null) {
            return null;
        }
        Path normalized = base.toAbsolutePath().normalize();
        migrateLegacyIfNeeded(normalized);
        return normalized.resolve(TMP_DIR);
    }

    public static Path legacyRoot(Path base) {
        if (base == null) {
            return null;
        }
        return base.toAbsolutePath().normalize().resolve(LEGACY_TMP_DIR);
    }

    public static Path resolve(Path base, String first, String... more) {
        Path path = root(base);
        if (path == null) {
            return null;
        }
        if (first != null && !first.isBlank()) {
            path = path.resolve(first);
        }
        if (more != null) {
            for (String segment : more) {
                if (segment != null && !segment.isBlank()) {
                    path = path.resolve(segment);
                }
            }
        }
        return path;
    }

    private static void migrateLegacyIfNeeded(Path base) {
        if (MIGRATED_BASES.contains(base)) {
            return;
        }
        synchronized (LOCK) {
            if (MIGRATED_BASES.contains(base)) {
                return;
            }
            Path legacy = base.resolve(LEGACY_TMP_DIR);
            Path current = base.resolve(TMP_DIR);
            try {
                if (Files.exists(legacy)) {
                    if (!Files.exists(current)) {
                        Files.move(legacy, current);
                    } else {
                        mergeLegacyTree(legacy, current);
                        deleteTree(legacy);
                    }
                }
            } catch (IOException ignored) {
            }
            MIGRATED_BASES.add(base);
        }
    }

    private static void mergeLegacyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = from.relativize(dir);
                Files.createDirectories(to.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = from.relativize(file);
                Path target = to.resolve(relative);
                Files.createDirectories(target.getParent());
                if (!Files.exists(target)) {
                    Files.move(file, target);
                } else {
                    Files.deleteIfExists(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
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
}
