package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
public class DlcConfig {
    public static final String MOD_ID = "dlc_manager";
    private static final String CONFIG_DIR = "dlc_manager";
    private static final String CONFIG_NAME = "config";
    private static final String CONFIG_SERVER_NAME = "config-server";

    public static final String DEFAULT_DLC_ROOT = "DLC";
    public static final String DEFAULT_TACZ_DLC_ROOT = "tacz";
    public static final String DEFAULT_RESOURCEPACK_ROOT = "resourcepacks";
    public static final String DEFAULT_DATAPACK_ROOT = "datapacks";
    public static final boolean DEFAULT_ENABLE_ALL_SHOWN = true;
    public static final boolean DEFAULT_CONFIG_BUTTON_SHOWN = false;
    public static final boolean DEFAULT_U_BUTTON_SHOWN = true;
    public static final boolean DEFAULT_UPDATE_MANAGER_ON = true;
    public static final boolean DEFAULT_SELF_UPDATE_AUTO_CHECK_ON = true;
    public static final boolean DEFAULT_PRESET_FUNC_ON = true;
    public static final boolean DEFAULT_PACK_FUNC_ON = true;
    public static final boolean DEFAULT_P_BUTTON_SHOWN = true;
    public static final boolean DEFAULT_SHOW_ON_FIRST_RUN = false;
    public static final int DEFAULT_ALARM_WAITING_TIME = 0;
    public static final boolean DEFAULT_SERVER_SUPPORT = true;
    public static final int DEFAULT_SERVER_PORT = 29690;
    public static final String DEFAULT_SERVER_PASSWORD = "";
    public static final boolean DEFAULT_API_ENABLED = false;
    public static final int DEFAULT_API_PORT = 10086;
    public static final String DEFAULT_API_TOKEN = "";
    public static final String DEFAULT_RCON_HOST = "";
    public static final int DEFAULT_RCON_PORT = 25575;
    public static final String DEFAULT_RCON_PASSWORD = "";
    public static final String DEFAULT_TITLE_ANCHOR = "auto";
    public static final int DEFAULT_TITLE_OFFSET_X = 0;
    public static final int DEFAULT_TITLE_OFFSET_Y = 0;
    public static final String DEFAULT_TITLE_UPDATE_ANCHOR = "auto";
    public static final int DEFAULT_TITLE_UPDATE_OFFSET_X = 0;
    public static final int DEFAULT_TITLE_UPDATE_OFFSET_Y = 0;
    public static final String DEFAULT_NOTICE_DISPLAY_MODE = "manager_button";
    public static final String DEFAULT_NOTICE_BUTTON_ANCHOR = "bottom_right";
    public static final int DEFAULT_NOTICE_BUTTON_OFFSET_X = 0;
    public static final int DEFAULT_NOTICE_BUTTON_OFFSET_Y = 0;
    public static final String DEFAULT_NOTICE_PANEL_ANCHOR = "top_left";
    public static final int DEFAULT_NOTICE_PANEL_OFFSET_X = 0;
    public static final int DEFAULT_NOTICE_PANEL_OFFSET_Y = 0;
    public static final int DEFAULT_NOTICE_PANEL_WIDTH = 0;
    public static final int DEFAULT_NOTICE_PANEL_HEIGHT = 0;
    public static final int MAX_NOTICE_OFFSET = 1000;
    public static final int MAX_NOTICE_PANEL_SIZE = 1000;
    public static final int DEFAULT_MAX_CONCURRENT_DOWNLOADS = 5;
    public static final int DEFAULT_MAX_SEGMENTS_PER_FILE = 4;
    public static final int DEFAULT_MIN_SEGMENT_MB = 20;
    public static final int DEFAULT_MAX_RETRY = 2;
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;
    public static final int DEFAULT_TASK_TIMEOUT_SECONDS = 60;
    public static final String DOWNLOAD_MIRROR_SMART_OFFICIAL = "smart_official";
    public static final String DOWNLOAD_MIRROR_SMART_MIRROR = "smart_mirror";
    public static final String DOWNLOAD_MIRROR_SMART_PRECHECK = "smart_precheck";
    public static final String DOWNLOAD_MIRROR_OFFICIAL_ONLY = "official_only";
    public static final String DOWNLOAD_MIRROR_MIRROR_ONLY = "mirror_only";
    public static final String DOWNLOAD_MIRROR_OFFICIAL_FIRST = "official_first";
    public static final String DOWNLOAD_MIRROR_MIRROR_FIRST = "mirror_first";
    public static final String DEFAULT_DOWNLOAD_MIRROR_MODE = DOWNLOAD_MIRROR_SMART_PRECHECK;
    public static final int DEFAULT_DOWNLOAD_MIRROR_PROBE_COUNT = 3;
    public static final String DOWNLOAD_MIRROR_PROBE_SINGLE = "single";
    public static final String DOWNLOAD_MIRROR_PROBE_MAJORITY = "majority";
    public static final String DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY = DOWNLOAD_MIRROR_PROBE_SINGLE;
    public static final String DEFAULT_MODPACK = "";
    public static final String DEFAULT_MODPACK_URL = "";
    public static final String DEFAULT_UPDATE_GIT_REPO_URL = "";

    private static String dlcRoot = DEFAULT_DLC_ROOT;
    private static String taczRoot = DEFAULT_TACZ_DLC_ROOT;
    private static String resourcepackRoot = DEFAULT_RESOURCEPACK_ROOT;
    private static String datapackRoot = DEFAULT_DATAPACK_ROOT;

    private static boolean enableAllButtonShown = DEFAULT_ENABLE_ALL_SHOWN;
    private static boolean configButtonShown = DEFAULT_CONFIG_BUTTON_SHOWN;
    private static boolean uButtonShown = DEFAULT_U_BUTTON_SHOWN;
    private static boolean updateManagerOn = DEFAULT_UPDATE_MANAGER_ON;
    private static boolean selfUpdateAutoCheckOn = DEFAULT_SELF_UPDATE_AUTO_CHECK_ON;
    private static boolean presetFuncOn = DEFAULT_PRESET_FUNC_ON;
    private static boolean packFuncOn = DEFAULT_PACK_FUNC_ON;
    private static boolean pButtonShown = DEFAULT_P_BUTTON_SHOWN;
    private static boolean showOnFirstRun = DEFAULT_SHOW_ON_FIRST_RUN;
    private static int alarmWaitingTime = DEFAULT_ALARM_WAITING_TIME;
    private static boolean serverSupportClient = DEFAULT_SERVER_SUPPORT;
    private static boolean serverSupportServer = DEFAULT_SERVER_SUPPORT;
    private static int serverPort = DEFAULT_SERVER_PORT;
    private static String serverPassword = DEFAULT_SERVER_PASSWORD;
    private static boolean apiEnabled = DEFAULT_API_ENABLED;
    private static int apiPort = DEFAULT_API_PORT;
    private static String apiToken = DEFAULT_API_TOKEN;
    private static String serverRconHost = DEFAULT_RCON_HOST;
    private static int serverRconPort = DEFAULT_RCON_PORT;
    private static String serverRconPassword = DEFAULT_RCON_PASSWORD;
    private static String titleAnchor = DEFAULT_TITLE_ANCHOR;
    private static int titleOffsetX = DEFAULT_TITLE_OFFSET_X;
    private static int titleOffsetY = DEFAULT_TITLE_OFFSET_Y;
    private static String titleUpdateAnchor = DEFAULT_TITLE_UPDATE_ANCHOR;
    private static int titleUpdateOffsetX = DEFAULT_TITLE_UPDATE_OFFSET_X;
    private static int titleUpdateOffsetY = DEFAULT_TITLE_UPDATE_OFFSET_Y;
    private static String noticeDisplayMode = DEFAULT_NOTICE_DISPLAY_MODE;
    private static String noticeButtonAnchor = DEFAULT_NOTICE_BUTTON_ANCHOR;
    private static int noticeButtonOffsetX = DEFAULT_NOTICE_BUTTON_OFFSET_X;
    private static int noticeButtonOffsetY = DEFAULT_NOTICE_BUTTON_OFFSET_Y;
    private static String noticePanelAnchor = DEFAULT_NOTICE_PANEL_ANCHOR;
    private static int noticePanelOffsetX = DEFAULT_NOTICE_PANEL_OFFSET_X;
    private static int noticePanelOffsetY = DEFAULT_NOTICE_PANEL_OFFSET_Y;
    private static int noticePanelWidth = DEFAULT_NOTICE_PANEL_WIDTH;
    private static int noticePanelHeight = DEFAULT_NOTICE_PANEL_HEIGHT;
    private static String modpack = DEFAULT_MODPACK;
    private static String modpackUrlClient = DEFAULT_MODPACK_URL;
    private static String updateGitRepoClient = DEFAULT_UPDATE_GIT_REPO_URL;
    private static int maxConcurrentDownloads = DEFAULT_MAX_CONCURRENT_DOWNLOADS;
    private static int maxSegmentsPerFile = DEFAULT_MAX_SEGMENTS_PER_FILE;
    private static int minSegmentMb = DEFAULT_MIN_SEGMENT_MB;
    private static int maxRetryCount = DEFAULT_MAX_RETRY;
    private static int connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;
    private static int taskTimeoutSeconds = DEFAULT_TASK_TIMEOUT_SECONDS;
    private static String downloadMirrorMode = DEFAULT_DOWNLOAD_MIRROR_MODE;
    private static int downloadMirrorProbeCount = DEFAULT_DOWNLOAD_MIRROR_PROBE_COUNT;
    private static String downloadMirrorProbeStrategy = DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY;

