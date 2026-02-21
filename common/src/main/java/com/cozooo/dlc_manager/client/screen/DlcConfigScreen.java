package com.cozooo.dlc_manager.client.screen;

import com.cozooo.dlc_manager.client.config.DlcPathConfigClient;
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
    private ButtonGeneric checkUpdateButton;
    private static final String[] ANCHOR_VALUES_WITH_AUTO = new String[] {
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
    };
    private static final String[] ANCHOR_VALUES = new String[] {
            "top_left",
            "top",
            "top_right",
            "left",
            "center",
            "right",
            "bottom_left",
            "bottom",
            "bottom_right"
    };
    private static final String[] NOTICE_DISPLAY_MODE_VALUES = new String[] {
            "manager_button",
            "main_menu_button",
            "main_menu_panel"
    };

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
        int browserWidth = this.getBrowserWidth();
        this.maxLabelWidth = computeDynamicLabelWidth(this.currentTab, browserWidth);
        int available = browserWidth - this.maxLabelWidth - 80;
        int configWidth = Math.max(110, Math.min(320, available));
        this.setConfigWidth(configWidth);
        super.initGui();
        x = addTabButton(Tab.PATHS, x, y, height);
        x += gap;
        x = addTabButton(Tab.UI, x, y, height);
        x += gap;
        addTabButton(Tab.DOWNLOADS, x, y, height);

        String checkLabel = StringUtils.translate("dlc_manager.config.check_update");
        String saveLabel = StringUtils.translate("dlc_manager.config.save");
        String cancelLabel = StringUtils.translate("dlc_manager.config.cancel");
        int checkWidth = Math.max(110, this.getStringWidth(checkLabel) + 16);
        int saveWidth = Math.max(80, this.getStringWidth(saveLabel) + 16);
        int cancelWidth = Math.max(80, this.getStringWidth(cancelLabel) + 16);
        int buttonHeight = 20;
        int buttonY = this.height - 28;
        int centerX = this.width / 2;
        int spacing = 10;
        int totalWidth = checkWidth + saveWidth + cancelWidth + spacing * 2;
        int startX = centerX - totalWidth / 2;
        int checkX = startX;
        int saveX = startX + checkWidth + spacing;
        int cancelX = saveX + saveWidth + spacing;
        this.checkUpdateButton = new ButtonGeneric(checkX, buttonY, checkWidth, buttonHeight, checkLabel);
        this.saveButton = new ButtonGeneric(saveX, buttonY, saveWidth, buttonHeight, saveLabel);
        this.cancelButton = new ButtonGeneric(cancelX, buttonY, cancelWidth, buttonHeight, cancelLabel);
        this.addButton(this.checkUpdateButton, new CheckUpdateButtonListener());
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
        DlcPathConfigClient.syncFromConfig();
        List<IConfigBase> configs = getConfigsForTab(tab);
        return new ArrayList<>(ConfigOptionWrapper.createFor(configs));
    }

    private static List<IConfigBase> getConfigsForTab(Tab tab) {
        return switch (tab) {
            case UI -> DlcPathConfigClient.getUiConfigs();
            case DOWNLOADS -> DlcPathConfigClient.getDownloadConfigs();
            case PATHS -> DlcPathConfigClient.getPathConfigs();
        };
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

    private static boolean isAnchorConfig(IConfigBase config) {
        if (config == null) {
            return false;
        }
        String name = config.getName();
        return "titleAnchor".equals(name)
            || "titleUpdateAnchor".equals(name)
            || "noticeButtonAnchor".equals(name)
            || "noticePanelAnchor".equals(name);
    }

    private static String[] anchorValuesForConfig(String configName) {
        if ("titleAnchor".equals(configName) || "titleUpdateAnchor".equals(configName)) {
            return ANCHOR_VALUES_WITH_AUTO;
        }
        return ANCHOR_VALUES;
    }

    private static boolean isNoticeDisplayModeConfig(IConfigBase config) {
        return config != null && "noticeDisplayMode".equals(config.getName());
    }

    private static String normalizeNoticeDisplayMode(String value) {
        if (value == null || value.isBlank()) {
            return NOTICE_DISPLAY_MODE_VALUES[0];
        }
        String normalized = value.trim().toLowerCase().replace(' ', '_').replace('-', '_');
        for (String option : NOTICE_DISPLAY_MODE_VALUES) {
            if (option.equals(normalized)) {
                return option;
            }
        }
        return NOTICE_DISPLAY_MODE_VALUES[0];
    }

    private static String nextNoticeDisplayMode(String value) {
        String normalized = normalizeNoticeDisplayMode(value);
        for (int i = 0; i < NOTICE_DISPLAY_MODE_VALUES.length; i++) {
            if (NOTICE_DISPLAY_MODE_VALUES[i].equals(normalized)) {
                return NOTICE_DISPLAY_MODE_VALUES[(i + 1) % NOTICE_DISPLAY_MODE_VALUES.length];
            }
        }
        return NOTICE_DISPLAY_MODE_VALUES[0];
    }

    private static String noticeDisplayModeLabel(String value) {
        String normalized = normalizeNoticeDisplayMode(value);
        String key = "dlc_manager.config.ui.noticeDisplayMode." + normalized;
        String label = StringUtils.translate(key);
        return label.equals(key) ? normalized : label;
    }

    private static String normalizeAnchorValue(String value) {
        if (value == null || value.isBlank()) {
            return "auto";
        }
        String normalized = value.trim().toLowerCase().replace(' ', '_');
        return switch (normalized) {
            case "topleft", "top-left" -> "top_left";
            case "topcenter", "top_center" -> "top";
            case "topright", "top-right" -> "top_right";
            case "centerleft", "center_left" -> "left";
            case "centerright", "center_right" -> "right";
            case "bottomleft", "bottom-left" -> "bottom_left";
            case "bottomcenter", "bottom_center" -> "bottom";
            case "bottomright", "bottom-right" -> "bottom_right";
            default -> normalized;
        };
    }

    private static int indexOfAnchorValue(String value, String[] values) {
        String normalized = normalizeAnchorValue(value);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(normalized)) {
                return i;
            }
        }
        return 0;
    }

    private static String nextAnchorValue(String configName, String value) {
        String[] values = anchorValuesForConfig(configName);
        int index = indexOfAnchorValue(value, values);
        int nextIndex = (index + 1) % values.length;
        return values[nextIndex];
    }

    private static String anchorTranslationPrefix(String configName) {
        if ("noticeButtonAnchor".equals(configName)) {
            return "dlc_manager.config.ui.noticeButtonAnchor.";
        }
        if ("noticePanelAnchor".equals(configName)) {
            return "dlc_manager.config.ui.noticePanelAnchor.";
        }
        if ("titleUpdateAnchor".equals(configName)) {
            return "dlc_manager.config.ui.titleUpdateAnchor.";
        }
        return "dlc_manager.config.ui.titleAnchor.";
    }

    private static String anchorDisplay(String configName, String value) {
        String normalized = normalizeAnchorValue(value);
        String key = anchorTranslationPrefix(configName) + normalized;
        String label = StringUtils.translate(key);
        return label.equals(key) ? normalized : label;
    }

    private void saveAndClose() {
        if (this.listWidget != null) {
            this.listWidget.applyPendingModifications();
            this.listWidget.clearConfigsModifiedFlag();
        }
        DlcPathConfigClient.applyToConfig();
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
            DlcPathConfigClient.syncFromConfig();
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

    private class CheckUpdateButtonListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            DlcConfigScreen.this.mc.setScreen(new DlcSelfUpdateScreen(DlcConfigScreen.this, null));
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
            if (isNoticeDisplayModeConfig(config)) {
                ButtonGeneric modeButton = new ButtonGeneric(x, y, width, height, noticeDisplayModeLabel(config.getStringValue()));
                ButtonGeneric resetButton = createResetButton(resetButtonX, y, config);
                this.addButton(modeButton, new NoticeDisplayModeCycleListener(config, modeButton, resetButton));
                this.addButton(resetButton, new ConfirmResetListenerGeneric(new NoticeDisplayModeResetListener(config, modeButton, resetButton)));
                return;
            }
            if (isAnchorConfig(config)) {
                ButtonGeneric anchorButton = new ButtonGeneric(x, y, width, height, anchorDisplay(config.getName(), config.getStringValue()));
                ButtonGeneric resetButton = createResetButton(resetButtonX, y, config);
                this.addButton(anchorButton, new AnchorCycleListener(config, anchorButton, resetButton));
                this.addButton(resetButton, new ConfirmResetListenerGeneric(new AnchorResetListener(config, anchorButton, resetButton)));
                return;
            }
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
            if (returnScreen instanceof DlcConfigScreen screen) {
                screen.initGui();
            }
        }, title, message, yes, no));
    }
    }

    private static class ConfirmResetListenerGeneric implements IButtonActionListener {
        private final IButtonActionListener delegate;

        private ConfirmResetListenerGeneric(IButtonActionListener delegate) {
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
            if (returnScreen instanceof DlcConfigScreen screen) {
                screen.initGui();
            }
        }, title, message, yes, no));
    }
    }

    private static class NoticeDisplayModeCycleListener implements IButtonActionListener {
        private final IConfigValue config;
        private final ButtonGeneric modeButton;
        private final ButtonGeneric resetButton;

        private NoticeDisplayModeCycleListener(IConfigValue config, ButtonGeneric modeButton, ButtonGeneric resetButton) {
            this.config = config;
            this.modeButton = modeButton;
            this.resetButton = resetButton;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            String nextValue = nextNoticeDisplayMode(config.getStringValue());
            config.setValueFromString(nextValue);
            modeButton.setDisplayString(noticeDisplayModeLabel(nextValue));
            resetButton.setEnabled(config.isModified());
        }
    }

    private static class NoticeDisplayModeResetListener implements IButtonActionListener {
        private final IConfigValue config;
        private final ButtonGeneric modeButton;
        private final ButtonGeneric resetButton;

        private NoticeDisplayModeResetListener(IConfigValue config, ButtonGeneric modeButton, ButtonGeneric resetButton) {
            this.config = config;
            this.modeButton = modeButton;
            this.resetButton = resetButton;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            config.resetToDefault();
            modeButton.setDisplayString(noticeDisplayModeLabel(config.getStringValue()));
            resetButton.setEnabled(config.isModified());
        }
    }

    private static class AnchorCycleListener implements IButtonActionListener {
        private final IConfigValue config;
        private final ButtonGeneric anchorButton;
        private final ButtonGeneric resetButton;

        private AnchorCycleListener(IConfigValue config, ButtonGeneric anchorButton, ButtonGeneric resetButton) {
            this.config = config;
            this.anchorButton = anchorButton;
            this.resetButton = resetButton;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            String nextValue = nextAnchorValue(config.getName(), config.getStringValue());
            config.setValueFromString(nextValue);
            anchorButton.setDisplayString(anchorDisplay(config.getName(), nextValue));
            resetButton.setEnabled(config.isModified());
        }
    }

    private static class AnchorResetListener implements IButtonActionListener {
        private final IConfigValue config;
        private final ButtonGeneric anchorButton;
        private final ButtonGeneric resetButton;

        private AnchorResetListener(IConfigValue config, ButtonGeneric anchorButton, ButtonGeneric resetButton) {
            this.config = config;
            this.anchorButton = anchorButton;
            this.resetButton = resetButton;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            config.resetToDefault();
            anchorButton.setDisplayString(anchorDisplay(config.getName(), config.getStringValue()));
            resetButton.setEnabled(config.isModified());
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
