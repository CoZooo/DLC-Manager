package com.cozooo.dlc_manager.client.config;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.util.StringUtils;
import java.util.List;

public final class DlcPathConfigClient {
    private static final ConfigString DLC_ROOT = new DlcConfigString("dlcRoot", DlcPathConfig.DEFAULT_DLC_ROOT, "dlc_manager.config.comment.paths.dlcRoot", "dlc_manager.config.paths.dlcRoot");
    private static final ConfigString TACZ_ROOT = new DlcConfigString("taczRoot", DlcPathConfig.DEFAULT_TACZ_DLC_ROOT, "dlc_manager.config.comment.paths.taczRoot", "dlc_manager.config.paths.taczRoot");
    private static final ConfigString RESOURCEPACK_ROOT = new DlcConfigString("resourcepackRoot", DlcPathConfig.DEFAULT_RESOURCEPACK_ROOT, "dlc_manager.config.comment.paths.resourcepackRoot", "dlc_manager.config.paths.resourcepackRoot");
    private static final ConfigString DATAPACK_ROOT = new DlcConfigString("datapackRoot", DlcPathConfig.DEFAULT_DATAPACK_ROOT, "dlc_manager.config.comment.paths.datapackRoot", "dlc_manager.config.paths.datapackRoot");

    private static final ConfigBoolean ENABLE_ALL_BUTTON = new DlcConfigBoolean("isEnableAllButtonShown", DlcPathConfig.DEFAULT_ENABLE_ALL_SHOWN, "dlc_manager.config.comment.ui.isEnableAllButtonShown", "dlc_manager.config.ui.isEnableAllButtonShown");
    private static final ConfigBoolean CONFIG_BUTTON = new DlcConfigBoolean("isConfigButtonShown", DlcPathConfig.DEFAULT_CONFIG_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isConfigButtonShown", "dlc_manager.config.ui.isConfigButtonShown");
    private static final ConfigBoolean U_BUTTON = new DlcConfigBoolean("isUButtomShown", DlcPathConfig.DEFAULT_U_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isUButtomShown", "dlc_manager.config.ui.isUButtomShown");
    private static final ConfigBoolean UPDATE_MANAGER_ON = new DlcConfigBoolean("isUpdateManagerOn", DlcPathConfig.DEFAULT_UPDATE_MANAGER_ON, "dlc_manager.config.comment.ui.isUpdateManagerOn", "dlc_manager.config.ui.isUpdateManagerOn");
    private static final ConfigBoolean SELF_UPDATE_AUTO_CHECK_ON = new DlcConfigBoolean("isSelfUpdateAutoCheckOn", DlcPathConfig.DEFAULT_SELF_UPDATE_AUTO_CHECK_ON, "dlc_manager.config.comment.ui.isSelfUpdateAutoCheckOn", "dlc_manager.config.ui.isSelfUpdateAutoCheckOn");
    private static final ConfigBoolean PRESET_FUNC_ON = new DlcConfigBoolean("isPresetFuncOn", DlcPathConfig.DEFAULT_PRESET_FUNC_ON, "dlc_manager.config.comment.ui.isPresetFuncOn", "dlc_manager.config.ui.isPresetFuncOn");
    private static final ConfigBoolean SERVER_SUPPORT_CLIENT = new DlcConfigBoolean("serverSupport", DlcPathConfig.DEFAULT_SERVER_SUPPORT, "dlc_manager.config.comment.ui.serverSupport", "dlc_manager.config.ui.serverSupport");
    private static final ConfigString TITLE_ANCHOR = new DlcConfigString("titleAnchor", DlcPathConfig.DEFAULT_TITLE_ANCHOR, "dlc_manager.config.comment.ui.titleAnchor", "dlc_manager.config.ui.titleAnchor");
    private static final ConfigInteger TITLE_OFFSET_X = new DlcConfigInteger("titleOffsetX", DlcPathConfig.DEFAULT_TITLE_OFFSET_X, -10000, 10000, "dlc_manager.config.comment.ui.titleOffsetX", "dlc_manager.config.ui.titleOffsetX");
    private static final ConfigInteger TITLE_OFFSET_Y = new DlcConfigInteger("titleOffsetY", DlcPathConfig.DEFAULT_TITLE_OFFSET_Y, -10000, 10000, "dlc_manager.config.comment.ui.titleOffsetY", "dlc_manager.config.ui.titleOffsetY");
    private static final ConfigInteger TITLE_WIDTH = new DlcConfigInteger("titleWidth", DlcPathConfig.DEFAULT_TITLE_WIDTH, 0, 10000, "dlc_manager.config.comment.ui.titleWidth", "dlc_manager.config.ui.titleWidth");
    private static final ConfigInteger TITLE_HEIGHT = new DlcConfigInteger("titleHeight", DlcPathConfig.DEFAULT_TITLE_HEIGHT, 0, 10000, "dlc_manager.config.comment.ui.titleHeight", "dlc_manager.config.ui.titleHeight");

    private static final ConfigInteger MAX_CONCURRENT_DOWNLOADS = new DlcConfigInteger("maxConcurrentDownloads", DlcPathConfig.DEFAULT_MAX_CONCURRENT_DOWNLOADS, 1, 64, "dlc_manager.config.comment.download.maxConcurrentDownloads", "dlc_manager.config.download.maxConcurrentDownloads");
    private static final ConfigInteger MAX_SEGMENTS_PER_FILE = new DlcConfigInteger("maxSegmentsPerFile", DlcPathConfig.DEFAULT_MAX_SEGMENTS_PER_FILE, 1, 64, "dlc_manager.config.comment.download.maxSegmentsPerFile", "dlc_manager.config.download.maxSegmentsPerFile");
    private static final ConfigInteger MIN_SEGMENT_MB = new DlcConfigInteger("minSegmentMB", DlcPathConfig.DEFAULT_MIN_SEGMENT_MB, 1, Integer.MAX_VALUE, "dlc_manager.config.comment.download.minSegmentMB", "dlc_manager.config.download.minSegmentMB");
    private static final ConfigInteger MAX_RETRY = new DlcConfigInteger("maxRetryCount", DlcPathConfig.DEFAULT_MAX_RETRY, 1, 100, "dlc_manager.config.comment.download.maxRetryCount", "dlc_manager.config.download.maxRetryCount");
    private static final ConfigInteger CONNECT_TIMEOUT_SECONDS = new DlcConfigInteger("connectTimeoutSeconds", DlcPathConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, 1, 36000, "dlc_manager.config.comment.download.connectTimeoutSeconds", "dlc_manager.config.download.connectTimeoutSeconds");
    private static final ConfigInteger TASK_TIMEOUT_SECONDS = new DlcConfigInteger("taskTimeoutSeconds", DlcPathConfig.DEFAULT_TASK_TIMEOUT_SECONDS, 1, 36000, "dlc_manager.config.comment.download.taskTimeoutSeconds", "dlc_manager.config.download.taskTimeoutSeconds");

    private static final List<IConfigBase> PATH_CONFIGS = List.of(
            DLC_ROOT,
            TACZ_ROOT,
            RESOURCEPACK_ROOT,
            DATAPACK_ROOT
    );

    private static final List<IConfigBase> UI_CONFIGS = List.of(
            ENABLE_ALL_BUTTON,
            CONFIG_BUTTON,
            U_BUTTON,
            UPDATE_MANAGER_ON,
            SELF_UPDATE_AUTO_CHECK_ON,
            PRESET_FUNC_ON,
            SERVER_SUPPORT_CLIENT,
            TITLE_ANCHOR,
            TITLE_OFFSET_X,
            TITLE_OFFSET_Y,
            TITLE_WIDTH,
            TITLE_HEIGHT
    );

    private static final List<IConfigBase> DOWNLOAD_CONFIGS = List.of(
            MAX_CONCURRENT_DOWNLOADS,
            MAX_SEGMENTS_PER_FILE,
            MIN_SEGMENT_MB,
            MAX_RETRY,
            CONNECT_TIMEOUT_SECONDS,
            TASK_TIMEOUT_SECONDS
    );

    private DlcPathConfigClient() {
    }

    public static List<IConfigBase> getPathConfigs() {
        return PATH_CONFIGS;
    }

    public static List<IConfigBase> getUiConfigs() {
        return UI_CONFIGS;
    }

    public static List<IConfigBase> getDownloadConfigs() {
        return DOWNLOAD_CONFIGS;
    }

    public static void syncFromConfig() {
        DLC_ROOT.setValueFromString(DlcPathConfig.getDlcRootRaw());
        TACZ_ROOT.setValueFromString(DlcPathConfig.getTaczRootRaw());
        RESOURCEPACK_ROOT.setValueFromString(DlcPathConfig.getResourcepackRootRaw());
        DATAPACK_ROOT.setValueFromString(DlcPathConfig.getDatapackRootRaw());

        ENABLE_ALL_BUTTON.setBooleanValue(DlcPathConfig.isEnableAllButtonShown());
        CONFIG_BUTTON.setBooleanValue(DlcPathConfig.isConfigButtonShown());
        U_BUTTON.setBooleanValue(DlcPathConfig.isUButtomShown());
        UPDATE_MANAGER_ON.setBooleanValue(DlcPathConfig.isUpdateManagerOn());
        SELF_UPDATE_AUTO_CHECK_ON.setBooleanValue(DlcPathConfig.isSelfUpdateAutoCheckOn());
        PRESET_FUNC_ON.setBooleanValue(DlcPathConfig.isPresetFuncOn());
        SERVER_SUPPORT_CLIENT.setBooleanValue(DlcPathConfig.isServerSupportOn());
        TITLE_ANCHOR.setValueFromString(DlcPathConfig.getTitleAnchor());
        TITLE_OFFSET_X.setIntegerValue(DlcPathConfig.getTitleOffsetX());
        TITLE_OFFSET_Y.setIntegerValue(DlcPathConfig.getTitleOffsetY());
        TITLE_WIDTH.setIntegerValue(DlcPathConfig.getTitleWidth());
        TITLE_HEIGHT.setIntegerValue(DlcPathConfig.getTitleHeight());

        MAX_CONCURRENT_DOWNLOADS.setIntegerValue(DlcPathConfig.getMaxConcurrentDownloads());
        MAX_SEGMENTS_PER_FILE.setIntegerValue(DlcPathConfig.getMaxSegmentsPerFile());
        MIN_SEGMENT_MB.setIntegerValue(toMb(DlcPathConfig.getMinSegmentBytes()));
        MAX_RETRY.setIntegerValue(DlcPathConfig.getMaxRetryCount());
        CONNECT_TIMEOUT_SECONDS.setIntegerValue(Math.max(1, DlcPathConfig.getConnectTimeoutMs() / 1000));
        TASK_TIMEOUT_SECONDS.setIntegerValue(Math.max(1, DlcPathConfig.getTaskTimeoutMs() / 1000));
    }

    public static void applyToConfig() {
        DlcPathConfig.setDlcRoot(DLC_ROOT.getStringValue());
        DlcPathConfig.setTaczRoot(TACZ_ROOT.getStringValue());
        DlcPathConfig.setResourcepackRoot(RESOURCEPACK_ROOT.getStringValue());
        DlcPathConfig.setDatapackRoot(DATAPACK_ROOT.getStringValue());

        DlcPathConfig.setEnableAllButtonShown(ENABLE_ALL_BUTTON.getBooleanValue());
        DlcPathConfig.setConfigButtonShown(CONFIG_BUTTON.getBooleanValue());
        DlcPathConfig.setUButtomShown(U_BUTTON.getBooleanValue());
        DlcPathConfig.setUpdateManagerOn(UPDATE_MANAGER_ON.getBooleanValue());
        DlcPathConfig.setSelfUpdateAutoCheckOn(SELF_UPDATE_AUTO_CHECK_ON.getBooleanValue());
        DlcPathConfig.setPresetFuncOn(PRESET_FUNC_ON.getBooleanValue());
        DlcPathConfig.setServerSupportOn(SERVER_SUPPORT_CLIENT.getBooleanValue());
        DlcPathConfig.setTitleAnchor(TITLE_ANCHOR.getStringValue());
        DlcPathConfig.setTitleOffsetX(TITLE_OFFSET_X.getIntegerValue());
        DlcPathConfig.setTitleOffsetY(TITLE_OFFSET_Y.getIntegerValue());
        DlcPathConfig.setTitleWidth(TITLE_WIDTH.getIntegerValue());
        DlcPathConfig.setTitleHeight(TITLE_HEIGHT.getIntegerValue());

        DlcPathConfig.setMaxConcurrentDownloads(MAX_CONCURRENT_DOWNLOADS.getIntegerValue());
        DlcPathConfig.setMaxSegmentsPerFile(MAX_SEGMENTS_PER_FILE.getIntegerValue());
        DlcPathConfig.setMinSegmentBytes((long) Math.max(1, MIN_SEGMENT_MB.getIntegerValue()) * 1024L * 1024L);
        DlcPathConfig.setMaxRetryCount(MAX_RETRY.getIntegerValue());
        DlcPathConfig.setConnectTimeoutMs(CONNECT_TIMEOUT_SECONDS.getIntegerValue() * 1000);
        DlcPathConfig.setTaskTimeoutMs(TASK_TIMEOUT_SECONDS.getIntegerValue() * 1000);
    }

    private static int toMb(long bytes) {
        long mb = (bytes + 1024L * 1024L - 1) / (1024L * 1024L);
        if (mb < 1) {
            return 1;
        }
        if (mb > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) mb;
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
            return StringUtils.translate(this.translationKey);
        }

        @Override
        public String getComment() {
            if (commentKey == null || commentKey.isBlank()) {
                return "";
            }
            return StringUtils.translate(commentKey);
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
            return StringUtils.translate(this.translationKey);
        }

        @Override
        public String getComment() {
            if (commentKey == null || commentKey.isBlank()) {
                return "";
            }
            return StringUtils.translate(commentKey);
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
            return StringUtils.translate(this.translationKey);
        }

        @Override
        public String getComment() {
            if (commentKey == null || commentKey.isBlank()) {
                return "";
            }
            return StringUtils.translate(commentKey);
        }
    }
}
