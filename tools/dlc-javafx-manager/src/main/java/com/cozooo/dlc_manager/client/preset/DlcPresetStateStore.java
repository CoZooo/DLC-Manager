package com.cozooo.dlc_manager.client.preset;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DlcPresetStateStore {
    private static final String STATE_BASE_NAME = "state";
    private static final String PRESETS_KEY = "presets";

    private DlcPresetStateStore() {
    }

    public static Path getStatePath() {
        Path configDir = CrossPlatformRuntime.configDir().resolve("dlc_manager");
        return DlcDcFormatUtil.resolveConfigPath(configDir, STATE_BASE_NAME);
    }

    public static Map<String, DlcPresetStatus> loadPresetStatus() {
        Map<String, DlcPresetStatus> result = new LinkedHashMap<>();
        Path path = getStatePath();
        if (path == null || !Files.exists(path)) {
            return result;
        }
        JsonObject root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
        if (root == null) {
            return result;
        }
        JsonObject presets = DlcDcConfigIO.getObjectIgnoreCase(root, PRESETS_KEY);
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
        JsonObject root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
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
        DlcDcConfigIO.removeIgnoreCase(root, PRESETS_KEY);
        root.add(PRESETS_KEY, presets);
        DlcDcConfigIO.write(path, root, DlcDcConfigIO.Layout.GENERIC);
    }
}
