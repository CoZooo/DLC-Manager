package com.cozooo.dlc_manager.client.update;

import com.cozooo.dclanguage.DcDocuments;
import com.cozooo.dclanguage.DcParseException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class DlcUpdateDcParser {
    private DlcUpdateDcParser() { }

    static JsonObject parse(String text) {
        if (text == null) return null;
        try {
            JsonObject root = DcDocuments.readText(text).toJson();
            adaptDeleteLists(root);
            return root;
        } catch (DcParseException exception) {
            return null;
        }
    }

    private static void adaptDeleteLists(JsonElement value) {
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(DlcUpdateDcParser::adaptDeleteLists);
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                JsonElement child = entry.getValue();
                if ("del".equalsIgnoreCase(entry.getKey()) && child.isJsonObject()
                    && child.getAsJsonObject().asMap().values().stream().allMatch(item -> item.isJsonObject() && item.getAsJsonObject().entrySet().isEmpty())) {
                    JsonArray paths = new JsonArray();
                    child.getAsJsonObject().keySet().forEach(paths::add);
                    entry.setValue(paths);
                } else adaptDeleteLists(child);
            }
        }
    }
}
