package com.cozooo.dlc_manager.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DlcFileMatch {
    private static final long LIGHT_LIMIT = 128 * DlcFileSize.KIB;
    private static final int SAMPLE_BYTES = (int)(64 * DlcFileSize.KIB);
    private record CachedHash(String stamp, String hash) {}
    private static final Map<Path, CachedHash> HASHES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Map<Path, CachedHash>> SCAN_HASHES = new ThreadLocal<>();
    private DlcFileMatch() {}

    public static String mode(String value) { return "name".equalsIgnoreCase(value) ? "name" : "hash"; }
    public static void beginScan() { SCAN_HASHES.set(new HashMap<>()); }
    public static void endScan() { SCAN_HASHES.remove(); }

    public static String hash(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        String stamp = stamp(normalized);
        Map<Path, CachedHash> scan = SCAN_HASHES.get();
        CachedHash cached = scan == null ? null : scan.get(normalized);
        if (cached == null || !cached.stamp.equals(stamp)) cached = HASHES.get(normalized);
        if (cached != null && cached.stamp.equals(stamp)) return cached.hash;
        String result = fullHash(normalized);
        if (!stamp.equals(stamp(normalized))) throw new IOException("Resource changed while hashing: " + path);
        CachedHash updated = new CachedHash(stamp, result);
        if (HASHES.size() >= 8192) HASHES.clear();
        HASHES.put(normalized, updated);
        if (scan != null) scan.put(normalized, updated);
        return result;
    }

    private static String stamp(Path path) throws IOException {
        MessageDigest digest = digest();
        BasicFileAttributes attributes = attributes(path);
        if (attributes.isDirectory()) {
            try (var paths = Files.walk(path)) {
                for (Path child : paths.sorted(Comparator.comparing(p -> path.relativize(p).toString())).toList()) {
                    checkInterrupted();
                    addStamp(digest, path.relativize(child).toString().replace('\\', '/'), child);
                }
            }
        } else addStamp(digest, "", path);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void addStamp(MessageDigest digest, String relative, Path path) throws IOException {
        BasicFileAttributes attributes = attributes(path);
        String value = relative + "\u0000" + (attributes.isDirectory() ? "directory" : "file") + "\u0000"
            + attributes.size() + "\u0000" + attributes.lastModifiedTime() + "\u0000" + attributes.fileKey() + "\u0000";
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        if (attributes.isRegularFile()) { digest.update(lightHash(path).getBytes(StandardCharsets.US_ASCII)); unchanged(path, attributes); }
    }

    public static String lightHash(Path path) throws IOException {
        BasicFileAttributes before = attributes(path);
        if (!before.isRegularFile()) throw new IOException("Not a regular resource: " + path);
        if (before.size() <= LIGHT_LIMIT) return fullHash(path);
        MessageDigest digest = digest();
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(before.size()).array());
        try (var channel = Files.newByteChannel(path)) {
            ByteBuffer buffer = ByteBuffer.allocate(SAMPLE_BYTES);
            for (long offset : new long[]{0, before.size() - buffer.capacity()}) {
                checkInterrupted(); buffer.clear(); channel.position(offset);
                while (buffer.hasRemaining()) {
                    checkInterrupted();
                    if (channel.read(buffer) < 0) throw new EOFException("Resource changed while hashing: " + path);
                }
                digest.update(buffer.array());
            }
        }
        unchanged(path, before);
        return "light-" + HexFormat.of().formatHex(digest.digest());
    }

    public static String fullHash(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        BasicFileAttributes before = attributes(normalized);
        MessageDigest digest = digest();
        if (before.isDirectory()) {
            try (var paths = Files.walk(normalized)) {
                for (Path child : paths.sorted(Comparator.comparing(p -> normalized.relativize(p).toString())).toList()) {
                    checkInterrupted();
                    BasicFileAttributes childAttributes = attributes(child);
                    digest.update((normalized.relativize(child).toString().replace('\\', '/') + "\u0000" + (childAttributes.isDirectory() ? "directory" : "file") + "\u0000").getBytes(StandardCharsets.UTF_8));
                    if (childAttributes.isRegularFile()) {
                        digest.update((childAttributes.size() + "\u0000").getBytes(StandardCharsets.UTF_8));
                        update(digest, child);
                    }
                }
            }
        } else update(digest, normalized);
        unchanged(normalized, before);
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String hash(java.io.InputStream input) throws IOException {
        checkInterrupted();
        MessageDigest digest = digest();
        byte[] buffer = new byte[SAMPLE_BYTES];
        int count;
        while ((count = input.read(buffer)) != -1) { checkInterrupted(); digest.update(buffer, 0, count); }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, Path file) throws IOException {
        BasicFileAttributes before = attributes(file);
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[SAMPLE_BYTES];
            int count;
            while ((count = input.read(buffer)) != -1) { checkInterrupted(); digest.update(buffer, 0, count); }
        }
        unchanged(file, before);
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        checkInterrupted();
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() && !attributes.isDirectory()) throw new IOException("Not a regular resource or directory: " + path);
        return attributes;
    }

    private static void unchanged(Path path, BasicFileAttributes before) throws IOException {
        BasicFileAttributes after = attributes(path);
        if (before.isDirectory() != after.isDirectory() || before.size() != after.size()
            || !before.lastModifiedTime().equals(after.lastModifiedTime()) || !java.util.Objects.equals(before.fileKey(), after.fileKey())) throw new IOException("Resource changed while hashing: " + path);
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Resource validation canceled");
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
