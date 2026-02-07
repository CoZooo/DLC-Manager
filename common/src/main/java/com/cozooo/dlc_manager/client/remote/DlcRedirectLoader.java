package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.Json5Util;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DlcRedirectLoader {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private DlcRedirectLoader() {
    }

    public static List<DlcRedirectEntry> load(Path dlcRoot) {
        if (dlcRoot == null) {
            return List.of();
        }
        Path redirectRoot = dlcRoot.resolve("remote").resolve("redirect");
        if (!Files.isDirectory(redirectRoot)) {
            return List.of();
        }
        List<DlcRedirectEntry> entries = new ArrayList<>();
        try (var stream = Files.walk(redirectRoot)) {
            stream.filter(path -> Json5Util.isJsonLikeName(path.getFileName().toString()))
                .filter(path -> !Json5Util.shouldSkipLegacy(path))
                .forEach(path -> {
                    Path migrated = Json5Util.migrateLegacy(path, PRETTY_GSON);
                    DlcRedirectEntry entry = readEntry(migrated);
                    if (entry != null) {
                        entries.add(entry);
                    }
                });
        } catch (IOException ignored) {
        }
        return entries;
    }

    private static DlcRedirectEntry readEntry(Path path) {
        JsonObject obj = Json5Util.readObject(path);
        if (obj == null) {
            return null;
        }
        String redirectUrl = JsonCaseUtil.getStringIgnoreCase(obj, "RedirectUrl");
        if (redirectUrl == null || redirectUrl.isBlank()) {
            return null;
        }

        DlcRedirectEntry entry = new DlcRedirectEntry();
        entry.setConfigPath(path);
        entry.setRedirectUrl(redirectUrl);
        entry.setAvailable(JsonCaseUtil.getStringIgnoreCase(obj, "available"));

        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        entry.setFallbackName(Json5Util.stripJsonExtension(fileName));

        entry.getDisplayNames().putAll(readLocalized(JsonCaseUtil.getObjectIgnoreCase(obj, "DisplayName")));
        entry.getNotices().putAll(readLocalized(JsonCaseUtil.getObjectIgnoreCase(obj, "notice")));
        entry.getTips().putAll(readLocalized(JsonCaseUtil.getObjectIgnoreCase(obj, "tip")));
        return entry;
    }

    private static Map<String, String> readLocalized(JsonObject obj) {
        Map<String, String> values = new HashMap<>();
        if (obj == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                String key = JsonCaseUtil.normalizeLangKey(entry.getKey());
                if (key != null) {
                    values.put(key, entry.getValue().getAsString());
                }
            }
        }
        return values;
    }
}