    public static void load() {
        loadClient();
    }

    public static void loadClient() {
        loadClientFromDisk();
        ensureClientConfigFile();
        migrateKnownConfigsToDc();
    }

    public static void loadServer() {
        loadServerFromDisk();
        ensureServerConfigFile();
        migrateKnownConfigsToDc();
    }

    public static void save() {
        saveClient();
    }

    public static void saveClient() {
        saveClientToDisk();
    }

    public static void saveServer() {
        saveServerToDisk();
    }

    public static void saveShared() {
        saveClientToDisk();
    }

    public static JsonObject buildServerConfigJson() {
        JsonObject root = new JsonObject();
        writeShared(root);
        writeServer(root);
        return root;
    }

    public static JsonObject buildSharedConfigJson() {
        JsonObject root = new JsonObject();
        writeShared(root);
        return root;
    }

    public static void applyServerConfigJson(JsonObject root) {
        if (root == null) {
            return;
        }
        readSection(root, "server", buildServerReaders());
    }

    public static void applySharedConfigJson(JsonObject root) {
        if (root == null) {
            return;
        }
        readSection(root, "api", buildApiReaders());
        readSection(root, "paths", buildPathReaders());
        readSection(root, "ui", buildUiReaders());
        readSection(root, "downloads", buildDownloadReaders());
        readSection(root, "modpack", buildModpackReaders());
    }

    public static Path getDlcRoot() {
        return getDlcRoots().get(0);
    }

    public static Path getTaczRoot() {
        return getTaczDlcRoots().get(0);
    }

    public static Path getResourcepackRoot() {
        return getResourcepackRoots().get(0);
    }

    public static Path getDatapackRoot() {
        return getDatapackRoots().get(0);
    }

    public static List<Path> getDlcRoots() {
        return resolvePaths(getDlcRootRaw(), DEFAULT_DLC_ROOT);
    }

    public static List<Path> getTaczDlcRoots() {
        return resolvePathsRelativeToDlc(getTaczRootRaw(), DEFAULT_TACZ_DLC_ROOT);
    }

    public static List<Path> getResourcepackRoots() {
        return resolvePaths(getResourcepackRootRaw(), DEFAULT_RESOURCEPACK_ROOT);
    }

    public static List<Path> getDatapackRoots() {
        return resolvePaths(getDatapackRootRaw(), DEFAULT_DATAPACK_ROOT);
    }

    public static String getDlcRootRaw() {
        return stringOrDefault(dlcRoot, DEFAULT_DLC_ROOT);
    }

    public static String getTaczRootRaw() {
        return stringOrDefault(taczRoot, DEFAULT_TACZ_DLC_ROOT);
    }

    public static String getResourcepackRootRaw() {
        return stringOrDefault(resourcepackRoot, DEFAULT_RESOURCEPACK_ROOT);
    }

    public static String getDatapackRootRaw() {
        return stringOrDefault(datapackRoot, DEFAULT_DATAPACK_ROOT);
    }

    public static boolean isEnableAllButtonShown() {
        return enableAllButtonShown;
    }

    public static boolean isConfigButtonShown() {
        return configButtonShown;
    }

    public static boolean isUButtomShown() {
        return uButtonShown;
    }

    public static boolean isUpdateManagerOn() {
        return updateManagerOn;
    }

    public static boolean isSelfUpdateAutoCheckOn() {
        return selfUpdateAutoCheckOn;
    }

    public static boolean isPresetFuncOn() {
        return presetFuncOn;
    }

    public static boolean isPackFuncOn() {
        return packFuncOn;
    }

    public static boolean isPButtomShown() {
        return pButtonShown;
    }

    public static boolean isShowOnFirstRun() {
        return showOnFirstRun;
    }

    public static int getAlarmWaitingTime() {
        return alarmWaitingTime;
    }

    public static boolean isServerSupportOn() {
        return serverSupportClient;
    }

    public static boolean isServerSupportOnServer() {
        return serverSupportServer;
    }

    public static int getServerPort() {
        return serverPort;
    }

    public static String getServerPassword() {
        return serverPassword;
    }

    public static boolean isApiEnabled() {
        return apiEnabled;
    }

    public static int getApiPort() {
        return apiPort;
    }

    public static String getApiToken() {
        return apiToken;
    }

    public static String getServerRconHost() {
        return serverRconHost;
    }

    public static int getServerRconPort() {
        return serverRconPort;
    }

    public static String getServerRconPassword() {
        return serverRconPassword;
    }

    public static String getTitleAnchor() {
        return titleAnchor;
    }

    public static int getTitleOffsetX() {
        return titleOffsetX;
    }

    public static int getTitleOffsetY() {
        return titleOffsetY;
    }

    public static String getTitleUpdateAnchor() {
        return titleUpdateAnchor;
    }

    public static int getTitleUpdateOffsetX() {
        return titleUpdateOffsetX;
    }

    public static int getTitleUpdateOffsetY() {
        return titleUpdateOffsetY;
    }

    public static String getNoticeDisplayMode() {
        return noticeDisplayMode == null ? DEFAULT_NOTICE_DISPLAY_MODE : noticeDisplayMode;
    }

    public static boolean isNoticeDisplayModeManagerButton() {
        return "manager_button".equalsIgnoreCase(getNoticeDisplayMode());
    }

    public static boolean isNoticeDisplayModeMainMenuButton() {
        return "main_menu_button".equalsIgnoreCase(getNoticeDisplayMode());
    }

    public static boolean isNoticeDisplayModeMainMenuPanel() {
        return "main_menu_panel".equalsIgnoreCase(getNoticeDisplayMode());
    }

    public static String getNoticeButtonAnchor() {
        return noticeButtonAnchor == null || noticeButtonAnchor.isBlank() ? DEFAULT_NOTICE_BUTTON_ANCHOR : noticeButtonAnchor;
    }

    public static int getNoticeButtonOffsetX() {
        return noticeButtonOffsetX;
    }

    public static int getNoticeButtonOffsetY() {
        return noticeButtonOffsetY;
    }

    public static String getNoticePanelAnchor() {
        return noticePanelAnchor == null || noticePanelAnchor.isBlank() ? DEFAULT_NOTICE_PANEL_ANCHOR : noticePanelAnchor;
    }

    public static int getNoticePanelOffsetX() {
        return noticePanelOffsetX;
    }

    public static int getNoticePanelOffsetY() {
        return noticePanelOffsetY;
    }

