package com.cozooo.dlc_manager.client.update;

import com.cozooo.dclanguage.DcDocument;
import com.cozooo.dclanguage.DcDocuments;
import com.cozooo.dlc_manager.core.DlcFileMatch;
import com.cozooo.dlc_manager.core.DlcPathUtil;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DlcUpdateHashIndex {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "dlc-update-hashes");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, String> hashes;

    private DlcUpdateHashIndex(Map<String, String> hashes) {
        this.hashes = Map.copyOf(hashes);
    }

    public static DlcUpdateHashIndex prepare(Path gameDir, Path dlcRoot) throws IOException {
        return onWorker(() -> synchronize(gameDir, dlcRoot));
    }

    public static void invalidate(Path gameDir, Path dlcRoot, List<String> changed) throws IOException {
        Path mods = gameDir.resolve("mods").toAbsolutePath().normalize();
        List<Path> targets = changed.stream().map(gameDir::resolve).map(p -> p.toAbsolutePath().normalize())
            .filter(p -> p.startsWith(mods) || mods.startsWith(p)).toList();
        if (targets.isEmpty()) return;
        onWorker(() -> {
            Path indexPath = indexPath(dlcRoot);
            if (!Files.exists(indexPath)) return null;
            DcDocument document = DcDocuments.read(indexPath);
            JsonObject files = files(document);
            boolean dirty = false;
            for (String name : new ArrayList<>(files.keySet())) {
                Path file = gameDir.resolve("mods").resolve(name).toAbsolutePath().normalize();
                for (Path target : targets) {
                    if (file.startsWith(target)) {
                        files.addProperty(name, "");
                        dirty = true;
                        break;
                    }
                }
            }
            if (dirty) DcDocuments.write(indexPath, document);
            return null;
        });
    }

    public List<Path> findDeleteMatches(Path gameDir, String raw, String expectedHash) {
        List<Path> named = DlcPathUtil.findMatches(gameDir, raw);
        if (!named.isEmpty()) return named;
        String pattern = DlcPathUtil.normalizePattern(raw);
        if (pattern == null || !pattern.startsWith("mods/") || expectedHash == null) return List.of();
        String expected = expectedHash.trim().toLowerCase(Locale.ROOT);
        if (!expected.matches("[0-9a-f]{64}")) return List.of();
        String parentPattern = pattern.substring(0, pattern.lastIndexOf('/'));
        Path mods = gameDir.resolve("mods").toAbsolutePath().normalize();
        List<Path> result = new ArrayList<>();
        for (var entry : hashes.entrySet()) {
            Path file = mods.resolve(entry.getKey()).normalize();
            String parent = "mods" + (entry.getKey().contains("/")
                ? "/" + entry.getKey().substring(0, entry.getKey().lastIndexOf('/')) : "");
            if (expected.equalsIgnoreCase(entry.getValue()) && DlcPathUtil.matches(parentPattern, parent)
                && file.startsWith(mods) && isSafeFile(mods, file)) result.add(file);
        }
        result.sort(Path::compareTo);
        return List.copyOf(result);
    }

    private static DlcUpdateHashIndex synchronize(Path gameDir, Path dlcRoot) throws IOException {
        Path path = indexPath(dlcRoot);
        DcDocument document = Files.exists(path) ? DcDocuments.read(path) : DcDocuments.create();
        JsonObject files = files(document);
        Map<String, String> current = new LinkedHashMap<>();
        Path mods = gameDir.resolve("mods").toAbsolutePath().normalize();
        if (Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)) {
            try (var stream = Files.walk(mods)) {
                for (Path file : stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                    if (!isSafeFile(mods, file)) continue;
                    String name = mods.relativize(file).toString().replace('\\', '/');
                    var saved = files.get(name);
                    String hash = saved != null && saved.isJsonPrimitive() && saved.getAsJsonPrimitive().isString()
                        ? saved.getAsString() : "";
                    if (hash.isBlank()) hash = DlcFileMatch.hash(file);
                    current.put(name, hash);
                }
            }
        }
        for (String name : new ArrayList<>(files.keySet())) if (!current.containsKey(name)) files.remove(name);
        current.forEach(files::addProperty);
        DcDocuments.write(path, document);
        return new DlcUpdateHashIndex(current);
    }

    private static boolean isSafeFile(Path mods, Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return false;
        for (Path part = file; part != null && part.startsWith(mods); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) return false;
        }
        return true;
    }

    private static JsonObject files(DcDocument document) throws IOException {
        var value = document.root().get("files");
        if (value != null && !value.isJsonObject()) throw new IOException("Invalid update hash list: files must be an object");
        if (value == null) document.root().add("files", new JsonObject());
        return document.root().getAsJsonObject("files");
    }

    private static Path indexPath(Path dlcRoot) {
        return dlcRoot.resolve("tmp/update/hash_list.dc");
    }

    private static <T> T onWorker(Callable<T> work) throws IOException {
        try {
            return WORKER.submit(work).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while organizing update resources", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException io) throw io;
            throw new IOException("Failed to organize update resources", exception.getCause());
        }
    }
}
