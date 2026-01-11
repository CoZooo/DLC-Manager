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
                        TooltipData data = new TooltipData(entry.tip, entry.displayName, entry.type, entry.typeValid, entry.hasTypeField,
                            entry.isShownInList, entry.allowEnableByAll, entry.dependencies, entry.conflicts);
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
        JsonObject obj = buildOrderedTooltipObject(
            null,
            dlcName,
            buildDefaultDisplayName(dlcName),
            toTypeString(type),
            true,
            true,
            new com.google.gson.JsonPrimitive(""),
            new com.google.gson.JsonPrimitive(""),
            new JsonObject()
        );
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
        JsonObject displayName = sanitizeLocalized(obj.getAsJsonObject("DisplayName"));
        if (displayName.entrySet().isEmpty()) {
            displayName = buildDefaultDisplayName(dlcName);
        }
        String typeValue = updateExistingType ? toTypeString(type) : getString(obj, "type");
        if (typeValue == null) {
            typeValue = toTypeString(type);
        }
        boolean shownInList = getBoolean(obj, "isShownInList", true);
        boolean allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true);
        JsonElement dependencies = isStringOrArray(obj.get("dependencies")) ? obj.get("dependencies")
            : new com.google.gson.JsonPrimitive("");
        JsonElement conflicts = isStringOrArray(obj.get("conflicts")) ? obj.get("conflicts")
            : new com.google.gson.JsonPrimitive("");
        JsonObject tip = obj.has("tip") && obj.get("tip").isJsonObject() ? obj.getAsJsonObject("tip") : new JsonObject();
        obj = buildOrderedTooltipObject(obj, dlcName, displayName, typeValue, shownInList, allowEnableByAll, dependencies, conflicts, tip);
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
            entry.isShownInList = getBoolean(obj, "isShownInList", true);
            entry.allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true);
            entry.dependencies = parseNameList(obj.get("dependencies"));
            entry.conflicts = parseNameList(obj.get("conflicts"));
            entry.displayName = readLocalizedMap(obj.getAsJsonObject("DisplayName"));
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

    private JsonObject buildOrderedTooltipObject(JsonObject source, String dlcName, JsonObject displayName, String type, boolean shownInList,
        boolean allowEnableByAll, JsonElement dependencies, JsonElement conflicts, JsonObject tip) {
        JsonObject ordered = new JsonObject();
        ordered.addProperty("dlc_name", dlcName);
        ordered.add("DisplayName", displayName == null ? buildDefaultDisplayName(dlcName) : displayName);
        ordered.addProperty("type", type);
        ordered.addProperty("isShownInList", shownInList);
        ordered.addProperty("AllowEnableByAll", allowEnableByAll);
        ordered.add("dependencies", dependencies == null ? new com.google.gson.JsonPrimitive("") : dependencies);
        ordered.add("conflicts", conflicts == null ? new com.google.gson.JsonPrimitive("") : conflicts);
        ordered.add("tip", tip == null ? new JsonObject() : tip);
        if (source != null) {
            for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                String key = entry.getKey();
                if (key == null || ordered.has(key)) {
                    continue;
                }
                ordered.add(key, entry.getValue());
            }
        }
        return ordered;
    }

    private JsonObject buildDefaultDisplayName(String dlcName) {
        JsonObject obj = new JsonObject();
        if (dlcName != null) {
            obj.addProperty("en_us", dlcName);
        }
        return obj;
    }

    private Map<String, String> readLocalizedMap(JsonObject obj) {
        Map<String, String> values = new HashMap<>();
        if (obj == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return values;
    }

    private JsonObject sanitizeLocalized(JsonObject obj) {
        JsonObject result = new JsonObject();
        if (obj == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                result.addProperty(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return result;
    }

    private static class TooltipEntry {
        String dlcName;
        Map<String, String> displayName;
        Map<String, String> tip;
        java.util.List<String> dependencies;
        java.util.List<String> conflicts;
        DlcType type;
        boolean typeValid;
        boolean hasTypeField;
        boolean isShownInList = true;
        boolean allowEnableByAll = true;
    }

    public static class TooltipData {
        private final Map<String, String> tips;
        private final Map<String, String> displayName;
        private final DlcType type;
        private final boolean typeValid;
        private final boolean hasTypeField;
        private final boolean isShownInList;
        private final boolean allowEnableByAll;
        private final java.util.List<String> dependencies;
        private final java.util.List<String> conflicts;

        public TooltipData(Map<String, String> tips, Map<String, String> displayName, DlcType type, boolean typeValid, boolean hasTypeField,
            boolean isShownInList, boolean allowEnableByAll, java.util.List<String> dependencies, java.util.List<String> conflicts) {
            this.tips = tips == null ? Map.of() : tips;
            this.displayName = displayName == null ? Map.of() : displayName;
            this.type = type;
            this.typeValid = typeValid;
            this.hasTypeField = hasTypeField;
            this.isShownInList = isShownInList;
            this.allowEnableByAll = allowEnableByAll;
            this.dependencies = dependencies == null ? java.util.List.of() : dependencies;
            this.conflicts = conflicts == null ? java.util.List.of() : conflicts;
        }

        public Map<String, String> getTips() {
            return tips;
        }

        public Map<String, String> getDisplayName() {
            return displayName;
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

        public boolean isShownInList() {
            return isShownInList;
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