    public static int getNoticePanelWidth() {
        return Math.max(0, noticePanelWidth);
    }

    public static int getNoticePanelHeight() {
        return Math.max(0, noticePanelHeight);
    }

    public static String getModpack() {
        return modpack == null ? DEFAULT_MODPACK : modpack;
    }

    public static String getModpackUrl() {
        return modpackUrlClient == null ? DEFAULT_MODPACK_URL : modpackUrlClient;
    }

    public static String getUpdateGitRepoUrl() {
        return updateGitRepoClient == null ? DEFAULT_UPDATE_GIT_REPO_URL : updateGitRepoClient;
    }

    public static String getServerModpackUrl() {
        return getModpackUrl();
    }

    public static String getServerUpdateGitRepoUrl() {
        return getUpdateGitRepoUrl();
    }

    public static int[] computeTitleButtonPosition(int screenWidth, int screenHeight, int buttonWidth, int buttonHeight, int defaultX, int defaultY) {
        return computeAnchoredPosition(getTitleAnchor(), getTitleOffsetX(), getTitleOffsetY(),
            screenWidth, screenHeight, buttonWidth, buttonHeight, defaultX, defaultY);
    }

    public static int[] computeTitleUpdateButtonPosition(int screenWidth, int screenHeight, int buttonWidth, int buttonHeight, int defaultX, int defaultY) {
        return computeAnchoredPosition(getTitleUpdateAnchor(), getTitleUpdateOffsetX(), getTitleUpdateOffsetY(),
            screenWidth, screenHeight, buttonWidth, buttonHeight, defaultX, defaultY);
    }

    public static int[] computeNoticeButtonPosition(int screenWidth, int screenHeight, int buttonWidth, int buttonHeight, int defaultX, int defaultY) {
        return computeAnchoredPosition(getNoticeButtonAnchor(), getNoticeButtonOffsetX(), getNoticeButtonOffsetY(),
            screenWidth, screenHeight, buttonWidth, buttonHeight, defaultX, defaultY);
    }

    public static int[] computeNoticePanelPosition(int screenWidth, int screenHeight, int panelWidth, int panelHeight, int defaultX, int defaultY) {
        return computeAnchoredPosition(getNoticePanelAnchor(), getNoticePanelOffsetX(), getNoticePanelOffsetY(),
            screenWidth, screenHeight, panelWidth, panelHeight, defaultX, defaultY);
    }

    public static boolean isTitleUpdateAnchorAuto() {
        String anchor = getTitleUpdateAnchor();
        return anchor == null || anchor.isBlank() || "auto".equalsIgnoreCase(anchor);
    }

    public static boolean shouldPlaceTitleUpdateButtonLeftOfTitle() {
        if (!isTitleUpdateAnchorAuto()) {
            return false;
        }
        String anchor = getTitleAnchor();
        if (anchor == null) {
            return false;
        }
        String normalized = anchor.trim().toLowerCase(Locale.ROOT);
        return "top_right".equals(normalized)
            || "right".equals(normalized)
            || "bottom_right".equals(normalized);
    }

    private static int[] computeAnchoredPosition(String anchor, int offsetX, int offsetY,
                                                 int screenWidth, int screenHeight,
                                                 int buttonWidth, int buttonHeight,
                                                 int defaultX, int defaultY) {
        if (anchor == null || anchor.isBlank() || "auto".equalsIgnoreCase(anchor)) {
            return new int[] { defaultX + offsetX, defaultY + offsetY };
        }
        String normalized = anchor.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        int x;
        int y;
        switch (normalized) {
            case "top_left", "topleft", "top-left" -> {
                x = 0;
                y = 0;
            }
            case "top", "top_center", "topcenter" -> {
                x = (screenWidth - buttonWidth) / 2;
                y = 0;
            }
            case "top_right", "topright", "top-right" -> {
                x = screenWidth - buttonWidth;
                y = 0;
            }
            case "left", "center_left", "centerleft" -> {
                x = 0;
                y = (screenHeight - buttonHeight) / 2;
            }
            case "center", "middle" -> {
                x = (screenWidth - buttonWidth) / 2;
                y = (screenHeight - buttonHeight) / 2;
            }
            case "right", "center_right", "centerright" -> {
                x = screenWidth - buttonWidth;
                y = (screenHeight - buttonHeight) / 2;
            }
            case "bottom_left", "bottomleft", "bottom-left" -> {
                x = 0;
                y = screenHeight - buttonHeight;
            }
            case "bottom", "bottom_center", "bottomcenter" -> {
                x = (screenWidth - buttonWidth) / 2;
                y = screenHeight - buttonHeight;
            }
            case "bottom_right", "bottomright", "bottom-right" -> {
                x = screenWidth - buttonWidth;
                y = screenHeight - buttonHeight;
            }
            default -> {
                x = defaultX;
                y = defaultY;
            }
        }
        return new int[] { x + offsetX, y + offsetY };
    }

    public static int getMaxConcurrentDownloads() {
        int value = maxConcurrentDownloads;
        if (value < 1) {
            return DEFAULT_MAX_CONCURRENT_DOWNLOADS;
        }
        return value;
    }

    public static int getMaxSegmentsPerFile() {
        int value = maxSegmentsPerFile;
        if (value < 1) {
            return DEFAULT_MAX_SEGMENTS_PER_FILE;
        }
        return value;
    }

    public static long getMinSegmentBytes() {
        int value = minSegmentMb;
        if (value < 1) {
            value = DEFAULT_MIN_SEGMENT_MB;
        }
        return (long) value * 1024L * 1024L;
    }

    public static int getMaxRetryCount() {
        int value = maxRetryCount;
        if (value < 1) {
            return DEFAULT_MAX_RETRY;
        }
        return value;
    }

    public static int getConnectTimeoutMs() {
        int value = connectTimeoutSeconds;
        if (value < 1) {
            value = DEFAULT_CONNECT_TIMEOUT_SECONDS;
        }
        return value * 1000;
    }

    public static int getTaskTimeoutMs() {
        int value = taskTimeoutSeconds;
        if (value < 1) {
            value = DEFAULT_TASK_TIMEOUT_SECONDS;
        }
        return value * 1000;
    }

    public static String getDownloadMirrorMode() {
        return normalizeDownloadMirrorMode(downloadMirrorMode);
    }

    public static int getDownloadMirrorProbeCount() {
        return Math.max(1, Math.min(10, downloadMirrorProbeCount));
    }

    public static String getDownloadMirrorProbeStrategy() {
        return normalizeDownloadMirrorProbeStrategy(downloadMirrorProbeStrategy);
    }

    public static void setDlcRoot(String value) {
        if (value != null) {
            dlcRoot = value;
        }
    }

    public static void setTaczRoot(String value) {
        if (value != null) {
            taczRoot = value;
        }
    }

    public static void setResourcepackRoot(String value) {
        if (value != null) {
            resourcepackRoot = value;
        }
    }

    public static void setDatapackRoot(String value) {
        if (value != null) {
            datapackRoot = value;
        }
    }

    public static void setEnableAllButtonShown(boolean value) {
        enableAllButtonShown = value;
    }

    public static void setConfigButtonShown(boolean value) {
        configButtonShown = value;
    }

    public static void setUButtomShown(boolean value) {
        uButtonShown = value;
    }

    public static void setUpdateManagerOn(boolean value) {
        updateManagerOn = value;
    }

    public static void setSelfUpdateAutoCheckOn(boolean value) {
        selfUpdateAutoCheckOn = value;
    }

    public static void setPresetFuncOn(boolean value) {
        presetFuncOn = value;
    }

    public static void setPackFuncOn(boolean value) {
        packFuncOn = value;
    }

    public static void setPButtomShown(boolean value) {
        pButtonShown = value;
    }

    public static void setShowOnFirstRun(boolean value) {
        showOnFirstRun = value;
    }

    public static void setAlarmWaitingTime(int value) {
        alarmWaitingTime = clampInt(value, 0, 10, DEFAULT_ALARM_WAITING_TIME);
    }

