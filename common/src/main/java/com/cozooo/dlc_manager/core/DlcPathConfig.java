package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import com.cozooo.dlc_manager.core.Json5Util;

public class DlcPathConfig {
    public static final String MOD_ID = "dlc_manager";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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
    public static final boolean DEFAULT_SERVER_SUPPORT = true;
    public static final int DEFAULT_SERVER_PORT = 29690;
    public static final String DEFAULT_SERVER_PASSWORD = "";
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
    public static final int DEFAULT_MAX_RETRY = 5;
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_TASK_TIMEOUT_SECONDS = 60;
    public static final String DEFAULT_MODPACK_URL = "";
    public static final String DEFAULT_UPDATE_GITHUB_REPO_URL = "";

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
    private static boolean serverSupportClient = DEFAULT_SERVER_SUPPORT;
    private static boolean serverSupportServer = DEFAULT_SERVER_SUPPORT;
    private static int serverPort = DEFAULT_SERVER_PORT;
    private static String serverPassword = DEFAULT_SERVER_PASSWORD;
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
    private static String modpackUrlClient = DEFAULT_MODPACK_URL;
    private static String updateGithubRepoClient = DEFAULT_UPDATE_GITHUB_REPO_URL;
    private static String modpackUrlServer = DEFAULT_MODPACK_URL;
    private static String updateGithubRepoServer = DEFAULT_UPDATE_GITHUB_REPO_URL;

    private static int maxConcurrentDownloads = DEFAULT_MAX_CONCURRENT_DOWNLOADS;
    private static int maxSegmentsPerFile = DEFAULT_MAX_SEGMENTS_PER_FILE;
    private static int minSegmentMb = DEFAULT_MIN_SEGMENT_MB;
    private static int maxRetryCount = DEFAULT_MAX_RETRY;
    private static int connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;
    private static int taskTimeoutSeconds = DEFAULT_TASK_TIMEOUT_SECONDS;

    public static void load() {
        loadClient();
    }

    public static void loadClient() {
        loadClientFromDisk();
        ensureClientConfigFile();
    }

