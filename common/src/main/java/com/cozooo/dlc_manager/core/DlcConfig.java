package com.cozooo.dlc_manager.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.cozooo.dlc_manager.platform.PlatformServices;

public class DlcConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, DlcState>>() {}.getType();

    private static Path configPath;
    private static Map<String, DlcState> stateById = new HashMap<>();

    public static void load() {
        configPath = PlatformServices.get().configDir().resolve("dlc_manager/state.json");
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                DlcConfigData data = GSON.fromJson(reader, DlcConfigData.class);
                if (data != null) {
                    if (data.stateById != null) {
                        stateById = new HashMap<>(data.stateById);
                    }
                }
            } catch (IOException ignored) {
                stateById = new HashMap<>();
            }
        }
        boolean migrated = migrateStoredPaths();
        migrated = migrateStateIds() || migrated;
        if (migrated) {
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                DlcConfigData data = new DlcConfigData();
                data.stateById = stateById;
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
            // Best-effort persistence.
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

    private static class DlcConfigData {
        Map<String, DlcState> stateById;
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
            // Best-effort.
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
                Path path = Path.of(raw);
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
                            // Best-effort.
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
                    migrated.add(raw);
                }
            }
            if (localChanged) {
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
            // Best-effort.
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
                    // Best-effort.
                }
            }
            if (normalized.startsWith(gameDir)) {
                String relative = gameDir.relativize(normalized).toString();
                return prefix + relative + suffix;
            }
        } catch (Exception ignored) {
            // Best-effort.
        }
        return key;
    }
}
