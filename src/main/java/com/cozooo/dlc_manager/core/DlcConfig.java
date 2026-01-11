package com.cozooo.dlc_manager.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

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

public class DlcConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, DlcState>>() {}.getType();

    private static Path configPath;
    private static Map<String, DlcState> stateById = new HashMap<>();

    public static void load() {
        configPath = FMLPaths.CONFIGDIR.get().resolve("dlc_manager/state.json");
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
}
