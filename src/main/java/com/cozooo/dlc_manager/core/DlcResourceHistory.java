package com.cozooo.dlc_manager.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DlcResourceHistory {
    private DlcResourceHistory() {}

    public static String key(String config, String identifier) {
        return UUID.nameUUIDFromBytes((config + "\u0000" + identifier).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    public static String relativePath(Path path) {
        return DlcPaths.gameDir().toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static Path resolvePath(String path) {
        return DlcPaths.gameDir().toAbsolutePath().normalize().resolve(path).normalize();
    }

    private static JsonObject read(Path root, String kind) throws IOException {
        Path file = root.resolve("tmp").resolve(kind).resolve("history.dc");
        JsonObject data = DlcDcConfigIO.read(file, DlcDcConfigIO.Layout.GENERIC);
        if (data == null) {
            if (Files.exists(file)) throw new IOException("Invalid resource history: " + file);
            data = new JsonObject();
            data.addProperty("format", 1);
            data.add("resources", new JsonObject());
            data.add("backups", new JsonObject());
        }
        if (!data.has("resources") || !data.get("resources").isJsonObject() || !data.has("backups") || !data.get("backups").isJsonObject()) {
            throw new IOException("Invalid resource history: " + file);
        }
        return data;
    }

    private static void save(Path root, String kind, JsonObject data) throws IOException {
        Path file = root.resolve("tmp").resolve(kind).resolve("history.dc");
        Files.createDirectories(file.getParent());
        Path staging = file.resolveSibling("history.pending.dc");
        if (!DlcDcConfigIO.write(staging, data, DlcDcConfigIO.Layout.GENERIC)) throw new IOException("Cannot save resource history: " + file);
        move(staging, file);
    }

    public static synchronized void downloaded(Path root, String key, String identifier, Path config, Path source) throws IOException {
        JsonObject data = read(root, "required");
        resource(data, root, key, identifier, config, source);
        save(root, "required", data);
    }

    private static JsonObject resource(JsonObject data, Path root, String key, String identifier, Path config, Path source) {
        JsonObject resources = data.getAsJsonObject("resources");
        JsonObject item = resources.has(key) ? resources.getAsJsonObject(key) : new JsonObject();
        if (item.has("active") && !item.get("active").getAsBoolean()) {
            item.add("targets", new JsonObject());
            item.addProperty("status", "downloaded");
        }
        item.addProperty("identifier", identifier);
        item.addProperty("config", config == null ? "" : relativePath(config));
        item.addProperty("source", relativePath(source));
        item.addProperty("downloaded", source.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize().resolve("downloaded/required")));
        JsonArray downloads = item.has("downloads") ? item.getAsJsonArray("downloads") : new JsonArray();
        String downloadedPath = relativePath(source);
        if (item.get("downloaded").getAsBoolean() && downloads.asList().stream().noneMatch(value -> downloadedPath.equals(value.getAsString()))) downloads.add(downloadedPath);
        item.add("downloads", downloads);
        item.addProperty("active", true);
        if (!item.has("targets")) item.add("targets", new JsonObject());
        if (!item.has("status")) item.addProperty("status", "downloaded");
        resources.add(key, item);
        return item;
    }

    public static void prepare(Path root, String kind, String key, String identifier, Path config, Path source, Path target, boolean replace) throws IOException {
        prepare(root, kind, key, identifier, config, source, target, replace, "hash");
    }

    public static synchronized void prepare(Path root, String kind, String key, String identifier, Path config, Path source, Path target, boolean replace, String mode) throws IOException {
        JsonObject data = read(root, kind);
        JsonObject item = resource(data, root, key, identifier, config, source);
        JsonObject targets = item.getAsJsonObject("targets");
        String targetKey = key(relativePath(target), "target");
        JsonObject previous = targets.has(targetKey) ? targets.getAsJsonObject(targetKey) : null;
        if (replace && Files.exists(target)) {
            Path directory = root.resolve("tmp").resolve(kind);
            String safeId = identifier == null || identifier.isBlank() ? key : identifier.replaceAll("[^\\p{L}\\p{N}._-]", "_");
            long timestamp = System.currentTimeMillis();
            Path backup;
            do { backup = directory.resolve(safeId + "." + timestamp++); } while (Files.exists(backup) || Files.exists(Path.of(backup + ".old")));
            copy(target, backup);
            JsonObject record = new JsonObject();
            record.addProperty("resource", key);
            record.addProperty("target", relativePath(target));
            record.addProperty("path", root.toAbsolutePath().normalize().relativize(backup.toAbsolutePath().normalize()).toString().replace('\\', '/'));
            record.addProperty("hash", DlcFileMatch.hash(backup));
            record.addProperty("timestamp", timestamp - 1);
            record.addProperty("old", false);
            data.getAsJsonObject("backups").add(UUID.randomUUID().toString(), record);
        }
        JsonObject installed = new JsonObject();
        installed.addProperty("path", relativePath(target));
        installed.addProperty("mode", DlcFileMatch.mode(mode));
        installed.addProperty("hash", DlcFileMatch.hash(!replace && Files.exists(target) ? target : source));
        installed.addProperty("owned", replace || "dlc".equals(kind) || previous != null && previous.get("owned").getAsBoolean());
        targets.add(targetKey, installed);
        item.addProperty("status", replace ? "installing" : "installed");
        save(root, kind, data);
    }

    public static synchronized void complete(Path root, String kind, String key) throws IOException {
        JsonObject data = read(root, kind);
        JsonObject item = data.getAsJsonObject("resources").getAsJsonObject(key);
        if (item != null) {
            item.addProperty("status", "installed");
            item.addProperty("installedAt", System.currentTimeMillis());
            save(root, kind, data);
        }
    }

    public static synchronized void rekey(Path root, String kind, String oldKey, String newKey) throws IOException {
        if (oldKey.equals(newKey)) return;
        JsonObject data = read(root, kind);
        JsonObject resources = data.getAsJsonObject("resources");
        if (!resources.has(oldKey)) return;
        JsonObject previous = resources.remove(oldKey).getAsJsonObject();
        if (resources.has(newKey)) {
            JsonObject current = resources.getAsJsonObject(newKey);
            if (previous.get("active").getAsBoolean()) {
                JsonObject targets = previous.getAsJsonObject("targets");
                current.getAsJsonObject("targets").entrySet().forEach(entry -> targets.add(entry.getKey(), entry.getValue()));
                current.add("targets", targets);
            }
        } else resources.add(newKey, previous);
        for (JsonElement value : data.getAsJsonObject("backups").asMap().values()) {
            JsonObject backup = value.getAsJsonObject();
            if (oldKey.equals(backup.get("resource").getAsString())) backup.addProperty("resource", newKey);
        }
        save(root, kind, data);
    }

    public static synchronized boolean restore(Path root, String kind, String key, boolean earliest) throws IOException {
        JsonObject data = read(root, kind);
        JsonObject resources = data.getAsJsonObject("resources");
        if (!resources.has(key)) return false;
        JsonObject item = resources.getAsJsonObject(key);
        if (!item.get("active").getAsBoolean()) { archiveConsumed(root, kind, data); return true; }
        JsonObject backups = data.getAsJsonObject("backups");
        Map<Path, Path> replacements = new LinkedHashMap<>();
        List<Path> removals = new ArrayList<>();
        for (JsonElement value : item.getAsJsonObject("targets").asMap().values()) {
            JsonObject installed = value.getAsJsonObject();
            String targetPath = installed.get("path").getAsString();
            Path target = resolvePath(targetPath);
            JsonObject selected = null;
            for (JsonElement backupValue : backups.asMap().values()) {
                JsonObject backup = backupValue.getAsJsonObject();
                if (!backup.get("resource").getAsString().equals(key) || backup.get("old").getAsBoolean() || !backup.get("target").getAsString().equals(targetPath)) continue;
                if (selected == null || (earliest ? backup.get("timestamp").getAsLong() < selected.get("timestamp").getAsLong() : backup.get("timestamp").getAsLong() > selected.get("timestamp").getAsLong())) selected = backup;
            }
            String mode = installed.has("mode") ? installed.get("mode").getAsString() : "hash";
            boolean ownsTarget = installed.get("owned").getAsBoolean();
            boolean matching = Files.exists(target) && ("name".equals(mode) || installed.get("hash").getAsString().equals(DlcFileMatch.hash(target)));
            if (ownsTarget && !matching && "dlc".equals(kind) && "hash".equals(mode)) {
                Path renamed = DlcFileMatch.findByHash(installed.get("hash").getAsString(), target.getParent());
                if (renamed != null) removals.add(renamed);
            }
            if (selected != null) {
                Path backup = checkedBackup(root, kind, selected.get("path").getAsString());
                if (!selected.get("hash").getAsString().equals(DlcFileMatch.hash(backup))) throw new IOException("Resource backup is damaged: " + backup);
                replacements.put(target, backup);
            } else if (ownsTarget && matching) {
                removals.add(target);
            }
        }
        for (Path target : removals) delete(target);
        for (var replacement : replacements.entrySet()) replace(replacement.getValue(), replacement.getKey());
        for (JsonElement backupValue : backups.asMap().values()) {
            JsonObject backup = backupValue.getAsJsonObject();
            if (backup.get("resource").getAsString().equals(key)) backup.addProperty("old", true);
        }
        item.addProperty("active", false);
        item.addProperty("status", "removed");
        save(root, kind, data);
        archiveConsumed(root, kind, data);
        return true;
    }

    private static void archiveConsumed(Path root, String kind, JsonObject data) throws IOException {
        boolean changed = false;
        for (JsonElement value : data.getAsJsonObject("backups").asMap().values()) {
            JsonObject backup = value.getAsJsonObject();
            String stored = backup.get("path").getAsString();
            if (!backup.get("old").getAsBoolean() || stored.endsWith(".old")) continue;
            Path path = checkedBackup(root, kind, stored);
            Path old = path.resolveSibling(path.getFileName() + ".old");
            if (Files.exists(path)) move(path, old);
            backup.addProperty("path", root.toAbsolutePath().normalize().relativize(old).toString().replace('\\', '/'));
            changed = true;
        }
        if (changed) save(root, kind, data);
    }

    public static void reconcileRequired(Path root, Set<String> currentKeys) throws IOException {
        reconcileRequired(root, currentKeys, Set.of());
    }

    public static synchronized void reconcileRequired(Path root, Set<String> currentKeys, Set<String> referencedDownloads) throws IOException {
        JsonObject data = read(root, "required");
        archiveConsumed(root, "required", data);
        Set<String> removed = new HashSet<>();
        for (var entry : data.getAsJsonObject("resources").entrySet()) {
            JsonObject item = entry.getValue().getAsJsonObject();
            if (!currentKeys.contains(entry.getKey()) && !item.get("config").getAsString().isBlank() && !Files.exists(resolvePath(item.get("config").getAsString()))) removed.add(entry.getKey());
        }
        for (String key : removed) {
            JsonObject item = data.getAsJsonObject("resources").getAsJsonObject(key);
            restore(root, "required", key, true);
            for (String stored : downloadedPaths(item)) {
                Path source = resolvePath(stored);
                if (!source.startsWith(root.toAbsolutePath().normalize().resolve("downloaded/required"))) throw new IOException("Invalid required resource cache: " + source);
                boolean shared = referencedDownloads.contains(stored) || data.getAsJsonObject("resources").entrySet().stream().anyMatch(entry -> currentKeys.contains(entry.getKey()) && downloadedPaths(entry.getValue().getAsJsonObject()).contains(stored));
                if (!shared) delete(source);
            }
        }
    }

    private static Set<String> downloadedPaths(JsonObject item) {
        Set<String> paths = new HashSet<>();
        if (item.has("downloads")) for (JsonElement path : item.getAsJsonArray("downloads")) paths.add(path.getAsString());
        if (item.get("downloaded").getAsBoolean()) paths.add(item.get("source").getAsString());
        return paths;
    }

    private static Path checkedBackup(Path root, String kind, String value) throws IOException {
        Path base = root.toAbsolutePath().normalize().resolve("tmp").resolve(kind);
        Path path = root.toAbsolutePath().normalize().resolve(value).normalize();
        if (!path.startsWith(base)) throw new IOException("Invalid resource backup path: " + value);
        return path;
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException exception) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    public static void replace(Path source, Path target) throws IOException {
        Path staging = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".partial");
        try {
            copy(source, staging);
            delete(target);
            move(staging, target);
        } finally { delete(staging); }
    }

    public static void copy(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) throw new IOException("Cannot copy symbolic link: " + source);
        if (Files.isDirectory(source)) {
            Files.createDirectories(target);
            try (var paths = Files.list(source)) { for (Path child : paths.toList()) copy(child, target.resolve(child.getFileName())); }
        } else {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void delete(Path path) throws IOException {
        if (!Files.exists(path)) return;
        if (Files.isSymbolicLink(path)) throw new IOException("Cannot delete symbolic link: " + path);
        if (Files.isDirectory(path)) {
            try (var paths = Files.list(path)) { for (Path child : paths.toList()) delete(child); }
        }
        Files.delete(path);
    }
}