    public static void loadServer() {
        loadServerFromDisk();
        ensureServerConfigFile();
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

    public static JsonObject buildServerConfigJson() {
        JsonObject root = new JsonObject();
        writeServer(root);
        writeServerUi(root);
        writePaths(root);
        writeDownloads(root);
        return root;
    }

    public static void applyServerConfigJson(JsonObject root) {
        if (root == null) {
            return;
        }
        readSection(root, "server", buildServerReaders());
        readSection(root, "ui", buildServerUiReaders());
        readSection(root, "paths", buildPathReaders());
        readSection(root, "downloads", buildDownloadReaders());
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

    public static String getModpackUrl() {
        return modpackUrlClient == null ? DEFAULT_MODPACK_URL : modpackUrlClient;
    }

    public static String getUpdateGithubRepoUrl() {
        return updateGithubRepoClient == null ? DEFAULT_UPDATE_GITHUB_REPO_URL : updateGithubRepoClient;
    }

    public static String getServerModpackUrl() {
        return modpackUrlServer == null ? DEFAULT_MODPACK_URL : modpackUrlServer;
    }

    public static String getServerUpdateGithubRepoUrl() {
        return updateGithubRepoServer == null ? DEFAULT_UPDATE_GITHUB_REPO_URL : updateGithubRepoServer;
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

    public static void setModpackUrl(String value) {
        modpackUrlClient = value == null ? DEFAULT_MODPACK_URL : value;
    }

    public static void setUpdateGithubRepoUrl(String value) {
        updateGithubRepoClient = value == null ? DEFAULT_UPDATE_GITHUB_REPO_URL : value;
    }

    public static void setServerModpackUrl(String value) {
        modpackUrlServer = value == null ? DEFAULT_MODPACK_URL : value;
    }

    public static void setServerUpdateGithubRepoUrl(String value) {
        updateGithubRepoServer = value == null ? DEFAULT_UPDATE_GITHUB_REPO_URL : value;
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
        serverSupportClient = DEFAULT_SERVER_SUPPORT;
        serverSupportServer = DEFAULT_SERVER_SUPPORT;
        serverPort = DEFAULT_SERVER_PORT;
        serverPassword = DEFAULT_SERVER_PASSWORD;
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
        modpackUrlClient = DEFAULT_MODPACK_URL;
        updateGithubRepoClient = DEFAULT_UPDATE_GITHUB_REPO_URL;
        modpackUrlServer = DEFAULT_MODPACK_URL;
        updateGithubRepoServer = DEFAULT_UPDATE_GITHUB_REPO_URL;
        maxConcurrentDownloads = DEFAULT_MAX_CONCURRENT_DOWNLOADS;
        maxSegmentsPerFile = DEFAULT_MAX_SEGMENTS_PER_FILE;
        minSegmentMb = DEFAULT_MIN_SEGMENT_MB;
        maxRetryCount = DEFAULT_MAX_RETRY;
        connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;
        taskTimeoutSeconds = DEFAULT_TASK_TIMEOUT_SECONDS;
    }

    private static void loadClientFromDisk() {
        resetDefaults();
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path jsonPath = Json5Util.resolveConfigPath(configDir, CONFIG_NAME);
        jsonPath = Json5Util.migrateLegacy(configDir.resolve(CONFIG_NAME + Json5Util.JSON_EXTENSION), GSON);
        if (Files.exists(jsonPath)) {
            loadFromJson(jsonPath);
        }
    }

    private static void loadServerFromDisk() {
        resetServerDefaults();
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path serverPath = Json5Util.resolveConfigPath(configDir, CONFIG_SERVER_NAME);
        serverPath = Json5Util.migrateLegacy(configDir.resolve(CONFIG_SERVER_NAME + Json5Util.JSON_EXTENSION), GSON);
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
        modpackUrlServer = DEFAULT_MODPACK_URL;
        updateGithubRepoServer = DEFAULT_UPDATE_GITHUB_REPO_URL;
    }

    private static void loadFromJson(Path jsonPath) {
        JsonObject root = Json5Util.readObject(jsonPath);
        if (root == null) {
            return;
        }
        readSection(root, "paths", buildPathReaders());
        readSection(root, "ui", buildUiReaders());
        readSection(root, "downloads", buildDownloadReaders());
    }

    private static void loadFromServerJson(Path jsonPath) {
        JsonObject root = Json5Util.readObject(jsonPath);
        if (root == null) {
            return;
        }
        readSection(root, "server", buildServerReaders());
        readSection(root, "ui", buildServerUiReaders());
        readSection(root, "paths", buildPathReaders());
        readSection(root, "downloads", buildDownloadReaders());
    }

    private static void ensureClientConfigFile() {
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path jsonPath = Json5Util.resolveConfigPath(configDir, CONFIG_NAME);
        Json5Util.migrateLegacy(configDir.resolve(CONFIG_NAME + Json5Util.JSON_EXTENSION), GSON);
        if (Files.exists(jsonPath)) {
            return;
        }
        saveClientToDisk();
    }

    private static void ensureServerConfigFile() {
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path serverPath = Json5Util.resolveConfigPath(configDir, CONFIG_SERVER_NAME);
        Json5Util.migrateLegacy(configDir.resolve(CONFIG_SERVER_NAME + Json5Util.JSON_EXTENSION), GSON);
        if (Files.exists(serverPath)) {
            return;
        }
        saveServerToDisk();
    }

    private static void saveClientToDisk() {
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path jsonPath = configDir.resolve(CONFIG_NAME + Json5Util.JSON5_EXTENSION);
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {
            return;
        }
        JsonObject root = new JsonObject();
        writePaths(root);
        writeUi(root);
        writeDownloads(root);
        writeJson(root, jsonPath);
    }

    private static void saveServerToDisk() {
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path serverPath = configDir.resolve(CONFIG_SERVER_NAME + Json5Util.JSON5_EXTENSION);
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {
            return;
        }
        JsonObject root = new JsonObject();
        writeServer(root);
        writeServerUi(root);
        writePaths(root);
        writeDownloads(root);
        writeJson(root, serverPath);
    }

    private static void writeJson(JsonObject root, Path path) {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(root, writer);
        } catch (IOException ignored) {
        }
    }

    private static void writePaths(JsonObject root) {
        JsonObject obj = new JsonObject();
        obj.addProperty("dlcRoot", dlcRoot);
        obj.addProperty("taczRoot", taczRoot);
        obj.addProperty("resourcepackRoot", resourcepackRoot);
        obj.addProperty("datapackRoot", datapackRoot);
        root.add("paths", obj);
    }

    private static void writeUi(JsonObject root) {
        JsonObject obj = new JsonObject();
        obj.addProperty("isEnableAllButtonShown", enableAllButtonShown);
        obj.addProperty("isConfigButtonShown", configButtonShown);
        obj.addProperty("isUButtomShown", uButtonShown);
        obj.addProperty("isUpdateManagerOn", updateManagerOn);
        obj.addProperty("isSelfUpdateAutoCheckOn", selfUpdateAutoCheckOn);
        obj.addProperty("isPresetFuncOn", presetFuncOn);
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
        obj.addProperty("modpackUrl", modpackUrlClient);
        obj.addProperty("updateGithubRepo", updateGithubRepoClient);
        root.add("ui", obj);
    }

    private static void writeServerUi(JsonObject root) {
        JsonObject obj = new JsonObject();
        obj.addProperty("isEnableAllButtonShown", enableAllButtonShown);
        obj.addProperty("isUpdateManagerOn", updateManagerOn);
        obj.addProperty("isSelfUpdateAutoCheckOn", selfUpdateAutoCheckOn);
        obj.addProperty("isPresetFuncOn", presetFuncOn);
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
        root.add("downloads", obj);
    }

    private static void writeServer(JsonObject root) {
        JsonObject obj = new JsonObject();
        obj.addProperty("serverSupport", serverSupportServer);
        obj.addProperty("serverPort", serverPort);
        obj.addProperty("serverPassword", serverPassword);
        obj.addProperty("modpackUrl", modpackUrlServer);
        obj.addProperty("updateGithubRepo", updateGithubRepoServer);
        if (!DEFAULT_RCON_HOST.equals(serverRconHost) || serverRconPort != DEFAULT_RCON_PORT || !DEFAULT_RCON_PASSWORD.equals(serverRconPassword)) {
            obj.addProperty("rconHost", serverRconHost);
            obj.addProperty("rconPort", serverRconPort);
            obj.addProperty("rconPassword", serverRconPassword);
        }
        root.add("server", obj);
    }

    private static void readSection(JsonObject root, String section, Map<String, Consumer<JsonElement>> keyMap) {
        if (root == null || keyMap.isEmpty()) {
            return;
        }
        JsonObject obj = JsonCaseUtil.getObjectIgnoreCase(root, section);
        if (obj == null) {
            return;
        }
        for (Map.Entry<String, Consumer<JsonElement>> entry : keyMap.entrySet()) {
            JsonElement value = JsonCaseUtil.getIgnoreCase(obj, entry.getKey());
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

    private static Map<String, Consumer<JsonElement>> buildUiReaders() {
        Map<String, Consumer<JsonElement>> map = new HashMap<>();
        map.put("isEnableAllButtonShown", value -> enableAllButtonShown = asBoolean(value, DEFAULT_ENABLE_ALL_SHOWN));
        map.put("isConfigButtonShown", value -> configButtonShown = asBoolean(value, DEFAULT_CONFIG_BUTTON_SHOWN));
        map.put("isUButtomShown", value -> uButtonShown = asBoolean(value, DEFAULT_U_BUTTON_SHOWN));
        map.put("isUpdateManagerOn", value -> updateManagerOn = asBoolean(value, DEFAULT_UPDATE_MANAGER_ON));
        map.put("isSelfUpdateAutoCheckOn", value -> selfUpdateAutoCheckOn = asBoolean(value, DEFAULT_SELF_UPDATE_AUTO_CHECK_ON));
        map.put("isPresetFuncOn", value -> presetFuncOn = asBoolean(value, DEFAULT_PRESET_FUNC_ON));
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
        map.put("updateGithubRepo", value -> updateGithubRepoClient = asString(value, DEFAULT_UPDATE_GITHUB_REPO_URL));
        return map;
    }

    private static Map<String, Consumer<JsonElement>> buildServerReaders() {
        Map<String, Consumer<JsonElement>> map = new HashMap<>();
        map.put("serverSupport", value -> serverSupportServer = asBoolean(value, DEFAULT_SERVER_SUPPORT));
        map.put("serverPort", value -> serverPort = clampInt(asInt(value, DEFAULT_SERVER_PORT), 1, 65535, DEFAULT_SERVER_PORT));
        map.put("serverPassword", value -> serverPassword = asString(value, DEFAULT_SERVER_PASSWORD));
        map.put("modpackUrl", value -> modpackUrlServer = asString(value, DEFAULT_MODPACK_URL));
        map.put("updateGithubRepo", value -> updateGithubRepoServer = asString(value, DEFAULT_UPDATE_GITHUB_REPO_URL));
        map.put("rconHost", value -> serverRconHost = asString(value, DEFAULT_RCON_HOST));
        map.put("rconPort", value -> serverRconPort = clampInt(asInt(value, DEFAULT_RCON_PORT), 1, 65535, DEFAULT_RCON_PORT));
        map.put("rconPassword", value -> serverRconPassword = asString(value, DEFAULT_RCON_PASSWORD));
        return map;
    }

    private static Map<String, Consumer<JsonElement>> buildServerUiReaders() {
        Map<String, Consumer<JsonElement>> map = new HashMap<>();
        map.put("isEnableAllButtonShown", value -> enableAllButtonShown = asBoolean(value, DEFAULT_ENABLE_ALL_SHOWN));
        map.put("isUpdateManagerOn", value -> updateManagerOn = asBoolean(value, DEFAULT_UPDATE_MANAGER_ON));
        map.put("isSelfUpdateAutoCheckOn", value -> selfUpdateAutoCheckOn = asBoolean(value, DEFAULT_SELF_UPDATE_AUTO_CHECK_ON));
        map.put("isPresetFuncOn", value -> presetFuncOn = asBoolean(value, DEFAULT_PRESET_FUNC_ON));
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
        return map;
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
        Path path = Path.of(raw);
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
            Path candidate = Path.of(trimmed);
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
}
