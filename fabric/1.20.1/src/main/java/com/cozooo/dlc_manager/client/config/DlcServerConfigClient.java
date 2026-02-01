package com.cozooo.dlc_manager.client.config;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigString;

import java.util.List;

public final class DlcServerConfigClient {
    private static final ConfigBoolean SERVER_SUPPORT = new DlcConfigBoolean("serverSupport", true,
        "dlc_manager.config.comment.ui.serverSupport", "dlc_manager.config.ui.serverSupport");
    private static final ConfigInteger SERVER_PORT = new DlcConfigInteger("serverPort", DlcPathConfig.DEFAULT_SERVER_PORT, 1, 65535,
        "dlc_manager.config.comment.ui.serverPort", "dlc_manager.config.ui.serverPort");
    private static final ConfigString SERVER_PASSWORD = new DlcConfigString("serverPassword", "",
        "dlc_manager.config.comment.ui.serverPassword", "dlc_manager.config.ui.serverPassword");

    private static final ConfigBoolean UI_ENABLE_ALL = new DlcConfigBoolean("isEnableAllButtonShown", DlcPathConfig.DEFAULT_ENABLE_ALL_SHOWN,
        "dlc_manager.config.comment.ui.isEnableAllButtonShown", "dlc_manager.config.ui.isEnableAllButtonShown");
    private static final ConfigBoolean UI_UPDATE_MANAGER = new DlcConfigBoolean("isUpdateManagerOn", DlcPathConfig.DEFAULT_UPDATE_MANAGER_ON,
        "dlc_manager.config.comment.ui.isUpdateManagerOn", "dlc_manager.config.ui.isUpdateManagerOn");
    private static final ConfigBoolean UI_SELF_UPDATE = new DlcConfigBoolean("isSelfUpdateAutoCheckOn", DlcPathConfig.DEFAULT_SELF_UPDATE_AUTO_CHECK_ON,
        "dlc_manager.config.comment.ui.isSelfUpdateAutoCheckOn", "dlc_manager.config.ui.isSelfUpdateAutoCheckOn");
    private static final ConfigBoolean UI_PRESET = new DlcConfigBoolean("isPresetFuncOn", DlcPathConfig.DEFAULT_PRESET_FUNC_ON,
        "dlc_manager.config.comment.ui.isPresetFuncOn", "dlc_manager.config.ui.isPresetFuncOn");

    private static final ConfigString DLC_ROOT = new DlcConfigString("dlcRoot", DlcPathConfig.DEFAULT_DLC_ROOT,
        "dlc_manager.config.comment.paths.dlcRoot", "dlc_manager.config.paths.dlcRoot");
    private static final ConfigString TACZ_ROOT = new DlcConfigString("taczRoot", DlcPathConfig.DEFAULT_TACZ_DLC_ROOT,
        "dlc_manager.config.comment.paths.taczRoot", "dlc_manager.config.paths.taczRoot");
    private static final ConfigString RESOURCEPACK_ROOT = new DlcConfigString("resourcepackRoot", DlcPathConfig.DEFAULT_RESOURCEPACK_ROOT,
        "dlc_manager.config.comment.paths.resourcepackRoot", "dlc_manager.config.paths.resourcepackRoot");
    private static final ConfigString DATAPACK_ROOT = new DlcConfigString("datapackRoot", DlcPathConfig.DEFAULT_DATAPACK_ROOT,
        "dlc_manager.config.comment.paths.datapackRoot", "dlc_manager.config.paths.datapackRoot");

    private static final ConfigInteger MAX_CONCURRENT = new DlcConfigInteger("maxConcurrentDownloads", DlcPathConfig.DEFAULT_MAX_CONCURRENT_DOWNLOADS, 1, 64,
        "dlc_manager.config.comment.download.maxConcurrentDownloads", "dlc_manager.config.download.maxConcurrentDownloads");
    private static final ConfigInteger MAX_SEGMENTS = new DlcConfigInteger("maxSegmentsPerFile", DlcPathConfig.DEFAULT_MAX_SEGMENTS_PER_FILE, 1, 64,
        "dlc_manager.config.comment.download.maxSegmentsPerFile", "dlc_manager.config.download.maxSegmentsPerFile");
    private static final ConfigInteger MIN_SEGMENT_MB = new DlcConfigInteger("minSegmentMB", DlcPathConfig.DEFAULT_MIN_SEGMENT_MB, 1, Integer.MAX_VALUE,
        "dlc_manager.config.comment.download.minSegmentMB", "dlc_manager.config.download.minSegmentMB");
    private static final ConfigInteger MAX_RETRY = new DlcConfigInteger("maxRetryCount", DlcPathConfig.DEFAULT_MAX_RETRY, 1, 100,
        "dlc_manager.config.comment.download.maxRetryCount", "dlc_manager.config.download.maxRetryCount");
    private static final ConfigInteger CONNECT_TIMEOUT = new DlcConfigInteger("connectTimeoutSeconds", DlcPathConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, 1, 36000,
        "dlc_manager.config.comment.download.connectTimeoutSeconds", "dlc_manager.config.download.connectTimeoutSeconds");
    private static final ConfigInteger TASK_TIMEOUT = new DlcConfigInteger("taskTimeoutSeconds", DlcPathConfig.DEFAULT_TASK_TIMEOUT_SECONDS, 1, 36000,
        "dlc_manager.config.comment.download.taskTimeoutSeconds", "dlc_manager.config.download.taskTimeoutSeconds");

