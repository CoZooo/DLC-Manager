package com.cozooo.dlc_manager.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public final class DlcFileMatch {
    private static final ThreadLocal<Map<Path, String>> SCAN_HASHES = new ThreadLocal<>();
    private DlcFileMatch() {}

    public static String mode(String value) { return "name".equalsIgnoreCase(value) ? "name" : "hash"; }
    public static void beginScan() { SCAN_HASHES.set(new HashMap<>()); }
    public static void endScan() { SCAN_HASHES.remove(); }

    public static String hash(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Map<Path, String> cache = SCAN_HASHES.get();
        if (cache != null && cache.containsKey(normalized)) return cache.get(normalized);
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        if (Files.isDirectory(normalized)) {
            try (var paths = Files.walk(normalized)) {
                for (Path child : paths.sorted(Comparator.comparing(p -> normalized.relativize(p).toString())).toList()) {
                    if (Files.isSymbolicLink(child)) throw new IOException("Cannot hash symbolic link: " + child);
                    digest.update((normalized.relativize(child).toString().replace('\\', '/') + "\u0000" + (Files.isDirectory(child) ? "directory" : "file") + "\u0000").getBytes(StandardCharsets.UTF_8));
                    if (Files.isRegularFile(child)) {
                        digest.update((Files.size(child) + "\u0000").getBytes(StandardCharsets.UTF_8));
                        update(digest, child);
                    }
                }
            }
        } else {
            if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) throw new IOException("Not a regular resource: " + normalized);
            update(digest, normalized);
        }
        String result = HexFormat.of().formatHex(digest.digest());
        if (cache != null) cache.put(normalized, result);
        return result;
    }

    private static void update(MessageDigest digest, Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
    }

    public static boolean matches(Path source, Path target, String mode) throws IOException {
        if (source == null || target == null || !Files.exists(target)) return false;
        if ("name".equals(mode(mode))) return true;
        if (!Files.exists(source) || Files.isDirectory(source) != Files.isDirectory(target)) return false;
        if (Files.isRegularFile(source) && Files.size(source) != Files.size(target)) return false;
        return hash(source).equals(hash(target));
    }

    public static Path find(Path source, Path directory, String mode) throws IOException {
        if (source == null || directory == null || !Files.isDirectory(directory)) return null;
        Path named = directory.resolve(source.getFileName());
        if (matches(source, named, mode)) return named;
        if ("name".equals(mode(mode))) return null;
        try (var paths = Files.list(directory)) {
            for (Path path : paths.sorted().toList()) {
                if (!path.equals(named) && matches(source, path, mode)) return path;
            }
        }
        return null;
    }
    public static Path findByHash(String expectedHash, Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) return null;
        try (var paths = Files.list(directory)) {
            for (Path path : paths.sorted().toList()) {
                if (!Files.isSymbolicLink(path) && (Files.isRegularFile(path) || Files.isDirectory(path)) && expectedHash.equals(hash(path))) return path;
            }
        }
        return null;
    }

}
