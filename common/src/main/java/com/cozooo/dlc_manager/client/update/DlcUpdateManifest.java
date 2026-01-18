package com.cozooo.dlc_manager.client.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

    public static DlcUpdateManifest load(Path updateJson) {
        if (updateJson == null || !Files.exists(updateJson)) {
            return null;
        }
        JsonObject obj;
        try (Reader reader = Files.newBufferedReader(updateJson)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            obj = GSON.fromJson(jsonReader, JsonObject.class);
        } catch (IOException ignored) {
            return null;
        }
        return fromJsonObject(obj);
    }

    public static DlcUpdateManifest fromJsonObject(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        DlcUpdateManifest manifest = new DlcUpdateManifest();
        manifest.version = parseVersion(obj.getAsJsonObject("version"));
        manifest.hidden = getBoolean(obj, "isHidden", false);
        manifest.fileOps = parseFileOps(obj.getAsJsonObject("file"));
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

    private static VersionInfo parseVersion(JsonObject obj) {
        VersionInfo info = new VersionInfo();
        if (obj == null) {
            return info;
        }
        info.base = getString(obj, "base");
        info.dlcOld = getString(obj, "dlc_old");
        info.dlcNew = getString(obj, "dlc_new");
        return info;
    }

    private static FileOps parseFileOps(JsonObject obj) {
        FileOps ops = new FileOps();
        if (obj == null) {
            return ops;
        }
        if (obj.has("del")) {
            ops.del.addAll(readStringArray(obj.get("del")));
        }
        if (obj.has("add")) {
            JsonElement addEl = obj.get("add");
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
        if (obj.has("modify")) {
            JsonElement modEl = obj.get("modify");
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

    private static AddEntry parseAddEntry(String key, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        AddEntry entry = new AddEntry();
        entry.sourcePath = key;
        entry.appliedTarget = getString(obj, "appliedTarget");
        entry.overrideMode = parseOverride(obj.get("override"));
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
        entry.which = parseWhich(obj.get("which"));
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
                // Skip invalid parts.
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
        if (obj == null || key == null) {
            return null;
        }
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj == null || key == null) {
            return fallback;
        }
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        if (obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isBoolean()) {
            return obj.get(key).getAsBoolean();
        }
        return fallback;
    }

    public static class VersionInfo {
        private String base;
        private String dlcOld;
        private String dlcNew;

        public String getBase() {
            return base;
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
        private final List<ModifyEntry> modify = new ArrayList<>();

        public List<String> getDel() {
            return del;
        }

        public Map<String, AddEntry> getAdd() {
            return add;
        }

        public List<ModifyEntry> getModify() {
            return modify;
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
