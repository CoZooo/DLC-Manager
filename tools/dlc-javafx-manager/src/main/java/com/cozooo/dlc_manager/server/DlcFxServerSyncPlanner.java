package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerFileEntry;
import com.cozooo.dlc_manager.client.server.DlcServerInfo;
import com.cozooo.dlc_manager.client.server.DlcServerItem;
import com.cozooo.dlc_manager.client.server.DlcServerSession;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class DlcFxServerSyncPlanner {
    private static final long MODIFIED_TIME_TOLERANCE_MS = 2_000L;

    private DlcFxServerSyncPlanner() {
    }

    public static Preview preview(Path gameDir,
                                  DlcServerInfo serverInfo,
                                  List<DlcServerFileEntry> serverMods,
                                  boolean excludeDlcManagedMods) throws IOException {
        Path modsDir = gameDir == null ? null : gameDir.resolve("mods");
        Map<String, LocalMod> localMods = scanLocalMods(modsDir);
        Map<String, DlcServerFileEntry> remoteMods = mapServerMods(serverMods);
        Set<String> excluded = excludeDlcManagedMods ? collectExcludedModNames(serverInfo) : Set.of();
        if (excludeDlcManagedMods) {
            excluded = new LinkedHashSet<>(excluded);
            excluded.addAll(collectLocalDlcManagedModNames(gameDir));
            excluded.add("dlc-manager.jar");
            excluded.add("dlc_manager.jar");
        }

        Preview preview = new Preview();
        for (LocalMod local : localMods.values()) {
            if (containsIgnoreCase(excluded, local.fileName)) {
                preview.excluded.add(new Row(RowKind.EXCLUDED, local.fileName, local.size, 0L, local.path, ""));
                continue;
            }
            DlcServerFileEntry remote = remoteMods.get(normalizeName(local.fileName));
            if (remote == null) {
                preview.missingRemote.add(new Row(RowKind.MISSING_REMOTE, local.fileName, local.size, 0L, local.path, "mods/" + local.fileName));
            } else if (remote.size != local.size) {
                preview.changed.add(new Row(RowKind.CHANGED, local.fileName, local.size, remote.size, local.path, "mods/" + local.fileName));
            } else if (isLocallyNewer(local.modified, remote.modified)) {
                preview.changed.add(new Row(RowKind.CHANGED, local.fileName, local.size, remote.size, local.path, "mods/" + local.fileName));
            } else {
                preview.ok.add(new Row(RowKind.OK, local.fileName, local.size, remote.size, local.path, "mods/" + local.fileName));
            }
        }
        for (DlcServerFileEntry remote : remoteMods.values()) {
            if (remote == null || remote.dir || remote.name == null || remote.name.isBlank()) {
                continue;
            }
            if (containsIgnoreCase(excluded, remote.name)) {
                continue;
            }
            if (!localMods.containsKey(normalizeName(remote.name))) {
                preview.extraRemote.add(new Row(RowKind.EXTRA_REMOTE, remote.name, 0L, remote.size, null, "mods/" + remote.name));
            }
        }
        return preview;
    }

    public static ApplyResult uploadChanged(DlcServerSession session,
                                            Preview preview,
                                            BooleanSupplier canceled) throws Exception {
        if (session == null || preview == null) {
            return new ApplyResult(0, 0, List.of());
        }
        List<Row> uploads = new ArrayList<>();
        uploads.addAll(preview.missingRemote);
        uploads.addAll(preview.changed);
        List<String> errors = new ArrayList<>();
        int uploaded = 0;
        for (Row row : uploads) {
            if (canceled != null && canceled.getAsBoolean()) {
                break;
            }
            if (row.localPath == null || row.remotePath == null || row.remotePath.isBlank()) {
                continue;
            }
            try {
                session.uploadFile(row.remotePath, row.localPath);
                uploaded++;
            } catch (Exception ex) {
                errors.add(row.fileName + ": " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }
        return new ApplyResult(uploaded, uploads.size(), errors);
    }

    private static Map<String, LocalMod> scanLocalMods(Path modsDir) throws IOException {
        Map<String, LocalMod> out = new HashMap<>();
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return out;
        }
        try (var stream = Files.list(modsDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".jar") && !lower.endsWith(".zip")) {
                    return;
                }
                try {
                    long modified = Files.getLastModifiedTime(path).toMillis();
                    out.put(normalizeName(name), new LocalMod(name, Files.size(path), modified, path));
                } catch (IOException ignored) {
                }
            });
        }
        return out;
    }

    private static Map<String, DlcServerFileEntry> mapServerMods(List<DlcServerFileEntry> entries) {
        Map<String, DlcServerFileEntry> out = new HashMap<>();
        if (entries == null) {
            return out;
        }
        for (DlcServerFileEntry entry : entries) {
            if (entry == null || entry.dir || entry.name == null || entry.name.isBlank()) {
                continue;
            }
            String lower = entry.name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".jar") && !lower.endsWith(".zip")) {
                continue;
            }
            out.put(normalizeName(entry.name), entry);
        }
        return out;
    }

    private static Set<String> collectExcludedModNames(DlcServerInfo info) {
        Set<String> names = new LinkedHashSet<>();
        if (info == null || info.items == null) {
            return names;
        }
        for (DlcServerItem item : info.items) {
            if (item == null || item.type == null || !"MOD".equalsIgnoreCase(item.type)) {
                continue;
            }
            addFileName(names, item.appliedTargetRaw);
            addFileName(names, item.name);
            addFileName(names, item.identifier);
        }
        return names;
    }

    private static Set<String> collectLocalDlcManagedModNames(Path gameDir) {
        Set<String> names = new HashSet<>();
        for (DlcItem item : DlcRuntimeState.getIndexedItems()) {
            if (item == null || item.getType() != DlcType.MOD) {
                continue;
            }
            addFileName(names, item.getAppliedTargetRaw());
            addFileName(names, item.getName());
            addFileName(names, item.getIdentifier());
            for (Path path : item.getSourcePaths()) {
                addFileName(names, path == null ? null : path.getFileName() == null ? null : path.getFileName().toString());
            }
        }
        return names;
    }

    private static void addFileName(Set<String> names, String value) {
        if (names == null || value == null || value.isBlank()) {
            return;
        }
        String normalized = value.replace('\\', '/').trim();
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (fileName.isBlank()) {
            return;
        }
        if (fileName.contains("*")) {
            return;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jar") || lower.endsWith(".zip")) {
            names.add(fileName);
        }
    }

    private static boolean containsIgnoreCase(Set<String> names, String value) {
        if (names == null || names.isEmpty() || value == null) {
            return false;
        }
        String normalized = normalizeName(value);
        for (String name : names) {
            if (normalized.equals(normalizeName(name))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLocallyNewer(long localModified, long remoteModified) {
        if (localModified <= 0L || remoteModified <= 0L) {
            return false;
        }
        return localModified > remoteModified + MODIFIED_TIME_TOLERANCE_MS;
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record LocalMod(String fileName, long size, long modified, Path path) {
    }

    public enum RowKind {
        OK,
        MISSING_REMOTE,
        CHANGED,
        EXTRA_REMOTE,
        EXCLUDED
    }

    public record Row(RowKind kind, String fileName, long localSize, long remoteSize, Path localPath, String remotePath) {
    }

    public record ApplyResult(int uploaded, int planned, List<String> errors) {
    }

    public static final class Preview {
        public final List<Row> ok = new ArrayList<>();
        public final List<Row> missingRemote = new ArrayList<>();
        public final List<Row> changed = new ArrayList<>();
        public final List<Row> extraRemote = new ArrayList<>();
        public final List<Row> excluded = new ArrayList<>();

        public int uploadCount() {
            return missingRemote.size() + changed.size();
        }

        public int totalCount() {
            return ok.size() + missingRemote.size() + changed.size() + extraRemote.size() + excluded.size();
        }
    }
}
