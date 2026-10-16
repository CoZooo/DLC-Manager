package com.cozooo.dlc_manager.client.update;

import com.cozooo.dclanguage.DcDocuments;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

public final class DlcUpdateJournal implements AutoCloseable {
    private static final ThreadLocal<DlcUpdateJournal> ACTIVE = new ThreadLocal<>();
    private final Path game;
    private final Path directory;
    private final JsonArray entries;
    private final List<Path> roots;

    private DlcUpdateJournal(Path game, Path directory, JsonArray entries, List<Path> roots) {
        this.game = game.toAbsolutePath().normalize();
        this.directory = directory.toAbsolutePath().normalize();
        this.entries = entries;
        this.roots = List.copyOf(roots);
    }

    public static DlcUpdateJournal open(Path game, Path directory) throws IOException {
        Path log = directory.resolve("recovery.dc");
        JsonObject saved = Files.exists(log) ? DcDocuments.read(log).toJson() : new JsonObject();
        JsonArray entries = saved.has("entries") ? saved.getAsJsonArray("entries") : new JsonArray();
        List<Path> roots = new java.util.ArrayList<>(com.cozooo.dlc_manager.core.DlcConfig.getDlcRoots());
        if (saved.has("roots")) {
            roots.clear();
            for (var root : saved.getAsJsonArray("roots")) roots.add(resolveRelativePath(game, root.getAsString()));
        }
        if (entries == null) throw new IOException("Invalid pending update recovery journal");
        DlcUpdateJournal journal = new DlcUpdateJournal(game, directory, entries, roots);
        ACTIVE.set(journal);
        com.cozooo.dlc_manager.client.helper.DlcFileOpsHelperClient.requireImmediateOperations(true);
        return journal;
    }

    public static boolean isActive() { return ACTIVE.get() != null; }

    public static void beforeOperation(Path target) {
        DlcUpdateJournal journal = ACTIVE.get();
        if (journal == null || target == null) return;
        Path path = target.toAbsolutePath().normalize();
        Path internal = journal.directory.getParent().getParent();
        Path runtime = journal.game.resolve(".dlc-manager");
        if (!journal.allowed(path) || path.startsWith(internal) || internal.startsWith(path) || path.startsWith(runtime) || runtime.startsWith(path)) {
            throw new IllegalStateException("Invalid pending update operation target: " + target);
        }
    }

    public static void beforeWriteCanonical(Path target) throws IOException {
        DlcUpdateJournal journal = ACTIVE.get();
        if (journal == null) return;
        List<Path> anchors = new java.util.ArrayList<>(journal.roots);
        anchors.add(journal.game);
        for (Path anchor : anchors) {
            Path original = anchor.toAbsolutePath().normalize();
            Path ancestor = original;
            while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
            Path canonical = ancestor.toRealPath().resolve(ancestor.relativize(original));
            if (target.startsWith(canonical)) {
                journal.capture(original.resolve(canonical.relativize(target)));
                return;
            }
        }
        throw new IOException("Invalid pending update target: " + target);
    }

    public static void beforeWrite(Path target) {
        DlcUpdateJournal journal = ACTIVE.get();
        if (journal == null || target == null) return;
        try { journal.capture(target); }
        catch (IOException exception) { throw new IllegalStateException("Cannot back up pending update target: " + target, exception); }
    }

    public void capture(Path target) throws IOException {
        Path normalized = target.toAbsolutePath().normalize();
        Path internal = directory.getParent().getParent();
        if (normalized.startsWith(internal)) return;
        if (internal.startsWith(normalized)) throw new IOException("Update target contains pending task: " + target);
        while (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) && normalized.getParent() != null
            && !normalized.getParent().equals(game) && !Files.exists(normalized.getParent(), LinkOption.NOFOLLOW_LINKS)) normalized = normalized.getParent();
        if (!allowed(normalized)) throw new IOException("Invalid pending update target: " + target);
        for (Path part = normalized; part != null && (part.equals(game) || allowed(part)); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Cannot update symbolic link: " + target);
        }
        for (var value : entries) {
            Path previous = resolve(value.getAsJsonObject().get("path").getAsString());
            if (normalized.startsWith(previous)) return;
        }
        String relative = relativePath(game, normalized);
        JsonArray trusted = new JsonArray();
        for (Path root : roots) trusted.add(relativePath(game, root));
        String name = Integer.toString(entries.size());
        boolean exists = Files.exists(normalized, LinkOption.NOFOLLOW_LINKS);
        if (exists) copy(normalized, directory.resolve("backups").resolve(name));
        JsonObject entry = new JsonObject();
        entry.addProperty("path", relative);
        entry.addProperty("backup", name);
        entry.addProperty("existed", exists);
        entries.add(entry);
        JsonObject root = new JsonObject();
        root.add("entries", entries);
        root.add("roots", trusted);
        DcDocuments.write(directory.resolve("recovery.dc"), DcDocuments.fromJson(root));
    }

    public static String relativePath(Path base, Path path) throws IOException {
        try { return base.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'); }
        catch (IllegalArgumentException exception) { throw new IOException("Cannot store a relative path across filesystem roots", exception); }
    }

    public static Path resolveRelativePath(Path base, String value) throws IOException {
        Path relative = Path.of(value);
        if (relative.isAbsolute() || value.startsWith("\\") || value.matches("^[A-Za-z]:.*")) throw new IOException("Expected a relative path");
        return base.toAbsolutePath().normalize().resolve(relative).normalize();
    }

    public void rollback() throws IOException {
        ACTIVE.remove();
        IOException failure = null;
        for (int index = entries.size() - 1; index >= 0; index--) {
            JsonObject entry = entries.get(index).getAsJsonObject();
            try {
                Path target = resolve(entry.get("path").getAsString());
                Path backup = directory.resolve("backups").resolve(entry.get("backup").getAsString()).normalize();
                if (!backup.startsWith(directory.resolve("backups"))) throw new IOException("Invalid recovery backup");
                if (entry.get("existed").getAsBoolean() && !Files.exists(backup)) throw new IOException("Missing recovery backup: " + backup);
                remove(target);
                if (entry.get("existed").getAsBoolean()) copy(backup, target);
            } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private boolean allowed(Path path) {
        if (path.equals(game)) return false;
        if (path.startsWith(game)) return true;
        return roots.stream()
            .anyMatch(root -> path.startsWith(root.toAbsolutePath().normalize()));
    }

    private Path resolve(String relative) throws IOException {
        Path target = resolveRelativePath(game, relative);
        if (!allowed(target) || target.startsWith(directory)) throw new IOException("Invalid recovery path");
        for (Path part = target; part != null && (part.equals(game) || allowed(part)); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Cannot restore symbolic link: " + target);
        }
        return target;
    }

    private static void copy(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) throw new IOException("Cannot back up symbolic link: " + source);
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }
        Files.createDirectories(target);
        try (var stream = Files.list(source)) {
            for (Path child : stream.toList()) copy(child, target.resolve(child.getFileName()));
        }
    }

    private static void remove(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(target)) throw new IOException("Cannot restore symbolic link: " + target);
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    @Override public void close() {
        ACTIVE.remove();
        com.cozooo.dlc_manager.client.helper.DlcFileOpsHelperClient.requireImmediateOperations(false);
    }
}
