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
                        TooltipData data = new TooltipData(entry.dlcIdentifier, entry.tip, entry.displayName, entry.type, entry.typeValid, entry.hasTypeField,
                            entry.inList, entry.hidden, entry.allowEnableByAll, entry.dependencies, entry.conflicts);
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
            dlcName,
            buildDefaultDisplayName(dlcName),
            toTypeString(type),
            true,
            false,
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
        JsonObject obj;
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
        boolean legacyShown = getBoolean(obj, "isShownInList", true);
        if (obj.has("isShownInList")) {
            obj.remove("isShownInList");
        }
        JsonObject displayName = sanitizeLocalized(obj.getAsJsonObject("DisplayName"));
        if (displayName.entrySet().isEmpty()) {
            displayName = buildDefaultDisplayName(dlcName);
        }
        String identifier = getString(obj, "dlc_identifier");
        if (identifier == null || identifier.isBlank()) {
            identifier = dlcName;
        }

        String typeValue = updateExistingType ? toTypeString(type) : getString(obj, "type");
        if (typeValue == null) {
            typeValue = toTypeString(type);
        }
        boolean inList = obj.has("isInList") ? getBoolean(obj, "isInList", true) : legacyShown;
        boolean hidden = getBoolean(obj, "isHidden", false);
        boolean allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true);
        JsonElement dependencies = isValidDependencyElement(obj.get("dependencies")) ? obj.get("dependencies")
            : new com.google.gson.JsonPrimitive("");
        JsonElement conflicts = isValidDependencyElement(obj.get("conflicts")) ? obj.get("conflicts")
            : new com.google.gson.JsonPrimitive("");
        JsonObject tip = obj.has("tip") && obj.get("tip").isJsonObject() ? obj.getAsJsonObject("tip") : new JsonObject();

        obj = buildOrderedTooltipObject(obj, dlcName, identifier, displayName, typeValue, inList, hidden, allowEnableByAll, dependencies, conflicts, tip);
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
            entry.dlcIdentifier = getString(obj, "dlc_identifier");
            if (entry.dlcIdentifier == null || entry.dlcIdentifier.isBlank()) {
                entry.dlcIdentifier = entry.dlcName;
            }
            entry.hasTypeField = obj.has("type");
            boolean legacyShown = getBoolean(obj, "isShownInList", true);
            entry.inList = obj.has("isInList") ? getBoolean(obj, "isInList", true) : legacyShown;
            entry.hidden = getBoolean(obj, "isHidden", false);
            entry.allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true);
            entry.dependencies = parseDependencyNames(obj.get("dependencies"));
            entry.conflicts = parseDependencyNames(obj.get("conflicts"));
            entry.displayName = readLocalized(obj.getAsJsonObject("DisplayName"));
            if (entry.displayName.isEmpty() && entry.dlcName != null) {
                entry.displayName = readLocalized(buildDefaultDisplayName(entry.dlcName));
            }
            String type = getString(obj, "type");
            if (type != null) {
                DlcType parsed = parseTypeString(type);
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

    private boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()
            && obj.get(key).getAsJsonPrimitive().isBoolean()) {
            return obj.get(key).getAsBoolean();
        }
        return fallback;
    }

    private boolean isValidDependencyElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        return element.isJsonPrimitive() || element.isJsonArray() || element.isJsonObject();
    }

    private JsonObject buildOrderedTooltipObject(JsonObject source, String dlcName, String dlcIdentifier, JsonObject displayName, String type,
        boolean inList, boolean hidden, boolean allowEnableByAll, JsonElement dependencies, JsonElement conflicts, JsonObject tip) {
        JsonObject ordered = new JsonObject();
        ordered.addProperty("dlc_name", dlcName);
        ordered.addProperty("dlc_identifier", dlcIdentifier == null ? dlcName : dlcIdentifier);
        ordered.add("DisplayName", displayName == null ? buildDefaultDisplayName(dlcName) : displayName);
        ordered.addProperty("type", type);
        ordered.addProperty("isInList", inList);
        ordered.addProperty("isHidden", hidden);
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

    private Map<String, String> readLocalized(JsonObject obj) {
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

    private java.util.List<String> parseDependencyNames(JsonElement element) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        collectDependencyNames(element, result);
        return new java.util.ArrayList<>(result);
    }

    private void collectDependencyNames(JsonElement element, java.util.Set<String> result) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            for (String part : raw.split("[\\n\\r,]+")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectDependencyNames(child, result);
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String name = getString(obj, "dlc_identifier");
            if (name == null || name.isBlank()) {
                name = getString(obj, "dlc_name");
            }
            if (name == null || name.isBlank()) {
                name = getString(obj, "name");
            }
            if (name != null && !name.isBlank()) {
                result.add(name.trim());
                collectDependencyNames(obj.get("dependencies"), result);
                return;
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                collectDependencyNames(entry.getValue(), result);
            }
        }
    }

    private static class TooltipEntry {
        String dlcName;
        String dlcIdentifier;
        Map<String, String> displayName = java.util.Map.of();
        Map<String, String> tip;
        java.util.List<String> dependencies = java.util.List.of();
        java.util.List<String> conflicts = java.util.List.of();
        DlcType type;
        boolean typeValid;
        boolean hasTypeField;
        boolean inList = true;
        boolean hidden = false;
        boolean allowEnableByAll = true;
    }

    public static class TooltipData {
        private final String identifier;
        private final Map<String, String> tips;
        private final Map<String, String> displayName;
        private final DlcType type;
        private final boolean typeValid;
        private final boolean hasTypeField;
        private final boolean inList;
        private final boolean hidden;
        private final boolean allowEnableByAll;
        private final java.util.List<String> dependencies;
        private final java.util.List<String> conflicts;

        public TooltipData(String identifier, Map<String, String> tips, Map<String, String> displayName, DlcType type, boolean typeValid, boolean hasTypeField,
            boolean inList, boolean hidden, boolean allowEnableByAll, java.util.List<String> dependencies, java.util.List<String> conflicts) {
            this.identifier = identifier;
            this.tips = tips == null ? Map.of() : tips;
            this.displayName = displayName == null ? Map.of() : displayName;
            this.type = type;
            this.typeValid = typeValid;
            this.hasTypeField = hasTypeField;
            this.inList = inList;
            this.hidden = hidden;
            this.allowEnableByAll = allowEnableByAll;
            this.dependencies = dependencies == null ? java.util.List.of() : dependencies;
            this.conflicts = conflicts == null ? java.util.List.of() : conflicts;
        }

        public Map<String, String> getTips() {
            return tips;
        }

        public String getIdentifier() {
            return identifier;
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

        public boolean isInList() {
            return inList;
        }

        public boolean isHidden() {
            return hidden;
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

    public static DlcType parseTypeString(String raw) {
        if (raw == null) {
            return null;
        }
        return TYPE_MAP.get(raw.toLowerCase());
    }

    public static String toTypeString(DlcType type) {
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

    public static DlcType parseType(String raw) {
        return parseTypeString(raw);
    }
}
