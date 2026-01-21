package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.core.DlcPathConfig;
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

public class DlcConfigScreen extends GuiModConfigs {
    private final Tab currentTab;
    private int maxLabelWidth;
    private WidgetListConfigOptionsBase<?, ?> listWidget;
    private ButtonGeneric saveButton;
    private ButtonGeneric cancelButton;

    public DlcConfigScreen(Screen parent) {
        this(parent, Tab.PATHS);
    }

    public DlcConfigScreen(Screen parent, Tab tab) {
        super(DlcPathConfig.MOD_ID, buildOptions(resolveTab(tab)), false, "dlc_manager.config.menu.title");
        this.currentTab = resolveTab(tab);
        this.setParent(parent);
        this.setTitle(StringUtils.translate("dlc_manager.config.menu.title"));
        this.maxLabelWidth = 220;
    }

    @Override
    public void initGui() {
        int x = 10;
        int y = 28;
        int height = 20;
        int gap = 4;
        int listY = y + height + 10;
        this.setListPosition(10, listY);
        this.maxLabelWidth = Math.min(150, this.width / 5);
        int browserWidth = this.getBrowserWidth();
        int available = browserWidth - this.maxLabelWidth - 80;
        int configWidth = Math.max(110, Math.min(260, available));
        this.setConfigWidth(configWidth);
        super.initGui();
        x = addTabButton(Tab.PATHS, x, y, height);
        x += gap;
        x = addTabButton(Tab.UI, x, y, height);
        x += gap;
        addTabButton(Tab.DOWNLOADS, x, y, height);

        int buttonWidth = 80;
        int buttonHeight = 20;
        int buttonY = this.height - 28;
        int centerX = this.width / 2;
        int spacing = 10;
        int saveX = centerX - spacing / 2 - buttonWidth;
        int cancelX = centerX + spacing / 2;
        this.saveButton = new ButtonGeneric(saveX, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.config.save"));
        this.cancelButton = new ButtonGeneric(cancelX, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.config.cancel"));
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
        this.drawString(guiGraphics, StringUtils.translate("dlc_manager.config.menu.title"), 10, 10, 0xFFFFFF);
    }

    @Override
    protected int getBrowserHeight() {
        return this.height - 90;
    }

    @Override
    protected WidgetListConfigOptions createListWidget(int x, int y) {
        this.listWidget = new DlcConfigListWidget(
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
        boolean modified = this.listWidget != null && this.listWidget.wereConfigsModified();
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
        return tab == null ? Tab.PATHS : tab;
    }

    private static List<ConfigOptionWrapper> buildOptions(Tab tab) {
        List<IConfigBase> configs = switch (tab) {
            case UI -> DlcPathConfig.getUiConfigs();
            case DOWNLOADS -> DlcPathConfig.getDownloadConfigs();
            case PATHS -> DlcPathConfig.getPathConfigs();
        };
        return new ArrayList<>(ConfigOptionWrapper.createFor(configs));
    }

    private class TabButtonListener implements IButtonActionListener {
        private final Tab target;

        private TabButtonListener(Tab target) {
            this.target = target;
        }

        @Override
        public void actionPerformedWithButton(fi.dy.masa.malilib.gui.button.ButtonBase button, int mouseButton) {
            if (target == currentTab) {
                return;
            }
            DlcConfigScreen.this.openTab(target);
        }
    }

    private void openTab(Tab tab) {
        this.mc.setScreen(new DlcConfigScreen(this.getParent(), tab));
    }

    private void saveAndClose() {
        if (this.listWidget != null) {
            this.listWidget.applyPendingModifications();
            this.listWidget.clearConfigsModifiedFlag();
        }
        DlcPathConfig.save();
        this.mc.setScreen(this.getParent());
    }

    private void cancelAndClose() {
        if (this.listWidget == null || !this.listWidget.wereConfigsModified()) {
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
            DlcPathConfig.load();
            minecraft.setScreen(this.getParent());
        }, title, message, yes, no));
    }

    private class SaveButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            DlcConfigScreen.this.saveAndClose();
        }
    }

    private class CancelButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            DlcConfigScreen.this.cancelAndClose();
        }
    }

    private static class DlcConfigListWidget extends WidgetListConfigOptions {
        private final int labelWidthCap;

        public DlcConfigListWidget(int x, int y, int width, int height, int configWidth, float zLevel, boolean useKeybindSearch,
                                   DlcConfigScreen parent, int labelWidthCap) {
            super(x, y, width, height, configWidth, zLevel, useKeybindSearch, parent);
            this.labelWidthCap = labelWidthCap;
        }

        @Override
        public int getMaxNameLengthWrapped(List<ConfigOptionWrapper> list) {
            return Math.min(super.getMaxNameLengthWrapped(list), this.labelWidthCap);
        }

        @Override
        protected WidgetConfigOption createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ConfigOptionWrapper entry) {
            return new DlcConfigOptionWidget(
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

    private static class DlcConfigOptionWidget extends WidgetConfigOption {
        public DlcConfigOptionWidget(int x, int y, int width, int height, int maxLabelWidth, int configWidth,
                                     ConfigOptionWrapper wrapper, int listIndex, GuiConfigsBase parent,
                                     WidgetListConfigOptionsBase<?, ?> list) {
            super(x, y, width, height, maxLabelWidth, configWidth, wrapper, listIndex, (IKeybindConfigGui) parent, list);
        }

        @Override
        protected void addConfigButtonEntry(int x, int y, IConfigResettable config, ButtonBase configButton) {
            ButtonGeneric resetButton = createResetButton(x, y, config);
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
            GuiTextFieldGeneric textField = createTextField(x, y + 1, width - 4, height - 3);
            textField.setMaxLength(this.maxTextfieldTextLength);
            textField.setValue(config.getStringValue());

            ButtonGeneric resetButton = createResetButton(resetButtonX, y, config);
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
            ButtonGeneric resetButton = createResetButton(resetButtonX, y, config);
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
    }

    private static class ConfirmResetListener implements IButtonActionListener {
        private final ConfigOptionListenerResetConfig delegate;

        private ConfirmResetListener(ConfigOptionListenerResetConfig delegate) {
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
            }, title, message, yes, no));
        }
    }

    public enum Tab {
        PATHS("dlc_manager.config.tab.paths"),
        UI("dlc_manager.config.tab.ui"),
        DOWNLOADS("dlc_manager.config.tab.download");

        private final String labelKey;

        Tab(String labelKey) {
            this.labelKey = labelKey;
        }
    }
}
