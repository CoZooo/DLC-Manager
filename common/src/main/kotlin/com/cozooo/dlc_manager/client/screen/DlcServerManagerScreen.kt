package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.server.DlcServerCache
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerInfo
import com.cozooo.dlc_manager.client.server.DlcServerItem
import com.cozooo.dlc_manager.client.server.DlcServerList
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcType
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.ArrayDeque
import java.util.EnumMap
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

open class DlcServerManagerScreen @JvmOverloads constructor(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
    info: DlcServerInfo?,
    startTab: Tab? = null,
    private val openedFromIngame: Boolean = false,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.server.title")) {
    enum class Tab {
        SERVERS,
    }

    private class DisplayEntry private constructor(
        val header: Boolean,
        val label: Component?,
        val item: DlcServerItem?,
    ) {
        companion object {
            fun header(label: Component): DisplayEntry = DisplayEntry(true, label, null)
            fun item(item: DlcServerItem): DisplayEntry = DisplayEntry(false, null, item)
        }
    }

    private val baseline: MutableMap<String?, Boolean> = HashMap()
    private var items: MutableList<DlcServerItem> = ArrayList()
    private val displayEntries: MutableList<DisplayEntry> = ArrayList()
    private var scrollIndex = 0
    private var applyButton: Button? = null
    private var backButton: Button? = null
    private var presetButton: Button? = null
    private var packButton: Button? = null
    private var enableAllButton: Button? = null
    private var disableAllButton: Button? = null
    @Volatile
    private var applying = false
    private var headerLine: String? = null
    private var activeFilter: DlcType? = null
    private var filterAllButton: Button? = null
    private val filterButtons: MutableMap<DlcType, Button> = EnumMap(DlcType::class.java)

    private var servers: MutableList<DlcServerEntry> = ArrayList()
    private var serverScrollIndex = 0
    private var serverSelectedIndex = -1
    private var pushButton: Button? = null
    private var rconButton: Button? = null
    private var filesButton: Button? = null
    private var updateManagerButton: Button? = null
    private var returnToGameButton: Button? = null
    @Volatile
    private var connecting = false

    private var lastMouseX = 0
    private var lastMouseY = 0

    private var activeTab: Tab = startTab ?: Tab.SERVERS
    private var tabDlc: Button? = null
    private var tabServers: Button? = null
    private var configButton: Button? = null

    private var draggingDlcScroll = false
    private var dlcScrollDragOffset = 0
    private var remoteMissingCount = 0
    private var requiredMissingCount = 0
    private var promptedRemoteDownload = false
    private var serverBaseVersion = ""
    private var serverDlcVersion = ""

    init {
        setInfo(info)
    }

    private fun setInfo(info: DlcServerInfo?) {
        items.clear()
        baseline.clear()
        items.addAll(info?.items ?: emptyList())
        for (item in items) {
            baseline[item.id] = item.enabled
        }
        val base = info?.baseVersion
        val dlc = info?.dlcVersion
        serverBaseVersion = base ?: ""
        serverDlcVersion = dlc ?: ""
        remoteMissingCount = info?.remoteMissing ?: 0
        requiredMissingCount = info?.requiredMissing ?: 0
        val baseBlank = base.isNullOrBlank()
        val dlcBlank = dlc.isNullOrBlank()
        headerLine = if (!baseBlank || !dlcBlank) {
            if (dlcBlank) base else if (baseBlank) dlc else "$base+$dlc"
        } else {
            null
        }
        rebuildDisplayEntries()
        applyPresetOverrides()
    }

    override fun init() {
        rebuildUi()
    }

    private fun promptRemoteDownloadIfMissing() {
        if (promptedRemoteDownload || remoteMissingCount <= 0) {
            return
        }
        promptedRemoteDownload = true
        val title = Component.translatable("dlc_manager.server.remote_missing_title", remoteMissingCount)
        val desc = Component.translatable("dlc_manager.server.remote_missing_desc")
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    if (accepted) {
                        startRemoteDownload()
                    }
                },
                title,
                desc,
                Component.translatable("dlc_manager.server.remote_missing_confirm"),
                Component.translatable("gui.cancel"),
            ),
        )
    }

    private fun rebuildUi() {
        clearWidgets()
        initTabs()
        initServerTab()
    }

    private fun initTabs() {
        val right = width - 2
        val y = 0
        val h = 14
        val gap = 6
        val configWidth = max(60, font.width(Component.translatable("dlc_manager.server.config")) + 12)
        val configX = right - configWidth
        val config = Button.builder(Component.translatable("dlc_manager.server.config")) { openConfig() }
            .bounds(configX, y, configWidth, h)
            .build()
        configButton = config
        val serverWidth = max(60, font.width(Component.translatable("dlc_manager.server.tab.servers")) + 12)
        val serverX = configX - gap - serverWidth
        val serversTab = Button.builder(Component.translatable("dlc_manager.server.tab.servers")) { openTab(Tab.SERVERS) }
            .bounds(serverX, y, serverWidth, h)
            .build()
        tabServers = serversTab
        val dlcWidth = max(48, font.width(Component.translatable("dlc_manager.server.tab.dlc")) + 12)
        val dlcX = serverX - gap - dlcWidth
        val dlcTab = Button.builder(Component.translatable("dlc_manager.server.tab.dlc")) { openDlcPage() }
            .bounds(dlcX, y, dlcWidth, h)
            .build()
        tabDlc = dlcTab
        addRenderableWidget(dlcTab)
        addRenderableWidget(serversTab)
        addRenderableWidget(config)
        updateTabStates()
    }

    private fun updateTabStates() {
        tabDlc?.active = true
        tabServers?.active = activeTab != Tab.SERVERS
    }

    private fun openTab(tab: Tab) {
        if (activeTab == tab) {
            return
        }
        activeTab = tab
        if (activeTab == Tab.SERVERS) {
            loadServers()
        }
        rebuildUi()
    }

    private fun openDlcPage() {
        var rootParent = parent
        if (parent is DlcServerMaLiLibScreen) {
            val root = parent.getRootParent()
            if (root != null) {
                rootParent = root
            }
        }
        Minecraft.getInstance().setScreen(DlcServerMaLiLibScreen(rootParent, entry, buildInfoSnapshot(), openedFromIngame))
    }

    private fun buildInfoSnapshot(): DlcServerInfo {
        val info = DlcServerInfo()
        info.items = ArrayList(items)
        info.remoteMissing = remoteMissingCount
        info.requiredMissing = requiredMissingCount
        info.baseVersion = serverBaseVersion
        info.dlcVersion = serverDlcVersion
        return info
    }

    private fun openConfig() {
        val target = entry ?: return
        Minecraft.getInstance().setScreen(DlcServerConfigMaLiLibScreen(this, target))
    }

    private fun openPreset() {
        Minecraft.getInstance().setScreen(object : DlcPresetManagerScreen(this@DlcServerManagerScreen) {
            override fun onClose() {
                applyPresetOverrides()
                super.onClose()
            }
        })
    }

    private fun openPackManager() {
        Minecraft.getInstance().setScreen(DlcPackManagerScreen(this))
    }

    private fun initDlcTab() {
        val gap = 8
        val panelLeft = panelLeft()
        val panelRight = panelRight()
        val panelWidth = panelRight - panelLeft
        val showPackButton = DlcConfig.isPackFuncOn() && !DlcConfig.isPButtomShown()
        val buttonCount = if (showPackButton) 6 else 5
        var buttonWidth = min(90, (panelWidth - 16 - gap * (buttonCount - 1)) / buttonCount)
        if (buttonWidth < 70) {
            buttonWidth = 70
        }
        val buttonHeight = 20
        val y = height - 22
        val rightX = panelRight - 8
        val leftX = panelLeft + 8
        val enableAll = Button.builder(Component.translatable("dlc_manager.enable_all")) { setAllEnabled(true) }
            .bounds(leftX, y, buttonWidth, buttonHeight)
            .build()
        val disableAll = Button.builder(Component.translatable("dlc_manager.disable_all")) { setAllEnabled(false) }
            .bounds(leftX + buttonWidth + gap, y, buttonWidth, buttonHeight)
            .build()
        val apply = Button.builder(Component.translatable("dlc_manager.apply")) { applyChanges() }
            .bounds(rightX - buttonWidth * 2 - gap, y, buttonWidth, buttonHeight)
            .build()
        val preset = Button.builder(Component.translatable("dlc_manager.preset.manager")) { openPreset() }
            .bounds(apply.x - gap - buttonWidth, y, buttonWidth, buttonHeight)
            .build()
        val pack = Button.builder(Component.translatable("dlc_manager.pack.manager")) { openPackManager() }
            .bounds(preset.x - gap - buttonWidth, y, buttonWidth, buttonHeight)
            .build()
        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(rightX - buttonWidth, y, buttonWidth, buttonHeight)
            .build()
        enableAllButton = enableAll
        disableAllButton = disableAll
        applyButton = apply
        presetButton = preset
        packButton = pack
        backButton = back
        addRenderableWidget(enableAll)
        addRenderableWidget(disableAll)
        if (showPackButton) {
            addRenderableWidget(pack)
        }
        addRenderableWidget(preset)
        addRenderableWidget(apply)
        addRenderableWidget(back)
        initDlcFilters()
        updateApplyState()
    }

    private fun initServerTab() {
        val buttonWidth = 120
        val buttonHeight = 20
        val centerX = width / 2
        val startY = height / 2 - buttonHeight - 8
        val push = Button.builder(Component.translatable("dlc_manager.server.push")) { openPush() }
            .bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
            .build()
        val rcon = Button.builder(Component.translatable("dlc_manager.server.rcon")) { openRcon() }
            .bounds(centerX - buttonWidth / 2, startY + buttonHeight + 8, buttonWidth, buttonHeight)
            .build()
        val files = Button.builder(Component.translatable("dlc_manager.server.files")) { openFiles() }
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + 8) * 2, buttonWidth, buttonHeight)
            .build()
        val updateManager = Button.builder(Component.translatable("dlc_manager.update.manager")) {
            Minecraft.getInstance().setScreen(DlcServerUpdateManagerScreen(this, entry))
        }.bounds(centerX - buttonWidth / 2, startY + (buttonHeight + 8) * 3, buttonWidth, buttonHeight).build()
        pushButton = push
        rconButton = rcon
        filesButton = files
        updateManagerButton = updateManager
        addRenderableWidget(push)
        addRenderableWidget(rcon)
        addRenderableWidget(files)
        addRenderableWidget(updateManager)
        if (openedFromIngame) {
            val returnToGame = Button.builder(Component.translatable("menu.returnToGame")) { Minecraft.getInstance().setScreen(null) }
                .bounds(centerX - buttonWidth / 2, height - 22, buttonWidth, buttonHeight)
                .build()
            returnToGameButton = returnToGame
            addRenderableWidget(returnToGame)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        lastMouseX = mouseX
        lastMouseY = mouseY
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        drawPanel(guiGraphics)
        guiGraphics.drawString(font, title, 8, 6, 0xFFFFFF, false)
        if (headerLine != null) {
            guiGraphics.drawString(font, headerLine, 8, 16, 0xC8C8C8, false)
        }
        renderServerTab(guiGraphics)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun drawPanel(guiGraphics: GuiGraphics) {
        val panelLeft = panelLeft()
        val panelRight = panelRight()
        val panelTop = panelTop()
        val panelBottom = panelBottom()
        val panelColor = 0xCC0F1114.toInt()
        val panelBorder = 0xFF2A2F36.toInt()
        val accent = 0xFF4C84FF.toInt()
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, panelColor)
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, panelBorder)
        guiGraphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, panelBorder)
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, panelBottom, panelBorder)
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, panelBorder)
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 120, panelTop + 2, accent)
    }

    private fun renderDlcTab(guiGraphics: GuiGraphics) {
        val listTop = listTop()
        val listBottom = listBottom()
        val maxVisible = max(1, (listBottom - listTop) / ROW_HEIGHT)
        val totalLines = displayEntries.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        var rowY = listTop
        var hovered: DisplayEntry? = null
        for (i in displayEntries.indices) {
            val entry = displayEntries[i]
            if (i < scrollIndex || i >= scrollIndex + maxVisible) {
                continue
            }
            val labelY = rowY + 6
            if (entry.header) {
                guiGraphics.drawString(font, entry.label ?: Component.empty(), 36, labelY, 0x8CB3FF, false)
            } else if (entry.item != null) {
                val item = entry.item
                guiGraphics.drawString(font, DlcTextFormatter.parse(resolveDisplayName(item)), 48, labelY, 0xE6E6E6, false)
                drawToggle(guiGraphics, rowY, item.enabled)
            }
            if (!entry.header && entry.item != null && isRowHovered(rowY)) {
                hovered = entry
            }
            rowY += ROW_HEIGHT
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll, scrollIndex)
        if (applying) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.server.applying"), width / 2, listBottom + 4, 0xC8C8C8)
        }
        if (hovered != null) {
            renderItemTooltip(guiGraphics, hovered.item)
        }
    }

    private fun renderServerTab(guiGraphics: GuiGraphics) {
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return false
    }

    private fun rebuildDisplayEntries() {
        displayEntries.clear()
        val grouped: MutableMap<DlcType, MutableList<DlcServerItem>> = EnumMap(DlcType::class.java)
        for (type in DlcType.values()) {
            grouped[type] = ArrayList()
        }
        for (item in items) {
            grouped[mapType(item.type)]!!.add(item)
        }
        if (activeFilter == null) {
            for (type in DlcType.values()) {
                val group = grouped[type]
                if (group.isNullOrEmpty()) {
                    continue
                }
                displayEntries.add(DisplayEntry.header(Component.translatable(type.getDisplayKey())))
                for (item in group) {
                    displayEntries.add(DisplayEntry.item(item))
                }
            }
        } else {
            val group = grouped[activeFilter]
            if (group != null) {
                for (item in group) {
                    displayEntries.add(DisplayEntry.item(item))
                }
            }
        }
    }

    private fun mapType(raw: String?): DlcType {
        if (raw == null) {
            return DlcType.UNKNOWN
        }
        val value = raw.trim().lowercase()
        if (value == "mod" || value == "mods") {
            return DlcType.MOD
        }
        if (value == "resourcepack" || value == "resourcepacks" || value == "resource_pack") {
            return DlcType.RESOURCE_PACK
        }
        if (value == "datapack" || value == "datapacks" || value == "data_pack") {
            return DlcType.DATA_PACK
        }
        if (value == "tacz" || value == "tacz_pack" || value == "tacz_packs" || value == "taczpack") {
            return DlcType.TACZ_PACK
        }
        return DlcType.UNKNOWN
    }

    private fun initDlcFilters() {
        filterButtons.clear()
        val panelLeft = panelLeft()
        val panelRight = panelRight()
        val available = panelRight - panelLeft - 20
        val count = DlcType.values().size + 1
        val gap = max(4, min(FILTER_TAB_GAP, 8))
        var tabWidth = (available - gap * (count - 1)) / count
        if (tabWidth < 48) {
            tabWidth = 48
        }
        var totalWidth = tabWidth * count + gap * (count - 1)
        if (totalWidth > available) {
            tabWidth = max(32, (available - gap * (count - 1)) / count)
            totalWidth = tabWidth * count + gap * (count - 1)
        }
        var startX = panelLeft + (available - totalWidth) / 2 + 10
        if (startX < panelLeft + 6) {
            startX = panelLeft + 6
        }
        val y = filterTabsY()
        val filterAll = Button.builder(Component.translatable("dlc_manager.filter_all")) { setFilter(null) }
            .bounds(startX, y, tabWidth, FILTER_TAB_HEIGHT)
            .build()
        filterAllButton = filterAll
        addRenderableWidget(filterAll)
        var index = 1
        for (type in DlcType.values()) {
            val button = Button.builder(Component.translatable(type.getDisplayKey())) { setFilter(type) }
                .bounds(startX + index * (tabWidth + gap), y, tabWidth, FILTER_TAB_HEIGHT)
                .build()
            filterButtons[type] = button
            addRenderableWidget(button)
            index++
        }
        updateFilterButtons()
    }

    private fun setFilter(type: DlcType?) {
        if (activeFilter == type) {
            return
        }
        activeFilter = type
        scrollIndex = 0
        rebuildDisplayEntries()
        updateFilterButtons()
    }

    private fun updateFilterButtons() {
        filterAllButton?.active = activeFilter != null
        for ((type, button) in filterButtons) {
            button.active = activeFilter != type
        }
    }

    private fun panelLeft(): Int = 16

    private fun panelRight(): Int = width - 16

    private fun panelTop(): Int = 28

    private fun panelBottom(): Int = height - 24

    private fun filterTabsY(): Int = panelTop() + 6

    private fun listTop(): Int = panelTop() + 6

    private fun listBottom(): Int = height - 54

    private fun drawToggle(guiGraphics: GuiGraphics, rowY: Int, enabled: Boolean) {
        val toggleWidth = 54
        val toggleHeight = 16
        val toggleX = panelRight() - 12 - toggleWidth
        val toggleY = rowY + (ROW_HEIGHT - toggleHeight) / 2
        val bg = if (enabled) 0xFF2E6BD9.toInt() else 0xFF3A3A3A.toInt()
        val border = 0xFF5A5A5A.toInt()
        guiGraphics.fill(toggleX, toggleY, toggleX + toggleWidth, toggleY + toggleHeight, bg)
        guiGraphics.fill(toggleX, toggleY, toggleX + toggleWidth, toggleY + 1, border)
        guiGraphics.fill(toggleX, toggleY + toggleHeight - 1, toggleX + toggleWidth, toggleY + toggleHeight, border)
        guiGraphics.fill(toggleX, toggleY, toggleX + 1, toggleY + toggleHeight, border)
        guiGraphics.fill(toggleX + toggleWidth - 1, toggleY, toggleX + toggleWidth, toggleY + toggleHeight, border)
        val label = if (enabled) {
            Component.translatable("dlc_manager.server.toggle_on").string
        } else {
            Component.translatable("dlc_manager.server.toggle_off").string
        }
        val textX = toggleX + (toggleWidth - font.width(label)) / 2
        val textY = toggleY + 4
        guiGraphics.drawString(font, label, textX, textY, 0xFFFFFF, false)
    }

    private fun isToggleClick(mouseX: Double, mouseY: Double, rowY: Int): Boolean {
        val toggleWidth = 54
        val toggleHeight = 16
        val toggleX = panelRight() - 12 - toggleWidth
        val toggleY = rowY + (ROW_HEIGHT - toggleHeight) / 2
        return mouseX >= toggleX && mouseX <= toggleX + toggleWidth &&
            mouseY >= toggleY && mouseY <= toggleY + toggleHeight
    }

    private fun rowYForIndex(index: Int, listTop: Int): Int {
        return listTop + (index - scrollIndex) * ROW_HEIGHT
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        rebuildUi()
    }

    private fun updateApplyState() {
        val apply = applyButton ?: return
        var dirty = false
        for (item in items) {
            var baselineValue = baseline[item.id]
            if (baselineValue == null) {
                baselineValue = false
            }
            if (baselineValue != item.enabled) {
                dirty = true
                break
            }
        }
        apply.active = dirty && !applying
        if (backButton != null) {
            backButton!!.active = !dirty && !applying && requiredMissingCount <= 0
        }
    }

    private fun setAllEnabled(enabled: Boolean) {
        for (item in items) {
            if (!matchesActiveFilter(item)) {
                continue
            }
            item.enabled = enabled
        }
        updateApplyState()
    }

    private fun matchesActiveFilter(item: DlcServerItem): Boolean {
        if (activeFilter == null) {
            return true
        }
        return mapType(item.type) == activeFilter
    }

    private fun applyPresetOverrides() {
        DlcPresetRuntimeState.reload()
        val entries = DlcPresetRuntimeState.getPresets()
        val enabledPresets = DlcPresetRuntimeState.getEnabledPresetIds()
        if (enabledPresets.isEmpty() || entries.isEmpty()) {
            return
        }
        val byId: MutableMap<String, DlcPresetEntry> = HashMap()
        for (entry in entries) {
            val id = entry.getId()
            if (id != null) {
                byId[id] = entry
            }
        }
        val enableIds: MutableSet<String> = HashSet()
        val conflictIds: MutableSet<String> = LinkedHashSet()
        for (id in enabledPresets) {
            val entry = byId[id] ?: continue
            for (enable in entry.getEnable()) {
                val normalized = normalizeServerIdentifier(enable)
                if (normalized.isNotBlank()) {
                    enableIds.add(normalized)
                }
            }
            for (conflict in entry.getConflicts()) {
                val normalized = normalizeServerIdentifier(conflict)
                if (normalized.isNotBlank()) {
                    conflictIds.add(normalized)
                }
            }
        }
        val expandedConflictIds = expandConflictIdentifiers(conflictIds)
        for (item in items) {
            val identifier = item.identifier ?: continue
            val key = normalizeServerIdentifier(identifier)
            if (enableIds.contains(key)) {
                item.enabled = true
            }
            if (expandedConflictIds.contains(key)) {
                item.enabled = false
            }
        }
        rebuildDisplayEntries()
        updateApplyState()
    }

    private fun expandConflictIdentifiers(conflictIds: Set<String>?): Set<String> {
        val expanded: MutableSet<String> = LinkedHashSet()
        if (conflictIds.isNullOrEmpty()) {
            return expanded
        }
        val queue: ArrayDeque<String> = ArrayDeque()
        for (id in conflictIds) {
            val normalized = normalizeServerIdentifier(id)
            if (normalized.isBlank()) {
                continue
            }
            if (expanded.add(normalized)) {
                queue.add(normalized)
            }
        }
        while (!queue.isEmpty()) {
            val current = queue.poll()
            for (dependent in collectDependentIdentifiers(current)) {
                if (dependent.isNotBlank() && expanded.add(dependent)) {
                    queue.add(dependent)
                }
            }
        }
        return expanded
    }

    private fun collectDependentIdentifiers(targetIdentifier: String?): Set<String> {
        val dependents: MutableSet<String> = LinkedHashSet()
        if (targetIdentifier.isNullOrBlank()) {
            return dependents
        }
        for (item in items) {
            val identifier = item.identifier
            val dependencies = item.dependencies
            if (identifier == null || dependencies == null || dependencies.isEmpty()) {
                continue
            }
            for (dep in dependencies) {
                if (normalizeServerIdentifier(dep) == targetIdentifier) {
                    val normalized = normalizeServerIdentifier(identifier)
                    if (normalized.isNotBlank()) {
                        dependents.add(normalized)
                    }
                    break
                }
            }
        }
        return dependents
    }

    private fun applyChanges() {
        if (applying) {
            return
        }
        applying = true
        updateApplyState()
        val changed: MutableList<DlcServerItem> = ArrayList()
        for (item in items) {
            var baselineValue = baseline[item.id]
            if (baselineValue == null) {
                baselineValue = false
            }
            if (baselineValue != item.enabled) {
                val copy = DlcServerItem()
                copy.id = item.id
                copy.enabled = item.enabled
                changed.add(copy)
            }
        }
        CompletableFuture
            .supplyAsync { applyRemoteChanges(changed) }
            .whenComplete { response, _ ->
                Minecraft.getInstance().execute {
                    applying = false
                    if (response != null && response.ok) {
                        for (item in items) {
                            baseline[item.id] = item.enabled
                        }
                    } else {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.apply_failed"),
                            Component.empty(),
                        )
                    }
                    updateApplyState()
                    if (response != null && response.ok && response.requiresRestart) {
                        promptServerRestart()
                    }
                }
            }
    }

    private fun applyRemoteChanges(changes: List<DlcServerItem>): DlcServerConnection.ApplyResponse? {
        return try {
            DlcServerConnection.applyChangesWithResponse(entry, 8000, changes)
        } catch (_: Exception) {
            null
        }
    }

    private fun promptServerRestart() {
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { result ->
                    if (result) {
                        CompletableFuture.runAsync {
                            try {
                                DlcServerConnection.restartServer(entry, 8000)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    Minecraft.getInstance().setScreen(this)
                },
                Component.translatable("dlc_manager.server.restart_title"),
                Component.translatable("dlc_manager.server.restart_prompt"),
            ),
        )
    }

    private fun startRemoteDownload() {
        Minecraft.getInstance().setScreen(DlcServerRemoteDownloadScreen(this, entry))
    }

    fun refreshServerInfo() {
        CompletableFuture
            .supplyAsync { fetchInfo(entry) }
            .whenComplete { info, _ ->
                Minecraft.getInstance().execute {
                    if (info != null) {
                        setInfo(info)
                        rebuildUi()
                    }
                }
            }
    }

    private fun loadServers() {
        DlcServerList.load()
        servers = ArrayList(DlcServerList.getEntries())
        val lastKey = DlcServerList.getLastSelectedKey()
        serverSelectedIndex = -1
        if (lastKey != null) {
            for (i in servers.indices) {
                val item = servers[i]
                if (lastKey == item.getKey()) {
                    serverSelectedIndex = i
                    break
                }
            }
        }
    }

    private fun openAdd() {
        Minecraft.getInstance().setScreen(
            DlcServerEditScreen(this, null) { entry ->
                servers.add(entry)
                DlcServerList.setEntries(servers)
                DlcServerList.save()
            },
        )
    }

    private fun connectSelected() {
        if (connecting) {
            return
        }
        val target = entry ?: return
        connecting = true
        CompletableFuture
            .supplyAsync { fetchInfo(target) }
            .whenComplete { info, _ ->
                Minecraft.getInstance().execute {
                    connecting = false
                    if (info == null) {
                        DlcScreenCompat.addOrUpdatePeriodicNotification(
                            Minecraft.getInstance().toasts,
                            Component.translatable("dlc_manager.server.connect_failed"),
                            Component.empty(),
                        )
                        return@execute
                    }
                    DlcServerCache.put(target.getKey(), info)
                    Minecraft.getInstance().setScreen(DlcServerManagerScreen(this, target, info, null, openedFromIngame))
                }
            }
    }

    private fun fetchInfo(entry: DlcServerEntry?): DlcServerInfo? {
        return try {
            DlcServerConnection.fetchServerInfo(entry, 5000)
        } catch (_: Exception) {
            null
        }
    }

    private fun getSelectedEntry(): DlcServerEntry? {
        if (serverSelectedIndex >= 0 && serverSelectedIndex < servers.size) {
            return servers[serverSelectedIndex]
        }
        return null
    }

    private fun openPush() {
        Minecraft.getInstance().setScreen(DlcServerPushScreen(this, entry))
    }

    private fun openRcon() {
        Minecraft.getInstance().setScreen(DlcServerRconScreen(this, entry))
    }

    private fun openFiles() {
        Minecraft.getInstance().setScreen(DlcServerFileManagerScreen(this, entry))
    }

    private fun resolveDisplayName(item: DlcServerItem?): String {
        if (item == null) {
            return ""
        }
        val displayNames = item.displayNames
        if (displayNames == null || displayNames.isEmpty()) {
            return item.name ?: ""
        }
        val lang = CrossPlatformRuntime.languageCode()
        if (displayNames.containsKey(lang)) {
            return displayNames[lang] ?: ""
        }
        if (displayNames.containsKey("en_us")) {
            return displayNames["en_us"] ?: ""
        }
        return displayNames.values.stream().findFirst().orElse(item.name ?: "")
    }

    private fun renderItemTooltip(guiGraphics: GuiGraphics, item: DlcServerItem?) {
        if (item == null || item.tips == null || item.tips!!.isEmpty()) {
            return
        }
        val lang = CrossPlatformRuntime.languageCode()
        var tip = item.tips!![lang]
        if (tip.isNullOrBlank()) {
            tip = item.tips!!.getOrDefault("en_us", "")
        }
        if (tip.isNullOrBlank()) {
            return
        }
        guiGraphics.renderTooltip(font, DlcTextFormatter.splitMultiline(font, tip, 240), 0, 0)
    }

    private fun isRowHovered(rowY: Int): Boolean {
        return lastMouseY >= rowY && lastMouseY <= rowY + ROW_HEIGHT
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int, index: Int) {
        if (totalLines <= maxVisible) {
            return
        }
        val barX = width - 20
        val barWidth = 6
        val barHeight = max(20, (bottom - top) * maxVisible / max(1, totalLines))
        val trackHeight = bottom - top
        val barY = top + (trackHeight - barHeight) * index / max(1, maxScroll)
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF3B3B3B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B.toInt())
    }

    private fun startDlcScrollDrag(mouseX: Double, mouseY: Double, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int): Boolean {
        if (totalLines <= maxVisible) {
            return false
        }
        val barX = width - 20
        val barWidth = 6
        val trackHeight = bottom - top
        val barHeight = max(20, trackHeight * maxVisible / max(1, totalLines))
        val barY = top + (trackHeight - barHeight) * scrollIndex / max(1, maxScroll)
        if (mouseX >= barX && mouseX <= barX + barWidth && mouseY >= barY && mouseY <= barY + barHeight) {
            draggingDlcScroll = true
            dlcScrollDragOffset = (mouseY - barY).toInt()
            return true
        }
        return false
    }

    private fun updateDlcScrollFromDrag(mouseY: Double, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int) {
        if (totalLines <= maxVisible || maxScroll <= 0) {
            scrollIndex = 0
            return
        }
        val trackHeight = bottom - top
        val barHeight = max(20, trackHeight * maxVisible / max(1, totalLines))
        val minY = top
        val maxY = bottom - barHeight
        val barY = max(minY.toDouble(), min(maxY.toDouble(), mouseY - dlcScrollDragOffset)).toInt()
        val range = max(1, maxY - minY)
        val ratio = (barY - minY) / range.toDouble()
        scrollIndex = max(0, min(maxScroll, (ratio * maxScroll).roundToInt()))
    }

    companion object {
        private const val ROW_HEIGHT = 22
        private const val FILTER_TAB_HEIGHT = 18
        private const val FILTER_TAB_GAP = 6

        private fun normalizeServerIdentifier(identifier: String?): String {
            if (identifier == null) {
                return ""
            }
            return identifier.trim().lowercase()
        }
    }
}
