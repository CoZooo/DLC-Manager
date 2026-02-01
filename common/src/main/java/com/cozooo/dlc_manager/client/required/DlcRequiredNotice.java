package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.cozooo.dlc_manager.core.Json5Util;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class DlcRequiredNotice {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private final Map<String, String> downloadDesc;
    private final Map<String, String> manageDesc;

    private DlcRequiredNotice(Map<String, String> downloadDesc, Map<String, String> manageDesc) {
        this.downloadDesc = downloadDesc;
        this.manageDesc = manageDesc;
    }

    public static DlcRequiredNotice load() {
        Map<String, String> download = new HashMap<>();
        Map<String, String> manage = new HashMap<>();
        Path dlcRoot = DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
        if (dlcRoot == null) {
            return new DlcRequiredNotice(download, manage);
        }
        Path requiredDir = dlcRoot.resolve("required");
        Json5Util.migrateLegacy(requiredDir.resolve("notice.json"), GSON);
        Path noticePath = Json5Util.resolveConfigPath(requiredDir, "notice");
        ensureNoticeFile(noticePath);
        if (!Files.exists(noticePath)) {
            return new DlcRequiredNotice(download, manage);
        }
        JsonObject obj = Json5Util.readObject(noticePath);
        if (obj == null) {
            return new DlcRequiredNotice(download, manage);
        }
        readLocalized(JsonCaseUtil.getObjectIgnoreCase(obj, "download_desc"), download);
        readLocalized(JsonCaseUtil.getObjectIgnoreCase(obj, "manage_desc"), manage);
        return new DlcRequiredNotice(download, manage);
    }

    public String resolveDownloadDesc() {
        return resolveLocalized(downloadDesc);
    }

    public String resolveManageDesc() {
        return resolveLocalized(manageDesc);
    }

    private static void readLocalized(JsonObject data, Map<String, String> out) {
        if (data == null) {
            return;
        }
        for (var entry : data.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                out.put(JsonCaseUtil.normalizeLangKey(entry.getKey()), entry.getValue().getAsString());
            }
        }
    }

    private static String resolveLocalized(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        String lang = JsonCaseUtil.normalizeLangKey(com.cozooo.dlc_manager.platform.PlatformServices.get().languageCode());
        if (lang != null && values.containsKey(lang)) {
            return values.get(lang);
        }
        if (values.containsKey("en_us")) {
            return values.get("en_us");
        }
        return values.values().stream().findFirst().orElse("");
    }

    private static void ensureNoticeFile(Path noticePath) {
        if (noticePath == null) {
            return;
        }
        if (Files.exists(noticePath)) {
            return;
        }
        JsonObject obj = new JsonObject();
        JsonObject download = new JsonObject();
        download.addProperty("en_us", "");
        obj.add("download_desc", download);
        JsonObject manage = new JsonObject();
        manage.addProperty("en_us", "");
        obj.add("manage_desc", manage);
        try {
            Files.createDirectories(noticePath.getParent());
            try (java.io.Writer writer = Files.newBufferedWriter(noticePath)) {
                GSON.toJson(obj, writer);
            }
        } catch (Exception ignored) {
            // Best-effort.
        }
    }
}
