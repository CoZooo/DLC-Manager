package com.cozooo.dlc_manager.client.preset;

import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.core.DcFormatUtil;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcPresetLoader {
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
                stream.filter(path -> path.getFileName() != null && DcFormatUtil.isDcName(path.getFileName().toString()))
                    .forEach(path -> {
                        DlcPresetEntry entry = readEntry(path, presetRoot);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    });
            } catch (IOException ignored) {
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
            }
        }
        return presetRoot;
    }

    private static DlcPresetEntry readEntry(Path path, Path presetRoot) {
        JsonObject obj = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC);
        if (obj == null) {
            return null;
        }

        Map<String, String> displayName = readLangMap(JsonCaseUtil.getObjectIgnoreCase(obj, "DisplayName"));
        Map<String, String> tip = readLangMap(JsonCaseUtil.getObjectIgnoreCase(obj, "tip"));
        List<String> enable = readStringArray(JsonCaseUtil.getArrayIgnoreCase(obj, "enable"));
        List<String> conflicts = readStringArray(JsonCaseUtil.getArrayIgnoreCase(obj, "conflicts"));
        boolean alarm = JsonCaseUtil.getBooleanIgnoreCase(obj, "alarm", false);

        String fileName = stripExtension(path.getFileName().toString());
        String id = buildId(path, presetRoot);
        return new DlcPresetEntry(id, fileName, path, displayName, tip, enable, conflicts, alarm, DlcPresetStatus.OFF);
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
        return DcFormatUtil.stripConfigExtension(name);
    }

}
