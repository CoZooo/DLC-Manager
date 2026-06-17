package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DlcRedirectLoader {
    private DlcRedirectLoader() {
    }

    public static List<DlcRedirectEntry> load(Path dlcRoot) {
        if (dlcRoot == null) {
            return List.of();
        }
        Path redirectRoot = dlcRoot.resolve("remote").resolve("redirect");
        if (!Files.isDirectory(redirectRoot)) {
            return List.of();
        }
        List<DlcRedirectEntry> entries = new ArrayList<>();
        try (var stream = Files.walk(redirectRoot)) {
            stream.filter(path -> path.getFileName() != null && DlcDcFormatUtil.isDcName(path.getFileName().toString()))
                .forEach(path -> {
                    DlcRedirectEntry entry = readEntry(path);
                    if (entry != null) {
                        entries.add(entry);
                    }
                });
        } catch (IOException ignored) {
        }
        return entries;
    }

    private static DlcRedirectEntry readEntry(Path path) {
        JsonObject obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
        if (obj == null) {
            return null;
        }
        String redirectUrl = DlcDcConfigIO.getStringIgnoreCase(obj, "RedirectUrl");
        if (redirectUrl == null || redirectUrl.isBlank()) {
            return null;
        }

        DlcRedirectEntry entry = new DlcRedirectEntry();
        entry.setConfigPath(path);
        entry.setRedirectUrl(redirectUrl);
        entry.setAvailable(DlcDcConfigIO.getIgnoreCase(obj, "available"));

        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        entry.setFallbackName(DlcDcFormatUtil.stripConfigExtension(fileName));

        entry.getDisplayNames().putAll(readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "DisplayName")));
        entry.getNotices().putAll(readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "notice")));
        entry.getTips().putAll(readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "tip")));
        return entry;
    }

    private static Map<String, String> readLocalized(JsonObject obj) {
        Map<String, String> values = new HashMap<>();
        if (obj == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                String key = DlcDcConfigIO.normalizeLangKey(entry.getKey());
                if (key != null) {
                    values.put(key, entry.getValue().getAsString());
                }
            }
        }
        return values;
    }
}
