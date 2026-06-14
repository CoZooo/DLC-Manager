package com.cozooo.dlc_manager.client.required;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.core.DcFormatUtil;
import com.cozooo.dlc_manager.core.DlcPathConfig;
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
        Path noticePath = DcFormatUtil.resolveConfigPath(requiredDir, "notice");
        ensureNoticeFile(noticePath);
        if (!Files.exists(noticePath)) {
            return new DlcRequiredNotice(download, manage);
        }
        JsonObject obj = DcConfigIO.read(noticePath, DcConfigIO.Layout.GENERIC);
        if (obj == null) {
            return new DlcRequiredNotice(download, manage);
        }
        readLocalized(DcConfigIO.getObjectIgnoreCase(obj, "download_desc"), download);
        readLocalized(DcConfigIO.getObjectIgnoreCase(obj, "manage_desc"), manage);
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
                out.put(DcConfigIO.normalizeLangKey(entry.getKey()), entry.getValue().getAsString());
            }
        }
    }

    private static String resolveLocalized(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        String lang = DcConfigIO.normalizeLangKey(CrossPlatformRuntime.languageCode());
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
            DcConfigIO.write(noticePath, obj, DcConfigIO.Layout.GENERIC);
        } catch (Exception ignored) {
        }
    }
}
