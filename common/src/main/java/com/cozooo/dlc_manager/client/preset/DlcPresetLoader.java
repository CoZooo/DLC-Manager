package com.cozooo.dlc_manager.client.preset;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcPresetLoader {
    private static final Gson GSON = new GsonBuilder().create();

    private DlcPresetLoader() {
    }

    public static List<DlcPresetEntry> loadAll() {
        return loadAll(DlcPathConfig.getDlcRoots());
    }

    public static List<DlcPresetEntry> loadAll(List<Path> roots) {
        List<DlcPresetEntry> entries = new ArrayList<>();
        if (roots == null || roots.isEmpty()) {
            return entries;
        }
        Set<Path> seen = new HashSet<>();
        for (Path root : roots) {
            if (root == null || seen.contains(root)) {
                continue;
            }
            seen.add(root);
            Path presetRoot = ensurePresetRoot(root);
            if (!Files.isDirectory(presetRoot)) {
                continue;
            }
            try (var stream = Files.walk(presetRoot)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        DlcPresetEntry entry = readEntry(path, presetRoot);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    });
            } catch (IOException ignored) {
                // Best-effort load.
            }
        }
        entries.sort((a, b) -> a.getFileName().compareToIgnoreCase(b.getFileName()));
        return entries;
    }

    public static Path ensurePresetRoot(Path dlcRoot) {
        if (dlcRoot == null) {
            return null;
        }
        Path presetRoot = dlcRoot.resolve("preset");
        if (!Files.exists(presetRoot)) {
            try {
                Files.createDirectories(presetRoot);
            } catch (IOException ignored) {
                // Best-effort create.
            }
        }
        return presetRoot;
    }

    private static DlcPresetEntry readEntry(Path path, Path presetRoot) {
        JsonObject obj;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            obj = GSON.fromJson(jsonReader, JsonObject.class);
        } catch (IOException | JsonSyntaxException ignored) {
            return null;
        }
        if (obj == null) {
            return null;
        }

        Map<String, String> displayName = readLangMap(JsonCaseUtil.getObjectIgnoreCase(obj, "DisplayName"));
        Map<String, String> tip = readLangMap(JsonCaseUtil.getObjectIgnoreCase(obj, "tip"));
        List<String> enable = readStringArray(JsonCaseUtil.getArrayIgnoreCase(obj, "enable"));
        List<String> conflicts = readStringArray(JsonCaseUtil.getArrayIgnoreCase(obj, "conflicts"));

        String fileName = stripExtension(path.getFileName().toString());
        String id = buildId(path, presetRoot);
        return new DlcPresetEntry(id, fileName, path, displayName, tip, enable, conflicts, DlcPresetStatus.OFF);
    }

    private static Map<String, String> readLangMap(JsonObject obj) {
        if (obj == null || obj.isJsonNull()) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isJsonNull()) {
                continue;
            }
            try {
                String value = entry.getValue().getAsString();
                if (value != null) {
                    String key = JsonCaseUtil.normalizeLangKey(entry.getKey());
                    if (key != null) {
                        map.put(key, value);
                    }
                }
            } catch (Exception ignored) {
                // Skip invalid entries.
            }
        }
        return map;
    }

    private static List<String> readStringArray(JsonArray array) {
        if (array == null || array.isJsonNull()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                String value = element.getAsString();
                if (value != null) {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            } catch (Exception ignored) {
                // Skip invalid values.
            }
        }
        return result;
    }

    private static String buildId(Path path, Path presetRoot) {
        if (presetRoot == null) {
            return stripExtension(path.getFileName().toString());
        }
        try {
            String relative = presetRoot.relativize(path).toString().replace('\\', '/');
            return stripExtension(relative);
        } catch (Exception ignored) {
            return stripExtension(path.getFileName().toString());
        }
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        if (name.toLowerCase().endsWith(".json")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }
}
