package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.config.DlcServerConfigClient
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.core.DlcConfig
import com.google.gson.JsonObject
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

class DlcServerConfigMaLiLibScreen private constructor(
    private val parentScreen: Screen?,
    private val entry: DlcServerEntry,
    tab: Tab,
    private val session: ConfigSession,
) : GuiModConfigs(DlcConfig.MOD_ID, buildOptions(resolveTab(tab)), false, "dlc_manager.server.config_title") {
    private val currentTab: Tab = resolveTab(tab)
    private var maxLabelWidth = 220
    private var listWidget: WidgetListConfigOptionsBase<*, *>? = null
    private var selfUpdateCheckButton: ButtonGeneric? = null
    private var saveButton: ButtonGeneric? = null
    private var cancelButton: ButtonGeneric? = null

    @JvmOverloads
    constructor(parent: Screen?, entry: DlcServerEntry, tab: Tab = Tab.SERVER) : this(parent, entry, tab, ConfigSession())

    init {
        setParent(parentScreen)
        setTitle(StringUtils.translate("dlc_manager.server.config_title"))
    }

    override fun initGui() {
        if (!session.configLoaded) {
            loadFromServer()
            session.configLoaded = true
        }
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

        x = addTabButton(Tab.SERVER, x, y, height)
        x += gap
        x = addTabButton(Tab.UI, x, y, height)
        x += gap
        x = addTabButton(Tab.PATHS, x, y, height)
        x += gap
        x = addTabButton(Tab.MODPACK, x, y, height)
        x += gap
        addTabButton(Tab.DOWNLOADS, x, y, height)

        val buttonWidth = 80
        val buttonHeight = 20
        val buttonY = this.height - 28
        val centerX = this.width / 2
        val spacing = 10
        val checkWidth = maxOf(110, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.self_update.button")) + 12)
        val totalWidth = checkWidth + spacing + buttonWidth + spacing + buttonWidth
        val startX = centerX - totalWidth / 2
        val checkX = startX
        val saveX = startX + checkWidth + spacing
        val cancelX = saveX + buttonWidth + spacing
        selfUpdateCheckButton = ButtonGeneric(checkX, buttonY, checkWidth, buttonHeight, StringUtils.translate("dlc_manager.self_update.button"))
        saveButton = ButtonGeneric(saveX, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.config.save"))
        cancelButton = ButtonGeneric(cancelX, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.config.cancel"))
        addButton(selfUpdateCheckButton, SelfUpdateButtonListener())
        addButton(saveButton, SaveButtonListener())
        addButton(cancelButton, CancelButtonListener())
        updateSaveButtonState()
    }

    override fun tick() {
        super.tick()
        updateSaveButtonState()
    }

    override fun drawTitle(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawString(guiGraphics, StringUtils.translate("dlc_manager.server.config_title"), 10, 10, 0xFFFFFF)
    }

    override fun getBrowserHeight(): Int {
        return height - 90
    }

    override fun createListWidget(x: Int, y: Int): WidgetListConfigOptions {
        listWidget = DlcServerConfigListWidget(
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

    private fun loadFromServer() {
        try {
            val json: JsonObject? = DlcServerConnection.fetchServerConfig(entry, 5000)
            if (json != null) {
                DlcServerConfigClient.loadFromJson(json)
            }
        } catch (_: Exception) {
        }
    }

    private fun saveAndClose() {
        flushCurrentModifications()
        val json = DlcServerConfigClient.toJson()
        val minecraft = Minecraft.getInstance()
        val ok = try {
            DlcServerConnection.saveServerConfig(entry, 5000, json)
        } catch (_: Exception) {
            false
        }
        if (ok) {
            session.modified = false
            mc.setScreen(getParent())
            return
        }
        val title = Component.translatable("dlc_manager.server.config_save_failed")
        val message = Component.translatable("dlc_manager.server.config_save_failed")
        val okText = Component.translatable("gui.ok")
        val backText = Component.translatable("gui.back")
        minecraft.setScreen(ConfirmScreen({ minecraft.setScreen(this) }, title, message, okText, backText))
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
                    minecraft.setScreen(getParent())
                },
                title,
                message,
                yes,
                no,
            )
        )
    }

    private fun openTab(tab: Tab) {
        flushCurrentModifications()
        mc.setScreen(DlcServerConfigMaLiLibScreen(getParent(), entry, tab, session))
    }

    private fun flushCurrentModifications() {
        val widget = listWidget ?: return
        if (widget.wereConfigsModified()) {
            session.modified = true
        }
        widget.applyPendingModifications()
        widget.clearConfigsModifiedFlag()
    }

    private inner class TabButtonListener(private val target: Tab) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            if (target == currentTab) {
                return
            }
            openTab(target)
        }
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

    private inner class SelfUpdateButtonListener : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            Minecraft.getInstance().setScreen(DlcSelfUpdateScreen(this@DlcServerConfigMaLiLibScreen, null))
        }
    }

    private class ConfigSession {
        var modified = false
        var configLoaded = false
    }

    enum class Tab(val labelKey: String) {
        SERVER("dlc_manager.config.tab.server"),
        UI("dlc_manager.config.tab.ui"),
        PATHS("dlc_manager.config.tab.paths"),
        MODPACK("dlc_manager.config.tab.modpack"),
        DOWNLOADS("dlc_manager.config.tab.download"),
    }

    private class DlcServerConfigListWidget(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        configWidth: Int,
        zLevel: Float,
        useKeybindSearch: Boolean,
        parent: DlcServerConfigMaLiLibScreen,
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
            return DlcServerConfigOptionWidget(
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

    private class DlcServerConfigOptionWidget(
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
                        if (returnScreen is DlcServerConfigMaLiLibScreen) {
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

    companion object {
        @JvmStatic
        private fun resolveTab(tab: Tab?): Tab {
            return tab ?: Tab.SERVER
        }

        @JvmStatic
        private fun buildOptions(tab: Tab): List<ConfigOptionWrapper> {
            val configs: List<IConfigBase> = getConfigsForTab(tab)
            return ArrayList(ConfigOptionWrapper.createFor(configs))
        }

        @JvmStatic
        private fun getConfigsForTab(tab: Tab): List<IConfigBase> {
            return when (tab) {
                Tab.SERVER -> DlcServerConfigClient.getServerConfigs()
                Tab.UI -> DlcServerConfigClient.getUiConfigs()
                Tab.PATHS -> DlcServerConfigClient.getPathConfigs()
                Tab.MODPACK -> DlcServerConfigClient.getModpackConfigs()
                Tab.DOWNLOADS -> DlcServerConfigClient.getDownloadConfigs()
            }
        }
    }
}
