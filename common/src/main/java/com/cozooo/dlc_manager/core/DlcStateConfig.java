package com.cozooo.dlc_manager.core;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.core.DcConfigIO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.cozooo.dlc_manager.platform.PlatformServices;

public class DlcStateConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, DlcState>>() {}.getType();

    private static Path configPath;
    private static Map<String, DlcState> stateById = new HashMap<>();

    public static void load() {
        Path configDir = CrossPlatformRuntime.configDir().resolve("dlc_manager");
        configPath = DcFormatUtil.resolveConfigPath(configDir, "state");
        stateById = new HashMap<>();
        boolean migrateLegacyStateSection = false;
        if (configPath != null && Files.exists(configPath)) {
            try {
                com.google.gson.JsonObject root = DcConfigIO.read(configPath, DcConfigIO.Layout.GENERIC);
                if (root != null) {
                    DlcStateConfigData data = GSON.fromJson(root, DlcStateConfigData.class);
                    if (data != null && data.DLC == null && data.stateById != null) {
                        migrateLegacyStateSection = true;
                    }
                    Map<String, DlcState> loaded = selectStateMap(data);
                    if (loaded != null && !loaded.isEmpty()) {
                        stateById = new HashMap<>(loaded);
                    } else {
                        Map<String, DlcState> recovered = recoverFlattenedStateMap(root);
                        if (!recovered.isEmpty()) {
                            stateById = recovered;
                        } else if (loaded != null) {
                            stateById = new HashMap<>(loaded);
                        }
                    }
                }
            } catch (Exception ignored) {
                stateById = new HashMap<>();
            }
        }
        boolean migrated = migrateLegacyStateSection;
        migrated = migrateStoredPaths() || migrated;
        migrated = migrateStateIds() || migrated;
        migrated = normalizeEnabledStates() || migrated;
        if (migrated) {
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(configPath.getParent());
            DlcStateConfigData data = new DlcStateConfigData();
            data.DLC = stateById;
            com.google.gson.JsonObject root = GSON.toJsonTree(data).getAsJsonObject();
            DcConfigIO.write(configPath, root, DcConfigIO.Layout.GENERIC);
        } catch (IOException ignored) {
        }
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
            if (state == null || state.appliedTargets == null || state.appliedTargets.isEmpty()) {
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
                        migrated.add(normalized.toString());
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
