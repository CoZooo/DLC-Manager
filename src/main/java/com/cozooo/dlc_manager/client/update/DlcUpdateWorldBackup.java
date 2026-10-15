package com.cozooo.dlc_manager.client.update;

import com.cozooo.dclanguage.DcDocuments;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public final class DlcUpdateWorldBackup {
    private DlcUpdateWorldBackup() { }
    public record World(String name, long modified) { }
    public record Snapshot(String id, String kind, long timestamp, List<String> worlds) { }

    public static List<World> listWorlds(Path game) throws IOException {
        game = canonical(game);
        Path saves = game.resolve("saves");
        if (!Files.exists(saves)) return List.of();
        safe(saves);
        List<World> result = new ArrayList<>();
        try (var paths = Files.list(saves)) {
            for (Path path : paths.toList()) {
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) continue;
                Path level = path.resolve("level.dat");
                if (Files.isRegularFile(level, LinkOption.NOFOLLOW_LINKS)) {
                    result.add(new World(path.getFileName().toString(), Files.getLastModifiedTime(level).toMillis()));
                }
            }
        }
        result.sort(Comparator.comparingLong(World::modified).reversed().thenComparing(World::name));
        return List.copyOf(result);
    }

    public static List<Snapshot> list(Path root) throws IOException {
        root = canonical(root);
        Path sets = root.resolve("worlds/sets");
        if (!Files.exists(sets)) return List.of();
        safe(sets);
        List<Snapshot> result = new ArrayList<>();
        try (var paths = Files.list(sets)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".dc")).toList()) {
                safe(file);
                JsonObject data = DcDocuments.read(file).toJson();
                List<String> names = new ArrayList<>();
                for (var value : data.getAsJsonArray("worlds")) names.add(validName(value.getAsJsonObject().get("name").getAsString()));
                if (names.isEmpty()) continue;
                result.add(new Snapshot(data.get("id").getAsString(), data.get("kind").getAsString(), data.get("timestamp").getAsLong(), List.copyOf(names)));
            }
        }
        result.sort(Comparator.comparingLong(Snapshot::timestamp).reversed().thenComparing(Snapshot::id));
        return List.copyOf(result);
    }

    public static String pointer(Path root, String kind) throws IOException {
        root = canonical(root);
        Path file = root.resolve("worlds/" + kind + ".dc");
        if (!Files.exists(file)) return null;
        safe(file);
        String id = DcDocuments.read(file).toJson().get("id").getAsString();
        validName(id);
        return available(root, id) ? id : null;
    }

    public static List<String> worldNames(Path root, String id) throws IOException {
        JsonObject data = read(canonical(root), id);
        List<String> names = new ArrayList<>();
        for (var value : data.getAsJsonArray("worlds")) names.add(validName(value.getAsJsonObject().get("name").getAsString()));
        return List.copyOf(names);
    }

    public static void point(Path root, String kind, String id) throws IOException {
        root = canonical(root);
        JsonObject data = new JsonObject();
        data.addProperty("id", validName(id));
        Path file = root.resolve("worlds/" + kind + ".dc");
        safe(file);
        DlcUpdateJournal.beforeWriteCanonical(file);
        write(file, data);
    }

    public static String capture(Path game, Path root, Collection<String> names, String kind) throws IOException {
        game = canonical(game);
        root = canonical(root);
        String id = UUID.randomUUID().toString();
        Path directory = root.resolve("worlds/data/" + id);
        safe(directory);
        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        data.addProperty("kind", kind);
        data.addProperty("timestamp", System.currentTimeMillis());
        JsonArray worlds = new JsonArray();
        for (String raw : new LinkedHashSet<>(names)) {
            String name = validName(raw);
            Path source = game.resolve("saves").resolve(name);
            safe(source);
            if (directory.startsWith(source) || source.startsWith(directory)) throw new IOException("World backup overlaps source world: " + name);
            boolean exists = Files.exists(source);
            JsonObject entry = new JsonObject();
            entry.addProperty("name", name);
            entry.addProperty("exists", exists);
            if (exists) {
                try (WorldLock ignored = lock(source)) { copy(source, directory.resolve(name)); }
            }
            worlds.add(entry);
        }
        data.add("worlds", worlds);
        Path manifest = root.resolve("worlds/sets/" + id + ".dc");
        safe(manifest);
        write(manifest, data);
        return id;
    }

    public static void restore(Path game, Path root, String id, Collection<String> selection) throws IOException {
        game = canonical(game);
        root = canonical(root);
        restoreWithRecovery(game, root, id, selection, "recovery");
    }

    private static String restoreWithRecovery(Path game, Path root, String id, Collection<String> selection, String kind) throws IOException {
        JsonObject data = read(root, id);
        List<String> names = new ArrayList<>();
        for (var value : data.getAsJsonArray("worlds")) {
            String name = validName(value.getAsJsonObject().get("name").getAsString());
            if (selection == null || selection.contains(name)) names.add(name);
        }
        if (names.isEmpty()) return null;
        String recovery = capture(game, root, names, kind);
        if (kind.equals("rollback")) point(root, "after", recovery);
        try {
            restoreRaw(game, root, id, names);
            if (!kind.equals("return")) {
                Map<String, String> targets = returnTargets(root);
                for (String name : names) targets.put(name, recovery);
                saveReturns(root, targets);
            }
        }
        catch (IOException | RuntimeException failure) {
            try { restoreRaw(game, root, recovery, names); }
            catch (Exception recoveryFailure) { failure.addSuppressed(recoveryFailure); }
            throw failure;
        }
        return recovery;
    }

    public static void rollback(Path game, Path root) throws IOException {
        game = canonical(game);
        root = canonical(root);
        String before = pointer(root, "before");
        if (before == null) return;
        restoreWithRecovery(game, root, before, null, "rollback");
    }

    public static Map<String, String> returnTargets(Path root) throws IOException {
        root = canonical(root);
        Path file = root.resolve("worlds/returns.dc");
        safe(file);
        Map<String, String> result = new LinkedHashMap<>();
        if (!Files.exists(file)) return result;
        JsonObject data = DcDocuments.read(file).toJson();
        if (!data.has("worlds")) return result;
        for (var entry : data.getAsJsonObject("worlds").entrySet()) {
            String name = validName(entry.getKey());
            String id = validName(entry.getValue().getAsString());
            if (available(root, id) && worldNames(root, id).contains(name)) result.put(name, id);
        }
        return result;
    }

    public static void returnBeforeRestore(Path game, Path root, String name) throws IOException {
        root = canonical(root);
        game = canonical(game);
        validName(name);
        Map<String, String> targets = returnTargets(root);
        String id = targets.get(name);
        if (id == null) throw new IOException("No state before world rollback: " + name);
        String temporary = restoreWithRecovery(game, root, id, List.of(name), "return");
        targets.remove(name);
        saveReturns(root, targets);
        if (!read(root, id).get("kind").getAsString().equals("update")) delete(root, id, List.of(name));
        if (temporary != null) delete(root, temporary, null);
    }

    public static void delete(Path root, String id, Collection<String> selection) throws IOException {
        root = canonical(root);
        JsonObject data = read(root, id);
        JsonArray remaining = new JsonArray();
        List<String> removed = new ArrayList<>();
        for (var value : data.getAsJsonArray("worlds")) {
            String name = validName(value.getAsJsonObject().get("name").getAsString());
            if (selection == null || selection.contains(name)) removed.add(name); else remaining.add(value);
        }
        if (removed.isEmpty() && selection != null) return;
        Path manifest = root.resolve("worlds/sets/" + validName(id) + ".dc");
        Path directory = root.resolve("worlds/data/" + id);
        Path trash = root.resolve("worlds/trash/" + UUID.randomUUID());
        safe(trash);
        for (String name : removed) checkTree(directory.resolve(name));
        List<String> moved = new ArrayList<>();
        try {
            Files.createDirectories(trash);
            for (String name : removed) {
                Path source = directory.resolve(name);
                if (Files.exists(source)) {
                    Files.move(source, trash.resolve(name));
                    moved.add(name);
                }
            }
            if (remaining.isEmpty()) Files.move(manifest, trash.resolve("meta.dc"));
            else {
                data.add("worlds", remaining);
                write(manifest, data);
            }
        } catch (IOException | RuntimeException failure) {
            for (String name : moved) {
                try { Files.move(trash.resolve(name), directory.resolve(name)); }
                catch (Exception restoreFailure) { failure.addSuppressed(restoreFailure); }
            }
            throw failure;
        }
        if (remaining.isEmpty()) {
            for (String kind : List.of("before", "after")) {
                Path file = root.resolve("worlds/" + kind + ".dc");
                safe(file);
                if (Files.exists(file) && id.equals(DcDocuments.read(file).toJson().get("id").getAsString())) Files.delete(file);
            }
        }
        saveReturns(root, returnTargets(root));
        removeTree(trash);
        if (Files.isDirectory(directory)) {
            try (var children = Files.list(directory)) { if (children.findAny().isEmpty()) Files.delete(directory); }
        }
    }

    public static void deleteAll(Path root) throws IOException {
        Path worlds = canonical(root).resolve("worlds");
        checkTree(worlds);
        removeTree(worlds);
    }

    private static void saveReturns(Path root, Map<String, String> targets) throws IOException {
        JsonObject worlds = new JsonObject();
        for (var entry : targets.entrySet()) worlds.addProperty(validName(entry.getKey()), validName(entry.getValue()));
        JsonObject data = new JsonObject();
        data.add("worlds", worlds);
        Path file = root.resolve("worlds/returns.dc");
        safe(file);
        DlcUpdateJournal.beforeWriteCanonical(file);
        write(file, data);
    }

    private static boolean available(Path root, String id) throws IOException {
        Path file = root.resolve("worlds/sets/" + validName(id) + ".dc");
        safe(file);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
    }

    private static void checkTree(Path path) throws IOException {
        safe(path);
        if (!Files.exists(path)) return;
        try (var paths = Files.walk(path)) { var iterator = paths.iterator(); while (iterator.hasNext()) safe(iterator.next()); }
    }

    private static void removeTree(Path path) throws IOException {
        checkTree(path);
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                safe(file);
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void write(Path file, JsonObject data) throws IOException {
        Files.createDirectories(file.getParent());
        var document = Files.exists(file) ? DcDocuments.read(file) : DcDocuments.create();
        JsonObject current = document.root();
        for (var entry : data.entrySet()) current.add(entry.getKey(), entry.getValue());
        DcDocuments.write(file, document);
    }

    private static JsonObject read(Path root, String id) throws IOException {
        Path file = root.resolve("worlds/sets/" + validName(id) + ".dc");
        safe(file);
        JsonObject data = DcDocuments.read(file).toJson();
        if (!id.equals(data.get("id").getAsString())) throw new IOException("Invalid world backup");
        return data;
    }

    private static void restoreRaw(Path game, Path root, String id, Collection<String> names) throws IOException {
        JsonObject data = read(root, id);
        for (var value : data.getAsJsonArray("worlds")) {
            JsonObject entry = value.getAsJsonObject();
            String name = validName(entry.get("name").getAsString());
            if (!names.contains(name)) continue;
            Path target = game.resolve("saves").resolve(name);
            Path source = root.resolve("worlds/data/" + validName(id)).resolve(name);
            safe(target);
            safe(source);
            boolean exists = entry.get("exists").getAsBoolean();
            if (exists && !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing world backup: " + name);
            DlcUpdateJournal.beforeWriteCanonical(target);
            try (WorldLock ignored = lock(target)) {
                clear(target);
                if (exists) copy(source, target);
            }
            if (!exists && Files.exists(target)) {
                Files.deleteIfExists(target.resolve("session.lock"));
                Files.delete(target);
            }
        }
    }

    private static String validName(String name) throws IOException {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\") || Path.of(name).isAbsolute()) {
            throw new IOException("Invalid world backup name");
        }
        return name;
    }

    private static Path canonical(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path ancestor = absolute;
        while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
        return ancestor.toRealPath().resolve(ancestor.relativize(absolute));
    }

    private static void safe(Path path) throws IOException {
        for (Path part = path.toAbsolutePath().normalize(); part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("World backups do not support symbolic links: " + part);
        }
    }

    private static void copy(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                safe(dir);
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.equals(source.resolve("session.lock"))) return FileVisitResult.CONTINUE;
                safe(file);
                if (!attrs.isRegularFile()) throw new IOException("Unsupported world file: " + file);
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void clear(Path target) throws IOException {
        if (!Files.exists(target)) return;
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                safe(file);
                if (!file.equals(target.resolve("session.lock"))) Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                if (!dir.equals(target)) Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static WorldLock lock(Path world) throws IOException {
        Path file = world.resolve("session.lock");
        safe(file);
        if (!Files.exists(file)) return new WorldLock(null, null);
        FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("World is open. Leave the world before backing up or restoring: " + world.getFileName());
            return new WorldLock(channel, lock);
        } catch (Exception failure) {
            channel.close();
            throw new IOException("World is open or locked: " + world.getFileName(), failure);
        }
    }

    private record WorldLock(FileChannel channel, FileLock lock) implements AutoCloseable {
        @Override public void close() throws IOException {
            try { if (lock != null) lock.release(); }
            finally { if (channel != null) channel.close(); }
        }
    }
}
