package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.core.DlcTmpPaths;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DlcServerOps {
    private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private DlcServerOps() {
    }

    public static ServerOpContext begin(Path dlcRoot, Path gameDir, String name) {
        String stamp = LocalDateTime.now().format(STAMP_FORMAT);
        String safeName = name == null || name.isBlank() ? "op" : name.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path root = DlcTmpPaths.resolve(dlcRoot, "server_ops", stamp + "_" + safeName);
        Path backup = root.resolve("backup");
        Path logPath = root.resolve("log.txt");
        try {
            Files.createDirectories(backup);
        } catch (IOException ignored) {
        }
        return new ServerOpContext(root, backup, logPath, gameDir);
    }

    public static final class ServerOpContext implements AutoCloseable {
        private final Path root;
        private final Path backupRoot;
        private final Path logPath;
        private final Path gameDir;
        private BufferedWriter writer;

        private ServerOpContext(Path root, Path backupRoot, Path logPath, Path gameDir) {
            this.root = root;
            this.backupRoot = backupRoot;
            this.logPath = logPath;
            this.gameDir = gameDir;
        }

        public Path getRoot() {
            return root;
        }

        public void log(String message) {
            if (message == null) {
                return;
            }
            try {
                if (writer == null) {
                    Files.createDirectories(logPath.getParent());
                    writer = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8);
                }
                writer.write(message);
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {
            }
        }

        public void backupIfExists(Path target) {
            if (target == null || !Files.exists(target)) {
                return;
            }
            Path backupPath = resolveBackupPath(target);
            if (backupPath == null) {
                return;
            }
            try {
                if (Files.isDirectory(target)) {
                    copyRecursive(target, backupPath);
                } else {
                    Files.createDirectories(backupPath.getParent());
                    Files.copy(target, backupPath, StandardCopyOption.REPLACE_EXISTING);
                }
                log("BACKUP " + displayPath(target));
            } catch (IOException ignored) {
            }
        }

        public void deleteRecursive(Path target) {
            if (target == null || !Files.exists(target)) {
                return;
            }
            try {
                Files.walkFileTree(target, new SimpleFileVisitor<>() {
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
                log("DELETE " + displayPath(target));
            } catch (IOException ignored) {
            }
        }

        private Path resolveBackupPath(Path target) {
            if (backupRoot == null) {
                return null;
            }
            Path normalizedTarget = target.normalize();
            if (gameDir != null) {
                Path normalizedRoot = gameDir.normalize();
                if (normalizedTarget.startsWith(normalizedRoot)) {
                    Path relative = normalizedRoot.relativize(normalizedTarget);
                    return backupRoot.resolve(relative);
                }
            }
            return backupRoot.resolve(normalizedTarget.getFileName());
        }

        public String displayPath(Path target) {
            if (target == null) {
                return "";
            }
            Path normalizedTarget = target.normalize();
            if (gameDir != null) {
                Path normalizedRoot = gameDir.normalize();
                if (normalizedTarget.startsWith(normalizedRoot)) {
                    return normalizedRoot.relativize(normalizedTarget).toString().replace('\\', '/');
                }
            }
            return target.toString();
        }

        @Override
        public void close() {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
                writer = null;
            }
        }
    }

    public static void copyRecursive(Path source, Path target) throws IOException {
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
                Path dest = target.resolve(relative);
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
