package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.config.DlcServerConfigClient;
import com.cozooo.dlc_manager.client.server.DlcServerEntry;
import com.cozooo.dlc_manager.client.server.DlcServerConnection;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigDouble;
import fi.dy.masa.malilib.config.IConfigInteger;
import fi.dy.masa.malilib.config.IConfigResettable;
import fi.dy.masa.malilib.config.IConfigSlider;
import fi.dy.masa.malilib.config.IConfigValue;
import fi.dy.masa.malilib.config.gui.ConfigOptionChangeListenerButton;
import fi.dy.masa.malilib.config.gui.ConfigOptionChangeListenerTextField;
import fi.dy.masa.malilib.config.gui.ConfigOptionListenerResetConfig;
import fi.dy.masa.malilib.config.gui.ConfigOptionListenerResetConfig.ConfigResetterButton;
import fi.dy.masa.malilib.config.gui.ConfigOptionListenerResetConfig.ConfigResetterTextField;
import fi.dy.masa.malilib.config.gui.GuiModConfigs;
import fi.dy.masa.malilib.config.gui.SliderCallbackDouble;
import fi.dy.masa.malilib.config.gui.SliderCallbackInteger;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.interfaces.ISliderCallback;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSlider;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public class DlcServerConfigMaLiLibScreen extends GuiModConfigs {
    private final Screen parent;
    private final DlcServerEntry entry;
    private final Tab currentTab;
    private final ConfigSession session;
    private int maxLabelWidth;
    private WidgetListConfigOptionsBase<?, ?> listWidget;
    private ButtonGeneric selfUpdateCheckButton;
    private ButtonGeneric saveButton;
    private ButtonGeneric cancelButton;
    private boolean configLoaded;
    private static final String[] DOWNLOAD_MIRROR_MODE_VALUES = new String[] {
        DlcPathConfig.DOWNLOAD_MIRROR_SMART_OFFICIAL,
        DlcPathConfig.DOWNLOAD_MIRROR_SMART_MIRROR,
        DlcPathConfig.DOWNLOAD_MIRROR_SMART_PRECHECK,
        DlcPathConfig.DOWNLOAD_MIRROR_OFFICIAL_ONLY,
        DlcPathConfig.DOWNLOAD_MIRROR_MIRROR_ONLY,
        DlcPathConfig.DOWNLOAD_MIRROR_OFFICIAL_FIRST,
        DlcPathConfig.DOWNLOAD_MIRROR_MIRROR_FIRST
    };
    private static final String[] DOWNLOAD_MIRROR_PROBE_STRATEGY_VALUES = new String[] {
        DlcPathConfig.DOWNLOAD_MIRROR_PROBE_SINGLE,
        DlcPathConfig.DOWNLOAD_MIRROR_PROBE_MAJORITY
    };

    public DlcServerConfigMaLiLibScreen(Screen parent, DlcServerEntry entry) {
        this(parent, entry, Tab.SERVER);
    }

    public DlcServerConfigMaLiLibScreen(Screen parent, DlcServerEntry entry, Tab tab) {
        this(parent, entry, tab, new ConfigSession());
    }

    private DlcServerConfigMaLiLibScreen(Screen parent, DlcServerEntry entry, Tab tab, ConfigSession session) {
        super(DlcPathConfig.MOD_ID, buildOptions(resolveTab(tab)), false, "dlc_manager.server.config_title");
        this.parent = parent;
        this.entry = entry;
        this.currentTab = resolveTab(tab);
        this.session = session;
        this.setParent(parent);
        this.setTitle(StringUtils.translate("dlc_manager.server.config_title"));
        this.maxLabelWidth = 220;
    }

    @Override
    public void initGui() {
        if (!this.session.configLoaded) {
            loadFromServer();
            this.session.configLoaded = true;
        }
        int x = 10;
        int y = 28;
        int height = 20;
        int gap = 4;
        int listY = y + height + 10;
        this.setListPosition(10, listY);
        int browserWidth = this.getBrowserWidth();
        this.maxLabelWidth = computeDynamicLabelWidth(this.currentTab, browserWidth);
        int available = browserWidth - this.maxLabelWidth - 80;
        int configWidth = Math.max(110, Math.min(320, available));
        this.setConfigWidth(configWidth);
        super.initGui();

        x = addTabButton(Tab.SERVER, x, y, height);
        x += gap;
        x = addTabButton(Tab.UI, x, y, height);
        x += gap;
        x = addTabButton(Tab.PATHS, x, y, height);
        x += gap;
        addTabButton(Tab.DOWNLOADS, x, y, height);

        int buttonWidth = 80;
        int buttonHeight = 20;
        int buttonY = this.height - 28;
        int centerX = this.width / 2;
        int spacing = 10;
        int checkWidth = Math.max(110, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.self_update.button")) + 12);
        int totalWidth = checkWidth + spacing + buttonWidth + spacing + buttonWidth;
        int startX = centerX - totalWidth / 2;
        int checkX = startX;
        int saveX = startX + checkWidth + spacing;
        int cancelX = saveX + buttonWidth + spacing;
        this.selfUpdateCheckButton = new ButtonGeneric(checkX, buttonY, checkWidth, buttonHeight, StringUtils.translate("dlc_manager.self_update.button"));
        this.saveButton = new ButtonGeneric(saveX, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.config.save"));
        this.cancelButton = new ButtonGeneric(cancelX, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.config.cancel"));
        this.addButton(this.selfUpdateCheckButton, new SelfUpdateButtonListener());
        this.addButton(this.saveButton, new SaveButtonListener());
        this.addButton(this.cancelButton, new CancelButtonListener());
        updateSaveButtonState();
    }

    @Override
    public void tick() {
        super.tick();
        updateSaveButtonState();
    }

    @Override
    protected void drawTitle(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.drawString(guiGraphics, StringUtils.translate("dlc_manager.server.config_title"), 10, 10, 0xFFFFFF);
    }

    @Override
    protected int getBrowserHeight() {
        return this.height - 90;
    }

    @Override
    protected WidgetListConfigOptions createListWidget(int x, int y) {
        this.listWidget = new DlcServerConfigListWidget(
            x,
            y,
            this.getBrowserWidth(),
            this.getBrowserHeight(),
            this.getConfigWidth(),
            0.0F,
            this.useKeybindSearch(),
            this,
            this.maxLabelWidth
        );
        return (WidgetListConfigOptions) this.listWidget;
    }

    private void updateSaveButtonState() {
        if (this.saveButton == null) {
            return;
        }
        boolean modified = this.session.modified || (this.listWidget != null && this.listWidget.wereConfigsModified());
        this.saveButton.setEnabled(modified);
    }

    private int addTabButton(Tab tab, int x, int y, int height) {
        String label = StringUtils.translate(tab.labelKey);
        int width = Math.max(60, this.getStringWidth(label) + 10);
        ButtonGeneric button = new ButtonGeneric(x, y, width, height, label);
        button.setEnabled(tab != currentTab);
        this.addButton(button, new TabButtonListener(tab));
        return x + width;
    }

    private static Tab resolveTab(Tab tab) {
        return tab == null ? Tab.SERVER : tab;
    }

    private static List<ConfigOptionWrapper> buildOptions(Tab tab) {
        List<IConfigBase> configs = getConfigsForTab(tab);
        return new ArrayList<>(ConfigOptionWrapper.createFor(configs));
    }

    private static List<IConfigBase> getConfigsForTab(Tab tab) {
        return switch (tab) {
            case SERVER -> DlcServerConfigClient.getServerConfigs();
            case UI -> DlcServerConfigClient.getUiConfigs();
            case PATHS -> DlcServerConfigClient.getPathConfigs();
            case DOWNLOADS -> DlcServerConfigClient.getDownloadConfigs();
        };
    }

    private static boolean isDownloadMirrorModeConfig(IConfigBase config) {
        return config != null && "downloadMirrorMode".equals(config.getName());
    }

    private static boolean isDownloadMirrorProbeStrategyConfig(IConfigBase config) {
        return config != null && "downloadMirrorProbeStrategy".equals(config.getName());
    }

    private static String nextDownloadMirrorMode(String value) {
        String normalized = DlcPathConfig.normalizeDownloadMirrorMode(value);
        for (int i = 0; i < DOWNLOAD_MIRROR_MODE_VALUES.length; i++) {
            if (DOWNLOAD_MIRROR_MODE_VALUES[i].equals(normalized)) {
                return DOWNLOAD_MIRROR_MODE_VALUES[(i + 1) % DOWNLOAD_MIRROR_MODE_VALUES.length];
            }
        }
        return DOWNLOAD_MIRROR_MODE_VALUES[0];
    }

    private static String downloadMirrorModeLabel(String value) {
        String normalized = DlcPathConfig.normalizeDownloadMirrorMode(value);
        String key = "dlc_manager.config.download.downloadMirrorMode." + normalized;
        String label = StringUtils.translate(key);
        return label.equals(key) ? normalized : label;
    }

    private static String nextDownloadMirrorProbeStrategy(String value) {
        String normalized = DlcPathConfig.normalizeDownloadMirrorProbeStrategy(value);
        for (int i = 0; i < DOWNLOAD_MIRROR_PROBE_STRATEGY_VALUES.length; i++) {
            if (DOWNLOAD_MIRROR_PROBE_STRATEGY_VALUES[i].equals(normalized)) {
                return DOWNLOAD_MIRROR_PROBE_STRATEGY_VALUES[(i + 1) % DOWNLOAD_MIRROR_PROBE_STRATEGY_VALUES.length];
            }
        }
        return DOWNLOAD_MIRROR_PROBE_STRATEGY_VALUES[0];
    }

    private static String downloadMirrorProbeStrategyLabel(String value) {
        String normalized = DlcPathConfig.normalizeDownloadMirrorProbeStrategy(value);
        String key = "dlc_manager.config.download.downloadMirrorProbeStrategy." + normalized;
        String label = StringUtils.translate(key);
        return label.equals(key) ? normalized : label;
    }

    private int computeDynamicLabelWidth(Tab tab, int browserWidth) {
        int longest = 0;
        for (IConfigBase config : getConfigsForTab(tab)) {
            if (config == null) {
                continue;
            }
            String label = config.getConfigGuiDisplayName();
            if (label == null || label.isBlank()) {
                label = config.getName();
            }
            longest = Math.max(longest, this.getStringWidth(label));
        }
        int desired = longest + 14;
        int maxByLayout = Math.max(60, browserWidth - 80 - 110);
        return Math.max(60, Math.min(desired, maxByLayout));
    }

    private void loadFromServer() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            JsonObject json = DlcServerConnection.fetchServerConfig(entry, 5000);
            if (json != null) {
                DlcServerConfigClient.loadFromJson(json);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveAndClose() {
        flushCurrentModifications();
        JsonObject json = DlcServerConfigClient.toJson();
        Minecraft minecraft = Minecraft.getInstance();
        boolean ok;
        try {
            ok = DlcServerConnection.saveServerConfig(entry, 5000, json);
        } catch (Exception e) {
            ok = false;
        }
        if (ok) {
            this.session.modified = false;
            this.mc.setScreen(this.getParent());
            return;
        }
        Component title = Component.translatable("dlc_manager.server.config_save_failed");
        Component message = Component.translatable("dlc_manager.server.config_save_failed");
        Component okText = Component.translatable("gui.ok");
        Component backText = Component.translatable("gui.back");
        minecraft.setScreen(new ConfirmScreen(accepted -> minecraft.setScreen(this), title, message, okText, backText));
    }

    private void cancelAndClose() {
        if (!this.session.modified && (this.listWidget == null || !this.listWidget.wereConfigsModified())) {
            this.mc.setScreen(this.getParent());
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen returnScreen = minecraft.screen;
        Component title = Component.translatable("dlc_manager.config.cancel_title");
        Component message = Component.translatable("dlc_manager.config.cancel_prompt");
        Component yes = Component.translatable("dlc_manager.config.cancel_yes");
        Component no = Component.translatable("dlc_manager.config.cancel_no");
        minecraft.setScreen(new ConfirmScreen(accepted -> {
            minecraft.setScreen(returnScreen);
            if (!accepted) {
                return;
            }
            minecraft.setScreen(this.getParent());
        }, title, message, yes, no));
    }

    private class TabButtonListener implements IButtonActionListener {
        private final Tab target;

        private TabButtonListener(Tab target) {
            this.target = target;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            if (target == currentTab) {
                return;
            }
            DlcServerConfigMaLiLibScreen.this.openTab(target);
        }
    }

    private void openTab(Tab tab) {
        flushCurrentModifications();
        this.mc.setScreen(new DlcServerConfigMaLiLibScreen(this.getParent(), entry, tab, this.session));
    }

    private void flushCurrentModifications() {
        if (this.listWidget == null) {
            return;
        }
        if (this.listWidget.wereConfigsModified()) {
            this.session.modified = true;
        }
        this.listWidget.applyPendingModifications();
        this.listWidget.clearConfigsModifiedFlag();
    }

    private class SaveButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            DlcServerConfigMaLiLibScreen.this.saveAndClose();
        }
    }

    private class CancelButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            DlcServerConfigMaLiLibScreen.this.cancelAndClose();
        }
    }

    private class SelfUpdateButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            Minecraft.getInstance().setScreen(new DlcSelfUpdateScreen(DlcServerConfigMaLiLibScreen.this, null));
        }
    }

    private static class ConfigSession {
        private boolean modified;
        private boolean configLoaded;
    }

    private static class DlcServerConfigListWidget extends WidgetListConfigOptions {
        private final int labelWidthCap;

        public DlcServerConfigListWidget(int x, int y, int width, int height, int configWidth, float zLevel, boolean useKeybindSearch,
                                         DlcServerConfigMaLiLibScreen parent, int labelWidthCap) {
            super(x, y, width, height, configWidth, zLevel, useKeybindSearch, parent);
            this.labelWidthCap = labelWidthCap;
        }

        @Override
        public int getMaxNameLengthWrapped(List<ConfigOptionWrapper> list) {
            return Math.min(super.getMaxNameLengthWrapped(list), this.labelWidthCap);
        }

        @Override
        protected WidgetConfigOption createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ConfigOptionWrapper entry) {
            return new DlcServerConfigOptionWidget(
                x,
                y,
                this.browserEntryWidth,
                this.browserEntryHeight,
                this.maxLabelWidth,
                this.configWidth,
                entry,
                listIndex,
                this.parent,
                this
            );
        }
    }

    private static class DlcServerConfigOptionWidget extends WidgetConfigOption {
        public DlcServerConfigOptionWidget(int x, int y, int width, int height, int maxLabelWidth, int configWidth,
                                            ConfigOptionWrapper wrapper, int listIndex, GuiConfigsBase parent,
                                            WidgetListConfigOptionsBase<?, ?> list) {
            super(x, y, width, height, maxLabelWidth, configWidth, wrapper, listIndex, (IKeybindConfigGui) parent, list);
        }

        @Override
        protected void addConfigButtonEntry(int x, int y, IConfigResettable config, ButtonBase configButton) {
            ButtonGeneric resetButton = createResetButtonLocal(x, y, config);
            ConfigOptionChangeListenerButton changeListener = new ConfigOptionChangeListenerButton(config, resetButton, null);
            ConfigOptionListenerResetConfig resetListener = new ConfigOptionListenerResetConfig(
                    config,
                    new ConfigResetterButton(configButton),
                    resetButton,
                    null
            );
            this.addButton(configButton, changeListener);
            this.addButton(resetButton, new ConfirmResetListener(resetListener));
        }

        @Override
        protected void addConfigTextFieldEntry(int x, int y, int resetButtonX, int width, int height, IConfigValue config) {
            if (isDownloadMirrorModeConfig(config)) {
                ButtonGeneric modeButton = new ButtonGeneric(x, y, width, height, downloadMirrorModeLabel(config.getStringValue()));
                ButtonGeneric resetButton = createResetButtonLocal(resetButtonX, y, config);
                this.addButton(modeButton, new DownloadMirrorModeCycleListener(config, modeButton, resetButton));
                this.addButton(resetButton, new ConfirmResetListener(new DownloadMirrorModeResetListener(config, modeButton, resetButton)));
                return;
            }
            if (isDownloadMirrorProbeStrategyConfig(config)) {
                ButtonGeneric strategyButton = new ButtonGeneric(x, y, width, height, downloadMirrorProbeStrategyLabel(config.getStringValue()));
                ButtonGeneric resetButton = createResetButtonLocal(resetButtonX, y, config);
                this.addButton(strategyButton, new DownloadMirrorProbeStrategyCycleListener(config, strategyButton, resetButton));
                this.addButton(resetButton, new ConfirmResetListener(new DownloadMirrorProbeStrategyResetListener(config, strategyButton, resetButton)));
                return;
            }
            GuiTextFieldGeneric textField = new DlcSafeTextField(x, y + 1, width - 4, height - 3, Minecraft.getInstance().font);
            textField.setMaxLength(this.maxTextfieldTextLength);
            textField.setValue(config.getStringValue());

            ButtonGeneric resetButton = createResetButtonLocal(resetButtonX, y, config);
            ConfigOptionChangeListenerTextField changeListener = new ConfigOptionChangeListenerTextField(config, textField, resetButton);
            ConfigOptionListenerResetConfig resetListener = new ConfigOptionListenerResetConfig(
                    config,
                    new ConfigResetterTextField(config, textField),
                    resetButton,
                    null
            );
            this.addTextField(textField, changeListener);
            this.addButton(resetButton, new ConfirmResetListener(resetListener));
        }

        @Override
        protected void addConfigSliderEntry(int x, int y, int resetButtonX, int width, int height, IConfigSlider config) {
            ButtonGeneric resetButton = createResetButtonLocal(resetButtonX, y, config);
            ISliderCallback callback;
            if (config instanceof IConfigDouble doubleConfig) {
                callback = new SliderCallbackDouble(doubleConfig, resetButton);
            } else if (config instanceof IConfigInteger intConfig) {
                callback = new SliderCallbackInteger(intConfig, resetButton);
            } else {
                return;
            }

            WidgetSlider slider = new WidgetSlider(x, y, width, height, callback);
            ConfigOptionListenerResetConfig resetListener = new ConfigOptionListenerResetConfig(
                    config,
                    null,
                    resetButton,
                    null
            );
            this.addWidget(slider);
            this.addButton(resetButton, new ConfirmResetListener(resetListener));
        }

        private ButtonGeneric createResetButtonLocal(int x, int y, IConfigResettable config) {
            String label = StringUtils.translate("dlc_manager.config.reset");
            int width = Math.max(36, StringUtils.getStringWidth(label) + 10);
            ButtonGeneric resetButton = new ButtonGeneric(x, y, width, 20, label);
            resetButton.setEnabled(config.isModified());
            return resetButton;
        }
    }

    private static class DownloadMirrorModeCycleListener implements IButtonActionListener {
        private final IConfigValue config;
        private final ButtonGeneric modeButton;
        private final ButtonGeneric resetButton;

        private DownloadMirrorModeCycleListener(IConfigValue config, ButtonGeneric modeButton, ButtonGeneric resetButton) {
            this.config = config;
            this.modeButton = modeButton;
            this.resetButton = resetButton;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            String nextValue = nextDownloadMirrorMode(config.getStringValue());
            config.setValueFromString(nextValue);
            modeButton.setDisplayString(downloadMirrorModeLabel(nextValue));
            resetButton.setEnabled(config.isModified());
        }
    }

    private static class DownloadMirrorModeResetListener implements IButtonActionListener {
        private final IConfigValue config;
        private final ButtonGeneric modeButton;
        private final ButtonGeneric resetButton;

        private DownloadMirrorModeResetListener(IConfigValue config, ButtonGeneric modeButton, ButtonGeneric resetButton) {
            this.config = config;
            this.modeButton = modeButton;
            this.resetButton = resetButton;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            config.resetToDefault();
            modeButton.setDisplayString(downloadMirrorModeLabel(config.getStringValue()));
            resetButton.setEnabled(config.isModified());
        }
    }

    private static class DownloadMirrorProbeStrategyCycleListener implements IButtonActionListener {
        private final IConfigValue config;
        private final ButtonGeneric strategyButton;
        private final ButtonGeneric resetButton;

        private DownloadMirrorProbeStrategyCycleListener(IConfigValue config, ButtonGeneric strategyButton, ButtonGeneric resetButton) {
            this.config = config;
            this.strategyButton = strategyButton;
            this.resetButton = resetButton;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            String nextValue = nextDownloadMirrorProbeStrategy(config.getStringValue());
            config.setValueFromString(nextValue);
            strategyButton.setDisplayString(downloadMirrorProbeStrategyLabel(nextValue));
            resetButton.setEnabled(config.isModified());
        }
    }

    private static class DownloadMirrorProbeStrategyResetListener implements IButtonActionListener {
        private final IConfigValue config;
        private final ButtonGeneric strategyButton;
        private final ButtonGeneric resetButton;

        private DownloadMirrorProbeStrategyResetListener(IConfigValue config, ButtonGeneric strategyButton, ButtonGeneric resetButton) {
            this.config = config;
            this.strategyButton = strategyButton;
            this.resetButton = resetButton;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            config.resetToDefault();
            strategyButton.setDisplayString(downloadMirrorProbeStrategyLabel(config.getStringValue()));
            resetButton.setEnabled(config.isModified());
        }
    }

    private static class ConfirmResetListener implements IButtonActionListener {
        private final IButtonActionListener delegate;

        private ConfirmResetListener(IButtonActionListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            Minecraft minecraft = Minecraft.getInstance();
            Screen returnScreen = minecraft.screen;
            Component title = Component.translatable("dlc_manager.config.reset_title");
            Component message = Component.translatable("dlc_manager.config.reset_prompt");
            Component yes = Component.translatable("dlc_manager.config.reset_yes");
            Component no = Component.translatable("dlc_manager.config.reset_no");
            minecraft.setScreen(new ConfirmScreen(accepted -> {
                minecraft.setScreen(returnScreen);
                if (!accepted) {
                    return;
                }
                delegate.actionPerformedWithButton(button, mouseButton);
                if (returnScreen instanceof DlcServerConfigMaLiLibScreen screen) {
                    screen.initGui();
                }
            }, title, message, yes, no));
        }
    }

    public enum Tab {
        SERVER("dlc_manager.config.tab.server"),
        UI("dlc_manager.config.tab.ui"),
        PATHS("dlc_manager.config.tab.paths"),
        DOWNLOADS("dlc_manager.config.tab.download");

        private final String labelKey;

        Tab(String labelKey) {
            this.labelKey = labelKey;
        }
    }
}
