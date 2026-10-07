package com.cozooo.dlc_manager.client.config;

import com.cozooo.dlc_manager.core.DlcConfig;
import java.util.List;

public final class DlcConfigClient {
    private static final DlcConfigValue.StringValue DLC_ROOT = new DlcConfigValue.StringValue("dlcRoot", DlcConfig.DEFAULT_DLC_ROOT, "dlc_manager.config.comment.paths.dlcRoot", "dlc_manager.config.paths.dlcRoot");
    private static final DlcConfigValue.StringValue TACZ_ROOT = new DlcConfigValue.StringValue("taczRoot", DlcConfig.DEFAULT_TACZ_DLC_ROOT, "dlc_manager.config.comment.paths.taczRoot", "dlc_manager.config.paths.taczRoot");
    private static final DlcConfigValue.StringValue RESOURCEPACK_ROOT = new DlcConfigValue.StringValue("resourcepackRoot", DlcConfig.DEFAULT_RESOURCEPACK_ROOT, "dlc_manager.config.comment.paths.resourcepackRoot", "dlc_manager.config.paths.resourcepackRoot");
    private static final DlcConfigValue.StringValue DATAPACK_ROOT = new DlcConfigValue.StringValue("datapackRoot", DlcConfig.DEFAULT_DATAPACK_ROOT, "dlc_manager.config.comment.paths.datapackRoot", "dlc_manager.config.paths.datapackRoot");