    public static void setServerSupportOn(boolean value) {
        serverSupportClient = value;
    }

    public static void setServerSupportOnServer(boolean value) {
        serverSupportServer = value;
    }

    public static void setServerPort(int value) {
        serverPort = clampInt(value, 1, 65535, DEFAULT_SERVER_PORT);
    }

    public static void setServerPassword(String value) {
        serverPassword = value == null ? DEFAULT_SERVER_PASSWORD : value;
    }

    public static void setApiEnabled(boolean value) {
        apiEnabled = value;
    }

    public static void setApiPort(int value) {
        apiPort = clampInt(value, 0, 65535, DEFAULT_API_PORT);
    }

    public static void setApiToken(String value) {
        apiToken = value == null ? DEFAULT_API_TOKEN : value;
    }

    public static void setServerRconHost(String value) {
        serverRconHost = value == null ? DEFAULT_RCON_HOST : value;
    }

    public static void setServerRconPort(int value) {
        serverRconPort = clampInt(value, 1, 65535, DEFAULT_RCON_PORT);
    }

    public static void setServerRconPassword(String value) {
        serverRconPassword = value == null ? DEFAULT_RCON_PASSWORD : value;
    }

    public static void setTitleAnchor(String value) {
        titleAnchor = value == null ? DEFAULT_TITLE_ANCHOR : value;
    }

    public static void setTitleOffsetX(int value) {
        titleOffsetX = value;
    }

    public static void setTitleOffsetY(int value) {
        titleOffsetY = value;
    }

    public static void setTitleUpdateAnchor(String value) {
        titleUpdateAnchor = value == null ? DEFAULT_TITLE_UPDATE_ANCHOR : value;
    }

    public static void setTitleUpdateOffsetX(int value) {
        titleUpdateOffsetX = value;
    }

    public static void setTitleUpdateOffsetY(int value) {
        titleUpdateOffsetY = value;
    }

    public static void setNoticeDisplayMode(String value) {
        noticeDisplayMode = normalizeNoticeDisplayMode(value);
    }

    public static void setNoticeButtonAnchor(String value) {
        noticeButtonAnchor = value == null || value.isBlank() ? DEFAULT_NOTICE_BUTTON_ANCHOR : value;
    }

    public static void setNoticeButtonOffsetX(int value) {
        noticeButtonOffsetX = Math.max(-MAX_NOTICE_OFFSET, Math.min(MAX_NOTICE_OFFSET, value));
    }

    public static void setNoticeButtonOffsetY(int value) {
        noticeButtonOffsetY = Math.max(-MAX_NOTICE_OFFSET, Math.min(MAX_NOTICE_OFFSET, value));
    }

    public static void setNoticePanelAnchor(String value) {
        noticePanelAnchor = value == null || value.isBlank() ? DEFAULT_NOTICE_PANEL_ANCHOR : value;
    }

    public static void setNoticePanelOffsetX(int value) {
        noticePanelOffsetX = Math.max(-MAX_NOTICE_OFFSET, Math.min(MAX_NOTICE_OFFSET, value));
    }

    public static void setNoticePanelOffsetY(int value) {
        noticePanelOffsetY = Math.max(-MAX_NOTICE_OFFSET, Math.min(MAX_NOTICE_OFFSET, value));
    }

    public static void setNoticePanelWidth(int value) {
        noticePanelWidth = Math.max(0, Math.min(MAX_NOTICE_PANEL_SIZE, value));
    }

    public static void setNoticePanelHeight(int value) {
        noticePanelHeight = Math.max(0, Math.min(MAX_NOTICE_PANEL_SIZE, value));
    }

    public static void setModpack(String value) {
        modpack = value == null ? DEFAULT_MODPACK : value;
    }

    public static void setModpackUrl(String value) {
        modpackUrlClient = value == null ? DEFAULT_MODPACK_URL : value;
    }

    public static void setUpdateGitRepoUrl(String value) {
        updateGitRepoClient = value == null ? DEFAULT_UPDATE_GIT_REPO_URL : value;
    }

    public static void setServerModpackUrl(String value) {
        setModpackUrl(value);
    }

    public static void setServerUpdateGitRepoUrl(String value) {
        setUpdateGitRepoUrl(value);
    }

    public static void setMaxConcurrentDownloads(int value) {
        maxConcurrentDownloads = Math.max(1, value);
    }

    public static void setMaxSegmentsPerFile(int value) {
        maxSegmentsPerFile = Math.max(1, value);
    }

    public static void setMinSegmentBytes(long value) {
        long mb = Math.max(1L, (value + (1024L * 1024L - 1)) / (1024L * 1024L));
        long clamped = Math.min(Integer.MAX_VALUE, mb);
        minSegmentMb = (int) clamped;
    }

    public static void setMaxRetryCount(int value) {
        maxRetryCount = Math.max(1, value);
    }

    public static void setConnectTimeoutMs(int value) {
        int seconds = Math.max(1, (int) Math.ceil(value / 1000.0));
        connectTimeoutSeconds = seconds;
    }

    public static void setTaskTimeoutMs(int value) {
        int seconds = Math.max(1, (int) Math.ceil(value / 1000.0));
        taskTimeoutSeconds = seconds;
    }

    public static void setDownloadMirrorMode(String value) {
        downloadMirrorMode = normalizeDownloadMirrorMode(value);
    }

    public static void setDownloadMirrorProbeCount(int value) {
        downloadMirrorProbeCount = Math.max(1, Math.min(10, value));
    }

    public static void setDownloadMirrorProbeStrategy(String value) {
        downloadMirrorProbeStrategy = normalizeDownloadMirrorProbeStrategy(value);
    }

    private static void resetDefaults() {
        dlcRoot = DEFAULT_DLC_ROOT;
        taczRoot = DEFAULT_TACZ_DLC_ROOT;
        resourcepackRoot = DEFAULT_RESOURCEPACK_ROOT;
        datapackRoot = DEFAULT_DATAPACK_ROOT;
        enableAllButtonShown = DEFAULT_ENABLE_ALL_SHOWN;
        configButtonShown = DEFAULT_CONFIG_BUTTON_SHOWN;
        uButtonShown = DEFAULT_U_BUTTON_SHOWN;
        updateManagerOn = DEFAULT_UPDATE_MANAGER_ON;
        selfUpdateAutoCheckOn = DEFAULT_SELF_UPDATE_AUTO_CHECK_ON;
        presetFuncOn = DEFAULT_PRESET_FUNC_ON;
        packFuncOn = DEFAULT_PACK_FUNC_ON;
        pButtonShown = DEFAULT_P_BUTTON_SHOWN;
        showOnFirstRun = DEFAULT_SHOW_ON_FIRST_RUN;
        alarmWaitingTime = DEFAULT_ALARM_WAITING_TIME;
        serverSupportClient = DEFAULT_SERVER_SUPPORT;
        serverSupportServer = DEFAULT_SERVER_SUPPORT;
        serverPort = DEFAULT_SERVER_PORT;
        serverPassword = DEFAULT_SERVER_PASSWORD;
        apiEnabled = DEFAULT_API_ENABLED;
        apiPort = DEFAULT_API_PORT;
        apiToken = DEFAULT_API_TOKEN;
        serverRconHost = DEFAULT_RCON_HOST;
        serverRconPort = DEFAULT_RCON_PORT;
        serverRconPassword = DEFAULT_RCON_PASSWORD;
        titleAnchor = DEFAULT_TITLE_ANCHOR;
        titleOffsetX = DEFAULT_TITLE_OFFSET_X;
        titleOffsetY = DEFAULT_TITLE_OFFSET_Y;
        titleUpdateAnchor = DEFAULT_TITLE_UPDATE_ANCHOR;
        titleUpdateOffsetX = DEFAULT_TITLE_UPDATE_OFFSET_X;
        titleUpdateOffsetY = DEFAULT_TITLE_UPDATE_OFFSET_Y;
        noticeDisplayMode = DEFAULT_NOTICE_DISPLAY_MODE;
        noticeButtonAnchor = DEFAULT_NOTICE_BUTTON_ANCHOR;
        noticeButtonOffsetX = DEFAULT_NOTICE_BUTTON_OFFSET_X;
        noticeButtonOffsetY = DEFAULT_NOTICE_BUTTON_OFFSET_Y;
        noticePanelAnchor = DEFAULT_NOTICE_PANEL_ANCHOR;
        noticePanelOffsetX = DEFAULT_NOTICE_PANEL_OFFSET_X;
        noticePanelOffsetY = DEFAULT_NOTICE_PANEL_OFFSET_Y;
        noticePanelWidth = DEFAULT_NOTICE_PANEL_WIDTH;
        noticePanelHeight = DEFAULT_NOTICE_PANEL_HEIGHT;
        modpack = DEFAULT_MODPACK;
        modpackUrlClient = DEFAULT_MODPACK_URL;
        updateGitRepoClient = DEFAULT_UPDATE_GIT_REPO_URL;
        maxConcurrentDownloads = DEFAULT_MAX_CONCURRENT_DOWNLOADS;
        maxSegmentsPerFile = DEFAULT_MAX_SEGMENTS_PER_FILE;
        minSegmentMb = DEFAULT_MIN_SEGMENT_MB;
        maxRetryCount = DEFAULT_MAX_RETRY;
        connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;
        taskTimeoutSeconds = DEFAULT_TASK_TIMEOUT_SECONDS;
        downloadMirrorMode = DEFAULT_DOWNLOAD_MIRROR_MODE;
        downloadMirrorProbeCount = DEFAULT_DOWNLOAD_MIRROR_PROBE_COUNT;
        downloadMirrorProbeStrategy = DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY;
    }

