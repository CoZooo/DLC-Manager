package com.cozooo.dlc_manager.ui.remote;
import com.cozooo.dlc_manager.ui.remote.DlcFxResourceOperationModel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class DlcFxRemoteDownloadProgressModel {
    private DlcFxRemoteDownloadProgressModel() {
    }

    public static void applyItemProgress(JsonObject status, DlcFxResourceOperationModel operationModel) {
        if (status == null || operationModel == null) {
            return;
        }
        JsonArray items = status.has("items") && status.get("items").isJsonArray()
            ? status.getAsJsonArray("items")
            : null;
        if (items == null) {
            return;
        }
        for (JsonElement element : items) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String key = firstNonBlank(str(item, "key"), str(item, "id"), str(item, "name"));
            if (key.isBlank()) {
                continue;
            }
            long total = lng(item, "totalBytes");
            long downloaded = lng(item, "downloadedBytes");
            boolean done = bool(item, "done");
            if (total > 0) {
                operationModel.setProgress(key, Math.min(1f, downloaded / (float) total));
            } else if (done) {
                operationModel.setProgress(key, 1f);
            }
        }
    }

    private static String str(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static boolean bool(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static long lng(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return 0L;
        }
        try {
            return e.getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
