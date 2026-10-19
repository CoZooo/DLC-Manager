package com.cozooo.dlc_manager.client.preset;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dclanguage.DcDocument;
import com.cozooo.dclanguage.DcDocuments;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
        return loadAll(DlcConfig.getDlcRoots());
    }

    public static List<DlcPresetEntry> loadAll(List<Path> roots) { return loadAll(roots, true); }

    public static List<DlcPresetEntry> loadAll(List<Path> roots, boolean migrate) {
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
                stream.filter(path -> path.getFileName() != null && DlcDcFormatUtil.isDcName(path.getFileName().toString()))
                    .forEach(path -> {
                        DlcPresetEntry entry = readEntry(path, migrate);
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

    private static DlcPresetEntry readEntry(Path path, boolean migrate) {
        JsonObject obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
        if (obj == null) {
            return null;
        }

        JsonObject basic = DlcDcConfigIO.getObjectIgnoreCase(obj, "basic");
        Map<String, String> displayName = readLangMap(DlcDcConfigIO.getObjectIgnoreCase(obj, "DisplayName"));
        Map<String, String> tip = readLangMap(DlcDcConfigIO.getObjectIgnoreCase(obj, "tip"));
        List<String> enable = readRelationList(DlcDcConfigIO.getIgnoreCase(obj, "dependencies"), DlcDcConfigIO.getIgnoreCase(obj, "enable"));
        List<String> conflicts = readRelationList(DlcDcConfigIO.getIgnoreCase(obj, "conflicts"), null);
        Boolean basicAlarm = DlcDcConfigIO.getBooleanIgnoreCase(basic, "alarm");
        boolean alarm = basicAlarm == null ? DlcDcConfigIO.getBooleanIgnoreCase(obj, "alarm", false) : basicAlarm;
        Map<String, String> alarmTip = alarm ? readLangMap(DlcDcConfigIO.getObjectIgnoreCase(obj, "Alarm")) : Map.of();

        String fileName = stripExtension(path.getFileName().toString());
        String id = readIdentifier(basic, obj, fileName);
        if (migrate) migrateIfNeeded(path, obj, id, alarm, enable, conflicts);
        return new DlcPresetEntry(id, fileName, path, displayName, tip, alarmTip, enable, conflicts, alarm, DlcPresetStatus.OFF);
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
                    String key = DlcDcConfigIO.normalizeLangKey(entry.getKey());
                    if (key != null) {
                        map.put(key, value);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    private static List<String> readRelationList(JsonElement primary, JsonElement legacy) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        collectRelationNames(primary, result);
        collectRelationNames(legacy, result);
        return new ArrayList<>(result);
    }

    private static void collectRelationNames(JsonElement element, java.util.Set<String> result) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            addSplitNames(element.getAsString(), result);
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectRelationNames(child, result);
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String identifier = DlcDcConfigIO.getStringIgnoreCase(obj, "identifier");
            if (identifier != null && !identifier.isBlank()) {
                result.add(identifier.trim());
                return;
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                int before = result.size();
                collectRelationNames(entry.getValue(), result);
                if (result.size() == before && entry.getKey() != null && !entry.getKey().isBlank()) {
                    result.add(entry.getKey().trim());
                }
            }
        }
    }

    private static void addSplitNames(String value, java.util.Set<String> result) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String part : value.split("[\\n\\r,]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
    }

    private static String readIdentifier(JsonObject basic, JsonObject obj, String fallback) {
        String identifier = DlcDcConfigIO.getStringIgnoreCase(basic, "identifier");
        if (identifier != null && !identifier.isBlank()) {
            return identifier.trim();
        }
        String legacyIdentifier = DlcDcConfigIO.getStringIgnoreCase(obj, "identifier");
        if (legacyIdentifier != null && !legacyIdentifier.isBlank()) {
            return legacyIdentifier.trim();
        }
        return fallback;
    }

    private static void migrateIfNeeded(Path path, JsonObject obj, String identifier, boolean alarm, List<String> enable, List<String> conflicts) {
        JsonObject basic = DlcDcConfigIO.getObjectIgnoreCase(obj, "basic");
        boolean hasMissingIdentifier = DlcDcConfigIO.getStringIgnoreCase(basic, "identifier") == null
            || DlcDcConfigIO.getStringIgnoreCase(basic, "identifier").isBlank();
        boolean hasRootIdentifier = DlcDcConfigIO.hasIgnoreCase(obj, "identifier");
        boolean hasRootAlarm = DlcDcConfigIO.hasIgnoreCase(obj, "alarm");
        boolean hasLegacyEnable = DlcDcConfigIO.hasIgnoreCase(obj, "enable");
        JsonElement conflictsElement = DlcDcConfigIO.getIgnoreCase(obj, "conflicts");
        boolean hasLegacyConflicts = conflictsElement != null && !conflictsElement.isJsonObject();
        if (!hasMissingIdentifier && !hasRootIdentifier && !hasRootAlarm && !hasLegacyEnable && !hasLegacyConflicts) {
            return;
        }
        JsonObject migrated = new JsonObject();
        JsonObject migratedBasic = new JsonObject();
        migratedBasic.addProperty("identifier", identifier);
        migratedBasic.addProperty("alarm", alarm);
        if (basic != null) {
            for (Map.Entry<String, JsonElement> entry : basic.entrySet()) {
                String key = entry.getKey();
                if ("identifier".equalsIgnoreCase(key) || "alarm".equalsIgnoreCase(key)) {
                    continue;
                }
                migratedBasic.add(key, entry.getValue());
            }
        }
        migrated.add("basic", migratedBasic);
        migrated.add("dependencies", relationObject(enable));
        migrated.add("conflicts", relationObject(conflicts));
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if ("basic".equalsIgnoreCase(key)
                || "identifier".equalsIgnoreCase(key)
                || "alarm".equalsIgnoreCase(key)
                || "enable".equalsIgnoreCase(key)
                || "dependencies".equalsIgnoreCase(key)
                || "conflicts".equalsIgnoreCase(key)) {
                continue;
            }
            migrated.add(key, entry.getValue());
        }
        try {
            DcDocument document = DcDocuments.read(path);
            document.root().entrySet().clear();
            for (var entry : migrated.entrySet()) document.root().add(entry.getKey(), entry.getValue().deepCopy());
            document.asSection("basic").asSection("dependencies").asSection("conflicts");
            DcDocuments.write(path, document);
        } catch (IOException | IllegalArgumentException | IllegalStateException ignored) {
        }
    }

    private static JsonObject relationObject(List<String> values) {
        JsonObject out = new JsonObject();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                out.add(value.trim(), new JsonPrimitive(value.trim()));
            }
        }
        return out;
    }

    private static String stripExtension(String name) {
        return DlcDcFormatUtil.stripConfigExtension(name);
    }

}