    private static void loadClientFromDisk() {
        resetDefaults();
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path jsonPath = resolveManagerConfigPath(configDir, CONFIG_NAME);
        if (Files.exists(jsonPath)) {
            loadFromJson(jsonPath);
        }
    }

    private static void loadServerFromDisk() {
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        resetDefaults();
        Path clientPath = resolveManagerConfigPath(configDir, CONFIG_NAME);
        if (Files.exists(clientPath)) {
            loadFromJson(clientPath);
        }
        Path serverPath = resolveManagerConfigPath(configDir, CONFIG_SERVER_NAME);
        if (Files.exists(serverPath)) {
            loadFromServerJson(serverPath);
        }
    }

    private static void resetServerDefaults() {
        serverSupportServer = DEFAULT_SERVER_SUPPORT;
        serverPort = DEFAULT_SERVER_PORT;
        serverPassword = DEFAULT_SERVER_PASSWORD;
        serverRconHost = DEFAULT_RCON_HOST;
        serverRconPort = DEFAULT_RCON_PORT;
        serverRconPassword = DEFAULT_RCON_PASSWORD;
    }

    private static void loadFromJson(Path jsonPath) {
        JsonObject root = DlcDcConfigIO.read(jsonPath, DlcDcConfigIO.Layout.GENERIC);
        if (root == null) {
            return;
        }
        readSection(root, "api", buildApiReaders());
        readSection(root, "paths", buildPathReaders());
        readSection(root, "ui", buildUiReaders());
        readSection(root, "downloads", buildDownloadReaders());
        readSection(root, "modpack", buildModpackReaders());
    }

    private static void loadFromServerJson(Path jsonPath) {
        resetServerDefaults();
        JsonObject root = DlcDcConfigIO.read(jsonPath, DlcDcConfigIO.Layout.GENERIC);
        if (root == null) {
            return;
        }
        readSection(root, "server", buildServerReaders());
    }

    private static void ensureClientConfigFile() {
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path jsonPath = resolveManagerConfigPath(configDir, CONFIG_NAME);
        if (Files.exists(jsonPath)) {
            return;
        }
        saveClientToDisk();
    }

    private static void ensureServerConfigFile() {
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path serverPath = resolveManagerConfigPath(configDir, CONFIG_SERVER_NAME);
        if (Files.exists(serverPath)) {
            return;
        }
        saveServerToDisk();
    }

    private static void saveClientToDisk() {
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path jsonPath = managerConfigPath(configDir, CONFIG_NAME);
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {
            return;
        }
        JsonObject root = new JsonObject();
        writeShared(root);
        writeJson(root, jsonPath);
    }

    private static void saveServerToDisk() {
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path serverPath = managerConfigPath(configDir, CONFIG_SERVER_NAME);
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {
            return;
        }
        JsonObject root = new JsonObject();
        writeServer(root);
        writeJson(root, serverPath);
    }

    private static void writeJson(JsonObject root, Path path) {
        DlcDcConfigIO.write(path, root, DlcDcConfigIO.Layout.GENERIC);
    }

    private static void writeShared(JsonObject root) {
        writeApi(root);
        writePaths(root);
        writeUi(root);
        writeDownloads(root);
        writeModpack(root);
    }

    private static Path resolveManagerConfigPath(Path configDir, String baseName) {
        if (configDir == null || baseName == null || baseName.isBlank()) {
            return null;
        }
        migrateManagerLegacyJson(configDir, baseName);
        Path path = managerConfigPath(configDir, baseName);
        if (Files.exists(path)) {
            return path;
        }
        return path;
    }

    private static Path managerConfigPath(Path configDir, String baseName) {
        return configDir.resolve(baseName + DlcDcFormatUtil.DC_EXTENSION);
    }

    private static void migrateManagerLegacyJson(Path configDir, String baseName) {
    }

    private static void writePaths(JsonObject root) {
        JsonObject obj = new JsonObject();
        obj.addProperty("dlcRoot", dlcRoot);
        obj.addProperty("taczRoot", taczRoot);
        obj.addProperty("resourcepackRoot", resourcepackRoot);
        obj.addProperty("datapackRoot", datapackRoot);
        root.add("paths", obj);
    }

    private static void writeApi(JsonObject root) {
        JsonObject obj = new JsonObject();
        obj.addProperty("apiEnabled", apiEnabled);
        obj.addProperty("apiPort", apiPort);
        obj.addProperty("apiToken", apiToken);
        root.add("api", obj);
    }

    private static void writeUi(JsonObject root) {
        JsonObject obj = new JsonObject();
        obj.addProperty("isEnableAllButtonShown", enableAllButtonShown);
        obj.addProperty("isConfigButtonShown", configButtonShown);
        obj.addProperty("isUButtomShown", uButtonShown);
        obj.addProperty("isUpdateManagerOn", updateManagerOn);
        obj.addProperty("isSelfUpdateAutoCheckOn", selfUpdateAutoCheckOn);
        obj.addProperty("isPresetFuncOn", presetFuncOn);
        obj.addProperty("isPackFuncOn", packFuncOn);
        obj.addProperty("isPButtomShown", pButtonShown);
        obj.addProperty("ShowOnFirstRun", showOnFirstRun);
        obj.addProperty("WaitingTime", alarmWaitingTime);
        obj.addProperty("serverSupport", serverSupportClient);
        obj.addProperty("titleAnchor", titleAnchor);
        obj.addProperty("titleOffsetX", titleOffsetX);
        obj.addProperty("titleOffsetY", titleOffsetY);
        obj.addProperty("titleUpdateAnchor", titleUpdateAnchor);
        obj.addProperty("titleUpdateOffsetX", titleUpdateOffsetX);
        obj.addProperty("titleUpdateOffsetY", titleUpdateOffsetY);
        obj.addProperty("noticeDisplayMode", noticeDisplayMode);
        obj.addProperty("noticeButtonAnchor", noticeButtonAnchor);
        obj.addProperty("noticeButtonOffsetX", noticeButtonOffsetX);
        obj.addProperty("noticeButtonOffsetY", noticeButtonOffsetY);
        obj.addProperty("noticePanelAnchor", noticePanelAnchor);
        obj.addProperty("noticePanelOffsetX", noticePanelOffsetX);
        obj.addProperty("noticePanelOffsetY", noticePanelOffsetY);
        obj.addProperty("noticePanelWidth", noticePanelWidth);
        obj.addProperty("noticePanelHeight", noticePanelHeight);
        root.add("ui", obj);
    }

