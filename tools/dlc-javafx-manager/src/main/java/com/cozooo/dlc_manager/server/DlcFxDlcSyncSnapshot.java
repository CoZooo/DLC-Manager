package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry;
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader;
import com.cozooo.dlc_manager.client.required.DlcRequiredService;
import com.cozooo.dlc_manager.client.server.DlcServerFileEntry;
import com.cozooo.dlc_manager.client.server.DlcServerSession;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.core.DlcTmpPaths;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/** Local and server state of DLC remote/required configs and their data files, keyed by DLC-root relative path. */
public final class DlcFxDlcSyncSnapshot {
    public static final String REMOTE_DIR = "remote";
    public static final String REQUIRED_DIR = "required";
    static final String WORK_DIR = "dlc_sync";

    public enum Kind {
        REMOTE,
        REQUIRED
    }

    public record FileState(String rel, long size, long modified, String hash) {
    }

    public record Item(String key,
                       Kind kind,
                       String identifier,
                       FileState localConfig,
                       FileState serverConfig,
                       String localDataRel,
                       String serverDataRel) {
    }

    public final Path localDlcRoot;
    public final Path localGameDir;
    public final String serverDlcRoot;
    public final String serverId;
    public final List<Item> items;
    public final Map<String, FileState> localFiles;
    public final Map<String, FileState> serverFiles;

    public DlcFxDlcSyncSnapshot(Path localDlcRoot,
                                String serverDlcRoot,
                                String serverId,
                                List<Item> items,
                                Map<String, FileState> localFiles,
                                Map<String, FileState> serverFiles) {
        this(localDlcRoot, DlcPaths.gameDir(), serverDlcRoot, serverId, items, localFiles, serverFiles);
    }

    public DlcFxDlcSyncSnapshot(Path localDlcRoot,
                                Path localGameDir,
                                String serverDlcRoot,
                                String serverId,
                                List<Item> items,
                                Map<String, FileState> localFiles,
                                Map<String, FileState> serverFiles) {
        this.localDlcRoot = localDlcRoot;
        this.localGameDir = localGameDir.toAbsolutePath().normalize();
        this.serverDlcRoot = serverDlcRoot;
        this.serverId = serverId == null ? "" : serverId;
        this.items = List.copyOf(items);
        this.localFiles = Map.copyOf(localFiles);
        this.serverFiles = Map.copyOf(serverFiles);
    }

    public String serverPath(String rel) {
        return join(serverDlcRoot, rel);
    }

