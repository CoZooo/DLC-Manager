package com.cozooo.dlc_manager.sync;

import com.cozooo.dlc_manager.core.DlcFileMatch;
import com.cozooo.dlc_manager.core.DlcResourceHistory;
import java.io.*;
import java.nio.file.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

public final class SyncArchive {
    private SyncArchive() {}
    public static long size(Path path) throws IOException {
        if (!Files.isDirectory(path)) return Files.size(path);
        long size = 0;
        try (var paths = Files.walk(path)) {
            for (Path child : paths.toList()) {
                check(() -> false);
                if (Files.isSymbolicLink(child)) throw new SyncFailure("symbolic_resource");
                if (Files.isRegularFile(child)) size = Math.addExact(size, Files.size(child));
            }
        }
        return size;
    }
    public static java.util.List<SyncModel.File> inventory(Path root, BooleanSupplier canceled) throws IOException {
        java.util.List<SyncModel.File> result = new java.util.ArrayList<>();
        if (!Files.isDirectory(root)) return result;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                check(canceled);
                if (Files.isSymbolicLink(path)) throw new SyncFailure("symbolic_resource");
                if (Files.isRegularFile(path)) result.add(new SyncModel.File(root.relativize(path).toString().replace('\\', '/'), Files.size(path), DlcFileMatch.hash(path)));
            }
        }
        return result;
    }
    public static java.util.List<SyncModel.File> reusable(Path root, java.util.List<SyncModel.File> existing, BooleanSupplier canceled) throws IOException {
        java.util.Map<String, SyncModel.File> files = new java.util.HashMap<>();
        for (SyncModel.File file : existing) files.put(file.id(), file);
        return inventory(root, canceled).stream().filter(file -> SyncModel.same(file, files.get(file.id()))).toList();
    }
    private static Path child(Path root, String name) throws IOException {
        if (name.isBlank() || name.contains("\\") || name.contains(":") || name.startsWith("/")
            || java.util.Arrays.stream(name.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) throw new SyncFailure("invalid_reuse_path");
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new SyncFailure("resource_escape");
        for (Path part = path; part != null && part.startsWith(root); part = part.getParent()) if (Files.isSymbolicLink(part)) throw new SyncFailure("symbolic_resource");
        return path;
    }
    public static void pack(Path source, Path archive, BooleanSupplier canceled) throws IOException { pack(source, archive, canceled, java.util.List.of()); }
    public static void pack(Path source, Path archive, BooleanSupplier canceled, java.util.List<SyncModel.File> reused) throws IOException {
        java.util.Set<String> omitted = new java.util.HashSet<>();
        for (SyncModel.File file : reused) omitted.add(file.id());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive)); var paths = Files.walk(source)) {
            output.setLevel(0);
            byte[] buffer = new byte[65536];
            for (Path child : paths.sorted().toList()) {
                check(canceled);
                if (child.equals(source)) continue;
                if (Files.isSymbolicLink(child)) throw new SyncFailure("symbolic_resource");
                String relative = source.relativize(child).toString().replace('\\', '/');
                if (Files.isRegularFile(child) && omitted.contains(relative)) continue;
                output.putNextEntry(new ZipEntry(relative + (Files.isDirectory(child) ? "/" : "")));
                if (Files.isRegularFile(child)) try (InputStream input = Files.newInputStream(child)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) { check(canceled); output.write(buffer, 0, count); }
                }
                output.closeEntry();
            }
        }
    }
    public static void install(Path archive, Path target, long size, String hash, SyncModel.Journal journal, BooleanSupplier canceled) throws IOException {
        install(archive, target, size, hash, journal, canceled, java.util.List.of());
    }
    public static void install(Path archive, Path target, long size, String hash, SyncModel.Journal journal, BooleanSupplier canceled, java.util.List<SyncModel.File> reused) throws IOException {
        if (!Files.exists(target.getParent())) journal.capture(target);
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempDirectory(target.getParent(), ".dlc-sync-");
        try {
            long received = 0;
            for (SyncModel.File file : reused) {
                check(canceled);
                Path source = child(target, file.id()), destination = child(staging, file.id());
                if (!Files.isRegularFile(source) || Files.size(source) != file.size() || !DlcFileMatch.fullHash(source).equals(file.hash())) throw new SyncFailure("reused_changed", file.id());
                Files.createDirectories(destination.getParent()); Files.copy(source, destination);
                received = Math.addExact(received, file.size());
                if (received > size) throw new SyncFailure("archive_size");
            }
            try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry entry;
                byte[] buffer = new byte[65536];
                while ((entry = input.getNextEntry()) != null) {
                    check(canceled);
                    String name = entry.getName();
                    if (name.contains("\\") || name.contains(":") || name.startsWith("/")) throw new SyncFailure("invalid_archive_path");
                    Path path = staging.resolve(name).normalize();
                    if (!path.startsWith(staging) || path.equals(staging)) throw new SyncFailure("archive_escape");
                    if (entry.isDirectory()) Files.createDirectories(path);
                    else {
                        Files.createDirectories(path.getParent());
                        try (OutputStream output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                check(canceled); received = Math.addExact(received, count);
                                if (received > size) throw new SyncFailure("archive_size");
                                output.write(buffer, 0, count);
                            }
                        }
                    }
                }
            }
            if (received != size || !DlcFileMatch.fullHash(staging).equals(hash)) throw new SyncFailure("directory_integrity");
            check(canceled); journal.capture(target); DlcResourceHistory.delete(target);
            try { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException error) { Files.move(staging, target); }
        } finally { DlcResourceHistory.delete(staging); }
    }
    private static void check(BooleanSupplier canceled) throws IOException {
        if (canceled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new SyncFailure("canceled");
    }
}