    private static final List<IConfigBase> SERVER_CONFIGS = List.of(
        SERVER_SUPPORT,
        SERVER_PORT,
        SERVER_PASSWORD
    );

    private static final List<IConfigBase> UI_CONFIGS = List.of(
        UI_ENABLE_ALL,
        UI_UPDATE_MANAGER,
        UI_SELF_UPDATE,
        UI_PRESET
    );

    private static final List<IConfigBase> PATH_CONFIGS = List.of(
        DLC_ROOT,
        TACZ_ROOT,
        RESOURCEPACK_ROOT,
        DATAPACK_ROOT
    );

    private static final List<IConfigBase> DOWNLOAD_CONFIGS = List.of(
        MAX_CONCURRENT,
        MAX_SEGMENTS,
        MIN_SEGMENT_MB,
        MAX_RETRY,
        CONNECT_TIMEOUT,
        TASK_TIMEOUT
    );

    private DlcServerConfigClient() {
    }

    public static List<IConfigBase> getServerConfigs() {
        return SERVER_CONFIGS;
    }

    public static List<IConfigBase> getUiConfigs() {
        return UI_CONFIGS;
    }

    public static List<IConfigBase> getPathConfigs() {
        return PATH_CONFIGS;
    }

    public static List<IConfigBase> getDownloadConfigs() {
        return DOWNLOAD_CONFIGS;
    }

    public static void loadFromJson(JsonObject root) {
        if (root == null) {
            return;
        }
        JsonObject server = asObject(root, "server");
        SERVER_SUPPORT.setBooleanValue(getBoolean(server, "serverSupport", SERVER_SUPPORT.getBooleanValue()));
        SERVER_PORT.setIntegerValue(getInt(server, "serverPort", SERVER_PORT.getIntegerValue()));
        SERVER_PASSWORD.setValueFromString(getString(server, "serverPassword", SERVER_PASSWORD.getStringValue()));

        JsonObject ui = asObject(root, "ui");
        UI_ENABLE_ALL.setBooleanValue(getBoolean(ui, "isEnableAllButtonShown", UI_ENABLE_ALL.getBooleanValue()));
        UI_UPDATE_MANAGER.setBooleanValue(getBoolean(ui, "isUpdateManagerOn", UI_UPDATE_MANAGER.getBooleanValue()));
        UI_SELF_UPDATE.setBooleanValue(getBoolean(ui, "isSelfUpdateAutoCheckOn", UI_SELF_UPDATE.getBooleanValue()));
        UI_PRESET.setBooleanValue(getBoolean(ui, "isPresetFuncOn", UI_PRESET.getBooleanValue()));

        JsonObject paths = asObject(root, "paths");
        DLC_ROOT.setValueFromString(getString(paths, "dlcRoot", DLC_ROOT.getStringValue()));
        TACZ_ROOT.setValueFromString(getString(paths, "taczRoot", TACZ_ROOT.getStringValue()));
        RESOURCEPACK_ROOT.setValueFromString(getString(paths, "resourcepackRoot", RESOURCEPACK_ROOT.getStringValue()));
        DATAPACK_ROOT.setValueFromString(getString(paths, "datapackRoot", DATAPACK_ROOT.getStringValue()));

        JsonObject downloads = asObject(root, "downloads");
        MAX_CONCURRENT.setIntegerValue(getInt(downloads, "maxConcurrentDownloads", MAX_CONCURRENT.getIntegerValue()));
        MAX_SEGMENTS.setIntegerValue(getInt(downloads, "maxSegmentsPerFile", MAX_SEGMENTS.getIntegerValue()));
        MIN_SEGMENT_MB.setIntegerValue(getInt(downloads, "minSegmentMB", MIN_SEGMENT_MB.getIntegerValue()));
        MAX_RETRY.setIntegerValue(getInt(downloads, "maxRetryCount", MAX_RETRY.getIntegerValue()));
        CONNECT_TIMEOUT.setIntegerValue(getInt(downloads, "connectTimeoutSeconds", CONNECT_TIMEOUT.getIntegerValue()));
        TASK_TIMEOUT.setIntegerValue(getInt(downloads, "taskTimeoutSeconds", TASK_TIMEOUT.getIntegerValue()));
    }

