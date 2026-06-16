package com.cozooo.dlc_manager.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import com.cozooo.dlc_manager.core.DcConfigIO;

public class DlcTooltipLoader {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, DlcType> TYPE_MAP = Map.of(
        "mod", DlcType.MOD,
        "resourcepack", DlcType.RESOURCE_PACK,
        "datapack", DlcType.DATA_PACK,
        "gunpack", DlcType.TACZ_PACK,
        "dlcpack", DlcType.DLC_PACK,
        "config", DlcType.UNKNOWN,
        "others", DlcType.UNKNOWN
    );

    public Map<String, TooltipData> loadTooltips(Path dlcRoot) {
        Map<String, TooltipData> result = new HashMap<>();
        Path infoDir = dlcRoot.resolve("info");
        if (!Files.isDirectory(infoDir)) {
            return result;
        }
        try (var stream = Files.list(infoDir)) {
            stream.filter(path -> path.getFileName() != null && DcFormatUtil.isDcName(path.getFileName().toString()))
                .filter(path -> !isInfoMetaFile(path))
                .forEach(path -> {
                    TooltipEntry entry = readEntry(path);
                    if (entry != null && entry.dlcName != null) {
                        TooltipData data = new TooltipData(entry.dlcIdentifier, entry.tip, entry.displayName, entry.type, entry.typeValid, entry.hasTypeField, entry.configType,
                            entry.inList, entry.hidden, entry.allowEnableByAll, entry.dependencies, entry.conflicts, entry.appliedTargetRaw, entry.redirectUrl, entry.fileNames, entry.alarm);
                        result.put(entry.dlcName, data);
                    }
                });
        } catch (IOException ignored) {
        }
        return result;
    }

