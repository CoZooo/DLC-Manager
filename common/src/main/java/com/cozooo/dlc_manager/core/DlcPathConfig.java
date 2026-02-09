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
    public static final int DEFAULT_MAX_CONCURRENT_DOWNLOADS = 5;
    public static final int DEFAULT_MAX_SEGMENTS_PER_FILE = 4;
    public static final int DEFAULT_MIN_SEGMENT_MB = 20;
    public static final int DEFAULT_MAX_RETRY = 5;
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_TASK_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_TITLE_WIDTH = 0;
    public static final int DEFAULT_TITLE_HEIGHT = 0;

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
    private static int titleWidth = DEFAULT_TITLE_WIDTH;
    private static int titleHeight = DEFAULT_TITLE_HEIGHT;

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

    public static int getTitleWidth() {
        return titleWidth;
    }

    public static int getTitleHeight() {
        return titleHeight;
    }

    public static int[] computeTitleButtonPosition(int screenWidth, int screenHeight, int buttonWidth, int buttonHeight, int defaultX, int defaultY) {
        String anchor = getTitleAnchor();
        int offsetX = getTitleOffsetX();
        int offsetY = getTitleOffsetY();
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

    public static void setTitleWidth(int value) {
        titleWidth = Math.max(0, value);
    }

    public static void setTitleHeight(int value) {
        titleHeight = Math.max(0, value);
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
        titleWidth = DEFAULT_TITLE_WIDTH;
        titleHeight = DEFAULT_TITLE_HEIGHT;
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
        resetDefaults();
        Path configDir = CrossPlatformRuntime.configDir().resolve(CONFIG_DIR);
        Path serverPath = Json5Util.resolveConfigPath(configDir, CONFIG_SERVER_NAME);
        serverPath = Json5Util.migrateLegacy(configDir.resolve(CONFIG_SERVER_NAME + Json5Util.JSON_EXTENSION), GSON);
        if (Files.exists(serverPath)) {
            loadFromServerJson(serverPath);
        }
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
        obj.addProperty("titleWidth", titleWidth);
        obj.addProperty("titleHeight", titleHeight);
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
        map.put("titleWidth", value -> titleWidth = Math.max(0, asInt(value, DEFAULT_TITLE_WIDTH)));
        map.put("titleHeight", value -> titleHeight = Math.max(0, asInt(value, DEFAULT_TITLE_HEIGHT)));
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