    private static final DlcConfigValue.BooleanValue ENABLE_ALL_BUTTON = new DlcConfigValue.BooleanValue("isEnableAllButtonShown", DlcConfig.DEFAULT_ENABLE_ALL_SHOWN, "dlc_manager.config.comment.ui.isEnableAllButtonShown", "dlc_manager.config.ui.isEnableAllButtonShown");
    private static final DlcConfigValue.BooleanValue CONFIG_BUTTON = new DlcConfigValue.BooleanValue("isConfigButtonShown", DlcConfig.DEFAULT_CONFIG_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isConfigButtonShown", "dlc_manager.config.ui.isConfigButtonShown");
    private static final DlcConfigValue.BooleanValue U_BUTTON = new DlcConfigValue.BooleanValue("isUButtomShown", DlcConfig.DEFAULT_U_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isUButtomShown", "dlc_manager.config.ui.isUButtomShown");
    private static final DlcConfigValue.BooleanValue UPDATE_MANAGER_ON = new DlcConfigValue.BooleanValue("isUpdateManagerOn", DlcConfig.DEFAULT_UPDATE_MANAGER_ON, "dlc_manager.config.comment.ui.isUpdateManagerOn", "dlc_manager.config.ui.isUpdateManagerOn");
    private static final DlcConfigValue.BooleanValue SELF_UPDATE_AUTO_CHECK_ON = new DlcConfigValue.BooleanValue("isSelfUpdateAutoCheckOn", DlcConfig.DEFAULT_SELF_UPDATE_AUTO_CHECK_ON, "dlc_manager.config.comment.ui.isSelfUpdateAutoCheckOn", "dlc_manager.config.ui.isSelfUpdateAutoCheckOn");
    private static final DlcConfigValue.BooleanValue PRESET_FUNC_ON = new DlcConfigValue.BooleanValue("isPresetFuncOn", DlcConfig.DEFAULT_PRESET_FUNC_ON, "dlc_manager.config.comment.ui.isPresetFuncOn", "dlc_manager.config.ui.isPresetFuncOn");
    private static final DlcConfigValue.BooleanValue PACK_FUNC_ON = new DlcConfigValue.BooleanValue("isPackFuncOn", DlcConfig.DEFAULT_PACK_FUNC_ON, "dlc_manager.config.comment.ui.isPackFuncOn", "dlc_manager.config.ui.isPackFuncOn");
    private static final DlcConfigValue.BooleanValue P_BUTTON = new DlcConfigValue.BooleanValue("isPButtomShown", DlcConfig.DEFAULT_P_BUTTON_SHOWN, "dlc_manager.config.comment.ui.isPButtomShown", "dlc_manager.config.ui.isPButtomShown");
    private static final DlcConfigValue.BooleanValue SHOW_ON_FIRST_RUN = new DlcConfigValue.BooleanValue("ShowOnFirstRun", DlcConfig.DEFAULT_SHOW_ON_FIRST_RUN, "dlc_manager.config.comment.ui.ShowOnFirstRun", "dlc_manager.config.ui.ShowOnFirstRun");
    private static final DlcConfigValue.IntegerValue ALARM_WAITING_TIME = new DlcConfigValue.IntegerValue("WaitingTime", DlcConfig.DEFAULT_ALARM_WAITING_TIME, 0, 10, "dlc_manager.config.comment.ui.WaitingTime", "dlc_manager.config.ui.WaitingTime");
    private static final DlcConfigValue.BooleanValue SERVER_SUPPORT_CLIENT = new DlcConfigValue.BooleanValue("serverSupport", DlcConfig.DEFAULT_SERVER_SUPPORT, "dlc_manager.config.comment.ui.serverSupport", "dlc_manager.config.ui.serverSupport");
    private static final DlcConfigValue.BooleanValue API_ENABLED = new DlcConfigValue.BooleanValue("apiEnabled", DlcConfig.DEFAULT_API_ENABLED, "dlc_manager.config.comment.ui.apiEnabled", "dlc_manager.config.ui.apiEnabled");
    private static final DlcConfigValue.IntegerValue API_PORT = new DlcConfigValue.IntegerValue("apiPort", DlcConfig.DEFAULT_API_PORT, 0, 65535, "dlc_manager.config.comment.ui.apiPort", "dlc_manager.config.ui.apiPort");
    private static final DlcConfigValue.StringValue API_TOKEN = new DlcConfigValue.StringValue("apiToken", DlcConfig.DEFAULT_API_TOKEN, "dlc_manager.config.comment.ui.apiToken", "dlc_manager.config.ui.apiToken");
    private static final DlcConfigValue.StringValue NOTICE_DISPLAY_MODE = new DlcConfigValue.StringValue("noticeDisplayMode", DlcConfig.DEFAULT_NOTICE_DISPLAY_MODE, "dlc_manager.config.comment.ui.noticeDisplayMode", "dlc_manager.config.ui.noticeDisplayMode");
    private static final DlcConfigValue.StringValue NOTICE_BUTTON_ANCHOR = new DlcConfigValue.StringValue("noticeButtonAnchor", DlcConfig.DEFAULT_NOTICE_BUTTON_ANCHOR, "dlc_manager.config.comment.ui.noticeButtonAnchor", "dlc_manager.config.ui.noticeButtonAnchor");
    private static final DlcConfigValue.IntegerValue NOTICE_BUTTON_OFFSET_X = new DlcConfigValue.IntegerValue("noticeButtonOffsetX", DlcConfig.DEFAULT_NOTICE_BUTTON_OFFSET_X, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticeButtonOffsetX", "dlc_manager.config.ui.noticeButtonOffsetX");
    private static final DlcConfigValue.IntegerValue NOTICE_BUTTON_OFFSET_Y = new DlcConfigValue.IntegerValue("noticeButtonOffsetY", DlcConfig.DEFAULT_NOTICE_BUTTON_OFFSET_Y, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticeButtonOffsetY", "dlc_manager.config.ui.noticeButtonOffsetY");
    private static final DlcConfigValue.StringValue NOTICE_PANEL_ANCHOR = new DlcConfigValue.StringValue("noticePanelAnchor", DlcConfig.DEFAULT_NOTICE_PANEL_ANCHOR, "dlc_manager.config.comment.ui.noticePanelAnchor", "dlc_manager.config.ui.noticePanelAnchor");
    private static final DlcConfigValue.IntegerValue NOTICE_PANEL_OFFSET_X = new DlcConfigValue.IntegerValue("noticePanelOffsetX", DlcConfig.DEFAULT_NOTICE_PANEL_OFFSET_X, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticePanelOffsetX", "dlc_manager.config.ui.noticePanelOffsetX");
    private static final DlcConfigValue.IntegerValue NOTICE_PANEL_OFFSET_Y = new DlcConfigValue.IntegerValue("noticePanelOffsetY", DlcConfig.DEFAULT_NOTICE_PANEL_OFFSET_Y, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, "dlc_manager.config.comment.ui.noticePanelOffsetY", "dlc_manager.config.ui.noticePanelOffsetY");
    private static final DlcConfigValue.IntegerValue NOTICE_PANEL_WIDTH = new DlcConfigValue.IntegerValue("noticePanelWidth", DlcConfig.DEFAULT_NOTICE_PANEL_WIDTH, 0, DlcConfig.MAX_NOTICE_PANEL_SIZE, "dlc_manager.config.comment.ui.noticePanelWidth", "dlc_manager.config.ui.noticePanelWidth");
    private static final DlcConfigValue.IntegerValue NOTICE_PANEL_HEIGHT = new DlcConfigValue.IntegerValue("noticePanelHeight", DlcConfig.DEFAULT_NOTICE_PANEL_HEIGHT, 0, DlcConfig.MAX_NOTICE_PANEL_SIZE, "dlc_manager.config.comment.ui.noticePanelHeight", "dlc_manager.config.ui.noticePanelHeight");
    private static final DlcConfigValue.StringValue TITLE_ANCHOR = new DlcConfigValue.StringValue("titleAnchor", DlcConfig.DEFAULT_TITLE_ANCHOR, "dlc_manager.config.comment.ui.titleAnchor", "dlc_manager.config.ui.titleAnchor");
    private static final DlcConfigValue.IntegerValue TITLE_OFFSET_X = new DlcConfigValue.IntegerValue("titleOffsetX", DlcConfig.DEFAULT_TITLE_OFFSET_X, -10000, 10000, "dlc_manager.config.comment.ui.titleOffsetX", "dlc_manager.config.ui.titleOffsetX");
    private static final DlcConfigValue.IntegerValue TITLE_OFFSET_Y = new DlcConfigValue.IntegerValue("titleOffsetY", DlcConfig.DEFAULT_TITLE_OFFSET_Y, -10000, 10000, "dlc_manager.config.comment.ui.titleOffsetY", "dlc_manager.config.ui.titleOffsetY");
    private static final DlcConfigValue.StringValue TITLE_UPDATE_ANCHOR = new DlcConfigValue.StringValue("titleUpdateAnchor", DlcConfig.DEFAULT_TITLE_UPDATE_ANCHOR, "dlc_manager.config.comment.ui.titleUpdateAnchor", "dlc_manager.config.ui.titleUpdateAnchor");
    private static final DlcConfigValue.IntegerValue TITLE_UPDATE_OFFSET_X = new DlcConfigValue.IntegerValue("titleUpdateOffsetX", DlcConfig.DEFAULT_TITLE_UPDATE_OFFSET_X, -10000, 10000, "dlc_manager.config.comment.ui.titleUpdateOffsetX", "dlc_manager.config.ui.titleUpdateOffsetX");
    private static final DlcConfigValue.IntegerValue TITLE_UPDATE_OFFSET_Y = new DlcConfigValue.IntegerValue("titleUpdateOffsetY", DlcConfig.DEFAULT_TITLE_UPDATE_OFFSET_Y, -10000, 10000, "dlc_manager.config.comment.ui.titleUpdateOffsetY", "dlc_manager.config.ui.titleUpdateOffsetY");
    private static final DlcConfigValue.StringValue MODPACK = new DlcConfigValue.StringValue("modpack", DlcConfig.DEFAULT_MODPACK, "dlc_manager.config.comment.modpack.modpack", "dlc_manager.config.modpack.modpack");
    private static final DlcConfigValue.StringValue MODPACK_URL = new DlcConfigValue.StringValue("modpackUrl", DlcConfig.DEFAULT_MODPACK_URL, "dlc_manager.config.comment.modpack.modpackUrl", "dlc_manager.config.modpack.modpackUrl");
    private static final DlcConfigValue.StringValue UPDATE_GIT_REPO = new DlcConfigValue.StringValue("updateGitRepo", DlcConfig.DEFAULT_UPDATE_GIT_REPO_URL, "dlc_manager.config.comment.modpack.updateGitRepo", "dlc_manager.config.modpack.updateGitRepo");

    private static final DlcConfigValue.IntegerValue MAX_CONCURRENT_DOWNLOADS = new DlcConfigValue.IntegerValue("maxConcurrentDownloads", DlcConfig.DEFAULT_MAX_CONCURRENT_DOWNLOADS, 1, 64, "dlc_manager.config.comment.download.maxConcurrentDownloads", "dlc_manager.config.download.maxConcurrentDownloads");
    private static final DlcConfigValue.IntegerValue MAX_SEGMENTS_PER_FILE = new DlcConfigValue.IntegerValue("maxSegmentsPerFile", DlcConfig.DEFAULT_MAX_SEGMENTS_PER_FILE, 1, 64, "dlc_manager.config.comment.download.maxSegmentsPerFile", "dlc_manager.config.download.maxSegmentsPerFile");
    private static final DlcConfigValue.IntegerValue MIN_SEGMENT_MB = new DlcConfigValue.IntegerValue("minSegmentMB", DlcConfig.DEFAULT_MIN_SEGMENT_MB, 1, Integer.MAX_VALUE, "dlc_manager.config.comment.download.minSegmentMB", "dlc_manager.config.download.minSegmentMB");
    private static final DlcConfigValue.IntegerValue MAX_RETRY = new DlcConfigValue.IntegerValue("maxRetryCount", DlcConfig.DEFAULT_MAX_RETRY, 1, 100, "dlc_manager.config.comment.download.maxRetryCount", "dlc_manager.config.download.maxRetryCount");
    private static final DlcConfigValue.IntegerValue CONNECT_TIMEOUT_SECONDS = new DlcConfigValue.IntegerValue("connectTimeoutSeconds", DlcConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, 1, 36000, "dlc_manager.config.comment.download.connectTimeoutSeconds", "dlc_manager.config.download.connectTimeoutSeconds");
    private static final DlcConfigValue.IntegerValue TASK_TIMEOUT_SECONDS = new DlcConfigValue.IntegerValue("taskTimeoutSeconds", DlcConfig.DEFAULT_TASK_TIMEOUT_SECONDS, 1, 36000, "dlc_manager.config.comment.download.taskTimeoutSeconds", "dlc_manager.config.download.taskTimeoutSeconds");
    private static final DlcConfigValue.StringValue DOWNLOAD_MIRROR_MODE = new DlcConfigValue.StringValue("downloadMirrorMode", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_MODE, "dlc_manager.config.comment.download.downloadMirrorMode", "dlc_manager.config.download.downloadMirrorMode");
    private static final DlcConfigValue.IntegerValue DOWNLOAD_MIRROR_PROBE_COUNT = new DlcConfigValue.IntegerValue("downloadMirrorProbeCount", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_COUNT, 1, 10, "dlc_manager.config.comment.download.downloadMirrorProbeCount", "dlc_manager.config.download.downloadMirrorProbeCount");
    private static final DlcConfigValue.StringValue DOWNLOAD_MIRROR_PROBE_STRATEGY = new DlcConfigValue.StringValue("downloadMirrorProbeStrategy", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY, "dlc_manager.config.comment.download.downloadMirrorProbeStrategy", "dlc_manager.config.download.downloadMirrorProbeStrategy");

    private static final List<DlcConfigValue> PATH_CONFIGS = List.of(
            DLC_ROOT,
            TACZ_ROOT,
            RESOURCEPACK_ROOT,
            DATAPACK_ROOT
    );

    private static final List<DlcConfigValue> UI_CONFIGS = List.of(
            ENABLE_ALL_BUTTON,
            CONFIG_BUTTON,
            U_BUTTON,
            UPDATE_MANAGER_ON,
            SELF_UPDATE_AUTO_CHECK_ON,
            PRESET_FUNC_ON,
            PACK_FUNC_ON,
            P_BUTTON,
            SHOW_ON_FIRST_RUN,
            ALARM_WAITING_TIME,
            NOTICE_DISPLAY_MODE,
            NOTICE_BUTTON_ANCHOR,
            NOTICE_BUTTON_OFFSET_X,
            NOTICE_BUTTON_OFFSET_Y,
            NOTICE_PANEL_ANCHOR,
            NOTICE_PANEL_OFFSET_X,
            NOTICE_PANEL_OFFSET_Y,
            NOTICE_PANEL_WIDTH,
            NOTICE_PANEL_HEIGHT,
            TITLE_ANCHOR,
            TITLE_OFFSET_X,
            TITLE_OFFSET_Y,
            TITLE_UPDATE_ANCHOR,
            TITLE_UPDATE_OFFSET_X,
            TITLE_UPDATE_OFFSET_Y
    );

    private static final List<DlcConfigValue> MODPACK_CONFIGS = List.of(
            MODPACK,
            MODPACK_URL,
            UPDATE_GIT_REPO
    );

    private static final List<DlcConfigValue> SERVER_CONFIGS = List.of(
            SERVER_SUPPORT_CLIENT,
            API_ENABLED,
            API_PORT,
            API_TOKEN
    );

    private static final List<DlcConfigValue> DOWNLOAD_CONFIGS = List.of(
            MAX_CONCURRENT_DOWNLOADS,
            MAX_SEGMENTS_PER_FILE,
            MIN_SEGMENT_MB,
            MAX_RETRY,
            CONNECT_TIMEOUT_SECONDS,
            TASK_TIMEOUT_SECONDS,
            DOWNLOAD_MIRROR_MODE,
            DOWNLOAD_MIRROR_PROBE_COUNT,
            DOWNLOAD_MIRROR_PROBE_STRATEGY
    );

    private DlcConfigClient() {
    }

    public static List<DlcConfigValue> getPathConfigs() {
        return PATH_CONFIGS;
    }

    public static List<DlcConfigValue> getUiConfigs() {
        return UI_CONFIGS;
    }

    public static List<DlcConfigValue> getModpackConfigs() {
        return MODPACK_CONFIGS;
    }

    public static List<DlcConfigValue> getServerConfigs() {
        return SERVER_CONFIGS;
    }

    public static List<DlcConfigValue> getDownloadConfigs() {
        return DOWNLOAD_CONFIGS;
    }

    public static void syncFromConfig() {
        DLC_ROOT.setValueFromString(DlcConfig.getDlcRootRaw());
        TACZ_ROOT.setValueFromString(DlcConfig.getTaczRootRaw());
        RESOURCEPACK_ROOT.setValueFromString(DlcConfig.getResourcepackRootRaw());
        DATAPACK_ROOT.setValueFromString(DlcConfig.getDatapackRootRaw());

        ENABLE_ALL_BUTTON.setBooleanValue(DlcConfig.isEnableAllButtonShown());
        CONFIG_BUTTON.setBooleanValue(DlcConfig.isConfigButtonShown());
        U_BUTTON.setBooleanValue(DlcConfig.isUButtomShown());
        UPDATE_MANAGER_ON.setBooleanValue(DlcConfig.isUpdateManagerOn());
        SELF_UPDATE_AUTO_CHECK_ON.setBooleanValue(DlcConfig.isSelfUpdateAutoCheckOn());
        PRESET_FUNC_ON.setBooleanValue(DlcConfig.isPresetFuncOn());
        PACK_FUNC_ON.setBooleanValue(DlcConfig.isPackFuncOn());
        P_BUTTON.setBooleanValue(DlcConfig.isPButtomShown());
        SHOW_ON_FIRST_RUN.setBooleanValue(DlcConfig.isShowOnFirstRun());
        ALARM_WAITING_TIME.setIntegerValue(DlcConfig.getAlarmWaitingTime());
        SERVER_SUPPORT_CLIENT.setBooleanValue(DlcConfig.isServerSupportOn());
        MODPACK.setValueFromString(DlcConfig.getModpack());
        MODPACK_URL.setValueFromString(DlcConfig.getModpackUrl());
        UPDATE_GIT_REPO.setValueFromString(DlcConfig.getUpdateGitRepoUrl());
        API_ENABLED.setBooleanValue(DlcConfig.isApiEnabled());
        API_PORT.setIntegerValue(DlcConfig.getApiPort());
        API_TOKEN.setValueFromString(DlcConfig.getApiToken());
        NOTICE_DISPLAY_MODE.setValueFromString(DlcConfig.getNoticeDisplayMode());
        NOTICE_BUTTON_ANCHOR.setValueFromString(DlcConfig.getNoticeButtonAnchor());
        NOTICE_BUTTON_OFFSET_X.setIntegerValue(DlcConfig.getNoticeButtonOffsetX());
        NOTICE_BUTTON_OFFSET_Y.setIntegerValue(DlcConfig.getNoticeButtonOffsetY());
        NOTICE_PANEL_ANCHOR.setValueFromString(DlcConfig.getNoticePanelAnchor());
        NOTICE_PANEL_OFFSET_X.setIntegerValue(DlcConfig.getNoticePanelOffsetX());
        NOTICE_PANEL_OFFSET_Y.setIntegerValue(DlcConfig.getNoticePanelOffsetY());
        NOTICE_PANEL_WIDTH.setIntegerValue(DlcConfig.getNoticePanelWidth());
        NOTICE_PANEL_HEIGHT.setIntegerValue(DlcConfig.getNoticePanelHeight());
        TITLE_ANCHOR.setValueFromString(DlcConfig.getTitleAnchor());
        TITLE_OFFSET_X.setIntegerValue(DlcConfig.getTitleOffsetX());
        TITLE_OFFSET_Y.setIntegerValue(DlcConfig.getTitleOffsetY());
        TITLE_UPDATE_ANCHOR.setValueFromString(DlcConfig.getTitleUpdateAnchor());
        TITLE_UPDATE_OFFSET_X.setIntegerValue(DlcConfig.getTitleUpdateOffsetX());
        TITLE_UPDATE_OFFSET_Y.setIntegerValue(DlcConfig.getTitleUpdateOffsetY());

        MAX_CONCURRENT_DOWNLOADS.setIntegerValue(DlcConfig.getMaxConcurrentDownloads());
        MAX_SEGMENTS_PER_FILE.setIntegerValue(DlcConfig.getMaxSegmentsPerFile());
        MIN_SEGMENT_MB.setIntegerValue(toMb(DlcConfig.getMinSegmentBytes()));
        MAX_RETRY.setIntegerValue(DlcConfig.getMaxRetryCount());
        CONNECT_TIMEOUT_SECONDS.setIntegerValue(Math.max(1, DlcConfig.getConnectTimeoutMs() / 1000));
        TASK_TIMEOUT_SECONDS.setIntegerValue(Math.max(1, DlcConfig.getTaskTimeoutMs() / 1000));
        DOWNLOAD_MIRROR_MODE.setValueFromString(DlcConfig.getDownloadMirrorMode());
        DOWNLOAD_MIRROR_PROBE_COUNT.setIntegerValue(DlcConfig.getDownloadMirrorProbeCount());
        DOWNLOAD_MIRROR_PROBE_STRATEGY.setValueFromString(DlcConfig.getDownloadMirrorProbeStrategy());
    }

    public static void applyToConfig() {
        DlcConfig.setDlcRoot(DLC_ROOT.getStringValue());
        DlcConfig.setTaczRoot(TACZ_ROOT.getStringValue());
        DlcConfig.setResourcepackRoot(RESOURCEPACK_ROOT.getStringValue());
        DlcConfig.setDatapackRoot(DATAPACK_ROOT.getStringValue());

        DlcConfig.setEnableAllButtonShown(ENABLE_ALL_BUTTON.getBooleanValue());
        DlcConfig.setConfigButtonShown(CONFIG_BUTTON.getBooleanValue());
        DlcConfig.setUButtomShown(U_BUTTON.getBooleanValue());
        DlcConfig.setUpdateManagerOn(UPDATE_MANAGER_ON.getBooleanValue());
        DlcConfig.setSelfUpdateAutoCheckOn(SELF_UPDATE_AUTO_CHECK_ON.getBooleanValue());
        DlcConfig.setPresetFuncOn(PRESET_FUNC_ON.getBooleanValue());
        DlcConfig.setPackFuncOn(PACK_FUNC_ON.getBooleanValue());
        DlcConfig.setPButtomShown(P_BUTTON.getBooleanValue());
        DlcConfig.setShowOnFirstRun(SHOW_ON_FIRST_RUN.getBooleanValue());
        DlcConfig.setAlarmWaitingTime(ALARM_WAITING_TIME.getIntegerValue());
        DlcConfig.setServerSupportOn(SERVER_SUPPORT_CLIENT.getBooleanValue());
        DlcConfig.setModpack(MODPACK.getStringValue());
        DlcConfig.setModpackUrl(MODPACK_URL.getStringValue());
        DlcConfig.setUpdateGitRepoUrl(UPDATE_GIT_REPO.getStringValue());
        DlcConfig.setApiEnabled(API_ENABLED.getBooleanValue());
        DlcConfig.setApiPort(API_PORT.getIntegerValue());
        DlcConfig.setApiToken(API_TOKEN.getStringValue());
        DlcConfig.setNoticeDisplayMode(NOTICE_DISPLAY_MODE.getStringValue());
        DlcConfig.setNoticeButtonAnchor(NOTICE_BUTTON_ANCHOR.getStringValue());
        DlcConfig.setNoticeButtonOffsetX(NOTICE_BUTTON_OFFSET_X.getIntegerValue());
        DlcConfig.setNoticeButtonOffsetY(NOTICE_BUTTON_OFFSET_Y.getIntegerValue());
        DlcConfig.setNoticePanelAnchor(NOTICE_PANEL_ANCHOR.getStringValue());
        DlcConfig.setNoticePanelOffsetX(NOTICE_PANEL_OFFSET_X.getIntegerValue());
        DlcConfig.setNoticePanelOffsetY(NOTICE_PANEL_OFFSET_Y.getIntegerValue());
        DlcConfig.setNoticePanelWidth(NOTICE_PANEL_WIDTH.getIntegerValue());
        DlcConfig.setNoticePanelHeight(NOTICE_PANEL_HEIGHT.getIntegerValue());
        DlcConfig.setTitleAnchor(TITLE_ANCHOR.getStringValue());
        DlcConfig.setTitleOffsetX(TITLE_OFFSET_X.getIntegerValue());
        DlcConfig.setTitleOffsetY(TITLE_OFFSET_Y.getIntegerValue());
        DlcConfig.setTitleUpdateAnchor(TITLE_UPDATE_ANCHOR.getStringValue());
        DlcConfig.setTitleUpdateOffsetX(TITLE_UPDATE_OFFSET_X.getIntegerValue());
        DlcConfig.setTitleUpdateOffsetY(TITLE_UPDATE_OFFSET_Y.getIntegerValue());

        DlcConfig.setMaxConcurrentDownloads(MAX_CONCURRENT_DOWNLOADS.getIntegerValue());
        DlcConfig.setMaxSegmentsPerFile(MAX_SEGMENTS_PER_FILE.getIntegerValue());
        DlcConfig.setMinSegmentBytes((long) Math.max(1, MIN_SEGMENT_MB.getIntegerValue()) * 1024L * 1024L);
        DlcConfig.setMaxRetryCount(MAX_RETRY.getIntegerValue());
        DlcConfig.setConnectTimeoutMs(CONNECT_TIMEOUT_SECONDS.getIntegerValue() * 1000);
        DlcConfig.setTaskTimeoutMs(TASK_TIMEOUT_SECONDS.getIntegerValue() * 1000);
        DlcConfig.setDownloadMirrorMode(DOWNLOAD_MIRROR_MODE.getStringValue());
        DlcConfig.setDownloadMirrorProbeCount(DOWNLOAD_MIRROR_PROBE_COUNT.getIntegerValue());
        DlcConfig.setDownloadMirrorProbeStrategy(DOWNLOAD_MIRROR_PROBE_STRATEGY.getStringValue());
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

}
