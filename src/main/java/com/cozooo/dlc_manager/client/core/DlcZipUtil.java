package com.cozooo.dlc_manager.client.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Stream;

public final class DlcZipUtil {
    private DlcZipUtil() {
    }

    public static Path createUniqueDir(Path baseDir, String prefix) throws IOException {
        if (baseDir == null) {
            throw new IOException("Base directory is not available");
        }
        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedBase);
        String safePrefix = sanitizePrefix(prefix);
        while (true) {
            Path candidate = normalizedBase.resolve(safePrefix + "-" + System.nanoTime()).normalize();
            if (!candidate.startsWith(normalizedBase)) {
                throw new IOException("Invalid zip staging directory");
            }
            try {
                return Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException ignored) {
            }
        }
    }

    public static void extractZip(Path zipPath, Path targetRoot) throws IOException {
        extractZip(zipPath, targetRoot, name -> true);
    }

    public static void extractZip(Path zipPath, Path targetRoot, Predicate<String> include) throws IOException {
        if (zipPath == null || !Files.isRegularFile(zipPath)) {
            throw new IOException("Archive is not a file");
        }
        if (targetRoot == null) {
            throw new IOException("Target directory is not available");
        }
        Path normalizedRoot = targetRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            for (ZipEntry entry : zip.stream().filter(entry -> include.test(entry.getName().replace('\\', '/')))
                .sorted(Comparator.comparing(ZipEntry::getName)).toList()) {
                extractEntry(zip, entry, normalizedRoot);
            }
        }
    }

    public static void deleteRecursive(Path path) {
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

    private static void extractEntry(ZipFile zip, ZipEntry entry, Path targetRoot) throws IOException {
        String name = entry.getName();
        if (name == null) {
            return;
        }
        String normalizedName = name.replace('\\', '/');
        if (normalizedName.isBlank()
            || normalizedName.startsWith("/")
            || normalizedName.indexOf('\u0000') >= 0
            || normalizedName.startsWith("__MACOSX/")
            || normalizedName.startsWith("._")
            || isDsStoreEntry(normalizedName)) {
            return;
        }
        Path target = targetRoot.resolve(normalizedName).normalize();
        if (!target.startsWith(targetRoot)) {
            return;
        }
        if (entry.isDirectory()) {
            Files.createDirectories(target);
            return;
        }
        Files.createDirectories(target.getParent());
        try (var input = zip.getInputStream(entry); var output = Files.newOutputStream(target)) {
            input.transferTo(output);
        }
    }

    private static String sanitizePrefix(String prefix) {
        String value = prefix == null ? "" : prefix.trim();
        if (value.isBlank()) {
            return "archive";
        }
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "archive" : sanitized;
    }

    private static boolean isDsStoreEntry(String name) {
        return ".DS_Store".equals(name) || name.endsWith("/.DS_Store");
    }
}
