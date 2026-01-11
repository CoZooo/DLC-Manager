package com.cozooo.dlc_manager.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DlcTooltipLoader {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, DlcType> TYPE_MAP = Map.of(
        "mod", DlcType.MOD,
        "resourcepack", DlcType.RESOURCE_PACK,
        "datapack", DlcType.DATA_PACK,
        "gunpack", DlcType.TACZ_PACK,
        "others", DlcType.UNKNOWN
    );

    public Map<String, TooltipData> loadTooltips(Path dlcRoot) {
        Map<String, TooltipData> result = new HashMap<>();
        Path infoDir = dlcRoot.resolve("info");
        if (!Files.isDirectory(infoDir)) {
            return result;
        }
        try (var stream = Files.list(infoDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> {
                    TooltipEntry entry = readEntry(path);
                    if (entry != null && entry.dlcName != null) {
                        TooltipData data = new TooltipData(entry.tip, entry.type, entry.typeValid, entry.hasTypeField, entry.allowEnableByAll,
                            entry.dependencies, entry.conflicts);
                        result.put(entry.dlcName, data);
                    }
                });
        } catch (IOException ignored) {
            // Best-effort.
        }
        return result;
    }

    public void ensureTooltipFile(Path infoDir, String dlcName, DlcType type, boolean updateExistingType) {
        if (dlcName == null || dlcName.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(infoDir);
        } catch (IOException ignored) {
            return;
        }
        Path path = infoDir.resolve(dlcName + ".json");
        if (Files.exists(path)) {
            updateTooltipFile(path, dlcName, type, updateExistingType);
            return;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("dlc_name", dlcName);
        obj.addProperty("type", toTypeString(type));
        obj.addProperty("AllowEnableByAll", true);
        obj.addProperty("dependencies", "");
        obj.addProperty("conflicts", "");
        obj.add("tip", new JsonObject());
        try (Writer writer = Files.newBufferedWriter(path)) {
            PRETTY_GSON.toJson(obj, writer);
        } catch (IOException ignored) {
            // Best-effort.
        }
    }

    private void updateTooltipFile(Path path, String dlcName, DlcType type, boolean updateExistingType) {
        JsonObject obj = null;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            obj = GSON.fromJson(jsonReader, JsonObject.class);
        } catch (IOException | JsonSyntaxException ignored) {
            return;
        }
        if (obj == null) {
            return;
        }
        obj.addProperty("dlc_name", dlcName);
        if (updateExistingType) {
            obj.addProperty("type", toTypeString(type));
        }
        if (!obj.has("AllowEnableByAll")
            || !obj.get("AllowEnableByAll").isJsonPrimitive()
            || !obj.get("AllowEnableByAll").getAsJsonPrimitive().isBoolean()) {
            obj.addProperty("AllowEnableByAll", true);
        }
        if (!isStringOrArray(obj.get("dependencies"))) {
            obj.addProperty("dependencies", "");
        }
        if (!isStringOrArray(obj.get("conflicts"))) {
            obj.addProperty("conflicts", "");
        }
        if (!obj.has("tip") || !obj.get("tip").isJsonObject()) {
            obj.add("tip", new JsonObject());
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            PRETTY_GSON.toJson(obj, writer);
        } catch (IOException ignored) {
            // Best-effort.
        }
    }

    private TooltipEntry readEntry(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            JsonObject obj = GSON.fromJson(jsonReader, JsonObject.class);
            if (obj == null) {
                return null;
            }
            TooltipEntry entry = new TooltipEntry();
            entry.dlcName = getString(obj, "dlc_name");
            entry.hasTypeField = obj.has("type");
            entry.allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true);
            entry.dependencies = parseNameList(obj.get("dependencies"));
            entry.conflicts = parseNameList(obj.get("conflicts"));
            String type = getString(obj, "type");
            if (type != null) {
                DlcType parsed = parseType(type);
                if (parsed != null) {
                    entry.type = parsed;
                    entry.typeValid = true;
                }
            }
            JsonObject tipObj = obj.has("tip") && obj.get("tip").isJsonObject() ? obj.getAsJsonObject("tip") : null;
            if (tipObj != null) {
                Map<String, String> tips = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : tipObj.entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        tips.put(e.getKey(), e.getValue().getAsString());
                    }
                }
                entry.tip = tips;
            }
            return entry;
        } catch (IOException | JsonSyntaxException ignored) {
            return null;
        }
    }

    private String getString(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private java.util.List<String> parseNameList(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return java.util.List.of();
        }
        java.util.List<String> result = new java.util.ArrayList<>();
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            for (String part : raw.split("[\\n\\r,]+")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child != null && child.isJsonPrimitive()) {
                    String trimmed = child.getAsString().trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            }
        }
        return result;
    }

    private boolean isStringOrArray(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        return element.isJsonPrimitive() || element.isJsonArray();
    }

    private boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isBoolean()) {
            return obj.get(key).getAsBoolean();
        }
        return fallback;
    }

    private static class TooltipEntry {
        String dlcName;
        Map<String, String> tip;
        java.util.List<String> dependencies;
        java.util.List<String> conflicts;
        DlcType type;
        boolean typeValid;
        boolean hasTypeField;
        boolean allowEnableByAll = true;
    }

    public static class TooltipData {
        private final Map<String, String> tips;
        private final DlcType type;
        private final boolean typeValid;
        private final boolean hasTypeField;
        private final boolean allowEnableByAll;
        private final java.util.List<String> dependencies;
        private final java.util.List<String> conflicts;

        public TooltipData(Map<String, String> tips, DlcType type, boolean typeValid, boolean hasTypeField, boolean allowEnableByAll,
            java.util.List<String> dependencies, java.util.List<String> conflicts) {
            this.tips = tips == null ? Map.of() : tips;
            this.type = type;
            this.typeValid = typeValid;
            this.hasTypeField = hasTypeField;
            this.allowEnableByAll = allowEnableByAll;
            this.dependencies = dependencies == null ? java.util.List.of() : dependencies;
            this.conflicts = conflicts == null ? java.util.List.of() : conflicts;
        }

        public Map<String, String> getTips() {
            return tips;
        }

        public DlcType getType() {
            return type;
        }

        public boolean isTypeValid() {
            return typeValid;
        }

        public boolean hasTypeField() {
            return hasTypeField;
        }

        public boolean AllowEnableByAll() {
            return allowEnableByAll;
        }

        public java.util.List<String> getDependencies() {
            return dependencies;
        }

        public java.util.List<String> getConflicts() {
            return conflicts;
        }
    }

    private DlcType parseType(String raw) {
        if (raw == null) {
            return null;
        }
        return TYPE_MAP.get(raw.toLowerCase());
    }

    public String toTypeString(DlcType type) {
        if (type == null) {
            return "others";
        }
        return switch (type) {
            case MOD -> "mod";
            case RESOURCE_PACK -> "resourcepack";
            case DATA_PACK -> "datapack";
            case TACZ_PACK -> "gunpack";
            case UNKNOWN -> "others";
        };
    }
}