    private static void writeDownloads(JsonObject root) {
        JsonObject obj = new JsonObject();
        obj.addProperty("maxConcurrentDownloads", maxConcurrentDownloads);
        obj.addProperty("maxSegmentsPerFile", maxSegmentsPerFile);
        obj.addProperty("minSegmentMB", minSegmentMb);
        obj.addProperty("maxRetryCount", maxRetryCount);
        obj.addProperty("connectTimeoutSeconds", connectTimeoutSeconds);
        obj.addProperty("taskTimeoutSeconds", taskTimeoutSeconds);
        obj.addProperty("downloadMirrorMode", getDownloadMirrorMode());
        obj.addProperty("downloadMirrorProbeCount", getDownloadMirrorProbeCount());
        obj.addProperty("downloadMirrorProbeStrategy", getDownloadMirrorProbeStrategy());
        root.add("downloads", obj);
    }

    private static void writeServer(JsonObject root) {
        JsonObject obj = new JsonObject();
        obj.addProperty("serverSupport", serverSupportServer);
        obj.addProperty("serverPort", serverPort);
        obj.addProperty("serverPassword", serverPassword);
        if (!DEFAULT_RCON_HOST.equals(serverRconHost) || serverRconPort != DEFAULT_RCON_PORT || !DEFAULT_RCON_PASSWORD.equals(serverRconPassword)) {
            obj.addProperty("rconHost", serverRconHost);
            obj.addProperty("rconPort", serverRconPort);
            obj.addProperty("rconPassword", serverRconPassword);
        }
        root.add("server", obj);
    }

    private static void writeModpack(JsonObject root) {
        JsonObject obj = new JsonObject();
        obj.addProperty("modpack", modpack);
        obj.addProperty("modpackUrl", modpackUrlClient);
        obj.addProperty("updateGitRepo", updateGitRepoClient);
        root.add("modpack", obj);
    }

    private static void readSection(JsonObject root, String section, Map<String, Consumer<JsonElement>> keyMap) {
        if (root == null || keyMap.isEmpty()) {
            return;
        }
        JsonObject obj = DlcDcConfigIO.getObjectIgnoreCase(root, section);
        if (obj == null) {
            return;
        }
        for (Map.Entry<String, Consumer<JsonElement>> entry : keyMap.entrySet()) {
            JsonElement value = DlcDcConfigIO.getIgnoreCase(obj, entry.getKey());
            if (value != null) {
                entry.getValue().accept(value);
            }
        }
    }

    private static Map<String, Consumer<JsonElement>> buildPathReaders() {
        Map<String, Consumer<JsonElement>> map = new HashMap<>();
        map.put("dlcRoot", value -> dlcRoot = asString(value, DEFAULT_DLC_ROOT));
        map.put("taczRoot", value -> taczRoot = asString(value, DEFAULT_TACZ_DLC_ROOT));
        map.put("resourcepackRoot", value -> resourcepackRoot = asString(value, DEFAULT_RESOURCEPACK_ROOT));
        map.put("datapackRoot", value -> datapackRoot = asString(value, DEFAULT_DATAPACK_ROOT));
        return map;
    }

    private static Map<String, Consumer<JsonElement>> buildApiReaders() {
        Map<String, Consumer<JsonElement>> map = new HashMap<>();
        map.put("apiEnabled", value -> apiEnabled = asBoolean(value, DEFAULT_API_ENABLED));
        map.put("apiPort", value -> apiPort = clampInt(asInt(value, DEFAULT_API_PORT), 0, 65535, DEFAULT_API_PORT));
        map.put("apiToken", value -> apiToken = asString(value, DEFAULT_API_TOKEN));
        return map;
    }

    private static Map<String, Consumer<JsonElement>> buildUiReaders() {
        Map<String, Consumer<JsonElement>> map = new HashMap<>();
        map.put("isEnableAllButtonShown", value -> enableAllButtonShown = asBoolean(value, DEFAULT_ENABLE_ALL_SHOWN));
        map.put("isConfigButtonShown", value -> configButtonShown = asBoolean(value, DEFAULT_CONFIG_BUTTON_SHOWN));
        map.put("isUButtomShown", value -> uButtonShown = asBoolean(value, DEFAULT_U_BUTTON_SHOWN));
        map.put("isUpdateManagerOn", value -> updateManagerOn = asBoolean(value, DEFAULT_UPDATE_MANAGER_ON));
        map.put("isSelfUpdateAutoCheckOn", value -> selfUpdateAutoCheckOn = asBoolean(value, DEFAULT_SELF_UPDATE_AUTO_CHECK_ON));
        map.put("isPresetFuncOn", value -> presetFuncOn = asBoolean(value, DEFAULT_PRESET_FUNC_ON));
        map.put("isPackFuncOn", value -> packFuncOn = asBoolean(value, DEFAULT_PACK_FUNC_ON));
        map.put("isPButtomShown", value -> pButtonShown = asBoolean(value, DEFAULT_P_BUTTON_SHOWN));
        map.put("ShowOnFirstRun", value -> showOnFirstRun = asBoolean(value, DEFAULT_SHOW_ON_FIRST_RUN));
        map.put("WaitingTime", value -> alarmWaitingTime = clampInt(asInt(value, DEFAULT_ALARM_WAITING_TIME), 0, 10, DEFAULT_ALARM_WAITING_TIME));
        map.put("serverSupport", value -> serverSupportClient = asBoolean(value, DEFAULT_SERVER_SUPPORT));
        map.put("titleAnchor", value -> titleAnchor = asString(value, DEFAULT_TITLE_ANCHOR));
        map.put("titleOffsetX", value -> titleOffsetX = asInt(value, DEFAULT_TITLE_OFFSET_X));
        map.put("titleOffsetY", value -> titleOffsetY = asInt(value, DEFAULT_TITLE_OFFSET_Y));
        map.put("titleUpdateAnchor", value -> titleUpdateAnchor = asString(value, DEFAULT_TITLE_UPDATE_ANCHOR));
        map.put("titleUpdateOffsetX", value -> titleUpdateOffsetX = asInt(value, DEFAULT_TITLE_UPDATE_OFFSET_X));
        map.put("titleUpdateOffsetY", value -> titleUpdateOffsetY = asInt(value, DEFAULT_TITLE_UPDATE_OFFSET_Y));
        map.put("noticeDisplayMode", value -> noticeDisplayMode = normalizeNoticeDisplayMode(asString(value, DEFAULT_NOTICE_DISPLAY_MODE)));
        map.put("noticeButtonAnchor", value -> noticeButtonAnchor = asString(value, DEFAULT_NOTICE_BUTTON_ANCHOR));
        map.put("noticeButtonOffsetX", value -> noticeButtonOffsetX = Math.max(-MAX_NOTICE_OFFSET, Math.min(MAX_NOTICE_OFFSET, asInt(value, DEFAULT_NOTICE_BUTTON_OFFSET_X))));
        map.put("noticeButtonOffsetY", value -> noticeButtonOffsetY = Math.max(-MAX_NOTICE_OFFSET, Math.min(MAX_NOTICE_OFFSET, asInt(value, DEFAULT_NOTICE_BUTTON_OFFSET_Y))));
        map.put("noticePanelAnchor", value -> noticePanelAnchor = asString(value, DEFAULT_NOTICE_PANEL_ANCHOR));
        map.put("noticePanelOffsetX", value -> noticePanelOffsetX = Math.max(-MAX_NOTICE_OFFSET, Math.min(MAX_NOTICE_OFFSET, asInt(value, DEFAULT_NOTICE_PANEL_OFFSET_X))));
        map.put("noticePanelOffsetY", value -> noticePanelOffsetY = Math.max(-MAX_NOTICE_OFFSET, Math.min(MAX_NOTICE_OFFSET, asInt(value, DEFAULT_NOTICE_PANEL_OFFSET_Y))));
        map.put("noticePanelWidth", value -> noticePanelWidth = Math.max(0, Math.min(MAX_NOTICE_PANEL_SIZE, asInt(value, DEFAULT_NOTICE_PANEL_WIDTH))));
        map.put("noticePanelHeight", value -> noticePanelHeight = Math.max(0, Math.min(MAX_NOTICE_PANEL_SIZE, asInt(value, DEFAULT_NOTICE_PANEL_HEIGHT))));
        map.put("modpackUrl", value -> modpackUrlClient = asString(value, DEFAULT_MODPACK_URL));
        map.put("updateGithubRepo", value -> updateGitRepoClient = asString(value, DEFAULT_UPDATE_GIT_REPO_URL));
        return map;
    }

