package com.cozooo.dlc_manager.ui.config;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class DlcFxConfigModel {
    private static final List<String> ANCHOR_VALUES_WITH_AUTO = List.of(
            "auto", "top_left", "top", "top_right", "left", "center", "right", "bottom_left", "bottom", "bottom_right"
    );
    private static final List<String> TITLE_ANCHOR_VALUES = List.of(
            "auto", DlcConfig.TITLE_ANCHOR_HIDDEN, "top_left", "top", "top_right", "left", "center", "right", "bottom_left", "bottom", "bottom_right"
    );
    private static final List<String> ANCHOR_VALUES = List.of(
            "top_left", "top", "top_right", "left", "center", "right", "bottom_left", "bottom", "bottom_right"
    );
    private static final List<String> NOTICE_DISPLAY_MODE_VALUES = List.of(
            "manager_button", "main_menu_button", "main_menu_panel"
    );
    private static final List<String> DOWNLOAD_MIRROR_MODE_VALUES = List.of(
            DlcConfig.DOWNLOAD_MIRROR_SMART_OFFICIAL,
            DlcConfig.DOWNLOAD_MIRROR_SMART_MIRROR,
            DlcConfig.DOWNLOAD_MIRROR_SMART_PRECHECK,
            DlcConfig.DOWNLOAD_MIRROR_OFFICIAL_ONLY,
            DlcConfig.DOWNLOAD_MIRROR_MIRROR_ONLY,
            DlcConfig.DOWNLOAD_MIRROR_OFFICIAL_FIRST,
            DlcConfig.DOWNLOAD_MIRROR_MIRROR_FIRST
    );
    private static final List<String> DOWNLOAD_MIRROR_PROBE_STRATEGY_VALUES = List.of(
            DlcConfig.DOWNLOAD_MIRROR_PROBE_SINGLE,
            DlcConfig.DOWNLOAD_MIRROR_PROBE_MAJORITY
    );

    private final ConfigSource source;
    private final Map<Tab, List<Option>> groups = new EnumMap<>(Tab.class);
    private final Map<String, Option> byName = new LinkedHashMap<>();

    public DlcFxConfigModel() {
        this(localSource());
    }

    public DlcFxConfigModel(ConfigSource source) {
        this.source = source == null ? localSource() : source;
        register(Tab.PATHS,
                string("dlcRoot", "paths", "dlcRoot", "dlc_manager.config.paths.dlcRoot", "dlc_manager.config.comment.paths.dlcRoot", DlcConfig.DEFAULT_DLC_ROOT),
                string("taczRoot", "paths", "taczRoot", "dlc_manager.config.paths.taczRoot", "dlc_manager.config.comment.paths.taczRoot", DlcConfig.DEFAULT_TACZ_DLC_ROOT),
                string("resourcepackRoot", "paths", "resourcepackRoot", "dlc_manager.config.paths.resourcepackRoot", "dlc_manager.config.comment.paths.resourcepackRoot", DlcConfig.DEFAULT_RESOURCEPACK_ROOT),
                string("datapackRoot", "paths", "datapackRoot", "dlc_manager.config.paths.datapackRoot", "dlc_manager.config.comment.paths.datapackRoot", DlcConfig.DEFAULT_DATAPACK_ROOT));

        List<Option> serverOptions = new ArrayList<>();
        serverOptions.add(bool("serverSupport", "ui", "serverSupport", "dlc_manager.config.ui.serverSupport", "dlc_manager.config.comment.ui.serverSupport", DlcConfig.DEFAULT_SERVER_SUPPORT));
        serverOptions.add(bool("apiEnabled", "api", "apiEnabled", "dlc_manager.config.ui.apiEnabled", "dlc_manager.config.comment.ui.apiEnabled", DlcConfig.DEFAULT_API_ENABLED));
        serverOptions.add(integer("apiPort", "api", "apiPort", "dlc_manager.config.ui.apiPort", "dlc_manager.config.comment.ui.apiPort", DlcConfig.DEFAULT_API_PORT, 0, 65535));
        serverOptions.add(string("apiToken", "api", "apiToken", "dlc_manager.config.ui.apiToken", "dlc_manager.config.comment.ui.apiToken", DlcConfig.DEFAULT_API_TOKEN));
        if (this.source.hasSection("server")) {
            serverOptions.add(integer("serverPort", "server", "serverPort", "dlc_manager.config.ui.serverPort", "dlc_manager.config.comment.ui.serverPort", DlcConfig.DEFAULT_SERVER_PORT, 1, 65535));
            serverOptions.add(string("serverPassword", "server", "serverPassword", "dlc_manager.config.ui.serverPassword", "dlc_manager.config.comment.ui.serverPassword", DlcConfig.DEFAULT_SERVER_PASSWORD));
            serverOptions.add(string("rconHost", "server", "rconHost", "dlc_manager.config.ui.rconHost", "dlc_manager.config.comment.ui.rconHost", DlcConfig.DEFAULT_RCON_HOST));
            serverOptions.add(integer("rconPort", "server", "rconPort", "dlc_manager.config.ui.rconPort", "dlc_manager.config.comment.ui.rconPort", DlcConfig.DEFAULT_RCON_PORT, 1, 65535));
            serverOptions.add(string("rconPassword", "server", "rconPassword", "dlc_manager.config.ui.rconPassword", "dlc_manager.config.comment.ui.rconPassword", DlcConfig.DEFAULT_RCON_PASSWORD));
        }
        register(Tab.SERVER, serverOptions.toArray(Option[]::new));

        register(Tab.UI,
                bool("isEnableAllButtonShown", "ui", "isEnableAllButtonShown", "dlc_manager.config.ui.isEnableAllButtonShown", "dlc_manager.config.comment.ui.isEnableAllButtonShown", DlcConfig.DEFAULT_ENABLE_ALL_SHOWN),
                bool("isConfigButtonShown", "ui", "isConfigButtonShown", "dlc_manager.config.ui.isConfigButtonShown", "dlc_manager.config.comment.ui.isConfigButtonShown", DlcConfig.DEFAULT_CONFIG_BUTTON_SHOWN),
                bool("isUButtomShown", "ui", "isUButtomShown", "dlc_manager.config.ui.isUButtomShown", "dlc_manager.config.comment.ui.isUButtomShown", DlcConfig.DEFAULT_U_BUTTON_SHOWN),
                bool("isUpdateManagerOn", "ui", "isUpdateManagerOn", "dlc_manager.config.ui.isUpdateManagerOn", "dlc_manager.config.comment.ui.isUpdateManagerOn", DlcConfig.DEFAULT_UPDATE_MANAGER_ON),
                bool("isSelfUpdateAutoCheckOn", "ui", "isSelfUpdateAutoCheckOn", "dlc_manager.config.ui.isSelfUpdateAutoCheckOn", "dlc_manager.config.comment.ui.isSelfUpdateAutoCheckOn", DlcConfig.DEFAULT_SELF_UPDATE_AUTO_CHECK_ON),
                bool("isPresetFuncOn", "ui", "isPresetFuncOn", "dlc_manager.config.ui.isPresetFuncOn", "dlc_manager.config.comment.ui.isPresetFuncOn", DlcConfig.DEFAULT_PRESET_FUNC_ON),
                bool("isPackFuncOn", "ui", "isPackFuncOn", "dlc_manager.config.ui.isPackFuncOn", "dlc_manager.config.comment.ui.isPackFuncOn", DlcConfig.DEFAULT_PACK_FUNC_ON),
                bool("isPButtomShown", "ui", "isPButtomShown", "dlc_manager.config.ui.isPButtomShown", "dlc_manager.config.comment.ui.isPButtomShown", DlcConfig.DEFAULT_P_BUTTON_SHOWN),
                bool("ShowOnFirstRun", "ui", "ShowOnFirstRun", "dlc_manager.config.ui.ShowOnFirstRun", "dlc_manager.config.comment.ui.ShowOnFirstRun", DlcConfig.DEFAULT_SHOW_ON_FIRST_RUN),
                integer("WaitingTime", "ui", "WaitingTime", "dlc_manager.config.ui.WaitingTime", "dlc_manager.config.comment.ui.WaitingTime", DlcConfig.DEFAULT_ALARM_WAITING_TIME, 0, 10),
                choice("noticeDisplayMode", "ui", "noticeDisplayMode", "dlc_manager.config.ui.noticeDisplayMode", "dlc_manager.config.comment.ui.noticeDisplayMode", DlcConfig.DEFAULT_NOTICE_DISPLAY_MODE, NOTICE_DISPLAY_MODE_VALUES, "dlc_manager.config.ui.noticeDisplayMode."),
                choice("noticeButtonAnchor", "ui", "noticeButtonAnchor", "dlc_manager.config.ui.noticeButtonAnchor", "dlc_manager.config.comment.ui.noticeButtonAnchor", DlcConfig.DEFAULT_NOTICE_BUTTON_ANCHOR, ANCHOR_VALUES, "dlc_manager.config.ui.noticeButtonAnchor."),
                integer("noticeButtonOffsetX", "ui", "noticeButtonOffsetX", "dlc_manager.config.ui.noticeButtonOffsetX", "dlc_manager.config.comment.ui.noticeButtonOffsetX", DlcConfig.DEFAULT_NOTICE_BUTTON_OFFSET_X, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET),
                integer("noticeButtonOffsetY", "ui", "noticeButtonOffsetY", "dlc_manager.config.ui.noticeButtonOffsetY", "dlc_manager.config.comment.ui.noticeButtonOffsetY", DlcConfig.DEFAULT_NOTICE_BUTTON_OFFSET_Y, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET),
                choice("noticePanelAnchor", "ui", "noticePanelAnchor", "dlc_manager.config.ui.noticePanelAnchor", "dlc_manager.config.comment.ui.noticePanelAnchor", DlcConfig.DEFAULT_NOTICE_PANEL_ANCHOR, ANCHOR_VALUES, "dlc_manager.config.ui.noticePanelAnchor."),
                integer("noticePanelOffsetX", "ui", "noticePanelOffsetX", "dlc_manager.config.ui.noticePanelOffsetX", "dlc_manager.config.comment.ui.noticePanelOffsetX", DlcConfig.DEFAULT_NOTICE_PANEL_OFFSET_X, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET),
                integer("noticePanelOffsetY", "ui", "noticePanelOffsetY", "dlc_manager.config.ui.noticePanelOffsetY", "dlc_manager.config.comment.ui.noticePanelOffsetY", DlcConfig.DEFAULT_NOTICE_PANEL_OFFSET_Y, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET),
                integer("noticePanelWidth", "ui", "noticePanelWidth", "dlc_manager.config.ui.noticePanelWidth", "dlc_manager.config.comment.ui.noticePanelWidth", DlcConfig.DEFAULT_NOTICE_PANEL_WIDTH, 0, DlcConfig.MAX_NOTICE_PANEL_SIZE),
                integer("noticePanelHeight", "ui", "noticePanelHeight", "dlc_manager.config.ui.noticePanelHeight", "dlc_manager.config.comment.ui.noticePanelHeight", DlcConfig.DEFAULT_NOTICE_PANEL_HEIGHT, 0, DlcConfig.MAX_NOTICE_PANEL_SIZE),
                choice("titleAnchor", "ui", "titleAnchor", "dlc_manager.config.ui.titleAnchor", "dlc_manager.config.comment.ui.titleAnchor", DlcConfig.DEFAULT_TITLE_ANCHOR, TITLE_ANCHOR_VALUES, "dlc_manager.config.ui.titleAnchor."),
                integer("titleOffsetX", "ui", "titleOffsetX", "dlc_manager.config.ui.titleOffsetX", "dlc_manager.config.comment.ui.titleOffsetX", DlcConfig.DEFAULT_TITLE_OFFSET_X, -10000, 10000),
                integer("titleOffsetY", "ui", "titleOffsetY", "dlc_manager.config.ui.titleOffsetY", "dlc_manager.config.comment.ui.titleOffsetY", DlcConfig.DEFAULT_TITLE_OFFSET_Y, -10000, 10000),
                choice("titleUpdateAnchor", "ui", "titleUpdateAnchor", "dlc_manager.config.ui.titleUpdateAnchor", "dlc_manager.config.comment.ui.titleUpdateAnchor", DlcConfig.DEFAULT_TITLE_UPDATE_ANCHOR, ANCHOR_VALUES_WITH_AUTO, "dlc_manager.config.ui.titleUpdateAnchor."),
                integer("titleUpdateOffsetX", "ui", "titleUpdateOffsetX", "dlc_manager.config.ui.titleUpdateOffsetX", "dlc_manager.config.comment.ui.titleUpdateOffsetX", DlcConfig.DEFAULT_TITLE_UPDATE_OFFSET_X, -10000, 10000),
                integer("titleUpdateOffsetY", "ui", "titleUpdateOffsetY", "dlc_manager.config.ui.titleUpdateOffsetY", "dlc_manager.config.comment.ui.titleUpdateOffsetY", DlcConfig.DEFAULT_TITLE_UPDATE_OFFSET_Y, -10000, 10000));

        register(Tab.MODPACK,
                string("modpack", "modpack", "modpack", "dlc_manager.config.modpack.modpack", "dlc_manager.config.comment.modpack.modpack", DlcConfig.DEFAULT_MODPACK),
                string("modpackUrl", "modpack", "modpackUrl", "dlc_manager.config.modpack.modpackUrl", "dlc_manager.config.comment.modpack.modpackUrl", DlcConfig.DEFAULT_MODPACK_URL),
                string("updateGitRepo", "modpack", "updateGitRepo", "dlc_manager.config.modpack.updateGitRepo", "dlc_manager.config.comment.modpack.updateGitRepo", DlcConfig.DEFAULT_UPDATE_GIT_REPO_URL),
                bool("autoUpdate", "modpack", "autoUpdate", "dlc_manager.config.modpack.autoUpdate", "dlc_manager.config.comment.modpack.autoUpdate", DlcConfig.DEFAULT_MODPACK_AUTO_UPDATE),
                bool("forceUpdate", "modpack", "forceUpdate", "dlc_manager.config.modpack.forceUpdate", "dlc_manager.config.comment.modpack.forceUpdate", DlcConfig.DEFAULT_MODPACK_FORCE_UPDATE));

        register(Tab.DOWNLOADS,
                integer("maxConcurrentDownloads", "downloads", "maxConcurrentDownloads", "dlc_manager.config.download.maxConcurrentDownloads", "dlc_manager.config.comment.download.maxConcurrentDownloads", DlcConfig.DEFAULT_MAX_CONCURRENT_DOWNLOADS, 1, 64),
                integer("maxSegmentsPerFile", "downloads", "maxSegmentsPerFile", "dlc_manager.config.download.maxSegmentsPerFile", "dlc_manager.config.comment.download.maxSegmentsPerFile", DlcConfig.DEFAULT_MAX_SEGMENTS_PER_FILE, 1, 64),
                integer("minSegmentMB", "downloads", "minSegmentMB", "dlc_manager.config.download.minSegmentMB", "dlc_manager.config.comment.download.minSegmentMB", DlcConfig.DEFAULT_MIN_SEGMENT_MB, 1, Integer.MAX_VALUE),
                integer("maxRetryCount", "downloads", "maxRetryCount", "dlc_manager.config.download.maxRetryCount", "dlc_manager.config.comment.download.maxRetryCount", DlcConfig.DEFAULT_MAX_RETRY, 1, 100),
                integer("connectTimeoutSeconds", "downloads", "connectTimeoutSeconds", "dlc_manager.config.download.connectTimeoutSeconds", "dlc_manager.config.comment.download.connectTimeoutSeconds", DlcConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, 1, 36000),
                integer("taskTimeoutSeconds", "downloads", "taskTimeoutSeconds", "dlc_manager.config.download.taskTimeoutSeconds", "dlc_manager.config.comment.download.taskTimeoutSeconds", DlcConfig.DEFAULT_TASK_TIMEOUT_SECONDS, 1, 36000),
                choice("downloadMirrorMode", "downloads", "downloadMirrorMode", "dlc_manager.config.download.downloadMirrorMode", "dlc_manager.config.comment.download.downloadMirrorMode", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_MODE, DOWNLOAD_MIRROR_MODE_VALUES, "dlc_manager.config.download.downloadMirrorMode."),
                integer("downloadMirrorProbeCount", "downloads", "downloadMirrorProbeCount", "dlc_manager.config.download.downloadMirrorProbeCount", "dlc_manager.config.comment.download.downloadMirrorProbeCount", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_COUNT, 1, 10),
                choice("downloadMirrorProbeStrategy", "downloads", "downloadMirrorProbeStrategy", "dlc_manager.config.download.downloadMirrorProbeStrategy", "dlc_manager.config.comment.download.downloadMirrorProbeStrategy", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY, DOWNLOAD_MIRROR_PROBE_STRATEGY_VALUES, "dlc_manager.config.download.downloadMirrorProbeStrategy."));
        loadFromConfig();
    }

    public static ConfigSource localSource() {
        return new JsonConfigSource(DlcConfig::buildSharedConfigJson, json -> {
            DlcConfig.applySharedConfigJson(json);
            DlcConfig.save();
        });
    }

    public static ConfigSource serverSource(JsonObject config, Consumer<JsonObject> onApply) {
        JsonObject[] holder = new JsonObject[] { copy(config) };
        return new JsonConfigSource(() -> holder[0], json -> {
            holder[0] = copy(json);
            if (onApply != null) {
                onApply.accept(json);
            }
        }, name -> !isHiddenServerOption(name));
    }

    public List<Option> options(Tab tab) {
        return groups.getOrDefault(tab, List.of());
    }

    public Option find(String name) {
        Option option = byName.get(name);
        if (option == null) {
            throw new IllegalArgumentException("Unknown config option: " + name);
        }
        return option;
    }

    public JsonObject snapshot() {
        return source.snapshot();
    }

    public void loadFromConfig() {
        source.reload();
        for (Option option : byName.values()) {
            option.load();
        }
    }

    public void applyToConfig() {
        for (Option option : byName.values()) {
            option.apply();
        }
        source.apply();
        loadFromConfig();
    }

    public boolean modified() {
        for (Option option : byName.values()) {
            if (option.modified()) {
                return true;
            }
        }
        return false;
    }

    private void register(Tab tab, Option... options) {
        List<Option> list = new ArrayList<>();
        groups.put(tab, list);
        for (Option option : options) {
            if (!source.isVisible(option.name())) {
                continue;
            }
            list.add(option);
            byName.put(option.name(), option);
        }
    }

    private static boolean isHiddenServerOption(String name) {
        return Objects.equals(name, "serverSupport")
                || Objects.equals(name, "rconHost")
                || Objects.equals(name, "rconPort")
                || Objects.equals(name, "rconPassword");
    }

    private Option string(String name, String section, String key, String labelKey, String commentKey, String defaultValue) {
        return new Option(source, name, section, key, OptionType.STRING, labelKey, commentKey, defaultValue, null, null);
    }

    private Option bool(String name, String section, String key, String labelKey, String commentKey, boolean defaultValue) {
        return new Option(source, name, section, key, OptionType.BOOLEAN, labelKey, commentKey, String.valueOf(defaultValue), null, null);
    }

    private Option integer(String name, String section, String key, String labelKey, String commentKey, int defaultValue, int min, int max) {
        return new Option(source, name, section, key, OptionType.INTEGER, labelKey, commentKey, String.valueOf(defaultValue), min, max);
    }

    private Option choice(String name, String section, String key, String labelKey, String commentKey, String defaultValue, List<String> choices, String choiceLabelPrefix) {
        return new Option(source, name, section, key, OptionType.CHOICE, labelKey, commentKey, defaultValue, null, null, choices, choiceLabelPrefix);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static JsonObject copy(JsonObject source) {
        return source == null ? new JsonObject() : source.deepCopy();
    }

    public enum Tab {
        PATHS("dlc_manager.config.tab.paths"),
        SERVER("dlc_manager.config.tab.server"),
        UI("dlc_manager.config.tab.ui"),
        MODPACK("dlc_manager.config.tab.modpack"),
        DOWNLOADS("dlc_manager.config.tab.download");

        private final String labelKey;

        Tab(String labelKey) {
            this.labelKey = labelKey;
        }

        public String label() {
            return DlcFxI18n.tr(labelKey);
        }
    }

    public enum OptionType {
        STRING,
        INTEGER,
        BOOLEAN,
        CHOICE
    }

    public interface ConfigSource {
        String get(String section, String key, String fallback);
        void set(String section, String key, String value, OptionType type);
        boolean hasSection(String section);
        default boolean isVisible(String name) {
            return true;
        }
        void reload();
        void apply();
        JsonObject snapshot();
    }

    private static final class JsonConfigSource implements ConfigSource {
        private final Supplier<JsonObject> loader;
        private final Consumer<JsonObject> applier;
        private final Predicate<String> visibleOption;
        private JsonObject config = new JsonObject();

        private JsonConfigSource(Supplier<JsonObject> loader, Consumer<JsonObject> applier) {
            this(loader, applier, name -> true);
        }

        private JsonConfigSource(Supplier<JsonObject> loader, Consumer<JsonObject> applier, Predicate<String> visibleOption) {
            this.loader = loader;
            this.applier = applier;
            this.visibleOption = visibleOption == null ? name -> true : visibleOption;
            reload();
        }

        @Override
        public String get(String section, String key, String fallback) {
            JsonObject obj = DlcDcConfigIO.getObjectIgnoreCase(config, section);
            JsonElement element = obj == null ? null : DlcDcConfigIO.getIgnoreCase(obj, key);
            if (element == null || !element.isJsonPrimitive()) {
                return fallback;
            }
            try {
                return element.getAsString();
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        @Override
        public void set(String section, String key, String value, OptionType type) {
            JsonObject obj = DlcDcConfigIO.getObjectIgnoreCase(config, section);
            if (obj == null) {
                obj = new JsonObject();
                config.add(section, obj);
            }
            switch (type) {
                case BOOLEAN -> obj.addProperty(key, Boolean.parseBoolean(value));
                case INTEGER -> obj.addProperty(key, parseInt(value, 0));
                case CHOICE, STRING -> obj.addProperty(key, value == null ? "" : value);
            }
        }

        @Override
        public boolean hasSection(String section) {
            return DlcDcConfigIO.getObjectIgnoreCase(config, section) != null;
        }

        @Override
        public boolean isVisible(String name) {
            return visibleOption.test(name);
        }

        @Override
        public void reload() {
            config = copy(loader == null ? null : loader.get());
        }

        @Override
        public void apply() {
            if (applier != null) {
                applier.accept(snapshot());
            }
        }

        @Override
        public JsonObject snapshot() {
            return copy(config);
        }
    }

    public static final class Option {
        private final ConfigSource source;
        private final String name;
        private final String section;
        private final String key;
        private final OptionType type;
        private final String labelKey;
        private final String commentKey;
        private final String defaultValue;
        private final Integer min;
        private final Integer max;
        private final List<String> choiceValues;
        private final String choiceLabelPrefix;
        private String value;

        private Option(ConfigSource source, String name, String section, String key, OptionType type, String labelKey, String commentKey, String defaultValue, Integer min, Integer max) {
            this(source, name, section, key, type, labelKey, commentKey, defaultValue, min, max, List.of(), null);
        }

        private Option(ConfigSource source, String name, String section, String key, OptionType type, String labelKey, String commentKey, String defaultValue, Integer min, Integer max, List<String> choiceValues, String choiceLabelPrefix) {
            this.source = source;
            this.name = name;
            this.section = section;
            this.key = key;
            this.type = type;
            this.labelKey = labelKey;
            this.commentKey = commentKey;
            this.defaultValue = defaultValue == null ? "" : defaultValue;
            this.min = min;
            this.max = max;
            this.choiceValues = List.copyOf(choiceValues);
            this.choiceLabelPrefix = choiceLabelPrefix;
            this.value = this.defaultValue;
        }

        public String name() {
            return name;
        }

        public OptionType type() {
            return type;
        }

        public String label() {
            return DlcFxI18n.tr(labelKey);
        }

        public String comment() {
            return commentKey == null || commentKey.isBlank() ? "" : DlcFxI18n.tr(commentKey);
        }

        public String value() {
            return value;
        }

        public boolean booleanValue() {
            return Boolean.parseBoolean(value);
        }

        public int integerValue() {
            return parseInt(value, parseInt(defaultValue, 0));
        }

        public Integer min() {
            return min;
        }

        public Integer max() {
            return max;
        }

        public List<String> choiceValues() {
            return choiceValues;
        }

        public String choiceLabel(String rawValue) {
            if (choiceLabelPrefix == null || rawValue == null) {
                return rawValue == null ? "" : rawValue;
            }
            String key = choiceLabelPrefix + rawValue;
            String label = DlcFxI18n.tr(key);
            return key.equals(label) ? rawValue : label;
        }

        public void load() {
            String loaded = source.get(section, key, defaultValue);
            value = normalize(loaded == null ? defaultValue : loaded);
        }

        public void apply() {
            source.set(section, key, normalize(value), type);
            load();
        }

        public boolean modified() {
            String current = source.get(section, key, defaultValue);
            return !Objects.equals(normalize(current == null ? defaultValue : current), normalize(value));
        }

        public boolean isDefaultValue() {
            return Objects.equals(normalize(defaultValue), normalize(value));
        }

        public boolean setValueFromString(String next) {
            String normalized = normalize(next);
            boolean changed = !Objects.equals(value, normalized);
            value = normalized;
            return changed;
        }

        public void resetToDefault() {
            value = normalize(defaultValue);
        }

        private String normalize(String raw) {
            String next = raw == null ? "" : raw;
            return switch (type) {
                case BOOLEAN -> String.valueOf(Boolean.parseBoolean(next));
                case INTEGER -> String.valueOf(clamp(parseInt(next, parseInt(defaultValue, 0)), min == null ? Integer.MIN_VALUE : min, max == null ? Integer.MAX_VALUE : max));
                case CHOICE -> {
                    String normalized = next.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
                    yield choiceValues.contains(normalized) ? normalized : defaultValue;
                }
                case STRING -> next;
            };
        }
    }
}
