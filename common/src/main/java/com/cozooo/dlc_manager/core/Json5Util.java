package com.cozooo.dlc_manager.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.marhali.json5.Json5;
import de.marhali.json5.Json5Array;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Null;
import de.marhali.json5.Json5Object;
import de.marhali.json5.Json5Primitive;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Json5Util {
    public static final String JSON5_EXTENSION = ".json5";
    public static final String JSON_EXTENSION = ".json";
    private static final Json5 JSON5 = new Json5();

    private Json5Util() {
    }

    public static boolean isJsonLikeName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(JSON5_EXTENSION) || lower.endsWith(JSON_EXTENSION);
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
        return name;
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
        Path json5 = dir.resolve(baseName + JSON5_EXTENSION);
        if (Files.exists(json5)) {
            return json5;
        }
        Path json = dir.resolve(baseName + JSON_EXTENSION);
        if (Files.exists(json)) {
            return json;
        }
        return json5;
    }

    public static JsonElement readElement(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Json5Element element = JSON5.parse(reader);
            return toGson(element);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
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
        if (!name.toLowerCase().endsWith(JSON_EXTENSION)) {
            return legacyPath;
        }
        Path json5 = toJson5Path(legacyPath);
        if (json5 != null && Files.exists(json5)) {
            return json5;
        }
        JsonElement element = readElement(legacyPath);
        if (element == null || json5 == null) {
            return legacyPath;
        }
        try {
            Files.createDirectories(json5.getParent());
            try (Writer writer = Files.newBufferedWriter(json5)) {
                gson.toJson(element, writer);
            }
            Files.deleteIfExists(legacyPath);
            return json5;
        } catch (IOException ignored) {
            return legacyPath;
        }
    }

    public static boolean shouldSkipLegacy(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        if (!name.endsWith(JSON_EXTENSION)) {
            return false;
        }
        Path json5 = toJson5Path(path);
        return json5 != null && Files.exists(json5);
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