    private boolean isInfoMetaFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String base = DcFormatUtil.stripConfigExtension(path.getFileName().toString());
        return "version".equalsIgnoreCase(base);
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
        Path path = DcFormatUtil.resolveConfigPath(infoDir, dlcName);
        if (path != null && Files.exists(path)) {
            updateTooltipFile(path, dlcName, type, updateExistingType);
            return;
        }
        Path existingByDlcName = findExistingTooltipByDlcName(infoDir, dlcName);
        if (existingByDlcName != null) {
            updateTooltipFile(existingByDlcName, dlcName, type, updateExistingType);
            return;
        }
        path = DcFormatUtil.resolveConfigPath(infoDir, dlcName);
        JsonObject obj = buildOrderedTooltipObject(
            null,
            dlcName,
            dlcName,
            buildDefaultDisplayName(dlcName),
            toTypeString(type),
            null,
            null,
            true,
            false,
            true,
            new com.google.gson.JsonPrimitive(""),
            new com.google.gson.JsonPrimitive(""),
            false,
            new JsonObject()
        );
        writeTooltipObject(path, obj);
    }

    private Path findExistingTooltipByDlcName(Path infoDir, String dlcName) {
        if (infoDir == null || dlcName == null || dlcName.isBlank() || !Files.isDirectory(infoDir)) {
            return null;
        }
        try (var stream = Files.list(infoDir)) {
            return stream
                .filter(path -> path.getFileName() != null && DcFormatUtil.isDcName(path.getFileName().toString()))
                .filter(path -> {
                    TooltipEntry entry = readEntry(path);
                    return entry != null && dlcName.equals(entry.dlcName);
                })
                .findFirst()
                .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void updateTooltipFile(Path path, String dlcName, DlcType type, boolean updateExistingType) {
        JsonObject obj = readTooltipObject(path);
        if (obj == null) {
            return;
        }
        boolean legacyShown = getBoolean(obj, "isShownInList", true);
        DcConfigIO.removeIgnoreCase(obj, "isShownInList");
        JsonObject displayName = localizedObjectFromAny(obj, "DisplayName");
        if (displayName.entrySet().isEmpty()) {
            displayName = buildDefaultDisplayName(dlcName);
        }
        String identifier = getIdentifier(obj);
        if (identifier == null || identifier.isBlank()) {
            identifier = dlcName;
        }

        String typeValue = updateExistingType ? toTypeString(type) : getString(obj, "type");
        if (typeValue == null) {
            typeValue = toTypeString(type);
        }
        boolean inList = getBoolean(obj, "isInList", legacyShown);
        boolean hidden = getBoolean(obj, "isHidden", false);
        boolean allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true);
        JsonElement dependencies = normalizeRelationElement(DcConfigIO.getIgnoreCase(obj, "dependencies"));
        JsonElement conflicts = normalizeRelationElement(DcConfigIO.getIgnoreCase(obj, "conflicts"));
        boolean alarm = getBoolean(obj, "alarm", false);
        JsonObject tip = localizedObjectFromAny(obj, "tip");
        String appliedTarget = getString(obj, "appliedTarget");

        String redirectUrl = getString(obj, "RedirectUrl");
        obj = buildOrderedTooltipObject(obj, dlcName, identifier, displayName, typeValue, appliedTarget, redirectUrl,
            inList, hidden, allowEnableByAll, dependencies, conflicts, alarm, tip);
        writeTooltipObject(path, obj);
    }

    private TooltipEntry readEntry(Path path) {
        JsonObject obj = readTooltipObject(path);
        if (obj == null) {
            return null;
        }
        if (!isTooltipConfigObject(obj)) {
            return null;
        }
        TooltipEntry entry = new TooltipEntry();
        entry.dlcName = getFileName(obj);
        if ((entry.dlcName == null || entry.dlcName.isBlank()) && path != null && path.getFileName() != null) {
            entry.dlcName = DcFormatUtil.stripConfigExtension(path.getFileName().toString());
        }
        if (entry.dlcName == null || entry.dlcName.isBlank()) {
            return null;
        }
        entry.dlcIdentifier = getIdentifier(obj);
        if (entry.dlcIdentifier == null || entry.dlcIdentifier.isBlank()) {
            entry.dlcIdentifier = entry.dlcName;
        }
        entry.hasTypeField = DcConfigIO.hasIgnoreCase(obj, "type");
        boolean legacyShown = getBoolean(obj, "isShownInList", true);
        entry.inList = getBoolean(obj, "isInList", legacyShown);
        entry.hidden = getBoolean(obj, "isHidden", false);
        entry.allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true);
        entry.dependencies = parseDependencyNames(DcConfigIO.getIgnoreCase(obj, "dependencies"));
        entry.conflicts = parseDependencyNames(DcConfigIO.getIgnoreCase(obj, "conflicts"));
        entry.alarm = getBoolean(obj, "alarm", false);
        entry.fileNames = parseFileNames(DcConfigIO.getIgnoreCase(obj, "fileNames"));
        if (entry.fileNames.isEmpty()) {
            entry.fileNames = parseFileNames(DcConfigIO.getIgnoreCase(obj, "fileName"));
        }
        if (entry.fileNames.isEmpty()) {
            entry.fileNames = parseFileNames(DcConfigIO.getIgnoreCase(obj, "files"));
        }
        entry.displayName = readLocalized(localizedObjectFromAny(obj, "DisplayName"));
        if (entry.displayName.isEmpty() && entry.dlcName != null) {
            entry.displayName = readLocalized(buildDefaultDisplayName(entry.dlcName));
        }
        entry.appliedTargetRaw = getString(obj, "appliedTarget");
        entry.redirectUrl = getString(obj, "RedirectUrl");
        String type = getString(obj, "type");
        if (type != null) {
            DlcType parsed = parseTypeString(type);
            if (parsed != null) {
                entry.type = parsed;
                entry.typeValid = true;
                entry.configType = "config".equalsIgnoreCase(type.trim());
            }
        }
        JsonObject tipObj = localizedObjectFromAny(obj, "tip");
        if (tipObj != null && !tipObj.entrySet().isEmpty()) {
            Map<String, String> tips = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : tipObj.entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    String key = DcConfigIO.normalizeLangKey(e.getKey());
                    if (key != null) {
                        tips.put(key, e.getValue().getAsString());
                    }
                }
            }
            entry.tip = tips;
        }
        return entry;
    }

    private boolean isTooltipConfigObject(JsonObject obj) {
        if (obj == null) {
            return false;
        }
        return DcConfigIO.hasIgnoreCase(obj, "file_name")
            || DcConfigIO.hasIgnoreCase(obj, "dlc_name")
            || DcConfigIO.hasIgnoreCase(obj, "identifier")
            || DcConfigIO.hasIgnoreCase(obj, "dlc_identifier")
            || DcConfigIO.hasIgnoreCase(obj, "DisplayName")
            || DcConfigIO.hasIgnoreCase(obj, "tip")
            || DcConfigIO.hasIgnoreCase(obj, "type")
            || DcConfigIO.hasIgnoreCase(obj, "appliedTarget")
            || DcConfigIO.hasIgnoreCase(obj, "RedirectUrl")
            || DcConfigIO.hasIgnoreCase(obj, "dependencies")
            || DcConfigIO.hasIgnoreCase(obj, "conflicts")
            || DcConfigIO.hasIgnoreCase(obj, "isInList")
            || DcConfigIO.hasIgnoreCase(obj, "isShownInList")
            || DcConfigIO.hasIgnoreCase(obj, "isHidden")
            || DcConfigIO.hasIgnoreCase(obj, "AllowEnableByAll")
            || DcConfigIO.hasIgnoreCase(obj, "alarm");
    }

    private JsonObject readTooltipObject(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }
        return DcConfigIO.read(path, DcConfigIO.Layout.DLC_STRUCTURED);
    }

    private void writeTooltipObject(Path path, JsonObject obj) {
        if (path == null) {
            return;
        }
        DcConfigIO.write(path, obj, DcConfigIO.Layout.DLC_STRUCTURED);
    }

    private boolean needsDcLayoutMigration(Path path) {
        if (path == null || path.getFileName() == null || !DcFormatUtil.isDcName(path.getFileName().toString())) {
            return false;
        }
        try {
            String text = Files.readString(path);
            if (text == null || text.isBlank()) {
                return false;
            }
            return !text.contains("[basic]");
        } catch (IOException ignored) {
            return false;
        }
    }

    private String getString(JsonObject obj, String key) {
        return DcConfigIO.getStringIgnoreCase(obj, key);
    }

    private String getIdentifier(JsonObject obj) {
        String identifier = getString(obj, "identifier");
        if (identifier == null || identifier.isBlank()) {
            identifier = getString(obj, "dlc_identifier");
        }
        return identifier;
    }

    private String getFileName(JsonObject obj) {
        String fileName = getString(obj, "file_name");
        if (fileName == null || fileName.isBlank()) {
            fileName = getString(obj, "dlc_name");
        }
        return fileName;
    }

    private boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        return DcConfigIO.getBooleanIgnoreCase(obj, key, fallback);
    }

    private boolean isValidDependencyElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        return element.isJsonPrimitive() || element.isJsonArray() || element.isJsonObject();
    }

    private boolean needsKeyMigration(JsonObject obj) {
        if (obj == null) {
            return false;
        }
        if (DcConfigIO.hasIgnoreCase(obj, "dlc_name") || DcConfigIO.hasIgnoreCase(obj, "dlc_identifier")) {
            return true;
        }
        return !DcConfigIO.hasIgnoreCase(obj, "file_name") || !DcConfigIO.hasIgnoreCase(obj, "identifier");
    }

    private boolean hasLegacyDownloadKeys(JsonObject obj) {
        if (obj == null) {
            return false;
        }
        return DcConfigIO.hasIgnoreCase(obj, "modrinth_projectid")
            || DcConfigIO.hasIgnoreCase(obj, "modrinth_versionid")
            || DcConfigIO.hasIgnoreCase(obj, "curseforge_projectid")
            || DcConfigIO.hasIgnoreCase(obj, "curseforge_fileid")
            || DcConfigIO.hasIgnoreCase(obj, "directUrl")
            || DcConfigIO.hasIgnoreCase(obj, "priority");
    }

    private JsonElement normalizeRelationElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new com.google.gson.JsonPrimitive("");
        }
        if (element.isJsonPrimitive()) {
            return element.deepCopy();
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                array.add(normalizeRelationElement(child));
            }
            return array;
        }
        JsonObject obj = element.getAsJsonObject();
        if (isIdentifierOnlyRelationObject(obj)) {
            JsonObject normalized = new JsonObject();
            String identifier = resolveRelationIdentifier(obj);
            normalized.addProperty("identifier", identifier == null ? "" : identifier);
            return normalized;
        }
        JsonObject normalized = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String targetKey = key;
            if (isIdentifierAlias(key)) {
                targetKey = "identifier";
            } else if (isFileNameAlias(key)) {
                targetKey = "file_name";
            }
            if (normalized.has(targetKey)) {
                continue;
            }
            normalized.add(targetKey, normalizeRelationElement(entry.getValue()));
        }
        return normalized;
    }

    private boolean isIdentifierOnlyRelationObject(JsonObject obj) {
        if (obj == null || obj.entrySet().isEmpty()) {
            return false;
        }
        boolean hasIdField = false;
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (isIdentifierAlias(key)) {
                hasIdField = true;
                continue;
            }
            return false;
        }
        return hasIdField;
    }

    private String resolveRelationIdentifier(JsonObject obj) {
        return getString(obj, "identifier");
    }

    private boolean isIdentifierAlias(String key) {
        return "identifier".equalsIgnoreCase(key);
    }

    private boolean isFileNameAlias(String key) {
        return "file_name".equalsIgnoreCase(key) || "dlc_name".equalsIgnoreCase(key);
    }

    private JsonObject buildOrderedTooltipObject(JsonObject source, String dlcName, String dlcIdentifier, JsonObject displayName, String type,
        String appliedTarget, String redirectUrl,
        boolean inList, boolean hidden, boolean allowEnableByAll, JsonElement dependencies, JsonElement conflicts, boolean alarm, JsonObject tip) {
        JsonObject ordered = new JsonObject();
        ordered.addProperty("file_name", dlcName);
        ordered.addProperty("identifier", dlcIdentifier == null ? dlcName : dlcIdentifier);
        JsonObject normalizedDisplay = displayName;
        if (normalizedDisplay == null || normalizedDisplay.entrySet().isEmpty()) {
            normalizedDisplay = buildDefaultDisplayName(dlcName);
        }
        ordered.add("DisplayName", normalizedDisplay);
        ordered.addProperty("type", type);
        if (appliedTarget != null && !appliedTarget.isBlank()) {
            ordered.addProperty("appliedTarget", appliedTarget);
        }
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            ordered.addProperty("RedirectUrl", redirectUrl);
        }
        ordered.addProperty("isInList", inList);
        ordered.addProperty("isHidden", hidden);
        ordered.addProperty("AllowEnableByAll", allowEnableByAll);
        ordered.add("dependencies", dependencies == null ? new com.google.gson.JsonPrimitive("") : dependencies);
        ordered.add("conflicts", conflicts == null ? new com.google.gson.JsonPrimitive("") : conflicts);
        ordered.addProperty("alarm", alarm);
        ordered.add("tip", tip == null ? new JsonObject() : tip);
        if (source != null) {
            for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                String key = entry.getKey();
                if (key == null || ordered.has(key)) {
                    continue;
                }
                if ("dlc_name".equalsIgnoreCase(key) || "dlc_identifier".equalsIgnoreCase(key)) {
                    continue;
                }
                if ("modrinth_projectid".equalsIgnoreCase(key)
                    || "modrinth_versionid".equalsIgnoreCase(key)
                    || "curseforge_projectid".equalsIgnoreCase(key)
                    || "curseforge_fileid".equalsIgnoreCase(key)
                    || "directUrl".equalsIgnoreCase(key)
                    || "priority".equalsIgnoreCase(key)) {
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
                String key = DcConfigIO.normalizeLangKey(entry.getKey());
                if (key != null) {
                    values.put(key, entry.getValue().getAsString());
                }
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
                String key = DcConfigIO.normalizeLangKey(entry.getKey());
                if (key != null) {
                    result.addProperty(key, entry.getValue().getAsString());
                }
            }
        }
        return result;
    }

    private JsonObject localizedObjectFromAny(JsonObject obj, String key) {
        JsonObject localized = sanitizeLocalized(DcConfigIO.getObjectIgnoreCase(obj, key));
        if (!localized.entrySet().isEmpty()) {
            return localized;
        }
        String single = DcConfigIO.getStringIgnoreCase(obj, key);
        if (single != null && !single.isBlank()) {
            localized.addProperty("en_us", single);
        }
        return localized;
    }

    private java.util.List<String> parseDependencyNames(JsonElement element) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        collectDependencyNames(element, result);
        return new java.util.ArrayList<>(result);
    }

    private java.util.List<String> parseFileNames(JsonElement element) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        collectFileNames(element, result);
        return new java.util.ArrayList<>(result);
    }

    private void collectFileNames(JsonElement element, java.util.Set<String> result) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            for (String part : raw.split("[\\n\\r,]+")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed.replace('\\', '/'));
                }
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectFileNames(child, result);
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                collectFileNames(entry.getValue(), result);
            }
        }
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
            String name = getString(obj, "identifier");
            if (name != null && !name.isBlank()) {
                result.add(name.trim());
                collectDependencyNames(DcConfigIO.getIgnoreCase(obj, "dependencies"), result);
                return;
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                int before = result.size();
                collectDependencyNames(entry.getValue(), result);
                if (result.size() == before && entry.getValue() != null && entry.getValue().isJsonObject()
                    && entry.getKey() != null && !entry.getKey().isBlank()) {
                    result.add(entry.getKey().trim());
                }
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
        boolean configType;
        boolean inList = true;
        boolean hidden = false;
        boolean allowEnableByAll = true;
        String appliedTargetRaw;
        String redirectUrl;
        java.util.List<String> fileNames = java.util.List.of();
        boolean alarm;
    }

    public static class TooltipData {
        private final String identifier;
        private final Map<String, String> tips;
        private final Map<String, String> displayName;
        private final DlcType type;
        private final boolean typeValid;
        private final boolean hasTypeField;
        private final boolean configType;
        private final boolean inList;
        private final boolean hidden;
        private final boolean allowEnableByAll;
        private final java.util.List<String> dependencies;
        private final java.util.List<String> conflicts;
        private final String appliedTargetRaw;
        private final String redirectUrl;
        private final java.util.List<String> fileNames;
        private final boolean alarm;

        public TooltipData(String identifier, Map<String, String> tips, Map<String, String> displayName, DlcType type, boolean typeValid, boolean hasTypeField, boolean configType,
            boolean inList, boolean hidden, boolean allowEnableByAll, java.util.List<String> dependencies, java.util.List<String> conflicts,
            String appliedTargetRaw, String redirectUrl, java.util.List<String> fileNames, boolean alarm) {
            this.identifier = identifier;
            this.tips = tips == null ? Map.of() : tips;
            this.displayName = displayName == null ? Map.of() : displayName;
            this.type = type;
            this.typeValid = typeValid;
            this.hasTypeField = hasTypeField;
            this.configType = configType;
            this.inList = inList;
            this.hidden = hidden;
            this.allowEnableByAll = allowEnableByAll;
            this.dependencies = dependencies == null ? java.util.List.of() : dependencies;
            this.conflicts = conflicts == null ? java.util.List.of() : conflicts;
            this.appliedTargetRaw = appliedTargetRaw;
            this.redirectUrl = redirectUrl;
            this.fileNames = fileNames == null ? java.util.List.of() : fileNames;
            this.alarm = alarm;
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

        public boolean isConfigType() {
            return configType;
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

        public String getAppliedTargetRaw() {
            return appliedTargetRaw;
        }

        public String getRedirectUrl() {
            return redirectUrl;
        }

        public java.util.List<String> getFileNames() {
            return fileNames;
        }

        public boolean isAlarm() {
            return alarm;
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
            case DLC_PACK -> "dlcpack";
            case UNKNOWN -> "others";
        };
    }

    public static DlcType parseType(String raw) {
        return parseTypeString(raw);
    }
}
