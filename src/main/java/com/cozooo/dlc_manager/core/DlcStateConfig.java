package com.cozooo.dlc_manager.core;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.cozooo.dlc_manager.platform.PlatformServices;

public class DlcStateConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, DlcState> stateById = new HashMap<>();

    public static void load() {
        reloadFromDisk();
        migrateStoredPaths();
        migrateStateIds();
        normalizeEnabledStates();
        for (List<DlcItem> items : new DlcScanner().scan(true).values()) {
            for (DlcItem item : items) {
                DlcState state = new DlcState();
                state.enabled = item.isEnabled();
                state.mode = item.getMatchMode();
                state.root = DlcResourceHistory.relativePath(item.getDlcRoot());
                state.appliedTargets = item.getAppliedTargets().stream().map(DlcResourceHistory::relativePath).toList();
                stateById.put(item.getId(), state);
            }
        }
        save();
    }

    public static void reloadFromDisk() {
        stateById = new HashMap<>();
        Path legacy = DlcPlatformServices.configDir().resolve("dlc_manager/state.dc");
        Map<String, DlcState> oldStates = readStates(legacy);
        for (Path root : DlcConfig.getDlcRoots()) {
            Path path = root.resolve("tmp/state.dc");
            Map<String, DlcState> loaded = Files.exists(path) ? readStates(path) : oldStates;
            for (var entry : loaded.entrySet()) {
                if (belongsToRoot(entry.getKey(), entry.getValue(), root)) stateById.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public static void save() {
        for (Path root : DlcConfig.getDlcRoots()) {
            Path path = root.resolve("tmp/state.dc");
            try {
                Files.createDirectories(path.getParent());
                DlcStateConfigData data = new DlcStateConfigData();
                Map<String, DlcState> states = new HashMap<>();
                stateById.forEach((id, state) -> { if (belongsToRoot(id, state, root)) states.put(id, state); });
                data.DLC = states;
                DlcDcConfigIO.write(path, GSON.toJsonTree(data).getAsJsonObject(), DlcDcConfigIO.Layout.GENERIC);
            } catch (IOException ignored) {}
        }
    }

    private static Map<String, DlcState> readStates(Path path) {
        try {
            com.google.gson.JsonObject object = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
            if (object == null) return Map.of();
            Map<String, DlcState> loaded = selectStateMap(GSON.fromJson(object, DlcStateConfigData.class));
            return loaded != null && !loaded.isEmpty() ? loaded : recoverFlattenedStateMap(object);
        } catch (RuntimeException exception) { return Map.of(); }
    }

    private static boolean belongsToRoot(String id, DlcState state, Path root) {
        String relative = DlcResourceHistory.relativePath(root);
        if (state != null && state.root != null) return state.root.equals(relative);
        int colon = id.indexOf(':');
        int separator = id.indexOf('|');
        if (colon >= 0 && separator > colon) return id.substring(colon + 1, separator).equals(relative);
        return !DlcConfig.getDlcRoots().isEmpty() && root.equals(DlcConfig.getDlcRoots().get(0));
    }

    public static DlcState getState(String id) {
        return stateById.get(id);
    }

    public static void setState(String id, DlcState state) {
        stateById.put(id, state);
    }

    public static Map<String, DlcState> getAllStates() {
        return Collections.unmodifiableMap(stateById);
    }

    public static class DlcState {
        public String root;
        public String mode = "hash";
        public boolean enabled;
        public List<String> appliedTargets;
    }

    private static class DlcStateConfigData {
        Map<String, DlcState> DLC;
        Map<String, DlcState> stateById;
    }

    private static Map<String, DlcState> selectStateMap(DlcStateConfigData data) {
        if (data == null) {
            return null;
        }
        if (data.DLC != null) {
            return data.DLC;
        }
        return data.stateById;
    }

    private static boolean migrateStoredPaths() {
        if (stateById == null || stateById.isEmpty()) {
            return false;
        }
        Path gameDir = DlcPaths.gameDir().toAbsolutePath().normalize();
        Path gameReal = null;
        try {
            gameReal = gameDir.toRealPath();
        } catch (Exception ignored) {
        }
        boolean changed = false;
        for (DlcState state : stateById.values()) {
            if (state == null) continue;
            if (state.root != null) {
                Path storedRoot = DlcPaths.parsePathLenient(state.root);
                if (storedRoot != null && storedRoot.isAbsolute()) {
                    state.root = DlcResourceHistory.relativePath(storedRoot);
                    changed = true;
                }
            }
            if (state.appliedTargets == null || state.appliedTargets.isEmpty()) {
                continue;
            }
            List<String> migrated = new java.util.ArrayList<>();
            boolean localChanged = false;
            for (String raw : state.appliedTargets) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Path path = DlcPaths.parsePathLenient(raw);
                if (path == null) {
                    localChanged = true;
                    continue;
                }
                if (path.isAbsolute()) {
                    Path normalized = path.toAbsolutePath().normalize();
                    boolean converted = false;
                    if (gameReal != null) {
                        try {
                            Path pathReal = normalized.toRealPath();
                            if (pathReal.startsWith(gameReal)) {
                                migrated.add(gameReal.relativize(pathReal).toString());
                                localChanged = true;
                                converted = true;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (!converted && normalized.startsWith(gameDir)) {
                        migrated.add(gameDir.relativize(normalized).toString());
                        localChanged = true;
                        converted = true;
                    }
                    if (!converted) {
                        migrated.add(DlcResourceHistory.relativePath(normalized));
                        localChanged = true;
                    }
                } else {
                    migrated.add(path.toString());
                    if (!raw.equals(path.toString())) {
                        localChanged = true;
                    }
                }
            }
            if (localChanged || migrated.size() != state.appliedTargets.size()) {
                state.appliedTargets = migrated;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean migrateStateIds() {
        if (stateById == null || stateById.isEmpty()) {
            return false;
        }
        Path gameDir = DlcPaths.gameDir().toAbsolutePath().normalize();
        Path gameReal = null;
        try {
            gameReal = gameDir.toRealPath();
        } catch (Exception ignored) {
        }
        boolean changed = false;
        Map<String, DlcState> migrated = new HashMap<>();
        for (Map.Entry<String, DlcState> entry : stateById.entrySet()) {
            String key = entry.getKey();
            DlcState state = entry.getValue();
            String newKey = migrateIdKey(key, gameDir, gameReal);
            if (!newKey.equals(key)) {
                changed = true;
            }
            DlcState existing = migrated.get(newKey);
            if (existing == null) {
                migrated.put(newKey, state);
            } else if (state != null && state.enabled && !existing.enabled) {
                migrated.put(newKey, state);
            }
        }
        if (changed) {
            stateById = migrated;
        }
        return changed;
    }

    private static boolean normalizeEnabledStates() {
        if (stateById == null || stateById.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (DlcState state : stateById.values()) {
            if (state == null || !state.enabled) {
                continue;
            }
            if (state.appliedTargets == null || state.appliedTargets.isEmpty()) {
                state.enabled = false;
                changed = true;
                continue;
            }
            boolean allExist = true;
            for (String raw : state.appliedTargets) {
                if (raw == null || raw.isBlank()) {
                    allExist = false;
                    break;
                }
                Path path = resolveStoredPath(raw);
                if (!Files.exists(path)) {
                    allExist = false;
                    break;
                }
            }
            if (!allExist) {
                state.enabled = false;
                changed = true;
            }
        }
        return changed;
    }

    private static Path resolveStoredPath(String raw) {
        if (raw == null) {
            return DlcPaths.gameDir();
        }
        Path path = DlcPaths.parsePathLenient(raw);
        if (path == null) {
            return DlcPaths.gameDir().resolve("__invalid_path__");
        }
        if (path.isAbsolute()) {
            return path;
        }
        return DlcPaths.gameDir().resolve(path);
    }

    private static String migrateIdKey(String key, Path gameDir, Path gameReal) {
        if (key == null) {
            return "";
        }
        int colon = key.indexOf(':');
        int pipe = key.lastIndexOf('|');
        if (colon < 0 || pipe < 0 || pipe <= colon) {
            return key;
        }
        String prefix = key.substring(0, colon + 1);
        String rootPart = key.substring(colon + 1, pipe);
        String suffix = key.substring(pipe);
        if (rootPart.isBlank()) {
            return key;
        }
        try {
            Path rootPath = Path.of(rootPart);
            if (!rootPath.isAbsolute()) {
                return key;
            }
            Path normalized = rootPath.toAbsolutePath().normalize();
            if (gameReal != null) {
                try {
                    Path rootReal = normalized.toRealPath();
                    if (rootReal.startsWith(gameReal)) {
                        String relative = gameReal.relativize(rootReal).toString();
                        return prefix + relative + suffix;
                    }
                } catch (Exception ignored) {
                }
            }
            if (normalized.startsWith(gameDir)) {
                String relative = gameDir.relativize(normalized).toString();
                return prefix + relative + suffix;
            }
        } catch (Exception ignored) {
        }
        return key;
    }

    private static Map<String, DlcState> recoverFlattenedStateMap(com.google.gson.JsonObject root) {
        Map<String, DlcState> recovered = new HashMap<>();
        if (root == null || root.entrySet().isEmpty()) {
            return recovered;
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || "DLC".equalsIgnoreCase(key) || "stateById".equalsIgnoreCase(key)) {
                continue;
            }
            com.google.gson.JsonElement value = entry.getValue();
            if (value == null || !value.isJsonObject()) {
                continue;
            }
            com.google.gson.JsonObject obj = value.getAsJsonObject();
            if (!obj.has("enabled") && !obj.has("appliedTargets")) {
                continue;
            }
            try {
                DlcState state = GSON.fromJson(obj, DlcState.class);
                if (state != null) {
                    recovered.put(key, state);
                }
            } catch (Exception ignored) {
            }
        }
        return recovered;
    }
}
