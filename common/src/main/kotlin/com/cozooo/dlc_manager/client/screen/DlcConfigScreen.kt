package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.config.DlcPathConfigClient
import com.cozooo.dlc_manager.core.DlcPathConfig
import fi.dy.masa.malilib.config.IConfigBase
import fi.dy.masa.malilib.config.IConfigDouble
import fi.dy.masa.malilib.config.IConfigInteger
import fi.dy.masa.malilib.config.IConfigResettable
import fi.dy.masa.malilib.config.IConfigSlider
import fi.dy.masa.malilib.config.IConfigValue
import fi.dy.masa.malilib.config.gui.ConfigOptionChangeListenerButton
import fi.dy.masa.malilib.config.gui.ConfigOptionChangeListenerTextField
import fi.dy.masa.malilib.config.gui.ConfigOptionListenerResetConfig
import fi.dy.masa.malilib.config.gui.ConfigOptionListenerResetConfig.ConfigResetterButton
import fi.dy.masa.malilib.config.gui.ConfigOptionListenerResetConfig.ConfigResetterTextField
import fi.dy.masa.malilib.config.gui.GuiModConfigs
import fi.dy.masa.malilib.config.gui.SliderCallbackDouble
import fi.dy.masa.malilib.config.gui.SliderCallbackInteger
import fi.dy.masa.malilib.gui.GuiConfigsBase
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric
import fi.dy.masa.malilib.gui.button.ButtonBase
import fi.dy.masa.malilib.gui.button.ButtonGeneric
import fi.dy.masa.malilib.gui.button.IButtonActionListener
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui
import fi.dy.masa.malilib.gui.interfaces.ISliderCallback
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase
import fi.dy.masa.malilib.gui.widgets.WidgetSlider
import fi.dy.masa.malilib.util.StringUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.ArrayList

