package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.core.DcConfigIO;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DlcPackOrder {
    private DlcPackOrder() {
    }

    public static List<String> load(Path path) {
        if (path == null || !Files.exists(path)) {
            return List.of();
        }
        JsonObject obj = DcConfigIO.read(path, DcConfigIO.Layout.GENERIC);
        if (obj == null) {
            return List.of();
        }
        JsonArray array = DcConfigIO.getArrayIgnoreCase(obj, "archives");
        if (array == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        array.forEach(element -> {
            if (element != null && element.isJsonPrimitive()) {
                String value = normalize(element.getAsString());
                if (value != null && !out.contains(value)) {
                    out.add(value);
                }
            }
        });
        return out;
    }

    public static boolean save(Path path, Collection<String> order) {
        if (path == null) {
            return false;
        }
        JsonObject obj = new JsonObject();
        JsonArray array = new JsonArray();
        if (order != null) {
            for (String key : order) {
                String value = normalize(key);
                if (value != null) {
                    array.add(value);
                }
            }
        }
        obj.add("archives", array);
        return DlcPackDcFiles.writeAtomic(path, obj);
    }

    public static List<String> normalizeOrder(Collection<String> savedOrder, Collection<String> currentKeys) {
        List<String> out = new ArrayList<>();
        if (savedOrder != null) {
            for (String key : savedOrder) {
                String value = normalize(key);
                if (value != null && currentKeys != null && currentKeys.contains(value) && !out.contains(value)) {
                    out.add(value);
                }
            }
        }
        if (currentKeys != null) {
            for (String key : currentKeys) {
                String value = normalize(key);
                if (value != null && !out.contains(value)) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
