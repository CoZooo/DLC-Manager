package com.cozooo.dlc_manager.client.update;

import com.cozooo.dclanguage.DcSchemaCatalog;
import com.cozooo.dlc_manager.core.DlcDcDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public final class DlcUpdateCompatibility {
    private static final DcSchemaCatalog CATALOG = DlcDcDefinition.catalog();
    private static final DcSchemaCatalog.Profile PROFILE = CATALOG.profile("update");

    private DlcUpdateCompatibility() { }

    public static JsonObject knownFields(JsonObject source, List<String> unknown) {
        return filter(source, List.of(), unknown).getAsJsonObject();
    }

    private static JsonElement filter(JsonElement value, List<String> path, List<String> unknown) {
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : value.getAsJsonArray()) {
                List<String> entryPath = new ArrayList<>(path);
                if (CATALOG.category(PROFILE, append(path, "*")) != null) entryPath.add("*");
                result.add(child.isJsonObject() || child.isJsonArray() ? filter(child, entryPath, unknown) : child.deepCopy());
            }
            return result;
        }
        if (!value.isJsonObject()) return value.deepCopy();
        JsonObject result = new JsonObject();
        for (var entry : value.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            List<String> childPath = append(path, key);
            var field = CATALOG.field(PROFILE, path, key);
            var category = CATALOG.category(PROFILE, childPath);
            if (field != null) result.add(key, entry.getValue().deepCopy());
            else if (category != null) result.add(key, filter(entry.getValue(), childPath, unknown));
            else unknown.add(String.join(".", childPath));
        }
        return result;
    }

    private static List<String> append(List<String> path, String key) {
        List<String> result = new ArrayList<>(path);
        result.add(key);
        return result;
    }
}
