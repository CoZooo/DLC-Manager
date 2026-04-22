package com.cozooo.dlc_manager.client.preset;
import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.core.DcFormatUtil;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DlcPresetStateStore {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String STATE_BASE_NAME = "state";
    private static final String PRESETS_KEY = "presets";
    private static final String DISABLED_PATHS_KEY = "presetDisabledPaths";

    private DlcPresetStateStore() {
    }

    public static Path getStatePath() {
        Path configDir = CrossPlatformRuntime.configDir().resolve("dlc_manager");
        return DcFormatUtil.resolveConfigPath(configDir, STATE_BASE_NAME);
    }

    public static Map<String, DlcPresetStatus> loadPresetStatus() {
        Map<String, DlcPresetStatus> result = new LinkedHashMap<>();
        Path path = getStatePath();
        if (path == null || !Files.exists(path)) {
            return result;
        }
        JsonObject root = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC);
        if (root == null) {
            return result;
        }
        JsonObject presets = JsonCaseUtil.getObjectIgnoreCase(root, PRESETS_KEY);
        if (presets == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : presets.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isJsonNull()) {
                continue;
            }
            try {
                String raw = entry.getValue().getAsString();
                DlcPresetStatus status = DlcPresetStatus.fromString(raw);
                result.put(entry.getKey(), status);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static void savePresetStatus(Map<String, DlcPresetStatus> statuses) {
        if (statuses == null) {
            statuses = Map.of();
        }
        Path path = getStatePath();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ignored) {
            return;
        }
        JsonObject root = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC);
        if (root == null) {
            root = new JsonObject();
        }

        JsonObject presets = new JsonObject();
        for (Map.Entry<String, DlcPresetStatus> entry : statuses.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            presets.addProperty(entry.getKey(), entry.getValue().toConfigValue());
        }
        JsonCaseUtil.removeIgnoreCase(root, PRESETS_KEY);
        root.add(PRESETS_KEY, presets);
        DcConfigIO.write(path, root, DcConfigIO.Layout.GENERIC);
    }

    public static Set<String> loadDisabledConflictPaths() {
        Set<String> result = new LinkedHashSet<>();
        Path path = getStatePath();
        if (path == null || !Files.exists(path)) {
            return result;
        }
        JsonObject root = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC);
        if (root == null) {
            return result;
        }
        var array = JsonCaseUtil.getArrayIgnoreCase(root, DISABLED_PATHS_KEY);
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                String value = element.getAsString();
                if (value != null) {
                    String trimmed = value.trim();
                    if (!trimmed.isBlank()) {
                        result.add(trimmed);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static void saveDisabledConflictPaths(Set<String> paths) {
        if (paths == null) {
            paths = Set.of();
        }
        Path statePath = getStatePath();
        if (statePath == null) {
            return;
        }
        try {
            Files.createDirectories(statePath.getParent());
        } catch (IOException ignored) {
            return;
        }
        JsonObject root = DcConfigIO.read(statePath, DcConfigIO.Layout.GENERIC);
        if (root == null) {
            root = new JsonObject();
        }
        JsonArray array = new JsonArray();
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            array.add(path);
        }
        JsonCaseUtil.removeIgnoreCase(root, DISABLED_PATHS_KEY);
        root.add(DISABLED_PATHS_KEY, array);
        DcConfigIO.write(statePath, root, DcConfigIO.Layout.GENERIC);
    }
}
