package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DlcPathConfig {
    public static final String MOD_ID = "dlc_manager";

    private static final String CONFIG_DIR = "dlc_manager";
    private static final String CONFIG_NAME = "config.json";

    static final String DEFAULT_DLC_ROOT = "DLC";
    static final String DEFAULT_TACZ_DLC_ROOT = "tacz";
    static final String DEFAULT_RESOURCEPACK_ROOT = "resourcepacks";
    static final String DEFAULT_DATAPACK_ROOT = "datapacks";
    static final boolean DEFAULT_ENABLE_ALL_SHOWN = true;
    static final boolean DEFAULT_CONFIG_BUTTON_SHOWN = true;
    static final boolean DEFAULT_U_BUTTON_SHOWN = true;
    static final boolean DEFAULT_UPDATE_MANAGER_ON = true;
    static final int DEFAULT_MAX_CONCURRENT_DOWNLOADS = 5;
    static final int DEFAULT_MAX_SEGMENTS_PER_FILE = 4;
    static final int DEFAULT_MIN_SEGMENT_MB = 20;
    static final int DEFAULT_MAX_RETRY = 5;
    static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    static final int DEFAULT_TASK_TIMEOUT_SECONDS = 90;

    private static boolean registered;

    private static final ConfigString DLC_ROOT = new DlcConfigString("dlcRoot", DEFAULT_DLC_ROOT, "dlc_manager.config.paths.dlcRoot");
    private static final ConfigString TACZ_ROOT = new DlcConfigString("taczRoot", DEFAULT_TACZ_DLC_ROOT, "dlc_manager.config.paths.taczRoot");
    private static final ConfigString RESOURCEPACK_ROOT = new DlcConfigString("resourcepackRoot", DEFAULT_RESOURCEPACK_ROOT, "dlc_manager.config.paths.resourcepackRoot");
    private static final ConfigString DATAPACK_ROOT = new DlcConfigString("datapackRoot", DEFAULT_DATAPACK_ROOT, "dlc_manager.config.paths.datapackRoot");

    private static final ConfigBoolean ENABLE_ALL_BUTTON = new DlcConfigBoolean("isEnableAllButtonShown", DEFAULT_ENABLE_ALL_SHOWN, "dlc_manager.config.ui.isEnableAllButtonShown");
    private static final ConfigBoolean CONFIG_BUTTON = new DlcConfigBoolean("isConfigButtonShown", DEFAULT_CONFIG_BUTTON_SHOWN, "dlc_manager.config.ui.isConfigButtonShown");
    private static final ConfigBoolean U_BUTTON = new DlcConfigBoolean("isUButtomShown", DEFAULT_U_BUTTON_SHOWN, "dlc_manager.config.ui.isUButtomShown");
    private static final ConfigBoolean UPDATE_MANAGER_ON = new DlcConfigBoolean("isUpdateManagerOn", DEFAULT_UPDATE_MANAGER_ON, "dlc_manager.config.ui.isUpdateManagerOn");

    private static final ConfigInteger MAX_CONCURRENT_DOWNLOADS = new DlcConfigInteger(
            "maxConcurrentDownloads",
            DEFAULT_MAX_CONCURRENT_DOWNLOADS,
            1,
            64,
            "",
            "dlc_manager.config.download.maxConcurrentDownloads"
    );
    private static final ConfigInteger MAX_SEGMENTS_PER_FILE = new DlcConfigInteger(
            "maxSegmentsPerFile",
            DEFAULT_MAX_SEGMENTS_PER_FILE,
            1,
            64,
            "",
            "dlc_manager.config.download.maxSegmentsPerFile"
    );
    private static final ConfigInteger MIN_SEGMENT_MB = new DlcConfigInteger(
            "minSegmentMB",
            DEFAULT_MIN_SEGMENT_MB,
            1,
            Integer.MAX_VALUE,
            "",
            "dlc_manager.config.download.minSegmentMB"
    );
    private static final ConfigInteger MAX_RETRY = new DlcConfigInteger(
            "maxRetryCount",
            DEFAULT_MAX_RETRY,
            1,
            100,
            "",
            "dlc_manager.config.download.maxRetryCount"
    );
    private static final ConfigInteger CONNECT_TIMEOUT_SECONDS = new DlcConfigInteger(
            "connectTimeoutSeconds",
            DEFAULT_CONNECT_TIMEOUT_SECONDS,
            1,
            36000,
            "",
            "dlc_manager.config.download.connectTimeoutSeconds"
    );
    private static final ConfigInteger TASK_TIMEOUT_SECONDS = new DlcConfigInteger(
            "taskTimeoutSeconds",
            DEFAULT_TASK_TIMEOUT_SECONDS,
            1,
            36000,
            "",
            "dlc_manager.config.download.taskTimeoutSeconds"
    );

    private static final Map<String, IConfigBase> PATH_KEY_MAP = Map.of(
            "dlcRoot", DLC_ROOT,
            "taczRoot", TACZ_ROOT,
            "resourcepackRoot", RESOURCEPACK_ROOT,
            "datapackRoot", DATAPACK_ROOT
    );
    private static final Map<String, IConfigBase> UI_KEY_MAP = Map.of(
            "isEnableAllButtonShown", ENABLE_ALL_BUTTON,
            "isConfigButtonShown", CONFIG_BUTTON,
            "isUButtomShown", U_BUTTON,
            "isUpdateManagerOn", UPDATE_MANAGER_ON
    );
    private static final Map<String, IConfigBase> DOWNLOAD_KEY_MAP = Map.of(
            "maxConcurrentDownloads", MAX_CONCURRENT_DOWNLOADS,
            "maxSegmentsPerFile", MAX_SEGMENTS_PER_FILE,
            "minSegmentMB", MIN_SEGMENT_MB,
            "maxRetryCount", MAX_RETRY,
            "connectTimeoutSeconds", CONNECT_TIMEOUT_SECONDS,
            "taskTimeoutSeconds", TASK_TIMEOUT_SECONDS
    );

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
            UPDATE_MANAGER_ON
    );
    private static final List<IConfigBase> DOWNLOAD_CONFIGS = List.of(
            MAX_CONCURRENT_DOWNLOADS,
            MAX_SEGMENTS_PER_FILE,
            MIN_SEGMENT_MB,
            MAX_RETRY,
            CONNECT_TIMEOUT_SECONDS,
            TASK_TIMEOUT_SECONDS
    );
    private static final List<IConfigBase> ALL_CONFIGS;

    private static final IConfigHandler CONFIG_HANDLER = new IConfigHandler() {
        @Override
        public void load() {
            loadFromDisk();
        }

        @Override
        public void save() {
            saveToDisk();
        }

        @Override
        public void onConfigsChanged() {
            saveToDisk();
        }
    };

    static {
        List<IConfigBase> all = new ArrayList<>();
        all.addAll(PATH_CONFIGS);
        all.addAll(UI_CONFIGS);
        all.addAll(DOWNLOAD_CONFIGS);
        ALL_CONFIGS = Collections.unmodifiableList(all);
    }

    public static void load() {
        if (!registered) {
            ConfigManager.getInstance().registerConfigHandler(MOD_ID, CONFIG_HANDLER);
            registered = true;
        }
        CONFIG_HANDLER.load();
        ensureConfigFile();
    }

    private static void ensureConfigFile() {
        Path configDir = PlatformServices.get().configDir().resolve(CONFIG_DIR);
        Path jsonPath = configDir.resolve(CONFIG_NAME);
        if (Files.exists(jsonPath)) {
            return;
        }
        saveToDisk();
    }

    public static void save() {
        CONFIG_HANDLER.save();
    }

    public static List<IConfigBase> getAllConfigOptions() {
        return ALL_CONFIGS;
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
        return stringOrDefault(DLC_ROOT.getStringValue(), DEFAULT_DLC_ROOT);
    }

    public static String getTaczRootRaw() {
        return stringOrDefault(TACZ_ROOT.getStringValue(), DEFAULT_TACZ_DLC_ROOT);
    }

    public static String getResourcepackRootRaw() {
        return stringOrDefault(RESOURCEPACK_ROOT.getStringValue(), DEFAULT_RESOURCEPACK_ROOT);
    }

    public static String getDatapackRootRaw() {
        return stringOrDefault(DATAPACK_ROOT.getStringValue(), DEFAULT_DATAPACK_ROOT);
    }

    public static boolean isEnableAllButtonShown() {
        return ENABLE_ALL_BUTTON.getBooleanValue();
    }

    public static boolean isConfigButtonShown() {
        return CONFIG_BUTTON.getBooleanValue();
    }

    public static boolean isUButtomShown() {
        return U_BUTTON.getBooleanValue();
    }

    public static boolean isUpdateManagerOn() {
        return UPDATE_MANAGER_ON.getBooleanValue();
    }

    public static int getMaxConcurrentDownloads() {
        int value = MAX_CONCURRENT_DOWNLOADS.getIntegerValue();
        if (value < 1) {
            return DEFAULT_MAX_CONCURRENT_DOWNLOADS;
        }
        return value;
    }

    public static int getMaxSegmentsPerFile() {
        int value = MAX_SEGMENTS_PER_FILE.getIntegerValue();
        if (value < 1) {
            return DEFAULT_MAX_SEGMENTS_PER_FILE;
        }
        return value;
    }

    public static long getMinSegmentBytes() {
        int value = MIN_SEGMENT_MB.getIntegerValue();
        if (value < 1) {
            value = DEFAULT_MIN_SEGMENT_MB;
        }
        return (long) value * 1024L * 1024L;
    }

    public static int getMaxRetryCount() {
        int value = MAX_RETRY.getIntegerValue();
        if (value < 1) {
            return DEFAULT_MAX_RETRY;
        }
        return value;
    }

    public static int getConnectTimeoutMs() {
        int value = CONNECT_TIMEOUT_SECONDS.getIntegerValue();
        if (value < 1) {
            value = DEFAULT_CONNECT_TIMEOUT_SECONDS;
        }
        return value * 1000;
    }

    public static int getTaskTimeoutMs() {
        int value = TASK_TIMEOUT_SECONDS.getIntegerValue();
        if (value < 1) {
            value = DEFAULT_TASK_TIMEOUT_SECONDS;
        }
        return value * 1000;
    }

    public static void setDlcRoot(String value) {
        if (value != null) {
            DLC_ROOT.setValueFromString(value);
        }
    }

    public static void setTaczRoot(String value) {
        if (value != null) {
            TACZ_ROOT.setValueFromString(value);
        }
    }

    public static void setResourcepackRoot(String value) {
        if (value != null) {
            RESOURCEPACK_ROOT.setValueFromString(value);
        }
    }

    public static void setDatapackRoot(String value) {
        if (value != null) {
            DATAPACK_ROOT.setValueFromString(value);
        }
    }

    public static void setEnableAllButtonShown(boolean value) {
        ENABLE_ALL_BUTTON.setBooleanValue(value);
    }

    public static void setConfigButtonShown(boolean value) {
        CONFIG_BUTTON.setBooleanValue(value);
    }

    public static void setUButtomShown(boolean value) {
        U_BUTTON.setBooleanValue(value);
    }

    public static void setUpdateManagerOn(boolean value) {
        UPDATE_MANAGER_ON.setBooleanValue(value);
    }

    public static void setMaxConcurrentDownloads(int value) {
        MAX_CONCURRENT_DOWNLOADS.setIntegerValue(value);
    }

    public static void setMaxSegmentsPerFile(int value) {
        MAX_SEGMENTS_PER_FILE.setIntegerValue(value);
    }

    public static void setMinSegmentBytes(long value) {
        long mb = Math.max(1L, (value + (1024L * 1024L - 1)) / (1024L * 1024L));
        long clamped = Math.min(Integer.MAX_VALUE, mb);
        MIN_SEGMENT_MB.setIntegerValue((int) clamped);
    }

    public static void setMaxRetryCount(int value) {
        MAX_RETRY.setIntegerValue(value);
    }

    public static void setConnectTimeoutMs(int value) {
        int seconds = Math.max(1, (int) Math.ceil(value / 1000.0));
        CONNECT_TIMEOUT_SECONDS.setIntegerValue(seconds);
    }

    public static void setTaskTimeoutMs(int value) {
        int seconds = Math.max(1, (int) Math.ceil(value / 1000.0));
        TASK_TIMEOUT_SECONDS.setIntegerValue(seconds);
    }

    private static Path resolvePath(String value, String fallback) {
        String raw = value == null || value.isBlank() ? fallback : value;
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            path = PlatformServices.get().gameDir().resolve(path);
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
        java.util.LinkedHashSet<Path> paths = new java.util.LinkedHashSet<>();
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
                for (Path dlcRoot : dlcRoots) {
                    Path resolved = dlcRoot.resolve(trimmed).normalize();
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
            for (Path dlcRoot : dlcRoots) {
                Path resolved = dlcRoot.resolve(fallback).normalize();
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

    private static void loadFromDisk() {
        Path configDir = PlatformServices.get().configDir().resolve(CONFIG_DIR);
        Path jsonPath = configDir.resolve(CONFIG_NAME);
        if (Files.exists(jsonPath)) {
            loadFromJson(jsonPath);
            return;
        }
    }

    private static void loadFromJson(Path jsonPath) {
        JsonElement element = JsonUtils.parseJsonFile(jsonPath.toFile());
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject root = element.getAsJsonObject();
        readSection(root, "paths", PATH_KEY_MAP);
        readSection(root, "ui", UI_KEY_MAP);
        readSection(root, "downloads", DOWNLOAD_KEY_MAP);
    }

    private static void saveToDisk() {
        Path configDir = PlatformServices.get().configDir().resolve(CONFIG_DIR);
        Path jsonPath = configDir.resolve(CONFIG_NAME);
        try {
            Files.createDirectories(configDir);
        } catch (IOException ignored) {
            return;
        }
        JsonObject root = new JsonObject();
        writeSection(root, "paths", PATH_KEY_MAP);
        writeSection(root, "ui", UI_KEY_MAP);
        writeSection(root, "downloads", DOWNLOAD_KEY_MAP);
        JsonUtils.writeJsonToFile(root, jsonPath.toFile());
    }

    private static class DlcConfigString extends ConfigString {
        private final String translationKey;

        private DlcConfigString(String name, String defaultValue, String translationKey) {
            super(name, defaultValue, "");
            this.translationKey = translationKey;
        }

        @Override
        public String getConfigGuiDisplayName() {
            return StringUtils.translate(this.translationKey);
        }
    }

    private static class DlcConfigBoolean extends ConfigBoolean {
        private final String translationKey;

        private DlcConfigBoolean(String name, boolean defaultValue, String translationKey) {
            super(name, defaultValue, "", name);
            this.translationKey = translationKey;
        }

        @Override
        public String getConfigGuiDisplayName() {
            return StringUtils.translate(this.translationKey);
        }
    }

    private static class DlcConfigInteger extends ConfigInteger {
        private final String translationKey;

        private DlcConfigInteger(String name, int defaultValue, int min, int max, String comment, String translationKey) {
            super(name, defaultValue, min, max, comment);
            this.translationKey = translationKey;
        }

        @Override
        public String getConfigGuiDisplayName() {
            return StringUtils.translate(this.translationKey);
        }
    }

    private static String stringOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static void readSection(JsonObject root, String section, Map<String, IConfigBase> keyMap) {
        if (root == null) {
            return;
        }
        JsonObject obj = root.getAsJsonObject(section);
        if (obj == null) {
            for (String key : root.keySet()) {
                if (key.equalsIgnoreCase(section)) {
                    obj = root.getAsJsonObject(key);
                    break;
                }
            }
        }
        if (obj == null) {
            return;
        }
        for (Map.Entry<String, IConfigBase> entry : keyMap.entrySet()) {
            if (obj.has(entry.getKey())) {
                entry.getValue().setValueFromJsonElement(obj.get(entry.getKey()));
            }
        }
    }

    private static void writeSection(JsonObject root, String section, Map<String, IConfigBase> keyMap) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, IConfigBase> entry : keyMap.entrySet()) {
            obj.add(entry.getKey(), entry.getValue().getAsJsonElement());
        }
        root.add(section, obj);
    }
}
