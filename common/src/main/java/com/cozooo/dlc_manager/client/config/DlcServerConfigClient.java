package com.cozooo.dlc_manager.client.config;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
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
    private static final ConfigInteger SERVER_PORT = new DlcConfigInteger("serverPort", DlcConfig.DEFAULT_SERVER_PORT, 1, 65535,
        "dlc_manager.config.comment.ui.serverPort", "dlc_manager.config.ui.serverPort");
    private static final ConfigString SERVER_PASSWORD = new DlcConfigString("serverPassword", "",
        "dlc_manager.config.comment.ui.serverPassword", "dlc_manager.config.ui.serverPassword");
    private static final ConfigString RCON_HOST = new DlcConfigString("rconHost", DlcConfig.DEFAULT_RCON_HOST,
        "dlc_manager.config.comment.ui.rconHost", "dlc_manager.config.ui.rconHost");
    private static final ConfigInteger RCON_PORT = new DlcConfigInteger("rconPort", DlcConfig.DEFAULT_RCON_PORT, 1, 65535,
        "dlc_manager.config.comment.ui.rconPort", "dlc_manager.config.ui.rconPort");
    private static final ConfigString RCON_PASSWORD = new DlcConfigString("rconPassword", DlcConfig.DEFAULT_RCON_PASSWORD,
        "dlc_manager.config.comment.ui.rconPassword", "dlc_manager.config.ui.rconPassword");
    private static final ConfigBoolean API_ENABLED = new DlcConfigBoolean("apiEnabled", DlcConfig.DEFAULT_API_ENABLED,
        "dlc_manager.config.comment.ui.apiEnabled", "dlc_manager.config.ui.apiEnabled");
    private static final ConfigInteger API_PORT = new DlcConfigInteger("apiPort", DlcConfig.DEFAULT_API_PORT, 0, 65535,
        "dlc_manager.config.comment.ui.apiPort", "dlc_manager.config.ui.apiPort");
    private static final ConfigString API_TOKEN = new DlcConfigString("apiToken", "",
        "dlc_manager.config.comment.ui.apiToken", "dlc_manager.config.ui.apiToken");
    private static final ConfigBoolean UI_ENABLE_ALL = new DlcConfigBoolean("isEnableAllButtonShown", DlcConfig.DEFAULT_ENABLE_ALL_SHOWN,
        "dlc_manager.config.comment.ui.isEnableAllButtonShown", "dlc_manager.config.ui.isEnableAllButtonShown");
    private static final ConfigBoolean UI_UPDATE_MANAGER = new DlcConfigBoolean("isUpdateManagerOn", DlcConfig.DEFAULT_UPDATE_MANAGER_ON,
        "dlc_manager.config.comment.ui.isUpdateManagerOn", "dlc_manager.config.ui.isUpdateManagerOn");
    private static final ConfigBoolean UI_SELF_UPDATE = new DlcConfigBoolean("isSelfUpdateAutoCheckOn", DlcConfig.DEFAULT_SELF_UPDATE_AUTO_CHECK_ON,
        "dlc_manager.config.comment.ui.isSelfUpdateAutoCheckOn", "dlc_manager.config.ui.isSelfUpdateAutoCheckOn");
    private static final ConfigBoolean UI_PRESET = new DlcConfigBoolean("isPresetFuncOn", DlcConfig.DEFAULT_PRESET_FUNC_ON,
        "dlc_manager.config.comment.ui.isPresetFuncOn", "dlc_manager.config.ui.isPresetFuncOn");
    private static final ConfigInteger UI_WAITING_TIME = new DlcConfigInteger("WaitingTime", DlcConfig.DEFAULT_ALARM_WAITING_TIME, 0, 10,
        "dlc_manager.config.comment.ui.WaitingTime", "dlc_manager.config.ui.WaitingTime");
    private static final ConfigString MODPACK = new DlcConfigString("modpack", DlcConfig.DEFAULT_MODPACK,
        "dlc_manager.config.comment.modpack.modpack", "dlc_manager.config.modpack.modpack");
    private static final ConfigString MODPACK_URL = new DlcConfigString("modpackUrl", DlcConfig.DEFAULT_MODPACK_URL,
        "dlc_manager.config.comment.modpack.modpackUrl", "dlc_manager.config.modpack.modpackUrl");
    private static final ConfigString UPDATE_GIT_REPO = new DlcConfigString("updateGitRepo", DlcConfig.DEFAULT_UPDATE_GIT_REPO_URL,
        "dlc_manager.config.comment.modpack.updateGitRepo", "dlc_manager.config.modpack.updateGitRepo");

    private static final ConfigString DLC_ROOT = new DlcConfigString("dlcRoot", DlcConfig.DEFAULT_DLC_ROOT,
        "dlc_manager.config.comment.paths.dlcRoot", "dlc_manager.config.paths.dlcRoot");
    private static final ConfigString TACZ_ROOT = new DlcConfigString("taczRoot", DlcConfig.DEFAULT_TACZ_DLC_ROOT,
        "dlc_manager.config.comment.paths.taczRoot", "dlc_manager.config.paths.taczRoot");
    private static final ConfigString RESOURCEPACK_ROOT = new DlcConfigString("resourcepackRoot", DlcConfig.DEFAULT_RESOURCEPACK_ROOT,
        "dlc_manager.config.comment.paths.resourcepackRoot", "dlc_manager.config.paths.resourcepackRoot");
    private static final ConfigString DATAPACK_ROOT = new DlcConfigString("datapackRoot", DlcConfig.DEFAULT_DATAPACK_ROOT,
        "dlc_manager.config.comment.paths.datapackRoot", "dlc_manager.config.paths.datapackRoot");

    private static final ConfigInteger MAX_CONCURRENT = new DlcConfigInteger("maxConcurrentDownloads", DlcConfig.DEFAULT_MAX_CONCURRENT_DOWNLOADS, 1, 64,
        "dlc_manager.config.comment.download.maxConcurrentDownloads", "dlc_manager.config.download.maxConcurrentDownloads");
    private static final ConfigInteger MAX_SEGMENTS = new DlcConfigInteger("maxSegmentsPerFile", DlcConfig.DEFAULT_MAX_SEGMENTS_PER_FILE, 1, 64,
        "dlc_manager.config.comment.download.maxSegmentsPerFile", "dlc_manager.config.download.maxSegmentsPerFile");
    private static final ConfigInteger MIN_SEGMENT_MB = new DlcConfigInteger("minSegmentMB", DlcConfig.DEFAULT_MIN_SEGMENT_MB, 1, Integer.MAX_VALUE,
        "dlc_manager.config.comment.download.minSegmentMB", "dlc_manager.config.download.minSegmentMB");
    private static final ConfigInteger MAX_RETRY = new DlcConfigInteger("maxRetryCount", DlcConfig.DEFAULT_MAX_RETRY, 1, 100,
        "dlc_manager.config.comment.download.maxRetryCount", "dlc_manager.config.download.maxRetryCount");
    private static final ConfigInteger CONNECT_TIMEOUT = new DlcConfigInteger("connectTimeoutSeconds", DlcConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, 1, 36000,
        "dlc_manager.config.comment.download.connectTimeoutSeconds", "dlc_manager.config.download.connectTimeoutSeconds");
    private static final ConfigInteger TASK_TIMEOUT = new DlcConfigInteger("taskTimeoutSeconds", DlcConfig.DEFAULT_TASK_TIMEOUT_SECONDS, 1, 36000,
        "dlc_manager.config.comment.download.taskTimeoutSeconds", "dlc_manager.config.download.taskTimeoutSeconds");
    private static final ConfigString DOWNLOAD_MIRROR_MODE = new DlcConfigString("downloadMirrorMode", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_MODE,
        "dlc_manager.config.comment.download.downloadMirrorMode", "dlc_manager.config.download.downloadMirrorMode");
    private static final ConfigInteger DOWNLOAD_MIRROR_PROBE_COUNT = new DlcConfigInteger("downloadMirrorProbeCount", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_COUNT, 1, 10,
        "dlc_manager.config.comment.download.downloadMirrorProbeCount", "dlc_manager.config.download.downloadMirrorProbeCount");
    private static final ConfigString DOWNLOAD_MIRROR_PROBE_STRATEGY = new DlcConfigString("downloadMirrorProbeStrategy", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY,
        "dlc_manager.config.comment.download.downloadMirrorProbeStrategy", "dlc_manager.config.download.downloadMirrorProbeStrategy");

    private static final List<IConfigBase> SERVER_CONFIGS = List.of(
        SERVER_SUPPORT,
        SERVER_PORT,
        SERVER_PASSWORD,
        RCON_HOST,
        RCON_PORT,
        RCON_PASSWORD,
        API_ENABLED,
        API_PORT,
        API_TOKEN
    );

    private static final List<IConfigBase> MODPACK_CONFIGS = List.of(
        MODPACK,
        MODPACK_URL,
        UPDATE_GIT_REPO
    );

    private static final List<IConfigBase> UI_CONFIGS = List.of(
        UI_ENABLE_ALL,
        UI_UPDATE_MANAGER,
        UI_SELF_UPDATE,
        UI_PRESET,
        UI_WAITING_TIME
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
        TASK_TIMEOUT,
        DOWNLOAD_MIRROR_MODE,
        DOWNLOAD_MIRROR_PROBE_COUNT,
        DOWNLOAD_MIRROR_PROBE_STRATEGY
    );

    private DlcServerConfigClient() {
    }

    public static List<IConfigBase> getServerConfigs() {
        return SERVER_CONFIGS;
    }

    public static List<IConfigBase> getUiConfigs() {
        return UI_CONFIGS;
    }

    public static List<IConfigBase> getModpackConfigs() {
        return MODPACK_CONFIGS;
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
        RCON_HOST.setValueFromString(getString(server, "rconHost", RCON_HOST.getStringValue()));
        RCON_PORT.setIntegerValue(getInt(server, "rconPort", RCON_PORT.getIntegerValue()));
        RCON_PASSWORD.setValueFromString(getString(server, "rconPassword", RCON_PASSWORD.getStringValue()));

        JsonObject api = asObject(root, "api");
        API_ENABLED.setBooleanValue(getBoolean(api, "apiEnabled", API_ENABLED.getBooleanValue()));
        API_PORT.setIntegerValue(getInt(api, "apiPort", API_PORT.getIntegerValue()));
        API_TOKEN.setValueFromString(getString(api, "apiToken", API_TOKEN.getStringValue()));

        JsonObject ui = asObject(root, "ui");
        UI_ENABLE_ALL.setBooleanValue(getBoolean(ui, "isEnableAllButtonShown", UI_ENABLE_ALL.getBooleanValue()));
        UI_UPDATE_MANAGER.setBooleanValue(getBoolean(ui, "isUpdateManagerOn", UI_UPDATE_MANAGER.getBooleanValue()));
        UI_SELF_UPDATE.setBooleanValue(getBoolean(ui, "isSelfUpdateAutoCheckOn", UI_SELF_UPDATE.getBooleanValue()));
        UI_PRESET.setBooleanValue(getBoolean(ui, "isPresetFuncOn", UI_PRESET.getBooleanValue()));
        UI_WAITING_TIME.setIntegerValue(getInt(ui, "WaitingTime", UI_WAITING_TIME.getIntegerValue()));

        JsonObject modpack = asObject(root, "modpack");
        MODPACK.setValueFromString(getString(modpack, "modpack", MODPACK.getStringValue()));
        MODPACK_URL.setValueFromString(getString(modpack, "modpackUrl", MODPACK_URL.getStringValue()));
        UPDATE_GIT_REPO.setValueFromString(getString(modpack, "updateGitRepo", getString(modpack, "updateGithubRepo", UPDATE_GIT_REPO.getStringValue())));

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
        DOWNLOAD_MIRROR_MODE.setValueFromString(DlcConfig.normalizeDownloadMirrorMode(getString(downloads, "downloadMirrorMode", DOWNLOAD_MIRROR_MODE.getStringValue())));
        DOWNLOAD_MIRROR_PROBE_COUNT.setIntegerValue(getInt(downloads, "downloadMirrorProbeCount", DOWNLOAD_MIRROR_PROBE_COUNT.getIntegerValue()));
        DOWNLOAD_MIRROR_PROBE_STRATEGY.setValueFromString(DlcConfig.normalizeDownloadMirrorProbeStrategy(getString(downloads, "downloadMirrorProbeStrategy", DOWNLOAD_MIRROR_PROBE_STRATEGY.getStringValue())));
    }

    public static JsonObject toJson() {
        JsonObject root = new JsonObject();
        JsonObject server = new JsonObject();
        server.addProperty("serverSupport", SERVER_SUPPORT.getBooleanValue());
        server.addProperty("serverPort", SERVER_PORT.getIntegerValue());
        server.addProperty("serverPassword", SERVER_PASSWORD.getStringValue());
        server.addProperty("rconHost", RCON_HOST.getStringValue());
        server.addProperty("rconPort", RCON_PORT.getIntegerValue());
        server.addProperty("rconPassword", RCON_PASSWORD.getStringValue());
        root.add("server", server);

        JsonObject api = new JsonObject();
        api.addProperty("apiEnabled", API_ENABLED.getBooleanValue());
        api.addProperty("apiPort", API_PORT.getIntegerValue());
        api.addProperty("apiToken", API_TOKEN.getStringValue());
        root.add("api", api);

        JsonObject ui = new JsonObject();
        ui.addProperty("isEnableAllButtonShown", UI_ENABLE_ALL.getBooleanValue());
        ui.addProperty("isUpdateManagerOn", UI_UPDATE_MANAGER.getBooleanValue());
        ui.addProperty("isSelfUpdateAutoCheckOn", UI_SELF_UPDATE.getBooleanValue());
        ui.addProperty("isPresetFuncOn", UI_PRESET.getBooleanValue());
        ui.addProperty("WaitingTime", UI_WAITING_TIME.getIntegerValue());
        root.add("ui", ui);

        JsonObject modpack = new JsonObject();
        modpack.addProperty("modpack", MODPACK.getStringValue());
        modpack.addProperty("modpackUrl", MODPACK_URL.getStringValue());
        modpack.addProperty("updateGitRepo", UPDATE_GIT_REPO.getStringValue());
        root.add("modpack", modpack);

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
        downloads.addProperty("downloadMirrorMode", DlcConfig.normalizeDownloadMirrorMode(DOWNLOAD_MIRROR_MODE.getStringValue()));
        downloads.addProperty("downloadMirrorProbeCount", Math.max(1, Math.min(10, DOWNLOAD_MIRROR_PROBE_COUNT.getIntegerValue())));
        downloads.addProperty("downloadMirrorProbeStrategy", DlcConfig.normalizeDownloadMirrorProbeStrategy(DOWNLOAD_MIRROR_PROBE_STRATEGY.getStringValue()));
        root.add("downloads", downloads);

        return root;
    }

    private static JsonObject asObject(JsonObject root, String key) {
        if (root == null) {
            return new JsonObject();
        }
        JsonElement element = DlcDcConfigIO.getIgnoreCase(root, key);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return new JsonObject();
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        JsonElement element = DlcDcConfigIO.getIgnoreCase(obj, key);
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
        JsonElement element = DlcDcConfigIO.getIgnoreCase(obj, key);
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
        JsonElement element = DlcDcConfigIO.getIgnoreCase(obj, key);
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
