package com.cozooo.dlc_manager.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import de.marhali.json5.Json5;
import de.marhali.json5.Json5Array;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Null;
import de.marhali.json5.Json5Object;
import de.marhali.json5.Json5Primitive;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class Json5Util {
    public static final String DC_EXTENSION = ".dc";
    public static final String JSON5_EXTENSION = ".json5";
    public static final String JSON_EXTENSION = ".json";
    private static final Json5 JSON5 = new Json5();
    private static final Gson LENIENT_GSON = new Gson();

    private Json5Util() {
    }

    public static boolean isJsonLikeName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(DC_EXTENSION) || lower.endsWith(JSON5_EXTENSION) || lower.endsWith(JSON_EXTENSION);
    }

    public static String stripJsonExtension(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(JSON5_EXTENSION)) {
            return name.substring(0, name.length() - JSON5_EXTENSION.length());
        }
        if (lower.endsWith(JSON_EXTENSION)) {
            return name.substring(0, name.length() - JSON_EXTENSION.length());
        }
        if (lower.endsWith(DC_EXTENSION)) {
            return name.substring(0, name.length() - DC_EXTENSION.length());
        }
        return name;
    }

    public static Path toDcPath(Path path) {
        if (path == null) {
            return null;
        }
        String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        if (name.toLowerCase().endsWith(DC_EXTENSION)) {
            return path;
        }
        String base = stripJsonExtension(name);
        return path.getParent() == null ? Path.of(base + DC_EXTENSION) : path.getParent().resolve(base + DC_EXTENSION);
    }

    public static Path toJson5Path(Path path) {
        if (path == null) {
            return null;
        }
        String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        if (name.toLowerCase().endsWith(JSON5_EXTENSION)) {
            return path;
        }
        if (name.toLowerCase().endsWith(JSON_EXTENSION)) {
            String base = name.substring(0, name.length() - JSON_EXTENSION.length());
            return path.getParent() == null ? Path.of(base + JSON5_EXTENSION) : path.getParent().resolve(base + JSON5_EXTENSION);
        }
        return path;
    }

    public static Path resolveConfigPath(Path dir, String baseName) {
        if (dir == null || baseName == null || baseName.isBlank()) {
            return null;
        }
        Path dc = dir.resolve(baseName + DC_EXTENSION);
        if (Files.exists(dc)) {
            return dc;
        }
        Path json5 = dir.resolve(baseName + JSON5_EXTENSION);
        if (Files.exists(json5)) {
            return json5;
        }
        Path json = dir.resolve(baseName + JSON_EXTENSION);
        if (Files.exists(json)) {
            return json;
        }
        return dc;
    }

    public static JsonElement readElement(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException ignored) {
            return null;
        }
        if (path.getFileName() != null && path.getFileName().toString().toLowerCase().endsWith(DC_EXTENSION)) {
            String trimmed = content == null ? "" : content.stripLeading();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                JsonElement jsonLike = readElementJson5(content);
                if (jsonLike != null) {
                    return jsonLike;
                }
                jsonLike = readElementLenient(content);
                if (jsonLike != null) {
                    return jsonLike;
                }
            }
            JsonObject dc = DcFormatUtil.readGenericText(content);
            if (dc != null && !dc.entrySet().isEmpty()) {
                return dc;
            }
        }
        JsonElement parsed = readElementJson5(content);
        if (parsed != null) {
            return parsed;
        }
        parsed = readElementLenient(content);
        if (parsed != null) {
            return parsed;
        }
        String normalized = normalizeBareMultilineStrings(content);
        if (normalized.equals(content)) {
            return null;
        }
        parsed = readElementJson5(normalized);
        if (parsed != null) {
            return parsed;
        }
        return readElementLenient(normalized);
    }

    private static JsonElement readElementJson5(String content) {
        if (content == null) {
            return null;
        }
        try (Reader reader = new StringReader(content)) {
            Json5Element element = JSON5.parse(reader);
            return toGson(element);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static JsonElement readElementLenient(String content) {
        if (content == null) {
            return null;
        }
        try (Reader reader = new StringReader(content)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            return LENIENT_GSON.fromJson(jsonReader, JsonElement.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeBareMultilineStrings(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(content.length() + 32);
        boolean inString = false;
        char quote = 0;
        boolean escaping = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (!inString) {
                if (c == '"' || c == '\'') {
                    inString = true;
                    quote = c;
                    escaping = false;
                }
                out.append(c);
                continue;
            }

            if (escaping) {
                out.append(c);
                escaping = false;
                continue;
            }
            if (c == '\\') {
                out.append(c);
                escaping = true;
                continue;
            }
            if (c == quote) {
                out.append(c);
                inString = false;
                quote = 0;
                continue;
            }
            if (c == '\r') {
                out.append("\\n");
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                continue;
            }
            if (c == '\n') {
                out.append("\\n");
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    public static JsonObject readObject(Path path) {
        JsonElement element = readElement(path);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return null;
    }

    public static Path migrateLegacy(Path legacyPath, Gson gson) {
        if (legacyPath == null) {
            return null;
        }
        String name = legacyPath.getFileName() == null ? legacyPath.toString() : legacyPath.getFileName().toString();
        String lower = name.toLowerCase();
        if (lower.endsWith(DC_EXTENSION)) {
            migrateJsonLikeDcInPlace(legacyPath, gson);
            return legacyPath;
        }
        if (!isJsonLikeName(name)) {
            return legacyPath;
        }
        Path dc = toDcPath(legacyPath);
        if (dc != null && Files.exists(dc)) {
            return dc;
        }
        JsonElement element = readElement(legacyPath);
        if (element == null || dc == null) {
            return legacyPath;
        }
        try {
            Files.createDirectories(dc.getParent());
            JsonElement normalized = normalizeLegacyKeys(element);
            boolean written;
            if (normalized != null && normalized.isJsonObject()) {
                written = DcFormatUtil.writeGeneric(dc, normalized.getAsJsonObject());
            } else {
                try (Writer writer = Files.newBufferedWriter(dc)) {
                    gson.toJson(normalized == null ? JsonNull.INSTANCE : normalized, writer);
                }
                written = true;
            }
            if (!written) {
                return legacyPath;
            }
            if (!dc.equals(legacyPath)) {
                Files.deleteIfExists(legacyPath);
            }
            return dc;
        } catch (IOException ignored) {
            return legacyPath;
        }
    }

    private static void migrateJsonLikeDcInPlace(Path path, Gson gson) {
        if (path == null || gson == null || !Files.exists(path)) {
            return;
        }
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException ignored) {
            return;
        }
        if (content == null) {
            return;
        }
        String trimmed = content.stripLeading();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return;
        }
        JsonElement parsed = readElementJson5(content);
        if (parsed == null) {
            parsed = readElementLenient(content);
        }
        if (parsed == null) {
            return;
        }
        JsonElement normalized = normalizeLegacyKeys(parsed);
        writeNormalizedToDc(path, normalized, gson);
    }

    public static boolean shouldSkipLegacy(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(DC_EXTENSION)) {
            return false;
        }
        if (!name.endsWith(JSON_EXTENSION) && !name.endsWith(JSON5_EXTENSION)) {
            return false;
        }
        Path dc = toDcPath(path);
        if (dc != null && Files.exists(dc)) {
            return true;
        }
        if (name.endsWith(JSON5_EXTENSION)) {
            return false;
        }
        Path json5 = toJson5Path(path);
        return json5 != null && Files.exists(json5);
    }

    public static boolean normalizeLegacyKeys(Path path, Gson gson) {
        if (path == null || gson == null || !Files.exists(path)) {
            return false;
        }
        JsonElement raw = readElement(path);
        if (raw == null) {
            return false;
        }
        NormalizeResult result = normalizeLegacyKeysInternal(raw);
        if (!result.changed) {
            return false;
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            return writeNormalizedToDc(path, result.element, gson);
        } catch (IOException ignored) {
            return false;
        }
    }

    public static JsonElement normalizeLegacyKeys(JsonElement element) {
        return normalizeLegacyKeysInternal(element).element;
    }

    private static NormalizeResult normalizeLegacyKeysInternal(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new NormalizeResult(JsonNull.INSTANCE, false);
        }
        if (element.isJsonArray()) {
            JsonArray input = element.getAsJsonArray();
            JsonArray output = new JsonArray();
            boolean changed = false;
            for (JsonElement child : input) {
                NormalizeResult childResult = normalizeLegacyKeysInternal(child);
                output.add(childResult.element);
                changed = changed || childResult.changed;
            }
            return new NormalizeResult(output, changed);
        }
        if (!element.isJsonObject()) {
            return new NormalizeResult(element.deepCopy(), false);
        }
        JsonObject input = element.getAsJsonObject();
        JsonObject output = new JsonObject();
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String mappedKey = mapLegacyKey(key);
            if (!mappedKey.equals(key)) {
                changed = true;
            }
            NormalizeResult childResult = normalizeLegacyKeysInternal(entry.getValue());
            if (output.has(mappedKey)) {
                changed = true;
                continue;
            }
            output.add(mappedKey, childResult.element);
            changed = changed || childResult.changed;
        }
        return new NormalizeResult(output, changed);
    }

    private static String mapLegacyKey(String key) {
        if (key == null) {
            return "";
        }
        if ("dlc_name".equalsIgnoreCase(key)) {
            return "file_name";
        }
        if ("dlc_identifier".equalsIgnoreCase(key)) {
            return "identifier";
        }
        return key;
    }

    private static boolean writeNormalizedToDc(Path path, JsonElement element, Gson gson) {
        if (path == null || gson == null) {
            return false;
        }
        Path target = Json5Util.toDcPath(path);
        if (target == null) {
            return false;
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            String lower = target.getFileName() == null ? "" : target.getFileName().toString().toLowerCase();
            if (lower.endsWith(DC_EXTENSION)
                && element != null
                && element.isJsonObject()
                && !isHotUpdateConfigPath(target)) {
                boolean written = DcFormatUtil.writeGeneric(target, element.getAsJsonObject());
                if (written) {
                    deleteLegacyIfMoved(path, target);
                }
                return written;
            }
            try (Writer writer = Files.newBufferedWriter(target)) {
                gson.toJson(element == null ? JsonNull.INSTANCE : element, writer);
            }
            deleteLegacyIfMoved(path, target);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static final class NormalizeResult {
        private final JsonElement element;
        private final boolean changed;

        private NormalizeResult(JsonElement element, boolean changed) {
            this.element = element;
            this.changed = changed;
        }
    }

    private static boolean isHotUpdateConfigPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        return "update.dc".equalsIgnoreCase(path.getFileName().toString());
    }

    private static void deleteLegacyIfMoved(Path original, Path target) {
        if (original == null || target == null || original.equals(target)) {
            return;
        }
        try {
            Files.deleteIfExists(original);
        } catch (IOException ignored) {
        }
    }

    private static JsonElement toGson(Json5Element element) {
        if (element == null || element instanceof Json5Null) {
            return JsonNull.INSTANCE;
        }
        if (element instanceof Json5Object object) {
            JsonObject result = new JsonObject();
            for (String key : object.keySet()) {
                result.add(key, toGson(object.get(key)));
            }
            return result;
        }
        if (element instanceof Json5Array array) {
            JsonArray result = new JsonArray();
            for (Json5Element item : array) {
                result.add(toGson(item));
            }
            return result;
        }
        if (element instanceof Json5Primitive primitive) {
            if (primitive.isBoolean()) {
                return new JsonPrimitive(primitive.getAsBoolean());
            }
            if (primitive.isNumber()) {
                return new JsonPrimitive(primitive.getAsNumber());
            }
            return new JsonPrimitive(primitive.getAsString());
        }
        return JsonNull.INSTANCE;
    }
}
