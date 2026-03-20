package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DlcUpdateManifest {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern LAST_JSON_PAIR = Pattern.compile("\".*?\"\\s*:\\s*\".*?\"");

    private VersionInfo version;
    private FileOps fileOps;
    private boolean hidden;
    private boolean updateDLCM;
    private String updateDLCMVersion;
    private Map<String, String> changelog = new java.util.LinkedHashMap<>();
    private UpdateOps updateOps = new UpdateOps();

    public static DlcUpdateManifest load(Path updateJson) {
        if (updateJson == null || !Files.exists(updateJson)) {
            return null;
        }
        JsonObject obj = null;
        String fileName = updateJson.getFileName() == null ? "" : updateJson.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".dc")) {
            try {
                String text = Files.readString(updateJson);
                String trimmed = text == null ? "" : text.stripLeading();
                if (trimmed.startsWith("{")) {
                    obj = GSON.fromJson(trimmed, JsonObject.class);
                } else {
                    obj = DlcUpdateDcParser.parse(text);
                }
            } catch (IOException ignored) {
                return null;
            }
        }
        if (obj == null) {
            try (Reader reader = Files.newBufferedReader(updateJson)) {
                JsonReader jsonReader = new JsonReader(reader);
                jsonReader.setLenient(true);
                obj = GSON.fromJson(jsonReader, JsonObject.class);
            } catch (IOException ignored) {
                return null;
            }
        }
        return fromJsonObject(obj);
    }

    public static DlcUpdateManifest fromJsonObject(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        DlcUpdateManifest manifest = new DlcUpdateManifest();
        manifest.version = parseVersion(JsonCaseUtil.getObjectIgnoreCase(obj, "version"));
        manifest.hidden = getBoolean(obj, "isHidden", false);
        UpdateDLCMConfig updateDLCMConfig = parseUpdateDLCM(JsonCaseUtil.getIgnoreCase(obj, "UpdateDLCM"));
        manifest.updateDLCM = updateDLCMConfig.enabled;
        manifest.updateDLCMVersion = updateDLCMConfig.version;
        JsonObject fileObj = JsonCaseUtil.getObjectIgnoreCase(obj, "file");
        manifest.fileOps = parseFileOps(fileObj);
        manifest.updateOps = parseUpdateOps(JsonCaseUtil.getIgnoreCase(obj, "update"));
        manifest.changelog = parseChangelog(JsonCaseUtil.getIgnoreCase(obj, "changelog"));
        if ((manifest.changelog == null || manifest.changelog.isEmpty()) && fileObj != null && JsonCaseUtil.hasIgnoreCase(fileObj, "changelog")) {
            manifest.changelog = parseChangelog(JsonCaseUtil.getIgnoreCase(fileObj, "changelog"));
        }
        return manifest;
    }

    public VersionInfo getVersion() {
        return version;
    }

    public FileOps getFileOps() {
        return fileOps;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isUpdateDLCM() {
        return updateDLCM;
    }

    public String getUpdateDLCMVersion() {
        return updateDLCMVersion;
    }

    public Map<String, String> getChangelog() {
        return changelog == null ? Map.of() : changelog;
    }

    public UpdateOps getUpdateOps() {
        return updateOps;
    }

    public String resolveChangelog(String lang) {
        if (changelog == null || changelog.isEmpty()) {
            return "";
        }
        String key = JsonCaseUtil.normalizeLangKey(lang);
        if (key != null && changelog.containsKey(key)) {
            return changelog.get(key);
        }
        if (changelog.containsKey("en_us")) {
            return changelog.get("en_us");
        }
        return changelog.values().stream().findFirst().orElse("");
    }

    private static VersionInfo parseVersion(JsonObject obj) {
        VersionInfo info = new VersionInfo();
        if (obj == null) {
            return info;
        }
        info.platform = normalizePlatform(getString(obj, "platform"));
        info.base = getString(obj, "base");
        info.baseNew = getString(obj, "base_new");
        String dlc = getString(obj, "dlc");
        if (dlc == null || dlc.isBlank()) {
            dlc = getString(obj, "dlc_old");
        }
        info.dlcOld = dlc;
        info.dlcNew = getString(obj, "dlc_new");
        return info;
    }

    private static Map<String, String> parseChangelog(JsonElement element) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (raw != null && !raw.isBlank()) {
                values.put("en_us", raw);
            }
            return values;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (var entry : obj.entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                    String raw = entry.getValue().getAsString();
                    if (raw != null && !raw.isBlank()) {
                        values.put(JsonCaseUtil.normalizeLangKey(entry.getKey()), raw);
                    }
                }
            }
        }
        return values;
    }

    private static UpdateDLCMConfig parseUpdateDLCM(JsonElement element) {
        UpdateDLCMConfig config = new UpdateDLCMConfig();
        if (element == null || element.isJsonNull()) {
            return config;
        }
        if (!element.isJsonPrimitive()) {
            return config;
        }
        if (element.getAsJsonPrimitive().isBoolean()) {
            config.enabled = element.getAsBoolean();
            return config;
        }
        String raw = element.getAsString();
        if (raw == null) {
            return config;
        }
        String value = raw.trim();
        if (value.isBlank() || "false".equalsIgnoreCase(value)) {
            return config;
        }
        if ("true".equalsIgnoreCase(value)) {
            config.enabled = true;
            return config;
        }
        config.enabled = true;
        config.version = value;
        return config;
    }

    private static FileOps parseFileOps(JsonObject obj) {
        FileOps ops = new FileOps();
        if (obj == null) {
            return ops;
        }
        if (JsonCaseUtil.hasIgnoreCase(obj, "del")) {
            ops.del.addAll(readStringArray(JsonCaseUtil.getIgnoreCase(obj, "del")));
        }
        if (JsonCaseUtil.hasIgnoreCase(obj, "add")) {
            JsonElement addEl = JsonCaseUtil.getIgnoreCase(obj, "add");
            if (addEl != null && addEl.isJsonObject()) {
                JsonObject addObj = addEl.getAsJsonObject();
                for (var entry : addObj.entrySet()) {
                    String key = entry.getKey();
                    if (key == null || key.isBlank()) {
                        continue;
                    }
                    AddEntry addEntry = parseAddEntry(key, entry.getValue());
                    if (addEntry != null) {
                        ops.add.put(key, addEntry);
                    }
                }
            }
        }
        if (JsonCaseUtil.hasIgnoreCase(obj, "move")) {
            JsonElement moveEl = JsonCaseUtil.getIgnoreCase(obj, "move");
            if (moveEl != null && moveEl.isJsonObject()) {
                JsonObject moveObj = moveEl.getAsJsonObject();
                for (var entry : moveObj.entrySet()) {
                    String from = entry.getKey();
                    if (from == null || from.isBlank()) {
                        continue;
                    }
                    JsonElement value = entry.getValue();
                    if (value == null || !value.isJsonPrimitive()) {
                        continue;
                    }
                    String to = value.getAsString();
                    if (to == null || to.isBlank()) {
                        continue;
                    }
                    ops.move.put(from, to);
                }
            }
        }
        if (JsonCaseUtil.hasIgnoreCase(obj, "modify")) {
            JsonElement modEl = JsonCaseUtil.getIgnoreCase(obj, "modify");
            if (modEl.isJsonObject()) {
                JsonObject modObj = modEl.getAsJsonObject();
                for (var entry : modObj.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value != null && value.isJsonArray()) {
                        for (JsonElement child : value.getAsJsonArray()) {
                            ModifyEntry mod = parseModifyEntry(entry.getKey(), child);
                            if (mod != null) {
                                ops.modify.add(mod);
                            }
                        }
                    } else {
                        ModifyEntry mod = parseModifyEntry(entry.getKey(), value);
                        if (mod != null) {
                            ops.modify.add(mod);
                        }
                    }
                }
            } else if (modEl.isJsonArray()) {
                for (JsonElement element : modEl.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject modObj = element.getAsJsonObject();
                    String path = getString(modObj, "path");
                    if (path == null || path.isBlank()) {
                        continue;
                    }
                    ModifyEntry mod = parseModifyEntry(path, modObj);
                    if (mod != null) {
                        ops.modify.add(mod);
                    }
                }
            }
        }
        return ops;
    }

    private static UpdateOps parseUpdateOps(JsonElement element) {
        UpdateOps ops = new UpdateOps();
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return ops;
        }
        JsonObject obj = element.getAsJsonObject();
        JsonObject remoteObj = JsonCaseUtil.getObjectIgnoreCase(obj, "remote");
        if (remoteObj != null) {
            for (var entry : remoteObj.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) {
                    continue;
                }
                UpdateEntry updateEntry = parseUpdateEntry(entry.getValue());
                if (updateEntry != null) {
                    ops.remote.put(key, updateEntry);
                }
            }
        }
        JsonObject requiredObj = JsonCaseUtil.getObjectIgnoreCase(obj, "required");
        if (requiredObj != null) {
            for (var entry : requiredObj.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) {
                    continue;
                }
                UpdateEntry updateEntry = parseUpdateEntry(entry.getValue());
                if (updateEntry != null) {
                    ops.required.put(key, updateEntry);
                }
            }
        }
        return ops;
    }

    private static UpdateEntry parseUpdateEntry(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        String curseforge = getString(obj, "curseforge");
        String modrinth = getString(obj, "modrinth");
        JsonElement directUrl = JsonCaseUtil.getIgnoreCase(obj, "directUrl");
        Map<String, JsonElement> extraFields = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            if ("curseforge".equalsIgnoreCase(key)
                || "modrinth".equalsIgnoreCase(key)
                || "directUrl".equalsIgnoreCase(key)) {
                continue;
            }
            flattenUpdateField(key, entry.getValue(), extraFields);
        }
        if (directUrl != null && !extraFields.containsKey("download.directUrl")) {
            extraFields.put("download.directUrl", directUrl.deepCopy());
        }
        if ((curseforge == null || curseforge.isBlank())
            && (modrinth == null || modrinth.isBlank())
            && extraFields.isEmpty()) {
            return null;
        }
        UpdateEntry entry = new UpdateEntry();
        entry.curseforge = curseforge;
        entry.modrinth = modrinth;
        entry.fields.putAll(extraFields);
        return entry;
    }

    private static void flattenUpdateField(String key, JsonElement value, Map<String, JsonElement> out) {
        if (key == null || key.isBlank() || out == null) {
            return;
        }
        if (value == null || value.isJsonNull()) {
            out.putIfAbsent(key, JsonNull.INSTANCE);
            return;
        }
        if (!value.isJsonObject()) {
            out.putIfAbsent(key, value.deepCopy());
            return;
        }
        JsonObject obj = value.getAsJsonObject();
        if (obj.entrySet().isEmpty()) {
            out.putIfAbsent(key, new JsonObject());
            return;
        }
        for (Map.Entry<String, JsonElement> child : obj.entrySet()) {
            String childKey = child.getKey();
            if (childKey == null || childKey.isBlank()) {
                continue;
            }
            flattenUpdateField(key + "." + childKey, child.getValue(), out);
        }
    }

    private static AddEntry parseAddEntry(String key, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        AddEntry entry = new AddEntry();
        entry.sourcePath = key;
        entry.appliedTarget = getString(obj, "appliedTarget");
        entry.overrideMode = parseOverride(JsonCaseUtil.getIgnoreCase(obj, "override"));
        entry.sourceIsDir = key != null && key.endsWith("/");
        return entry;
    }

    private static ModifyEntry parseModifyEntry(String path, JsonElement element) {
        if (path == null || path.isBlank() || element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        String find = getString(obj, "find");
        String replace = getString(obj, "replace");
        if (find == null || replace == null) {
            return null;
        }
        ModifyEntry entry = new ModifyEntry();
        entry.targetPath = path;
        entry.find = find;
        entry.replace = replace;
        entry.which = parseWhich(JsonCaseUtil.getIgnoreCase(obj, "which"));
        entry.normalizedFind = normalizeFind(find);
        return entry;
    }

    private static String normalizeFind(String find) {
        if (find == null) {
            return null;
        }
        Matcher matcher = LAST_JSON_PAIR.matcher(find);
        String last = null;
        while (matcher.find()) {
            last = matcher.group();
        }
        if (last != null && !last.isBlank()) {
            return last.trim();
        }
        return find.trim();
    }

    private static OverrideMode parseOverride(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return OverrideMode.TRUE;
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean() ? OverrideMode.TRUE : OverrideMode.FALSE;
            }
            String raw = element.getAsString();
            if (raw == null) {
                return OverrideMode.TRUE;
            }
            String value = raw.trim().toLowerCase(Locale.ROOT);
            return switch (value) {
                case "all" -> OverrideMode.ALL;
                case "none" -> OverrideMode.NONE;
                case "false" -> OverrideMode.FALSE;
                default -> OverrideMode.TRUE;
            };
        }
        return OverrideMode.TRUE;
    }

    private static WhichSpec parseWhich(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return WhichSpec.all();
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isNumber()) {
                int value = element.getAsInt();
                return value == -1 ? WhichSpec.last() : WhichSpec.indexes(List.of(value));
            }
            String raw = element.getAsString();
            if (raw == null) {
                return WhichSpec.all();
            }
            return parseWhichString(raw);
        }
        if (element.isJsonArray()) {
            List<Integer> indexes = new ArrayList<>();
            boolean last = false;
            for (JsonElement child : element.getAsJsonArray()) {
                if (child == null || child.isJsonNull()) {
                    continue;
                }
                if (child.isJsonPrimitive()) {
                    if (child.getAsJsonPrimitive().isNumber()) {
                        int value = child.getAsInt();
                        if (value == -1) {
                            last = true;
                        } else {
                            indexes.add(value);
                        }
                    } else {
                        WhichSpec spec = parseWhichString(child.getAsString());
                        if (spec.all) {
                            return WhichSpec.all();
                        }
                        if (spec.last) {
                            last = true;
                        }
                        indexes.addAll(spec.indexes);
                    }
                }
            }
            if (last) {
                return WhichSpec.last();
            }
            if (indexes.isEmpty()) {
                return WhichSpec.all();
            }
            return WhichSpec.indexes(indexes);
        }
        return WhichSpec.all();
    }

    private static WhichSpec parseWhichString(String raw) {
        if (raw == null) {
            return WhichSpec.all();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank() || "all".equals(value)) {
            return WhichSpec.all();
        }
        if ("-1".equals(value) || "last".equals(value)) {
            return WhichSpec.last();
        }
        List<Integer> indexes = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int num = Integer.parseInt(trimmed);
                if (num == -1) {
                    return WhichSpec.last();
                }
                indexes.add(num);
            } catch (NumberFormatException ignored) {
            }
        }
        if (indexes.isEmpty()) {
            return WhichSpec.all();
        }
        return WhichSpec.indexes(indexes);
    }

    private static List<String> readStringArray(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (element.isJsonPrimitive()) {
            values.add(element.getAsString());
            return values;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                if (child == null || child.isJsonNull()) {
                    continue;
                }
                if (child.isJsonPrimitive()) {
                    values.add(child.getAsString());
                }
            }
        }
        return values;
    }

    private static String getString(JsonObject obj, String key) {
        return JsonCaseUtil.getStringIgnoreCase(obj, key);
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        return JsonCaseUtil.getBooleanIgnoreCase(obj, key, fallback);
    }

    private static final class UpdateDLCMConfig {
        private boolean enabled;
        private String version;
    }

    private static String normalizePlatform(String value) {
        if (value == null) {
            return "universal";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("client".equals(normalized) || "server".equals(normalized) || "universal".equals(normalized)) {
            return normalized;
        }
        return "universal";
    }

    public static class VersionInfo {
        private String platform = "universal";
        private String base;
        private String baseNew;
        private String dlcOld;
        private String dlcNew;

        public String getPlatform() {
            return platform == null || platform.isBlank() ? "universal" : platform;
        }

        public String getBase() {
            return base;
        }

        public String getBaseNew() {
            return baseNew;
        }

        public String getDlcOld() {
            return dlcOld;
        }

        public String getDlcNew() {
            return dlcNew;
        }
    }

    public static class FileOps {
        private final List<String> del = new ArrayList<>();
        private final Map<String, AddEntry> add = new LinkedHashMap<>();
        private final Map<String, String> move = new LinkedHashMap<>();
        private final List<ModifyEntry> modify = new ArrayList<>();

        public List<String> getDel() {
            return del;
        }

        public Map<String, AddEntry> getAdd() {
            return add;
        }

        public Map<String, String> getMove() {
            return move;
        }

        public List<ModifyEntry> getModify() {
            return modify;
        }
    }

    public static class UpdateOps {
        private final Map<String, UpdateEntry> remote = new LinkedHashMap<>();
        private final Map<String, UpdateEntry> required = new LinkedHashMap<>();

        public Map<String, UpdateEntry> getRemote() {
            return remote;
        }

        public Map<String, UpdateEntry> getRequired() {
            return required;
        }
    }

    public static class UpdateEntry {
        private String curseforge;
        private String modrinth;
        private final Map<String, JsonElement> fields = new LinkedHashMap<>();

        public String getCurseforge() {
            return curseforge;
        }

        public String getModrinth() {
            return modrinth;
        }

        public Map<String, JsonElement> getFields() {
            return fields;
        }
    }

    public static class AddEntry {
        private String sourcePath;
        private String appliedTarget;
        private OverrideMode overrideMode = OverrideMode.TRUE;
        private boolean sourceIsDir;

        public String getSourcePath() {
            return sourcePath;
        }

        public String getAppliedTarget() {
            return appliedTarget;
        }

        public OverrideMode getOverrideMode() {
            return overrideMode;
        }

        public boolean isSourceDir() {
            return sourceIsDir;
        }
    }

    public static class ModifyEntry {
        private String targetPath;
        private String find;
        private String normalizedFind;
        private String replace;
        private WhichSpec which = WhichSpec.all();

        public String getTargetPath() {
            return targetPath;
        }

        public String getFind() {
            return find;
        }

        public String getNormalizedFind() {
            return normalizedFind;
        }

        public String getReplace() {
            return replace;
        }

        public WhichSpec getWhich() {
            return which;
        }
    }

    public enum OverrideMode {
        TRUE,
        FALSE,
        ALL,
        NONE
    }

    public static class WhichSpec {
        private final boolean all;
        private final boolean last;
        private final List<Integer> indexes;

        private WhichSpec(boolean all, boolean last, List<Integer> indexes) {
            this.all = all;
            this.last = last;
            this.indexes = indexes == null ? List.of() : indexes;
        }

        public static WhichSpec all() {
            return new WhichSpec(true, false, List.of());
        }

        public static WhichSpec last() {
            return new WhichSpec(false, true, List.of());
        }

        public static WhichSpec indexes(List<Integer> indexes) {
            return new WhichSpec(false, false, indexes);
        }

        public boolean isAll() {
            return all;
        }

        public boolean isLast() {
            return last;
        }

        public List<Integer> getIndexes() {
            return indexes;
        }
    }
}