    public static JsonObject toJson() {
        JsonObject root = new JsonObject();
        JsonObject server = new JsonObject();
        server.addProperty("serverSupport", SERVER_SUPPORT.getBooleanValue());
        server.addProperty("serverPort", SERVER_PORT.getIntegerValue());
        server.addProperty("serverPassword", SERVER_PASSWORD.getStringValue());
        root.add("server", server);

        JsonObject ui = new JsonObject();
        ui.addProperty("isEnableAllButtonShown", UI_ENABLE_ALL.getBooleanValue());
        ui.addProperty("isUpdateManagerOn", UI_UPDATE_MANAGER.getBooleanValue());
        ui.addProperty("isSelfUpdateAutoCheckOn", UI_SELF_UPDATE.getBooleanValue());
        ui.addProperty("isPresetFuncOn", UI_PRESET.getBooleanValue());
        root.add("ui", ui);

        JsonObject paths = new JsonObject();
        paths.addProperty("dlcRoot", DLC_ROOT.getStringValue());
        paths.addProperty("taczRoot", TACZ_ROOT.getStringValue());
        paths.addProperty("resourcepackRoot", RESOURCEPACK_ROOT.getStringValue());
        paths.addProperty("datapackRoot", DATAPACK_ROOT.getStringValue());
        root.add("paths", paths);

        JsonObject downloads = new JsonObject();
        downloads.addProperty("maxConcurrentDownloads", MAX_CONCURRENT.getIntegerValue());
        downloads.addProperty("maxSegmentsPerFile", MAX_SEGMENTS.getIntegerValue());
        downloads.addProperty("minSegmentMB", MIN_SEGMENT_MB.getIntegerValue());
        downloads.addProperty("maxRetryCount", MAX_RETRY.getIntegerValue());
        downloads.addProperty("connectTimeoutSeconds", CONNECT_TIMEOUT.getIntegerValue());
        downloads.addProperty("taskTimeoutSeconds", TASK_TIMEOUT.getIntegerValue());
        root.add("downloads", downloads);

        return root;
    }

    private static JsonObject asObject(JsonObject root, String key) {
        if (root == null) {
            return new JsonObject();
        }
        JsonElement element = JsonCaseUtil.getIgnoreCase(root, key);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return new JsonObject();
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        JsonElement element = JsonCaseUtil.getIgnoreCase(obj, key);
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsBoolean();
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        JsonElement element = JsonCaseUtil.getIgnoreCase(obj, key);
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsInt();
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        JsonElement element = JsonCaseUtil.getIgnoreCase(obj, key);
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsString();
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static class DlcConfigString extends ConfigString {
        private final String translationKey;
        private final String commentKey;

        private DlcConfigString(String name, String defaultValue, String commentKey, String translationKey) {
            super(name, defaultValue, "");
            this.translationKey = translationKey;
            this.commentKey = commentKey;
        }

        @Override
        public String getConfigGuiDisplayName() {
            return fi.dy.masa.malilib.util.StringUtils.translate(this.translationKey);
        }

        @Override
        public String getComment() {
            if (commentKey == null || commentKey.isBlank()) {
                return "";
            }
            return fi.dy.masa.malilib.util.StringUtils.translate(commentKey);
        }
    }

    private static class DlcConfigBoolean extends ConfigBoolean {
        private final String translationKey;
        private final String commentKey;

        private DlcConfigBoolean(String name, boolean defaultValue, String commentKey, String translationKey) {
            super(name, defaultValue, "", name);
            this.translationKey = translationKey;
            this.commentKey = commentKey;
        }

        @Override
        public String getConfigGuiDisplayName() {
            return fi.dy.masa.malilib.util.StringUtils.translate(this.translationKey);
        }

        @Override
        public String getComment() {
            if (commentKey == null || commentKey.isBlank()) {
                return "";
            }
            return fi.dy.masa.malilib.util.StringUtils.translate(commentKey);
        }
    }

    private static class DlcConfigInteger extends ConfigInteger {
        private final String translationKey;
        private final String commentKey;

        private DlcConfigInteger(String name, int defaultValue, int min, int max, String commentKey, String translationKey) {
            super(name, defaultValue, min, max, "");
            this.translationKey = translationKey;
            this.commentKey = commentKey;
        }

        @Override
        public String getConfigGuiDisplayName() {
            return fi.dy.masa.malilib.util.StringUtils.translate(this.translationKey);
        }

        @Override
        public String getComment() {
            if (commentKey == null || commentKey.isBlank()) {
                return "";
            }
            return fi.dy.masa.malilib.util.StringUtils.translate(commentKey);
        }
    }
}
