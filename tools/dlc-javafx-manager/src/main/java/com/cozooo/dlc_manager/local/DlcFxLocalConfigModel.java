package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.ui.DlcFxI18n;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DlcFxLocalConfigModel {
    private static final List<String> ANCHOR_VALUES_WITH_AUTO = List.of(
            "auto",
            "top_left",
            "top",
            "top_right",
            "left",
            "center",
            "right",
            "bottom_left",
            "bottom",
            "bottom_right"
    );
    private static final List<String> ANCHOR_VALUES = List.of(
            "top_left",
            "top",
            "top_right",
            "left",
            "center",
            "right",
            "bottom_left",
            "bottom",
            "bottom_right"
    );
    private static final List<String> NOTICE_DISPLAY_MODE_VALUES = List.of(
            "manager_button",
            "main_menu_button",
            "main_menu_panel"
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

    private final Map<Tab, List<Option>> groups = new EnumMap<>(Tab.class);
    private final Map<String, Option> byName = new LinkedHashMap<>();

    public DlcFxLocalConfigModel() {
        register(Tab.PATHS,
                string("dlcRoot", "dlc_manager.config.paths.dlcRoot", "dlc_manager.config.comment.paths.dlcRoot", DlcConfig.DEFAULT_DLC_ROOT, DlcConfig::getDlcRootRaw, DlcConfig::setDlcRoot),
                string("taczRoot", "dlc_manager.config.paths.taczRoot", "dlc_manager.config.comment.paths.taczRoot", DlcConfig.DEFAULT_TACZ_DLC_ROOT, DlcConfig::getTaczRootRaw, DlcConfig::setTaczRoot),
                string("resourcepackRoot", "dlc_manager.config.paths.resourcepackRoot", "dlc_manager.config.comment.paths.resourcepackRoot", DlcConfig.DEFAULT_RESOURCEPACK_ROOT, DlcConfig::getResourcepackRootRaw, DlcConfig::setResourcepackRoot),
                string("datapackRoot", "dlc_manager.config.paths.datapackRoot", "dlc_manager.config.comment.paths.datapackRoot", DlcConfig.DEFAULT_DATAPACK_ROOT, DlcConfig::getDatapackRootRaw, DlcConfig::setDatapackRoot));

        register(Tab.SERVER,
                bool("serverSupport", "dlc_manager.config.ui.serverSupport", "dlc_manager.config.comment.ui.serverSupport", DlcConfig.DEFAULT_SERVER_SUPPORT, DlcConfig::isServerSupportOn, DlcConfig::setServerSupportOn),
                bool("apiEnabled", "dlc_manager.config.ui.apiEnabled", "dlc_manager.config.comment.ui.apiEnabled", DlcConfig.DEFAULT_API_ENABLED, DlcConfig::isApiEnabled, DlcConfig::setApiEnabled),
                integer("apiPort", "dlc_manager.config.ui.apiPort", "dlc_manager.config.comment.ui.apiPort", DlcConfig.DEFAULT_API_PORT, 0, 65535, DlcConfig::getApiPort, DlcConfig::setApiPort),
                string("apiToken", "dlc_manager.config.ui.apiToken", "dlc_manager.config.comment.ui.apiToken", DlcConfig.DEFAULT_API_TOKEN, DlcConfig::getApiToken, DlcConfig::setApiToken));

        register(Tab.UI,
                bool("isEnableAllButtonShown", "dlc_manager.config.ui.isEnableAllButtonShown", "dlc_manager.config.comment.ui.isEnableAllButtonShown", DlcConfig.DEFAULT_ENABLE_ALL_SHOWN, DlcConfig::isEnableAllButtonShown, DlcConfig::setEnableAllButtonShown),
                bool("isConfigButtonShown", "dlc_manager.config.ui.isConfigButtonShown", "dlc_manager.config.comment.ui.isConfigButtonShown", DlcConfig.DEFAULT_CONFIG_BUTTON_SHOWN, DlcConfig::isConfigButtonShown, DlcConfig::setConfigButtonShown),
                bool("isUButtomShown", "dlc_manager.config.ui.isUButtomShown", "dlc_manager.config.comment.ui.isUButtomShown", DlcConfig.DEFAULT_U_BUTTON_SHOWN, DlcConfig::isUButtomShown, DlcConfig::setUButtomShown),
                bool("isUpdateManagerOn", "dlc_manager.config.ui.isUpdateManagerOn", "dlc_manager.config.comment.ui.isUpdateManagerOn", DlcConfig.DEFAULT_UPDATE_MANAGER_ON, DlcConfig::isUpdateManagerOn, DlcConfig::setUpdateManagerOn),
                bool("isSelfUpdateAutoCheckOn", "dlc_manager.config.ui.isSelfUpdateAutoCheckOn", "dlc_manager.config.comment.ui.isSelfUpdateAutoCheckOn", DlcConfig.DEFAULT_SELF_UPDATE_AUTO_CHECK_ON, DlcConfig::isSelfUpdateAutoCheckOn, DlcConfig::setSelfUpdateAutoCheckOn),
                bool("isPresetFuncOn", "dlc_manager.config.ui.isPresetFuncOn", "dlc_manager.config.comment.ui.isPresetFuncOn", DlcConfig.DEFAULT_PRESET_FUNC_ON, DlcConfig::isPresetFuncOn, DlcConfig::setPresetFuncOn),
                bool("isPackFuncOn", "dlc_manager.config.ui.isPackFuncOn", "dlc_manager.config.comment.ui.isPackFuncOn", DlcConfig.DEFAULT_PACK_FUNC_ON, DlcConfig::isPackFuncOn, DlcConfig::setPackFuncOn),
                bool("isPButtomShown", "dlc_manager.config.ui.isPButtomShown", "dlc_manager.config.comment.ui.isPButtomShown", DlcConfig.DEFAULT_P_BUTTON_SHOWN, DlcConfig::isPButtomShown, DlcConfig::setPButtomShown),
                bool("ShowOnFirstRun", "dlc_manager.config.ui.ShowOnFirstRun", "dlc_manager.config.comment.ui.ShowOnFirstRun", DlcConfig.DEFAULT_SHOW_ON_FIRST_RUN, DlcConfig::isShowOnFirstRun, DlcConfig::setShowOnFirstRun),
                integer("WaitingTime", "dlc_manager.config.ui.WaitingTime", "dlc_manager.config.comment.ui.WaitingTime", DlcConfig.DEFAULT_ALARM_WAITING_TIME, 0, 10, DlcConfig::getAlarmWaitingTime, DlcConfig::setAlarmWaitingTime),
                choice("noticeDisplayMode", "dlc_manager.config.ui.noticeDisplayMode", "dlc_manager.config.comment.ui.noticeDisplayMode", DlcConfig.DEFAULT_NOTICE_DISPLAY_MODE, NOTICE_DISPLAY_MODE_VALUES, DlcConfig::getNoticeDisplayMode, DlcConfig::setNoticeDisplayMode, "dlc_manager.config.ui.noticeDisplayMode."),
                choice("noticeButtonAnchor", "dlc_manager.config.ui.noticeButtonAnchor", "dlc_manager.config.comment.ui.noticeButtonAnchor", DlcConfig.DEFAULT_NOTICE_BUTTON_ANCHOR, ANCHOR_VALUES, DlcConfig::getNoticeButtonAnchor, DlcConfig::setNoticeButtonAnchor, "dlc_manager.config.ui.noticeButtonAnchor."),
                integer("noticeButtonOffsetX", "dlc_manager.config.ui.noticeButtonOffsetX", "dlc_manager.config.comment.ui.noticeButtonOffsetX", DlcConfig.DEFAULT_NOTICE_BUTTON_OFFSET_X, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, DlcConfig::getNoticeButtonOffsetX, DlcConfig::setNoticeButtonOffsetX),
                integer("noticeButtonOffsetY", "dlc_manager.config.ui.noticeButtonOffsetY", "dlc_manager.config.comment.ui.noticeButtonOffsetY", DlcConfig.DEFAULT_NOTICE_BUTTON_OFFSET_Y, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, DlcConfig::getNoticeButtonOffsetY, DlcConfig::setNoticeButtonOffsetY),
                choice("noticePanelAnchor", "dlc_manager.config.ui.noticePanelAnchor", "dlc_manager.config.comment.ui.noticePanelAnchor", DlcConfig.DEFAULT_NOTICE_PANEL_ANCHOR, ANCHOR_VALUES, DlcConfig::getNoticePanelAnchor, DlcConfig::setNoticePanelAnchor, "dlc_manager.config.ui.noticePanelAnchor."),
                integer("noticePanelOffsetX", "dlc_manager.config.ui.noticePanelOffsetX", "dlc_manager.config.comment.ui.noticePanelOffsetX", DlcConfig.DEFAULT_NOTICE_PANEL_OFFSET_X, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, DlcConfig::getNoticePanelOffsetX, DlcConfig::setNoticePanelOffsetX),
                integer("noticePanelOffsetY", "dlc_manager.config.ui.noticePanelOffsetY", "dlc_manager.config.comment.ui.noticePanelOffsetY", DlcConfig.DEFAULT_NOTICE_PANEL_OFFSET_Y, -DlcConfig.MAX_NOTICE_OFFSET, DlcConfig.MAX_NOTICE_OFFSET, DlcConfig::getNoticePanelOffsetY, DlcConfig::setNoticePanelOffsetY),
                integer("noticePanelWidth", "dlc_manager.config.ui.noticePanelWidth", "dlc_manager.config.comment.ui.noticePanelWidth", DlcConfig.DEFAULT_NOTICE_PANEL_WIDTH, 0, DlcConfig.MAX_NOTICE_PANEL_SIZE, DlcConfig::getNoticePanelWidth, DlcConfig::setNoticePanelWidth),
                integer("noticePanelHeight", "dlc_manager.config.ui.noticePanelHeight", "dlc_manager.config.comment.ui.noticePanelHeight", DlcConfig.DEFAULT_NOTICE_PANEL_HEIGHT, 0, DlcConfig.MAX_NOTICE_PANEL_SIZE, DlcConfig::getNoticePanelHeight, DlcConfig::setNoticePanelHeight),
                choice("titleAnchor", "dlc_manager.config.ui.titleAnchor", "dlc_manager.config.comment.ui.titleAnchor", DlcConfig.DEFAULT_TITLE_ANCHOR, ANCHOR_VALUES_WITH_AUTO, DlcConfig::getTitleAnchor, DlcConfig::setTitleAnchor, "dlc_manager.config.ui.titleAnchor."),
                integer("titleOffsetX", "dlc_manager.config.ui.titleOffsetX", "dlc_manager.config.comment.ui.titleOffsetX", DlcConfig.DEFAULT_TITLE_OFFSET_X, -10000, 10000, DlcConfig::getTitleOffsetX, DlcConfig::setTitleOffsetX),
                integer("titleOffsetY", "dlc_manager.config.ui.titleOffsetY", "dlc_manager.config.comment.ui.titleOffsetY", DlcConfig.DEFAULT_TITLE_OFFSET_Y, -10000, 10000, DlcConfig::getTitleOffsetY, DlcConfig::setTitleOffsetY),
                choice("titleUpdateAnchor", "dlc_manager.config.ui.titleUpdateAnchor", "dlc_manager.config.comment.ui.titleUpdateAnchor", DlcConfig.DEFAULT_TITLE_UPDATE_ANCHOR, ANCHOR_VALUES_WITH_AUTO, DlcConfig::getTitleUpdateAnchor, DlcConfig::setTitleUpdateAnchor, "dlc_manager.config.ui.titleUpdateAnchor."),
                integer("titleUpdateOffsetX", "dlc_manager.config.ui.titleUpdateOffsetX", "dlc_manager.config.comment.ui.titleUpdateOffsetX", DlcConfig.DEFAULT_TITLE_UPDATE_OFFSET_X, -10000, 10000, DlcConfig::getTitleUpdateOffsetX, DlcConfig::setTitleUpdateOffsetX),
                integer("titleUpdateOffsetY", "dlc_manager.config.ui.titleUpdateOffsetY", "dlc_manager.config.comment.ui.titleUpdateOffsetY", DlcConfig.DEFAULT_TITLE_UPDATE_OFFSET_Y, -10000, 10000, DlcConfig::getTitleUpdateOffsetY, DlcConfig::setTitleUpdateOffsetY));

        register(Tab.MODPACK,
                string("modpack", "dlc_manager.config.modpack.modpack", "dlc_manager.config.comment.modpack.modpack", DlcConfig.DEFAULT_MODPACK, DlcConfig::getModpack, DlcConfig::setModpack),
                string("modpackUrl", "dlc_manager.config.modpack.modpackUrl", "dlc_manager.config.comment.modpack.modpackUrl", DlcConfig.DEFAULT_MODPACK_URL, DlcConfig::getModpackUrl, DlcConfig::setModpackUrl),
                string("updateGitRepo", "dlc_manager.config.modpack.updateGitRepo", "dlc_manager.config.comment.modpack.updateGitRepo", DlcConfig.DEFAULT_UPDATE_GIT_REPO_URL, DlcConfig::getUpdateGitRepoUrl, DlcConfig::setUpdateGitRepoUrl));

        register(Tab.DOWNLOADS,
                integer("maxConcurrentDownloads", "dlc_manager.config.download.maxConcurrentDownloads", "dlc_manager.config.comment.download.maxConcurrentDownloads", DlcConfig.DEFAULT_MAX_CONCURRENT_DOWNLOADS, 1, 64, DlcConfig::getMaxConcurrentDownloads, DlcConfig::setMaxConcurrentDownloads),
                integer("maxSegmentsPerFile", "dlc_manager.config.download.maxSegmentsPerFile", "dlc_manager.config.comment.download.maxSegmentsPerFile", DlcConfig.DEFAULT_MAX_SEGMENTS_PER_FILE, 1, 64, DlcConfig::getMaxSegmentsPerFile, DlcConfig::setMaxSegmentsPerFile),
                integer("minSegmentMB", "dlc_manager.config.download.minSegmentMB", "dlc_manager.config.comment.download.minSegmentMB", DlcConfig.DEFAULT_MIN_SEGMENT_MB, 1, Integer.MAX_VALUE, () -> toMb(DlcConfig.getMinSegmentBytes()), value -> DlcConfig.setMinSegmentBytes((long) Math.max(1, value) * 1024L * 1024L)),
                integer("maxRetryCount", "dlc_manager.config.download.maxRetryCount", "dlc_manager.config.comment.download.maxRetryCount", DlcConfig.DEFAULT_MAX_RETRY, 1, 100, DlcConfig::getMaxRetryCount, DlcConfig::setMaxRetryCount),
                integer("connectTimeoutSeconds", "dlc_manager.config.download.connectTimeoutSeconds", "dlc_manager.config.comment.download.connectTimeoutSeconds", DlcConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, 1, 36000, () -> Math.max(1, DlcConfig.getConnectTimeoutMs() / 1000), value -> DlcConfig.setConnectTimeoutMs(value * 1000)),
                integer("taskTimeoutSeconds", "dlc_manager.config.download.taskTimeoutSeconds", "dlc_manager.config.comment.download.taskTimeoutSeconds", DlcConfig.DEFAULT_TASK_TIMEOUT_SECONDS, 1, 36000, () -> Math.max(1, DlcConfig.getTaskTimeoutMs() / 1000), value -> DlcConfig.setTaskTimeoutMs(value * 1000)),
                choice("downloadMirrorMode", "dlc_manager.config.download.downloadMirrorMode", "dlc_manager.config.comment.download.downloadMirrorMode", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_MODE, DOWNLOAD_MIRROR_MODE_VALUES, DlcConfig::getDownloadMirrorMode, DlcConfig::setDownloadMirrorMode, "dlc_manager.config.download.downloadMirrorMode."),
                integer("downloadMirrorProbeCount", "dlc_manager.config.download.downloadMirrorProbeCount", "dlc_manager.config.comment.download.downloadMirrorProbeCount", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_COUNT, 1, 10, DlcConfig::getDownloadMirrorProbeCount, DlcConfig::setDownloadMirrorProbeCount),
                choice("downloadMirrorProbeStrategy", "dlc_manager.config.download.downloadMirrorProbeStrategy", "dlc_manager.config.comment.download.downloadMirrorProbeStrategy", DlcConfig.DEFAULT_DOWNLOAD_MIRROR_PROBE_STRATEGY, DOWNLOAD_MIRROR_PROBE_STRATEGY_VALUES, DlcConfig::getDownloadMirrorProbeStrategy, DlcConfig::setDownloadMirrorProbeStrategy, "dlc_manager.config.download.downloadMirrorProbeStrategy."));
        loadFromConfig();
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

    public void loadFromConfig() {
        for (Option option : byName.values()) {
            option.load();
        }
    }

    public void applyToConfig() {
        for (Option option : byName.values()) {
            option.apply();
        }
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
        List<Option> list = new ArrayList<>(List.of(options));
        groups.put(tab, list);
        for (Option option : options) {
            byName.put(option.name(), option);
        }
    }

    private static Option string(String name, String labelKey, String commentKey, String defaultValue, Supplier<String> getter, Consumer<String> setter) {
        return new Option(name, OptionType.STRING, labelKey, commentKey, defaultValue, null, null, getter, setter);
    }

    private static Option bool(String name, String labelKey, String commentKey, boolean defaultValue, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new Option(name, OptionType.BOOLEAN, labelKey, commentKey, String.valueOf(defaultValue), null, null,
                () -> String.valueOf(getter.get()),
                value -> setter.accept(Boolean.parseBoolean(value)));
    }

    private static Option integer(String name, String labelKey, String commentKey, int defaultValue, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter) {
        return new Option(name, OptionType.INTEGER, labelKey, commentKey, String.valueOf(defaultValue), min, max,
                () -> String.valueOf(getter.get()),
                value -> setter.accept(clamp(parseInt(value, defaultValue), min, max)));
    }

    private static Option choice(String name, String labelKey, String commentKey, String defaultValue, List<String> choices, Supplier<String> getter, Consumer<String> setter, String choiceLabelPrefix) {
        return new Option(name, OptionType.CHOICE, labelKey, commentKey, defaultValue, null, null, getter, setter, choices, choiceLabelPrefix);
    }

    private static int toMb(long bytes) {
        long mb = (bytes + 1024L * 1024L - 1) / (1024L * 1024L);
        if (mb < 1) {
            return 1;
        }
        return (int) Math.min(Integer.MAX_VALUE, mb);
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

    public static final class Option {
        private final String name;
        private final OptionType type;
        private final String labelKey;
        private final String commentKey;
        private final String defaultValue;
        private final Integer min;
        private final Integer max;
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private final List<String> choiceValues;
        private final String choiceLabelPrefix;
        private String value;

        private Option(String name, OptionType type, String labelKey, String commentKey, String defaultValue, Integer min, Integer max, Supplier<String> getter, Consumer<String> setter) {
            this(name, type, labelKey, commentKey, defaultValue, min, max, getter, setter, List.of(), null);
        }

        private Option(String name, OptionType type, String labelKey, String commentKey, String defaultValue, Integer min, Integer max, Supplier<String> getter, Consumer<String> setter, List<String> choiceValues, String choiceLabelPrefix) {
            this.name = name;
            this.type = type;
            this.labelKey = labelKey;
            this.commentKey = commentKey;
            this.defaultValue = defaultValue == null ? "" : defaultValue;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
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
            String loaded = getter.get();
            value = normalize(loaded == null ? defaultValue : loaded);
        }

        public void apply() {
            setter.accept(normalize(value));
            load();
        }

        public boolean modified() {
            String current = getter.get();
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