    public Path localPath(String rel) throws IOException {
        Path root = localDlcRoot.toAbsolutePath().normalize();
        Path resolved = root.resolve(rel).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IOException("invalid path: " + rel);
        }
        return resolved;
    }

    String serverCacheKey(String rel) {
        return serverId + "|" + serverDlcRoot + "|" + rel;
    }

    static Path workDir(Path localDlcRoot) {
        return DlcTmpPaths.resolve(localDlcRoot, WORK_DIR);
    }

    static Path cachePath(Path localDlcRoot) {
        return workDir(localDlcRoot).resolve("hash_cache.dc");
    }

    /** Uses the server's own {@code paths.dlcRoot}; only paths inside the server game directory are reachable over TCP. */
    public static String resolveServerDlcRoot(JsonObject configGetResponse) throws IOException {
        JsonObject config = DlcFxServerConfigSyncPlanner.extractServerConfig(configGetResponse);
        String raw = DlcConfig.DEFAULT_DLC_ROOT;
        JsonElement paths = config == null ? null : config.get("paths");
        if (paths != null && paths.isJsonObject()) {
            JsonElement value = paths.getAsJsonObject().get("dlcRoot");
            if (value != null && value.isJsonPrimitive() && !value.getAsString().isBlank()) {
                raw = value.getAsString();
            }
        }
        String first = raw.split(",")[0].trim().replace('\\', '/');
        while (first.startsWith("./")) {
            first = first.substring(2);
        }
        while (first.endsWith("/")) {
            first = first.substring(0, first.length() - 1);
        }
        if (first.isBlank() || first.startsWith("/") || first.matches("^[A-Za-z]:.*") || first.contains("..")) {
            throw new IOException("server dlcRoot is outside game directory: " + raw);
        }
        return first;
    }

    public static DlcFxDlcSyncSnapshot collect(DlcServerSession session,
                                               Path localDlcRoot,
                                               String serverDlcRoot,
                                               String serverId,
                                               DlcFxLightHash.Cache cache,
                                               boolean deepVerify) throws IOException {
        return collect(session, localDlcRoot, DlcPaths.gameDir(), serverDlcRoot, serverId, cache, deepVerify, () -> false);
    }

    public static DlcFxDlcSyncSnapshot collect(DlcServerSession session,
                                               Path localDlcRoot,
                                               Path localGameDir,
                                               String serverDlcRoot,
                                               String serverId,
                                               DlcFxLightHash.Cache cache,
                                               boolean deepVerify,
                                               BooleanSupplier canceled) throws IOException {
        checkCanceled(canceled);
        Path root = localDlcRoot.toAbsolutePath().normalize();
        Path work = workDir(root);
        Path mirror = work.resolve("server_mirror");
        deleteRecursive(mirror);
        Files.createDirectories(mirror);

        Map<String, Builder> builders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        collectLocal(root, builders, cache);

        ServerTree tree = new ServerTree(session, serverDlcRoot, canceled);
        List<String> serverConfigs = new ArrayList<>(tree.walkDc(REMOTE_DIR, REMOTE_DIR + "/redirect"));
        for (Map.Entry<String, DlcServerFileEntry> entry : tree.dir(REQUIRED_DIR).entrySet()) {
            String name = entry.getKey();
            if (!entry.getValue().dir && DlcDcFormatUtil.isDcName(name) && !isNotice(name)) {
                serverConfigs.add(REQUIRED_DIR + "/" + name);
            }
        }
        for (String rel : serverConfigs) {
            checkCanceled(canceled);
            DlcServerFileEntry remote = tree.file(rel);
            Path target = mirror.resolve(rel).normalize();
            if (remote == null || !target.startsWith(mirror) || !session.downloadFileTo(join(serverDlcRoot, rel), target, canceled)) {
                continue;
            }
            Builder builder = builders.computeIfAbsent(rel, key -> new Builder(key, kindOf(key)));
            builder.serverConfig = new FileState(rel, remote.size, remote.modified, DlcFxLightHash.compute(target));
        }
        for (DlcRemoteEntry entry : DlcRemoteLoader.load(mirror)) {
            Builder builder = builders.get(relativize(mirror, entry.getConfigPath()));
            if (builder != null && builder.serverConfig != null) {
                builder.serverDataRel = relativize(mirror, entry.getTargetPath());
                builder.identifier(entry.getDlcIdentifier());
            }
        }
        Map<String, DlcServerFileEntry> requiredFiles = tree.dir(REQUIRED_DIR);
        for (DlcRequiredEntry entry : DlcRequiredLoader.load(mirror)) {
            Builder builder = builders.get(relativize(mirror, entry.getConfigPath()));
            if (builder != null && builder.serverConfig != null) {
                String name = matchRequiredFile(entry, requiredFiles);
                builder.serverDataRel = name == null ? null : REQUIRED_DIR + "/" + name;
                builder.identifier(entry.getIdentifier());
            }
        }

        Map<String, FileState> localFiles = new HashMap<>();
        Map<String, FileState> serverFiles = new HashMap<>();
        Path verifyDir = work.resolve("verify");
        for (Builder builder : builders.values()) {
            checkCanceled(canceled);
            for (String rel : new String[]{builder.localDataRel, builder.serverDataRel}) {
                if (rel == null || localFiles.containsKey(rel) || serverFiles.containsKey(rel)) {
                    continue;
                }
                FileState local = localState(root, rel, cache);
                if (local != null) {
                    localFiles.put(rel, local);
                }
                DlcServerFileEntry remote = tree.file(rel);
                if (remote == null || remote.dir) {
                    continue;
                }
                String cacheKey = serverId + "|" + serverDlcRoot + "|" + rel;
                String hash = remote.hash;
                if (hash == null && deepVerify && local != null && local.size() == remote.size) {
                    hash = fetchServerHash(session, join(serverDlcRoot, rel), verifyDir, canceled);
                }
                if (hash != null) {
                    cache.putServer(cacheKey, remote.size, remote.modified, hash);
                }
                serverFiles.put(rel, new FileState(rel, remote.size, remote.modified, hash));
            }
        }
        deleteRecursive(verifyDir);
        cache.save();

        List<Item> items = new ArrayList<>();
        for (Builder builder : builders.values()) {
            items.add(builder.build());
        }
        items.sort(Comparator.comparing(Item::key, String.CASE_INSENSITIVE_ORDER));
        return new DlcFxDlcSyncSnapshot(root, localGameDir, serverDlcRoot, serverId, items, localFiles, serverFiles);
    }

    private static void collectLocal(Path root, Map<String, Builder> builders, DlcFxLightHash.Cache cache) throws IOException {
        List<DlcRemoteEntry> remotes = DlcRemoteLoader.load(root);
        List<DlcRequiredEntry> required = DlcRequiredLoader.load(root);
        for (DlcRemoteEntry entry : remotes) {
            String key = relativize(root, entry.getConfigPath());
            if (key == null) {
                continue;
            }
            Builder builder = builders.computeIfAbsent(key, k -> new Builder(k, Kind.REMOTE));
            builder.localConfig = localState(root, key, cache);
            builder.localDataRel = relativize(root, entry.getTargetPath());
            builder.identifier(entry.getDlcIdentifier());
        }
        for (DlcRequiredEntry entry : required) {
            String key = relativize(root, entry.getConfigPath());
            if (key == null) {
                continue;
            }
            Builder builder = builders.computeIfAbsent(key, k -> new Builder(k, Kind.REQUIRED));
            builder.localConfig = localState(root, key, cache);
            String name = DlcRequiredService.resolveExistingFileName(entry);
            builder.localDataRel = name == null || name.isBlank() ? null : relativize(root, entry.resolveRequiredPath(name));
            builder.identifier(entry.getIdentifier());
        }
    }

    private static FileState localState(Path root, String rel, DlcFxLightHash.Cache cache) throws IOException {
        Path path = root.resolve(rel).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return null;
        }
        return new FileState(rel, Files.size(path), Files.getLastModifiedTime(path).toMillis(), cache.local(rel, path));
    }

    private static String fetchServerHash(DlcServerSession session, String serverPath, Path verifyDir,
                                          BooleanSupplier canceled) throws IOException {
        Files.createDirectories(verifyDir);
        Path temp = verifyDir.resolve(UUID.randomUUID() + ".bin");
        try {
            return session.downloadFileTo(serverPath, temp, canceled) ? DlcFxLightHash.compute(temp) : null;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Server-side equivalent of {@link DlcRequiredService#resolveExistingFileName} over a remote listing. */
    static String matchRequiredFile(DlcRequiredEntry entry, Map<String, DlcServerFileEntry> files) {
        for (String preferred : new String[]{entry.getResolvedFileName(), entry.getRequiredFileName()}) {
            DlcServerFileEntry file = preferred == null ? null : files.get(preferred);
            if (file != null && !file.dir && isDataFileName(preferred)) {
                return preferred;
            }
        }
        String base = entry.getBaseName();
        if (base == null || base.isBlank()) {
            return null;
        }
        String prefix = base.toLowerCase(Locale.ROOT) + ".";
        for (Map.Entry<String, DlcServerFileEntry> file : files.entrySet()) {
            String name = file.getKey();
            if (!file.getValue().dir && name.toLowerCase(Locale.ROOT).startsWith(prefix) && isDataFileName(name)) {
                return name;
            }
        }
        String intended = entry.getResolvedFileName();
        if (intended == null || intended.isBlank()) {
            intended = entry.getRequiredFileName();
        }
        return isDataFileName(intended) ? intended : null;
    }

    private static boolean isDataFileName(String name) {
        return name != null && !DlcDcFormatUtil.isDcName(name) && !name.toLowerCase(Locale.ROOT).endsWith(".part");
    }

    private static boolean isNotice(String fileName) {
        return "notice".equalsIgnoreCase(DlcDcFormatUtil.stripConfigExtension(fileName));
    }

    static Kind kindOf(String key) {
        return key.toLowerCase(Locale.ROOT).startsWith(REQUIRED_DIR + "/") ? Kind.REQUIRED : Kind.REMOTE;
    }

    static String relativize(Path root, Path path) {
        if (root == null || path == null) {
            return null;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot) || normalized.equals(normalizedRoot)) {
            return null;
        }
        return normalizedRoot.relativize(normalized).toString().replace('\\', '/');
    }

    static String join(String left, String right) {
        String a = left == null ? "" : left.replace('\\', '/');
        String b = right == null ? "" : right.replace('\\', '/');
        while (a.endsWith("/")) {
            a = a.substring(0, a.length() - 1);
        }
        while (b.startsWith("/")) {
            b = b.substring(1);
        }
        return a.isBlank() ? b : b.isBlank() ? a : a + "/" + b;
    }

    static String parentOf(String rel) {
        int slash = rel.lastIndexOf('/');
        return slash < 0 ? "" : rel.substring(0, slash);
    }

    static String nameOf(String rel) {
        int slash = rel.lastIndexOf('/');
        return slash < 0 ? rel : rel.substring(slash + 1);
    }

    static void deleteRecursive(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path child : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(child);
            }
        }
    }

    private static void checkCanceled(BooleanSupplier canceled) throws IOException {
        if (Thread.currentThread().isInterrupted() || (canceled != null && canceled.getAsBoolean())) {
            throw new java.io.InterruptedIOException("canceled");
        }
    }

    private static final class Builder {
        final String key;
        final Kind kind;
        String identifier;
        FileState localConfig;
        FileState serverConfig;
        String localDataRel;
        String serverDataRel;

        Builder(String key, Kind kind) {
            this.key = key;
            this.kind = kind;
        }

        void identifier(String value) {
            if ((identifier == null || identifier.isBlank()) && value != null && !value.isBlank()) {
                identifier = value.trim();
            }
        }

        Item build() {
            String id = identifier == null || identifier.isBlank()
                    ? DlcDcFormatUtil.stripConfigExtension(nameOf(key))
                    : identifier;
            return new Item(key, kind, id, localConfig, serverConfig, localDataRel, serverDataRel);
        }
    }

    /** Lazily lists server directories relative to the server DLC root through the existing file_list command. */
    private static final class ServerTree {
        private final DlcServerSession session;
        private final String serverDlcRoot;
        private final BooleanSupplier canceled;
        private final Map<String, Map<String, DlcServerFileEntry>> dirs = new HashMap<>();

        ServerTree(DlcServerSession session, String serverDlcRoot, BooleanSupplier canceled) {
            this.session = session;
            this.serverDlcRoot = serverDlcRoot;
            this.canceled = canceled;
        }

        Map<String, DlcServerFileEntry> dir(String rel) throws IOException {
            checkCanceled(canceled);
            Map<String, DlcServerFileEntry> cached = dirs.get(rel);
            if (cached != null) {
                return cached;
            }
            Map<String, DlcServerFileEntry> entries = new LinkedHashMap<>();
            for (DlcServerFileEntry entry : session.listFiles(join(serverDlcRoot, rel), true)) {
                if (entry != null && entry.name != null && !entry.name.isBlank()) {
                    entries.put(entry.name, entry);
                }
            }
            dirs.put(rel, entries);
            return entries;
        }

        DlcServerFileEntry file(String rel) throws IOException {
            return dir(parentOf(rel)).get(nameOf(rel));
        }

        List<String> walkDc(String rel, String skip) throws IOException {
            List<String> out = new ArrayList<>();
            if (rel.equalsIgnoreCase(skip)) {
                return out;
            }
            for (DlcServerFileEntry entry : dir(rel).values()) {
                String child = join(rel, entry.name);
                if (entry.dir) {
                    out.addAll(walkDc(child, skip));
                } else if (DlcDcFormatUtil.isDcName(entry.name)) {
                    out.add(child);
                }
            }
            return out;
        }
    }
}
