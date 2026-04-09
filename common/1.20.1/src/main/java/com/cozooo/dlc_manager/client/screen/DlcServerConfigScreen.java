package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DlcServerConfigScreen extends Screen {
    private static class LabelEntry {
        private final Component label;
        private final int y;

        private LabelEntry(Component label, int y) {
            this.label = label;
            this.y = y;
        }
    }

    private final Screen parent;
    private final DlcServerEntry entry;
    private final List<LabelEntry> labels = new ArrayList<>();

    private boolean serverSupport;
    private boolean apiEnabled;
    private boolean uiEnableAll;
    private boolean uiUpdateManagerOn;
    private boolean uiSelfUpdateAutoCheckOn;
    private boolean uiPresetFuncOn;

    private EditBox serverPortField;
    private EditBox serverPasswordField;
    private EditBox apiPortField;
    private EditBox apiTokenField;
    private EditBox dlcRootField;
    private EditBox taczRootField;
    private EditBox resourcepackRootField;
    private EditBox datapackRootField;
    private EditBox maxConcurrentField;
    private EditBox maxSegmentsField;
    private EditBox minSegmentField;
    private EditBox maxRetryField;
    private EditBox connectTimeoutField;
    private EditBox taskTimeoutField;

    private Button serverSupportButton;
    private Button apiEnabledButton;
    private Button uiEnableAllButton;
    private Button uiUpdateManagerButton;
    private Button uiSelfUpdateButton;
    private Button uiPresetButton;
    private Button selfUpdateCheckButton;
    private Button saveButton;
    private Button backButton;

    private int labelX;
    private int fieldX;
    private boolean loading;

    public DlcServerConfigScreen(Screen parent, DlcServerEntry entry) {
        super(Component.translatable("dlc_manager.server.config_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        labels.clear();
        int centerX = this.width / 2;
        int labelWidth = 160;
        int fieldWidth = 220;
        int totalWidth = labelWidth + 6 + fieldWidth;
        labelX = centerX - totalWidth / 2;
        fieldX = labelX + labelWidth + 6;
        int y = 40;
        int row = 22;

        serverSupport = DlcPathConfig.isServerSupportOnServer();
        apiEnabled = DlcPathConfig.isApiEnabled();
        uiEnableAll = DlcPathConfig.isEnableAllButtonShown();
        uiUpdateManagerOn = DlcPathConfig.isUpdateManagerOn();
        uiSelfUpdateAutoCheckOn = DlcPathConfig.isSelfUpdateAutoCheckOn();
        uiPresetFuncOn = DlcPathConfig.isPresetFuncOn();

        addLabel("dlc_manager.config.ui.serverSupport", y);
        serverSupportButton = Button.builder(toggleLabel("dlc_manager.config.ui.serverSupport", serverSupport), btn -> {
            serverSupport = !serverSupport;
            btn.setMessage(toggleLabel("dlc_manager.config.ui.serverSupport", serverSupport));
        }).bounds(fieldX, y, fieldWidth, 20).build();
        addRenderableWidget(serverSupportButton);
        y += row;

        addLabel("dlc_manager.config.ui.serverPort", y);
        serverPortField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.ui.serverPort"));
        serverPortField.setMaxLength(8);
        addRenderableWidget(serverPortField);
        y += row;

        addLabel("dlc_manager.config.ui.serverPassword", y);
        serverPasswordField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.ui.serverPassword"));
        serverPasswordField.setMaxLength(256);
        addRenderableWidget(serverPasswordField);
        y += row + 6;

        addLabel("dlc_manager.config.ui.apiEnabled", y);
        apiEnabledButton = Button.builder(toggleLabel("dlc_manager.config.ui.apiEnabled", apiEnabled), btn -> {
            apiEnabled = !apiEnabled;
            btn.setMessage(toggleLabel("dlc_manager.config.ui.apiEnabled", apiEnabled));
        }).bounds(fieldX, y, fieldWidth, 20).build();
        addRenderableWidget(apiEnabledButton);
        y += row;

        addLabel("dlc_manager.config.ui.apiPort", y);
        apiPortField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.ui.apiPort"));
        apiPortField.setMaxLength(8);
        addRenderableWidget(apiPortField);
        y += row;

        addLabel("dlc_manager.config.ui.apiToken", y);
        apiTokenField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.ui.apiToken"));
        apiTokenField.setMaxLength(256);
        addRenderableWidget(apiTokenField);
        y += row + 6;

        addLabel("dlc_manager.config.ui.isEnableAllButtonShown", y);
        uiEnableAllButton = Button.builder(toggleLabel("dlc_manager.config.ui.isEnableAllButtonShown", uiEnableAll), btn -> {
            uiEnableAll = !uiEnableAll;
            btn.setMessage(toggleLabel("dlc_manager.config.ui.isEnableAllButtonShown", uiEnableAll));
        }).bounds(fieldX, y, fieldWidth, 20).build();
        addRenderableWidget(uiEnableAllButton);
        y += row;

        addLabel("dlc_manager.config.ui.isUpdateManagerOn", y);
        uiUpdateManagerButton = Button.builder(toggleLabel("dlc_manager.config.ui.isUpdateManagerOn", uiUpdateManagerOn), btn -> {
            uiUpdateManagerOn = !uiUpdateManagerOn;
            btn.setMessage(toggleLabel("dlc_manager.config.ui.isUpdateManagerOn", uiUpdateManagerOn));
        }).bounds(fieldX, y, fieldWidth, 20).build();
        addRenderableWidget(uiUpdateManagerButton);
        y += row;

        addLabel("dlc_manager.config.ui.isSelfUpdateAutoCheckOn", y);
        uiSelfUpdateButton = Button.builder(toggleLabel("dlc_manager.config.ui.isSelfUpdateAutoCheckOn", uiSelfUpdateAutoCheckOn), btn -> {
            uiSelfUpdateAutoCheckOn = !uiSelfUpdateAutoCheckOn;
            btn.setMessage(toggleLabel("dlc_manager.config.ui.isSelfUpdateAutoCheckOn", uiSelfUpdateAutoCheckOn));
        }).bounds(fieldX, y, fieldWidth, 20).build();
        addRenderableWidget(uiSelfUpdateButton);
        y += row;

        addLabel("dlc_manager.config.ui.isPresetFuncOn", y);
        uiPresetButton = Button.builder(toggleLabel("dlc_manager.config.ui.isPresetFuncOn", uiPresetFuncOn), btn -> {
            uiPresetFuncOn = !uiPresetFuncOn;
            btn.setMessage(toggleLabel("dlc_manager.config.ui.isPresetFuncOn", uiPresetFuncOn));
        }).bounds(fieldX, y, fieldWidth, 20).build();
        addRenderableWidget(uiPresetButton);
        y += row + 6;

        addLabel("dlc_manager.config.dlc_root", y);
        dlcRootField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.dlc_root"));
        dlcRootField.setMaxLength(256);
        addRenderableWidget(dlcRootField);
        y += row;

        addLabel("dlc_manager.config.tacz_root", y);
        taczRootField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.tacz_root"));
        taczRootField.setMaxLength(256);
        addRenderableWidget(taczRootField);
        y += row;

        addLabel("dlc_manager.config.resourcepack_root", y);
        resourcepackRootField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.resourcepack_root"));
        resourcepackRootField.setMaxLength(256);
        addRenderableWidget(resourcepackRootField);
        y += row;

        addLabel("dlc_manager.config.datapack_root", y);
        datapackRootField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.datapack_root"));
        datapackRootField.setMaxLength(256);
        addRenderableWidget(datapackRootField);
        y += row + 6;

        addLabel("dlc_manager.config.download.max_concurrent", y);
        maxConcurrentField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.download.max_concurrent"));
        maxConcurrentField.setMaxLength(8);
        addRenderableWidget(maxConcurrentField);
        y += row;

        addLabel("dlc_manager.config.download.max_segments", y);
        maxSegmentsField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.download.max_segments"));
        maxSegmentsField.setMaxLength(8);
        addRenderableWidget(maxSegmentsField);
        y += row;

        addLabel("dlc_manager.config.download.min_segment_bytes", y);
        minSegmentField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.download.min_segment_bytes"));
        minSegmentField.setMaxLength(8);
        addRenderableWidget(minSegmentField);
        y += row;

        addLabel("dlc_manager.config.download.max_retry", y);
        maxRetryField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.download.max_retry"));
        maxRetryField.setMaxLength(8);
        addRenderableWidget(maxRetryField);
        y += row;

        addLabel("dlc_manager.config.download.connect_timeout", y);
        connectTimeoutField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.download.connect_timeout"));
        connectTimeoutField.setMaxLength(8);
        addRenderableWidget(connectTimeoutField);
        y += row;

        addLabel("dlc_manager.config.download.task_timeout", y);
        taskTimeoutField = new EditBox(this.font, fieldX, y, fieldWidth, 20, Component.translatable("dlc_manager.config.download.task_timeout"));
        taskTimeoutField.setMaxLength(8);
        addRenderableWidget(taskTimeoutField);

        int buttonHeight = 20;
        int bottomY = this.height - 36;
        int gap = 6;
        int saveWidth = 100;
        int backWidth = 100;
        int checkWidth = Math.max(110, this.font.width(Component.translatable("dlc_manager.self_update.button")) + 12);
        int totalButtonWidth = checkWidth + gap + saveWidth + gap + backWidth;
        int startX = centerX - totalButtonWidth / 2;
        selfUpdateCheckButton = Button.builder(Component.translatable("dlc_manager.self_update.button"),
                btn -> Minecraft.getInstance().setScreen(new DlcSelfUpdateScreen(this, null)))
            .bounds(startX, bottomY, checkWidth, buttonHeight)
            .build();
        saveButton = Button.builder(Component.translatable("dlc_manager.config.save"), btn -> saveConfig())
            .bounds(startX + checkWidth + gap, bottomY, saveWidth, buttonHeight)
            .build();
        backButton = Button.builder(Component.translatable("gui.back"), btn -> Minecraft.getInstance().setScreen(parent))
            .bounds(saveButton.getX() + saveWidth + gap, bottomY, backWidth, buttonHeight)
            .build();
        addRenderableWidget(selfUpdateCheckButton);
        addRenderableWidget(saveButton);
        addRenderableWidget(backButton);

        loadConfig();
        populateFromDefaults();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, getTitle(), this.width / 2, 16, 0xFFFFFF);
        for (LabelEntry entry : labels) {
            guiGraphics.drawString(this.font, entry.label, labelX, entry.y + 6, 0xC8C8C8, false);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void addLabel(String key, int y) {
        labels.add(new LabelEntry(Component.translatable(key), y));
    }

    private Component toggleLabel(String key, boolean value) {
        return Component.translatable(key)
            .copy()
            .append(Component.literal(": "))
            .append(Component.translatable(value ? "options.on" : "options.off"));
    }

    private void populateFromDefaults() {
        serverPortField.setValue(Integer.toString(DlcPathConfig.getServerPort()));
        serverPasswordField.setValue(DlcPathConfig.getServerPassword());
        apiEnabledButton.setMessage(toggleLabel("dlc_manager.config.ui.apiEnabled", apiEnabled));
        apiPortField.setValue(Integer.toString(DlcPathConfig.getApiPort()));
        apiTokenField.setValue(DlcPathConfig.getApiToken());
        dlcRootField.setValue(DlcPathConfig.getDlcRootRaw());
        taczRootField.setValue(DlcPathConfig.getTaczRootRaw());
        resourcepackRootField.setValue(DlcPathConfig.getResourcepackRootRaw());
        datapackRootField.setValue(DlcPathConfig.getDatapackRootRaw());
        maxConcurrentField.setValue(Integer.toString(DlcPathConfig.getMaxConcurrentDownloads()));
        maxSegmentsField.setValue(Integer.toString(DlcPathConfig.getMaxSegmentsPerFile()));
        minSegmentField.setValue(Integer.toString(getDefaultMinSegmentMb()));
        maxRetryField.setValue(Integer.toString(DlcPathConfig.getMaxRetryCount()));
        connectTimeoutField.setValue(Integer.toString(getDefaultConnectTimeoutSeconds()));
        taskTimeoutField.setValue(Integer.toString(getDefaultTaskTimeoutSeconds()));
    }

    private void loadConfig() {
        if (loading) {
            return;
        }
        loading = true;
        CompletableFuture
            .supplyAsync(this::fetchConfig)
            .whenComplete((config, throwable) -> Minecraft.getInstance().execute(() -> {
                loading = false;
                if (config != null) {
                    applyConfig(config);
                }
            }));
    }

    private JsonObject fetchConfig() {
        try {
            return DlcServerConnection.fetchServerConfig(entry, 8000);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyConfig(JsonObject root) {
        JsonObject server = JsonCaseUtil.getObjectIgnoreCase(root, "server");
        if (server != null) {
            serverSupport = getBoolean(server, "serverSupport", serverSupport);
            serverPortField.setValue(Integer.toString(getInt(server, "serverPort", DlcPathConfig.getServerPort())));
            serverPasswordField.setValue(getString(server, "serverPassword", ""));
            serverSupportButton.setMessage(toggleLabel("dlc_manager.config.ui.serverSupport", serverSupport));
        }
        JsonObject api = JsonCaseUtil.getObjectIgnoreCase(root, "api");
        if (api != null) {
            apiEnabled = getBoolean(api, "apiEnabled", apiEnabled);
            apiPortField.setValue(Integer.toString(getInt(api, "apiPort", DlcPathConfig.getApiPort())));
            apiTokenField.setValue(getString(api, "apiToken", DlcPathConfig.getApiToken()));
            apiEnabledButton.setMessage(toggleLabel("dlc_manager.config.ui.apiEnabled", apiEnabled));
        }
        JsonObject ui = JsonCaseUtil.getObjectIgnoreCase(root, "ui");
        if (ui != null) {
            uiEnableAll = getBoolean(ui, "isEnableAllButtonShown", uiEnableAll);
            uiUpdateManagerOn = getBoolean(ui, "isUpdateManagerOn", uiUpdateManagerOn);
            uiSelfUpdateAutoCheckOn = getBoolean(ui, "isSelfUpdateAutoCheckOn", uiSelfUpdateAutoCheckOn);
            uiPresetFuncOn = getBoolean(ui, "isPresetFuncOn", uiPresetFuncOn);
            uiEnableAllButton.setMessage(toggleLabel("dlc_manager.config.ui.isEnableAllButtonShown", uiEnableAll));
            uiUpdateManagerButton.setMessage(toggleLabel("dlc_manager.config.ui.isUpdateManagerOn", uiUpdateManagerOn));
            uiSelfUpdateButton.setMessage(toggleLabel("dlc_manager.config.ui.isSelfUpdateAutoCheckOn", uiSelfUpdateAutoCheckOn));
            uiPresetButton.setMessage(toggleLabel("dlc_manager.config.ui.isPresetFuncOn", uiPresetFuncOn));
        }
        JsonObject paths = JsonCaseUtil.getObjectIgnoreCase(root, "paths");
        if (paths != null) {
            dlcRootField.setValue(getString(paths, "dlcRoot", DlcPathConfig.getDlcRootRaw()));
            taczRootField.setValue(getString(paths, "taczRoot", DlcPathConfig.getTaczRootRaw()));
            resourcepackRootField.setValue(getString(paths, "resourcepackRoot", DlcPathConfig.getResourcepackRootRaw()));
            datapackRootField.setValue(getString(paths, "datapackRoot", DlcPathConfig.getDatapackRootRaw()));
        }
        JsonObject downloads = JsonCaseUtil.getObjectIgnoreCase(root, "downloads");
        if (downloads != null) {
            maxConcurrentField.setValue(Integer.toString(getInt(downloads, "maxConcurrentDownloads", DlcPathConfig.getMaxConcurrentDownloads())));
            maxSegmentsField.setValue(Integer.toString(getInt(downloads, "maxSegmentsPerFile", DlcPathConfig.getMaxSegmentsPerFile())));
            minSegmentField.setValue(Integer.toString(getInt(downloads, "minSegmentMB", getDefaultMinSegmentMb())));
            maxRetryField.setValue(Integer.toString(getInt(downloads, "maxRetryCount", DlcPathConfig.getMaxRetryCount())));
            connectTimeoutField.setValue(Integer.toString(getInt(downloads, "connectTimeoutSeconds", getDefaultConnectTimeoutSeconds())));
            taskTimeoutField.setValue(Integer.toString(getInt(downloads, "taskTimeoutSeconds", getDefaultTaskTimeoutSeconds())));
        }
    }

    private void saveConfig() {
        JsonObject root = new JsonObject();
        JsonObject server = new JsonObject();
        server.addProperty("serverSupport", serverSupport);
        server.addProperty("serverPort", clampInt(parseInt(serverPortField.getValue(), DlcPathConfig.getServerPort()), 1, 65535));
        server.addProperty("serverPassword", serverPasswordField.getValue());
        root.add("server", server);

        JsonObject api = new JsonObject();
        api.addProperty("apiEnabled", apiEnabled);
        api.addProperty("apiPort", clampInt(parseInt(apiPortField.getValue(), DlcPathConfig.getApiPort()), 0, 65535));
        api.addProperty("apiToken", apiTokenField.getValue());
        root.add("api", api);

        JsonObject ui = new JsonObject();
        ui.addProperty("isEnableAllButtonShown", uiEnableAll);
        ui.addProperty("isUpdateManagerOn", uiUpdateManagerOn);
        ui.addProperty("isSelfUpdateAutoCheckOn", uiSelfUpdateAutoCheckOn);
        ui.addProperty("isPresetFuncOn", uiPresetFuncOn);
        root.add("ui", ui);

        JsonObject paths = new JsonObject();
        paths.addProperty("dlcRoot", dlcRootField.getValue());
        paths.addProperty("taczRoot", taczRootField.getValue());
        paths.addProperty("resourcepackRoot", resourcepackRootField.getValue());
        paths.addProperty("datapackRoot", datapackRootField.getValue());
        root.add("paths", paths);

        JsonObject downloads = new JsonObject();
        downloads.addProperty("maxConcurrentDownloads", parseInt(maxConcurrentField.getValue(), DlcPathConfig.getMaxConcurrentDownloads()));
        downloads.addProperty("maxSegmentsPerFile", parseInt(maxSegmentsField.getValue(), DlcPathConfig.getMaxSegmentsPerFile()));
        downloads.addProperty("minSegmentMB", parseInt(minSegmentField.getValue(), getDefaultMinSegmentMb()));
        downloads.addProperty("maxRetryCount", parseInt(maxRetryField.getValue(), DlcPathConfig.getMaxRetryCount()));
        downloads.addProperty("connectTimeoutSeconds", parseInt(connectTimeoutField.getValue(), getDefaultConnectTimeoutSeconds()));
        downloads.addProperty("taskTimeoutSeconds", parseInt(taskTimeoutField.getValue(), getDefaultTaskTimeoutSeconds()));
        root.add("downloads", downloads);

        CompletableFuture
            .supplyAsync(() -> submitConfig(root))
            .whenComplete((ok, throwable) -> Minecraft.getInstance().execute(() -> {
                if (ok != null && ok) {
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.config_save_success"), Component.empty());
                } else {
                    SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                        Component.translatable("dlc_manager.server.config_save_failed"), Component.empty());
                }
            }));
    }

    private boolean submitConfig(JsonObject root) {
        try {
            return DlcServerConnection.saveServerConfig(entry, 8000, root);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int parseInt(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
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

    private static int getDefaultMinSegmentMb() {
        return toMb(DlcPathConfig.getMinSegmentBytes());
    }

    private static int getDefaultConnectTimeoutSeconds() {
        return Math.max(1, DlcPathConfig.getConnectTimeoutMs() / 1000);
    }

    private static int getDefaultTaskTimeoutSeconds() {
        return Math.max(1, DlcPathConfig.getTaskTimeoutMs() / 1000);
    }

    private static int toMb(long bytes) {
        long mb = bytes / (1024L * 1024L);
        return mb > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, mb);
    }
}