class DlcConfigScreen private constructor(
    parent: Screen?,
    tab: Tab,
    private val session: ConfigSession,
    syncFromBacking: Boolean,
) : GuiModConfigs(DlcPathConfig.MOD_ID, buildOptions(resolveTab(tab), syncFromBacking), false, "dlc_manager.config.menu.title") {
    private val currentTab: Tab = resolveTab(tab)
    private var maxLabelWidth = 220
    private var listWidget: WidgetListConfigOptionsBase<*, *>? = null
    private var saveButton: ButtonGeneric? = null
    private var cancelButton: ButtonGeneric? = null
    private var checkUpdateButton: ButtonGeneric? = null

    @JvmOverloads
    constructor(parent: Screen?, tab: Tab = Tab.PATHS) : this(parent, tab, ConfigSession(), true)

    init {
        setParent(parent)
        setTitle(StringUtils.translate("dlc_manager.config.menu.title"))
    }

    override fun initGui() {
        var x = 10
        val y = 28
        val height = 20
        val gap = 4
        val listY = y + height + 10
        setListPosition(10, listY)
        val browserWidth = getBrowserWidth()
        maxLabelWidth = computeDynamicLabelWidth(currentTab, browserWidth)
        val available = browserWidth - maxLabelWidth - 80
        val configWidth = maxOf(110, minOf(320, available))
        setConfigWidth(configWidth)
        super.initGui()
        x = addTabButton(Tab.PATHS, x, y, height)
        x += gap
        x = addTabButton(Tab.SERVER, x, y, height)
        x += gap
        x = addTabButton(Tab.UI, x, y, height)
        x += gap
        x = addTabButton(Tab.MODPACK, x, y, height)
        x += gap
        addTabButton(Tab.DOWNLOADS, x, y, height)

        val checkLabel = StringUtils.translate("dlc_manager.config.check_update")
        val saveLabel = StringUtils.translate("dlc_manager.config.save")
        val cancelLabel = StringUtils.translate("dlc_manager.config.cancel")
        val checkWidth = maxOf(110, getStringWidth(checkLabel) + 16)
        val saveWidth = maxOf(80, getStringWidth(saveLabel) + 16)
        val cancelWidth = maxOf(80, getStringWidth(cancelLabel) + 16)
        val buttonHeight = 20
        val buttonY = this.height - 28
        val centerX = this.width / 2
        val spacing = 10
        val totalWidth = checkWidth + saveWidth + cancelWidth + spacing * 2
        val startX = centerX - totalWidth / 2
        val checkX = startX
        val saveX = startX + checkWidth + spacing
        val cancelX = saveX + saveWidth + spacing
        checkUpdateButton = ButtonGeneric(checkX, buttonY, checkWidth, buttonHeight, checkLabel)
        saveButton = ButtonGeneric(saveX, buttonY, saveWidth, buttonHeight, saveLabel)
        cancelButton = ButtonGeneric(cancelX, buttonY, cancelWidth, buttonHeight, cancelLabel)
        addButton(checkUpdateButton, CheckUpdateButtonListener())
        addButton(saveButton, SaveButtonListener())
        addButton(cancelButton, CancelButtonListener())
        updateSaveButtonState()
    }

    override fun tick() {
        super.tick()
        updateSaveButtonState()
    }

    override fun drawTitle(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawString(guiGraphics, StringUtils.translate("dlc_manager.config.menu.title"), 10, 10, 0xFFFFFF)
    }

    override fun getBrowserHeight(): Int {
        return height - 90
    }

    override fun createListWidget(x: Int, y: Int): WidgetListConfigOptions {
        listWidget = DlcConfigListWidget(
            x,
            y,
            getBrowserWidth(),
            getBrowserHeight(),
            getConfigWidth(),
            0.0f,
            useKeybindSearch(),
            this,
            maxLabelWidth,
        )
        return listWidget as WidgetListConfigOptions
    }

    private fun updateSaveButtonState() {
        val button = saveButton ?: return
        val modified = session.modified || listWidget?.wereConfigsModified() == true
        button.setEnabled(modified)
    }

    private fun addTabButton(tab: Tab, x: Int, y: Int, height: Int): Int {
        val label = StringUtils.translate(tab.labelKey)
        val width = maxOf(60, getStringWidth(label) + 10)
        val button = ButtonGeneric(x, y, width, height, label)
        button.setEnabled(tab != currentTab)
        addButton(button, TabButtonListener(tab))
        return x + width
    }

    private fun computeDynamicLabelWidth(tab: Tab, browserWidth: Int): Int {
        var longest = 0
        for (config in getConfigsForTab(tab)) {
            var label = config.configGuiDisplayName
            if (label.isNullOrBlank()) {
                label = config.name
            }
            longest = maxOf(longest, getStringWidth(label))
        }
        val desired = longest + 14
        val maxByLayout = maxOf(60, browserWidth - 80 - 110)
        return maxOf(60, minOf(desired, maxByLayout))
    }

    private inner class TabButtonListener(private val target: Tab) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            if (target == currentTab) {
                return
            }
            openTab(target)
        }
    }

    private fun openTab(tab: Tab) {
        flushCurrentModifications()
        mc.setScreen(DlcConfigScreen(getParent(), tab, session, false))
    }

    private fun saveAndClose() {
        flushCurrentModifications()
        DlcPathConfigClient.applyToConfig()
        DlcPathConfig.save()
        session.modified = false
        mc.setScreen(getParent())
    }

    private fun cancelAndClose() {
        if (!session.modified && listWidget?.wereConfigsModified() != true) {
            mc.setScreen(getParent())
            return
        }
        val minecraft = Minecraft.getInstance()
        val returnScreen = minecraft.screen
        val title = Component.translatable("dlc_manager.config.cancel_title")
        val message = Component.translatable("dlc_manager.config.cancel_prompt")
        val yes = Component.translatable("dlc_manager.config.cancel_yes")
        val no = Component.translatable("dlc_manager.config.cancel_no")
        minecraft.setScreen(
            ConfirmScreen(
                { accepted ->
                    minecraft.setScreen(returnScreen)
                    if (!accepted) {
                        return@ConfirmScreen
                    }
                    DlcPathConfig.load()
                    DlcPathConfigClient.syncFromConfig()
                    session.modified = false
                    minecraft.setScreen(getParent())
                },
                title,
                message,
                yes,
                no,
            )
        )
    }

    private fun flushCurrentModifications() {
        val widget = listWidget ?: return
        if (widget.wereConfigsModified()) {
            session.modified = true
        }
        widget.applyPendingModifications()
        widget.clearConfigsModifiedFlag()
    }

    private inner class SaveButtonListener : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            saveAndClose()
        }
    }

    private inner class CancelButtonListener : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            cancelAndClose()
        }
    }

    private inner class CheckUpdateButtonListener : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            mc.setScreen(DlcSelfUpdateScreen(this@DlcConfigScreen, null))
        }
    }

    private class ConfigSession {
        var modified = false
    }

    enum class Tab(val labelKey: String) {
        SERVER("dlc_manager.config.tab.server"),
        PATHS("dlc_manager.config.tab.paths"),
        UI("dlc_manager.config.tab.ui"),
        MODPACK("dlc_manager.config.tab.modpack"),
        DOWNLOADS("dlc_manager.config.tab.download"),
    }

    private class DlcConfigListWidget(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        configWidth: Int,
        zLevel: Float,
        useKeybindSearch: Boolean,
        parent: DlcConfigScreen,
        private val labelWidthCap: Int,
    ) : WidgetListConfigOptions(x, y, width, height, configWidth, zLevel, useKeybindSearch, parent) {
        override fun getMaxNameLengthWrapped(list: List<ConfigOptionWrapper>): Int {
            return minOf(super.getMaxNameLengthWrapped(list), labelWidthCap)
        }

        override fun createListEntryWidget(
            x: Int,
            y: Int,
            listIndex: Int,
            isOdd: Boolean,
            entry: ConfigOptionWrapper,
        ): WidgetConfigOption {
            return DlcConfigOptionWidget(
                x,
                y,
                browserEntryWidth,
                browserEntryHeight,
                maxLabelWidth,
                configWidth,
                entry,
                listIndex,
                parent,
                this,
            )
        }
    }

    private class DlcConfigOptionWidget(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        maxLabelWidth: Int,
        configWidth: Int,
        wrapper: ConfigOptionWrapper,
        listIndex: Int,
        parent: GuiConfigsBase,
        list: WidgetListConfigOptionsBase<*, *>,
    ) : WidgetConfigOption(x, y, width, height, maxLabelWidth, configWidth, wrapper, listIndex, parent as IKeybindConfigGui, list) {
        override fun addConfigButtonEntry(x: Int, y: Int, config: IConfigResettable, configButton: ButtonBase) {
            val resetButton = createResetButtonLocal(x, y, config)
            val changeListener = ConfigOptionChangeListenerButton(config, resetButton, null)
            val resetListener = ConfigOptionListenerResetConfig(
                config,
                ConfigResetterButton(configButton),
                resetButton,
                null,
            )
            addButton(configButton, changeListener)
            addButton(resetButton, ConfirmResetListener(resetListener))
        }

        override fun addConfigTextFieldEntry(x: Int, y: Int, resetButtonX: Int, width: Int, height: Int, config: IConfigValue) {
            if (isNoticeDisplayModeConfig(config)) {
                val modeButton = ButtonGeneric(x, y, width, height, noticeDisplayModeLabel(config.stringValue))
                val resetButton = createResetButtonLocal(resetButtonX, y, config)
                addButton(modeButton, NoticeDisplayModeCycleListener(config, modeButton, resetButton))
                addButton(resetButton, ConfirmResetListenerGeneric(NoticeDisplayModeResetListener(config, modeButton, resetButton)))
                return
            }
            if (isAnchorConfig(config)) {
                val anchorButton = ButtonGeneric(x, y, width, height, anchorDisplay(config.name, config.stringValue))
                val resetButton = createResetButtonLocal(resetButtonX, y, config)
                addButton(anchorButton, AnchorCycleListener(config, anchorButton, resetButton))
                addButton(resetButton, ConfirmResetListenerGeneric(AnchorResetListener(config, anchorButton, resetButton)))
                return
            }
            val textField: GuiTextFieldGeneric = DlcSafeTextField(x, y + 1, width - 4, height - 3, Minecraft.getInstance().font)
            textField.setMaxLength(maxTextfieldTextLength)
            textField.setValue(config.stringValue)

            val resetButton = createResetButtonLocal(resetButtonX, y, config)
            val changeListener = ConfigOptionChangeListenerTextField(config, textField, resetButton)
            val resetListener = ConfigOptionListenerResetConfig(
                config,
                ConfigResetterTextField(config, textField),
                resetButton,
                null,
            )
            addTextField(textField, changeListener)
            addButton(resetButton, ConfirmResetListener(resetListener))
        }

        override fun addConfigSliderEntry(x: Int, y: Int, resetButtonX: Int, width: Int, height: Int, config: IConfigSlider) {
            val resetButton = createResetButtonLocal(resetButtonX, y, config)
            val callback: ISliderCallback = when (config) {
                is IConfigDouble -> SliderCallbackDouble(config, resetButton)
                is IConfigInteger -> SliderCallbackInteger(config, resetButton)
                else -> return
            }

            val slider = WidgetSlider(x, y, width, height, callback)
            val resetListener = ConfigOptionListenerResetConfig(
                config,
                null,
                resetButton,
                null,
            )
            addWidget(slider)
            addButton(resetButton, ConfirmResetListener(resetListener))
        }

        private fun createResetButtonLocal(x: Int, y: Int, config: IConfigResettable): ButtonGeneric {
            val label = StringUtils.translate("dlc_manager.config.reset")
            val width = maxOf(36, StringUtils.getStringWidth(label) + 10)
            val resetButton = ButtonGeneric(x, y, width, 20, label)
            resetButton.setEnabled(config.isModified)
            return resetButton
        }
    }

    private class ConfirmResetListener(private val delegate: ConfigOptionListenerResetConfig) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            confirmReset(button, mouseButton, delegate)
        }
    }

    private class ConfirmResetListenerGeneric(private val delegate: IButtonActionListener) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            confirmReset(button, mouseButton, delegate)
        }
    }

    private class NoticeDisplayModeCycleListener(
        private val config: IConfigValue,
        private val modeButton: ButtonGeneric,
        private val resetButton: ButtonGeneric,
    ) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            val nextValue = nextNoticeDisplayMode(config.stringValue)
            config.setValueFromString(nextValue)
            modeButton.setDisplayString(noticeDisplayModeLabel(nextValue))
            resetButton.setEnabled(config.isModified)
        }
    }

    private class NoticeDisplayModeResetListener(
        private val config: IConfigValue,
        private val modeButton: ButtonGeneric,
        private val resetButton: ButtonGeneric,
    ) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            config.resetToDefault()
            modeButton.setDisplayString(noticeDisplayModeLabel(config.stringValue))
            resetButton.setEnabled(config.isModified)
        }
    }

    private class AnchorCycleListener(
        private val config: IConfigValue,
        private val anchorButton: ButtonGeneric,
        private val resetButton: ButtonGeneric,
    ) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            val nextValue = nextAnchorValue(config.name, config.stringValue)
            config.setValueFromString(nextValue)
            anchorButton.setDisplayString(anchorDisplay(config.name, nextValue))
            resetButton.setEnabled(config.isModified)
        }
    }

    private class AnchorResetListener(
        private val config: IConfigValue,
        private val anchorButton: ButtonGeneric,
        private val resetButton: ButtonGeneric,
    ) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            config.resetToDefault()
            anchorButton.setDisplayString(anchorDisplay(config.name, config.stringValue))
            resetButton.setEnabled(config.isModified)
        }
    }

    companion object {
        private val ANCHOR_VALUES_WITH_AUTO = arrayOf(
            "auto",
            "top_left",
            "top",
            "top_right",
            "left",
            "center",
            "right",
            "bottom_left",
            "bottom",
            "bottom_right",
        )
        private val ANCHOR_VALUES = arrayOf(
            "top_left",
            "top",
            "top_right",
            "left",
            "center",
            "right",
            "bottom_left",
            "bottom",
            "bottom_right",
        )
        private val NOTICE_DISPLAY_MODE_VALUES = arrayOf(
            "manager_button",
            "main_menu_button",
            "main_menu_panel",
        )

        @JvmStatic
        private fun resolveTab(tab: Tab?): Tab {
            return tab ?: Tab.PATHS
        }

        @JvmStatic
        private fun buildOptions(tab: Tab, syncFromBacking: Boolean): List<ConfigOptionWrapper> {
            if (syncFromBacking) {
                DlcPathConfigClient.syncFromConfig()
            }
            val configs: List<IConfigBase> = getConfigsForTab(tab)
            return ArrayList(ConfigOptionWrapper.createFor(configs))
        }

        @JvmStatic
        private fun getConfigsForTab(tab: Tab): List<IConfigBase> {
            return when (tab) {
                Tab.SERVER -> DlcPathConfigClient.getServerConfigs()
                Tab.UI -> DlcPathConfigClient.getUiConfigs()
                Tab.MODPACK -> DlcPathConfigClient.getModpackConfigs()
                Tab.DOWNLOADS -> DlcPathConfigClient.getDownloadConfigs()
                Tab.PATHS -> DlcPathConfigClient.getPathConfigs()
            }
        }

        private fun isAnchorConfig(config: IConfigBase?): Boolean {
            if (config == null) {
                return false
            }
            return when (config.name) {
                "titleAnchor", "titleUpdateAnchor", "noticeButtonAnchor", "noticePanelAnchor" -> true
                else -> false
            }
        }

        private fun anchorValuesForConfig(configName: String?): Array<String> {
            if (configName == "titleAnchor" || configName == "titleUpdateAnchor") {
                return ANCHOR_VALUES_WITH_AUTO
            }
            return ANCHOR_VALUES
        }

        private fun isNoticeDisplayModeConfig(config: IConfigBase?): Boolean {
            return config != null && config.name == "noticeDisplayMode"
        }

        private fun normalizeNoticeDisplayMode(value: String?): String {
            if (value.isNullOrBlank()) {
                return NOTICE_DISPLAY_MODE_VALUES[0]
            }
            val normalized = value.trim().lowercase().replace(' ', '_').replace('-', '_')
            for (option in NOTICE_DISPLAY_MODE_VALUES) {
                if (option == normalized) {
                    return option
                }
            }
            return NOTICE_DISPLAY_MODE_VALUES[0]
        }

        private fun nextNoticeDisplayMode(value: String?): String {
            val normalized = normalizeNoticeDisplayMode(value)
            for (i in NOTICE_DISPLAY_MODE_VALUES.indices) {
                if (NOTICE_DISPLAY_MODE_VALUES[i] == normalized) {
                    return NOTICE_DISPLAY_MODE_VALUES[(i + 1) % NOTICE_DISPLAY_MODE_VALUES.size]
                }
            }
            return NOTICE_DISPLAY_MODE_VALUES[0]
        }

        private fun noticeDisplayModeLabel(value: String?): String {
            val normalized = normalizeNoticeDisplayMode(value)
            val key = "dlc_manager.config.ui.noticeDisplayMode.$normalized"
            val label = StringUtils.translate(key)
            return if (label == key) normalized else label
        }

        private fun normalizeAnchorValue(value: String?): String {
            if (value.isNullOrBlank()) {
                return "auto"
            }
            return when (val normalized = value.trim().lowercase().replace(' ', '_')) {
                "topleft", "top-left" -> "top_left"
                "topcenter", "top_center" -> "top"
                "topright", "top-right" -> "top_right"
                "centerleft", "center_left" -> "left"
                "centerright", "center_right" -> "right"
                "bottomleft", "bottom-left" -> "bottom_left"
                "bottomcenter", "bottom_center" -> "bottom"
                "bottomright", "bottom-right" -> "bottom_right"
                else -> normalized
            }
        }

        private fun indexOfAnchorValue(value: String?, values: Array<String>): Int {
            val normalized = normalizeAnchorValue(value)
            for (i in values.indices) {
                if (values[i] == normalized) {
                    return i
                }
            }
            return 0
        }

        private fun nextAnchorValue(configName: String?, value: String?): String {
            val values = anchorValuesForConfig(configName)
            val index = indexOfAnchorValue(value, values)
            val nextIndex = (index + 1) % values.size
            return values[nextIndex]
        }

        private fun anchorTranslationPrefix(configName: String?): String {
            if (configName == "noticeButtonAnchor") {
                return "dlc_manager.config.ui.noticeButtonAnchor."
            }
            if (configName == "noticePanelAnchor") {
                return "dlc_manager.config.ui.noticePanelAnchor."
            }
            if (configName == "titleUpdateAnchor") {
                return "dlc_manager.config.ui.titleUpdateAnchor."
            }
            return "dlc_manager.config.ui.titleAnchor."
        }

        private fun anchorDisplay(configName: String?, value: String?): String {
            val normalized = normalizeAnchorValue(value)
            val key = anchorTranslationPrefix(configName) + normalized
            val label = StringUtils.translate(key)
            return if (label == key) normalized else label
        }

        private fun confirmReset(button: ButtonBase, mouseButton: Int, delegate: IButtonActionListener) {
            val minecraft = Minecraft.getInstance()
            val returnScreen = minecraft.screen
            val title = Component.translatable("dlc_manager.config.reset_title")
            val message = Component.translatable("dlc_manager.config.reset_prompt")
            val yes = Component.translatable("dlc_manager.config.reset_yes")
            val no = Component.translatable("dlc_manager.config.reset_no")
            minecraft.setScreen(
                ConfirmScreen(
                    { accepted ->
                        minecraft.setScreen(returnScreen)
                        if (!accepted) {
                            return@ConfirmScreen
                        }
                        delegate.actionPerformedWithButton(button, mouseButton)
                        if (returnScreen is DlcConfigScreen) {
                            returnScreen.initGui()
                        }
                    },
                    title,
                    message,
                    yes,
                    no,
                )
            )
        }
    }
}