    private static Map<String, Consumer<JsonElement>> buildServerReaders() {
        Map<String, Consumer<JsonElement>> map = new HashMap<>();
        map.put("serverSupport", value -> serverSupportServer = asBoolean(value, DEFAULT_SERVER_SUPPORT));
        map.put("serverPort", value -> serverPort = clampInt(asInt(value, DEFAULT_SERVER_PORT), 1, 65535, DEFAULT_SERVER_PORT));
        map.put("serverPassword", value -> serverPassword = asString(value, DEFAULT_SERVER_PASSWORD));
        map.put("rconHost", value -> serverRconHost = asString(value, DEFAULT_RCON_HOST));
        map.put("rconPort", value -> serverRconPort = clampInt(asInt(value, DEFAULT_RCON_PORT), 1, 65535, DEFAULT_RCON_PORT));
        map.put("rconPassword", value -> serverRconPassword = asString(value, DEFAULT_RCON_PASSWORD));
        return map;
    }

    private static Map<String, Consumer<JsonElement>> buildModpackReaders() {
        Map<String, Consumer<JsonElement>> map = new LinkedHashMap<>();
        map.put("modpack", value -> modpack = asString(value, DEFAULT_MODPACK));
        map.put("modpackUrl", value -> modpackUrlClient = asString(value, DEFAULT_MODPACK_URL));
        map.put("updateGithubRepo", value -> updateGitRepoClient = asString(value, DEFAULT_UPDATE_GIT_REPO_URL));
        map.put("updateGitRepo", value -> updateGitRepoClient = asString(value, DEFAULT_UPDATE_GIT_REPO_URL));
        return map;
    }

    private static Map<String, Consumer<JsonElement>> buildDownloadReaders() {
        Map<String, Consumer<JsonElement>> map = new HashMap<>();
        map.put("maxConcurrentDownloads", value -> maxConcurrentDownloads = Math.max(1, asInt(value, DEFAULT_MAX_CONCURRENT_DOWNLOADS)));
        map.put("maxSegmentsPerFile", value -> maxSegmentsPerFile = Math.max(1, asInt(value, DEFAULT_MAX_SEGMENTS_PER_FILE)));
        map.put("minSegmentMB", value -> minSegmentMb = Math.max(1, asInt(value, DEFAULT_MIN_SEGMENT_MB)));
        map.put("maxRetryCount", value -> maxRetryCount = Math.max(1, asInt(value, DEFAULT_MAX_RETRY)));
        map.put("connectTimeoutSeconds", value -> connectTimeoutSeconds = Math.max(1, asInt(value, DEFAULT_CONNECT_TIMEOUT_SECONDS)));
        map.put("taskTimeoutSeconds", value -> taskTimeoutSeconds = Math.max(1, asInt(value, DEFAULT_TASK_TIMEOUT_SECONDS)));
        map.put("downloadMirrorMode", value -> downloadMirrorMode = normalizeDownloadMirrorMode(asString(value, DEFAULT_DOWNLOAD_MIRROR_MODE)));
        map.put("downloadMirrorProbeCount", value -> downloadMirrorProbeCount = Math.max(1, Math.min(10, asInt(value, DEFAULT_DOWNLOAD_MIRROR_PROBE_COUNT))));
        map.put("downloadMirrorProbeStrategy", value -> downloadMirrorProbeStrategy = normalizeDownloadMirrorProbeStrategy(asString(value, DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY)));
        return map;
    }

    public static String normalizeDownloadMirrorMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case DOWNLOAD_MIRROR_SMART_OFFICIAL -> DOWNLOAD_MIRROR_SMART_OFFICIAL;
            case DOWNLOAD_MIRROR_SMART_MIRROR -> DOWNLOAD_MIRROR_SMART_MIRROR;
            case DOWNLOAD_MIRROR_SMART_PRECHECK -> DOWNLOAD_MIRROR_SMART_PRECHECK;
            case DOWNLOAD_MIRROR_OFFICIAL_ONLY -> DOWNLOAD_MIRROR_OFFICIAL_ONLY;
            case DOWNLOAD_MIRROR_MIRROR_ONLY -> DOWNLOAD_MIRROR_MIRROR_ONLY;
            case DOWNLOAD_MIRROR_OFFICIAL_FIRST -> DOWNLOAD_MIRROR_OFFICIAL_FIRST;
            case DOWNLOAD_MIRROR_MIRROR_FIRST -> DOWNLOAD_MIRROR_MIRROR_FIRST;
            default -> DEFAULT_DOWNLOAD_MIRROR_MODE;
        };
    }

    public static String normalizeDownloadMirrorProbeStrategy(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case DOWNLOAD_MIRROR_PROBE_SINGLE -> DOWNLOAD_MIRROR_PROBE_SINGLE;
            case DOWNLOAD_MIRROR_PROBE_MAJORITY -> DOWNLOAD_MIRROR_PROBE_MAJORITY;
            default -> DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY;
        };
    }

    private static String stringOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static int asInt(JsonElement element, int fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            if (element.isJsonPrimitive()) {
                return element.getAsInt();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static boolean asBoolean(JsonElement element, boolean fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            if (element.isJsonPrimitive()) {
                return element.getAsBoolean();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static String asString(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null) {
                    return value;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private static String normalizeNoticeDisplayMode(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_NOTICE_DISPLAY_MODE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "manager", "managerbutton", "manager_button", "dlc_manager_button" -> "manager_button";
            case "mainmenubutton", "main_menu_button", "title_button" -> "main_menu_button";
            case "mainmenupanel", "main_menu_panel", "title_panel" -> "main_menu_panel";
            default -> DEFAULT_NOTICE_DISPLAY_MODE;
        };
    }

    private static Path resolvePath(String value, String fallback) {
        String raw = value == null || value.isBlank() ? fallback : value;
        Path path = DlcPaths.parsePathLenient(raw);
        if (path == null) {
            path = DlcPaths.parsePathLenient(fallback);
        }
        if (path == null) {
            return CrossPlatformRuntime.gameDir().toAbsolutePath().normalize();
        }
        if (!path.isAbsolute()) {
            path = CrossPlatformRuntime.gameDir().resolve(path);
        }
        return path.normalize();
    }

    private static List<Path> resolvePaths(String value, String fallback) {
        String raw = value == null || value.isBlank() ? fallback : value;
        String[] parts = raw.split(",");
        List<Path> paths = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            paths.add(resolvePath(trimmed, fallback));
        }
        if (paths.isEmpty()) {
            paths.add(resolvePath(fallback, fallback));
        }
        return paths;
    }

    private static List<Path> resolvePathsRelativeToDlc(String value, String fallback) {
        String raw = value == null || value.isBlank() ? fallback : value;
        String[] parts = raw.split(",");
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        List<Path> dlcRoots = getDlcRoots();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path candidate = DlcPaths.parsePathLenient(trimmed);
            if (candidate == null) {
                continue;
            }
            if (candidate.isAbsolute()) {
                paths.add(candidate.normalize());
            } else {
                boolean added = false;
                for (Path dlcRootPath : dlcRoots) {
                    Path resolved = dlcRootPath.resolve(trimmed).normalize();
                    if (Files.exists(resolved)) {
                        paths.add(resolved);
                        added = true;
                    }
                }
                if (!added && !dlcRoots.isEmpty()) {
                    paths.add(dlcRoots.get(0).resolve(trimmed).normalize());
                }
            }
        }
        if (paths.isEmpty()) {
            boolean added = false;
            for (Path dlcRootPath : dlcRoots) {
                Path resolved = dlcRootPath.resolve(fallback).normalize();
                if (Files.exists(resolved)) {
                    paths.add(resolved);
                    added = true;
                }
            }
            if (!added && !dlcRoots.isEmpty()) {
                paths.add(dlcRoots.get(0).resolve(fallback).normalize());
            }
        }
        return new ArrayList<>(paths);
    }

    private static void migrateKnownConfigsToDc() {
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        migrateLocalConfigDirToDc(configDir);
        for (Path dlcRootPath : getDlcRoots()) {
            migrateDlcConfigTreeToDc(dlcRootPath);
        }
    }

    private static void migrateLocalConfigDirToDc(Path configDir) {
        if (configDir == null || !Files.isDirectory(configDir)) {
            return;
        }
        try (var stream = Files.walk(configDir)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName() != null)
                .filter(path -> DlcDcFormatUtil.isDcName(path.getFileName().toString()))
                .filter(path -> !isManagerConfigFile(path))
                .forEach(path -> {
                });
        } catch (IOException ignored) {
        }
    }

    private static boolean isManagerConfigFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String base = DlcDcFormatUtil.stripConfigExtension(path.getFileName().toString());
        return CONFIG_NAME.equalsIgnoreCase(base) || CONFIG_SERVER_NAME.equalsIgnoreCase(base);
    }

    private static void migrateDlcConfigTreeToDc(Path dlcRootPath) {
        if (dlcRootPath == null || !Files.isDirectory(dlcRootPath)) {
            return;
        }
        List<String> configDirs = List.of("info", "remote", "required", "preset", "tmp", "update");
        for (String dirName : configDirs) {
            Path target = dlcRootPath.resolve(dirName);
            if ("info".equals(dirName)) {
                migrateInfoConfigsToDc(target);
            } else if ("remote".equals(dirName)) {
                migrateRemoteConfigsToDc(target);
            } else {
                migrateTreeConfigsToDc(target);
            }
        }
    }

    private static void migrateTreeConfigsToDc(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName() != null)
                .filter(path -> DlcDcFormatUtil.isDcName(path.getFileName().toString()))
                .forEach(path -> {
                });
        } catch (IOException ignored) {
        }
    }

    private static void migrateInfoConfigsToDc(Path infoRoot) {
        if (infoRoot == null || !Files.isDirectory(infoRoot)) {
            return;
        }
        try (var stream = Files.walk(infoRoot)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName() != null)
                .filter(path -> DlcDcFormatUtil.isDcName(path.getFileName().toString()))
                .forEach(path -> {
                    String baseName = DlcDcFormatUtil.stripConfigExtension(path.getFileName().toString());
                    if ("version".equalsIgnoreCase(baseName) || "notice".equalsIgnoreCase(baseName)) {
                        return;
                    }
                    migrateInfoEntryConfig(path);
                });
        } catch (IOException ignored) {
        }
    }

    private static void migrateInfoEntryConfig(Path path) {
        if (path == null || !Files.exists(path) || path.getFileName() == null) {
            return;
        }
        JsonObject obj = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.DLC_STRUCTURED);
        if (obj == null) {
            return;
        }
        String fallbackName = DlcDcFormatUtil.stripConfigExtension(path.getFileName().toString());
        JsonObject normalized = normalizeInfoEntry(obj, fallbackName);
        DlcDcConfigIO.write(path, normalized, DlcDcConfigIO.Layout.DLC_STRUCTURED);
    }

    private static JsonObject normalizeInfoEntry(JsonObject input, String fallbackName) {
        JsonObject out = new JsonObject();
        if (input != null) {
            for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) {
                    continue;
                }
                if ("isShownInList".equalsIgnoreCase(key)) {
                    continue;
                }
                out.add(key, entry.getValue());
            }
        }

        String fileName = DlcDcConfigIO.getStringIgnoreCase(out, "file_name");
        if (fileName == null || fileName.isBlank()) {
            fileName = fallbackName == null ? "" : fallbackName;
            DlcDcConfigIO.removeIgnoreCase(out, "file_name");
            out.addProperty("file_name", fileName);
        }

        String identifier = DlcDcConfigIO.getStringIgnoreCase(out, "identifier");
        if (identifier == null || identifier.isBlank()) {
            identifier = fileName;
            DlcDcConfigIO.removeIgnoreCase(out, "identifier");
            out.addProperty("identifier", identifier == null ? "" : identifier);
        }

        if (!DlcDcConfigIO.hasIgnoreCase(out, "type")) {
            out.addProperty("type", "others");
        }
        if (!DlcDcConfigIO.hasIgnoreCase(out, "isInList")) {
            boolean legacyShown = DlcDcConfigIO.getBooleanIgnoreCase(input, "isShownInList", true);
            out.addProperty("isInList", legacyShown);
        }
        if (!DlcDcConfigIO.hasIgnoreCase(out, "isHidden")) {
            out.addProperty("isHidden", false);
        }
        if (!DlcDcConfigIO.hasIgnoreCase(out, "AllowEnableByAll")) {
            out.addProperty("AllowEnableByAll", true);
        }
        if (!DlcDcConfigIO.hasIgnoreCase(out, "dependencies")) {
            out.addProperty("dependencies", "");
        }
        if (!DlcDcConfigIO.hasIgnoreCase(out, "conflicts")) {
            out.addProperty("conflicts", "");
        }
        if (!DlcDcConfigIO.hasIgnoreCase(out, "alarm")) {
            out.addProperty("alarm", false);
        }

        normalizeLocalizedField(out, "DisplayName", fileName);
        normalizeLocalizedField(out, "tip", null);
        return out;
    }

    private static void normalizeLocalizedField(JsonObject obj, String key, String defaultEnUs) {
        JsonObject localized = new JsonObject();
        JsonObject fromObject = DlcDcConfigIO.getObjectIgnoreCase(obj, key);
        if (fromObject != null) {
            for (Map.Entry<String, JsonElement> entry : fromObject.entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || !value.isJsonPrimitive()) {
                    continue;
                }
                String lang = DlcDcConfigIO.normalizeLangKey(entry.getKey());
                if (lang == null || lang.isBlank()) {
                    continue;
                }
                localized.addProperty(lang, value.getAsString());
            }
        } else {
            String single = DlcDcConfigIO.getStringIgnoreCase(obj, key);
            if (single != null && !single.isBlank()) {
                localized.addProperty("en_us", single);
            }
        }
        if (localized.entrySet().isEmpty() && defaultEnUs != null && !defaultEnUs.isBlank()) {
            localized.addProperty("en_us", defaultEnUs);
        }
        DlcDcConfigIO.removeIgnoreCase(obj, key);
        obj.add(key, localized);
    }

    private static void migrateRemoteConfigsToDc(Path remoteRoot) {
        if (remoteRoot == null || !Files.isDirectory(remoteRoot)) {
            return;
        }
        Path redirectRoot = remoteRoot.resolve("redirect").normalize();
        try (var stream = Files.walk(remoteRoot)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName() != null)
                .filter(path -> DlcDcFormatUtil.isDcName(path.getFileName().toString()))
                .forEach(path -> {
                    Path normalized = path.normalize();
                    if (normalized.startsWith(redirectRoot)) {
                        return;
                    }
                    migrateRemoteEntryConfig(path);
                });
        } catch (IOException ignored) {
        }
    }

    private static void migrateRemoteEntryConfig(Path path) {
        if (path == null || !Files.exists(path) || path.getFileName() == null) {
            return;
        }
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(DlcDcFormatUtil.DC_EXTENSION)) {
            JsonObject obj = readRemoteDcObject(path);
            if (obj == null) {
                return;
            }
            DlcDcFormatUtil.write(path, obj);
        }
    }

    private static JsonObject readRemoteDcObject(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        return DlcDcFormatUtil.read(path);
    }
}
