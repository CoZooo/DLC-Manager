package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerInfo
import com.cozooo.dlc_manager.client.server.DlcServerItem
import com.cozooo.dlc_manager.client.server.DlcServerList
import com.cozooo.dlc_manager.core.DlcType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.ArrayDeque
import java.util.EnumMap
import java.util.concurrent.CompletableFuture
import kotlin.math.max

open class DlcServerManagerScreen @JvmOverloads constructor(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
    info: DlcServerInfo?,
    startTab: Tab? = null,
    private val openedFromIngame: Boolean = false,
) : DlcPageScreen(parent, Component.translatable("dlc_manager.server.title")) {
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
    private var packManagerButton: Button? = null
    private var returnToGameButton: Button? = null
    @Volatile
    private var connecting = false

    private var lastMouseX = 0
    private var lastMouseY = 0

    private var activeTab: Tab = startTab ?: Tab.SERVERS

    private var draggingDlcScroll = false
    private var dlcScrollDragOffset = 0
    private var remoteMissingCount = 0
    private var requiredMissingCount = 0
    private var promptedRemoteDownload = false
    private var hasServerTaczMod = false
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
        hasServerTaczMod = info?.hasTaczMod == true
        if (!hasServerTaczMod && activeFilter == DlcType.TACZ_PACK) {
            activeFilter = null
        }
        rebuildDisplayEntries()
        applyPresetOverrides()
    }

    private fun initControls() {
        rebuildUi()
    }

    private fun rebuildUi() {
        clearWidgets()
        initServerTab()
    }

    private fun openDlcPage() {
        var rootParent = parent
        if (parent is DlcServerScreen) {
            val root = parent.getRootParent()
            if (root != null) {
                rootParent = root
            }
        }
        Minecraft.getInstance().setScreen(DlcServerScreen(rootParent, entry, buildInfoSnapshot(), openedFromIngame))
    }

    private fun buildInfoSnapshot(): DlcServerInfo {
        val info = DlcServerInfo()
        info.items = ArrayList(items)
        info.remoteMissing = remoteMissingCount
        info.requiredMissing = requiredMissingCount
        info.hasTaczMod = hasServerTaczMod
        info.baseVersion = serverBaseVersion
        info.dlcVersion = serverDlcVersion
        return info
    }

    private fun openPackManager() {
        entry?.let { Minecraft.getInstance().setScreen(DlcServerPackManagerScreen(this, it)) }
    }

    private fun initServerTab() {
        val buttonWidth = 120
        val buttonHeight = 20
        val centerX = width / 2
        val buttonGap = 8
        val buttonCount = 5
        val totalHeight = buttonCount * buttonHeight + (buttonCount - 1) * buttonGap
        val startY = max(panelTop() + 12, (panelTop() + panelBottom() - totalHeight) / 2 - 6)
        val push = Button.builder(Component.translatable("dlc_manager.server.push")) { openPush() }
            .bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
            .build()
        val rcon = Button.builder(Component.translatable("dlc_manager.server.rcon")) { openRcon() }
            .bounds(centerX - buttonWidth / 2, startY + buttonHeight + buttonGap, buttonWidth, buttonHeight)
            .build()
        val files = Button.builder(Component.translatable("dlc_manager.server.files")) { openFiles() }
            .bounds(centerX - buttonWidth / 2, startY + (buttonHeight + buttonGap) * 2, buttonWidth, buttonHeight)
            .build()
        val updateManager = Button.builder(Component.translatable("dlc_manager.update.manager")) {
            Minecraft.getInstance().setScreen(DlcServerUpdateManagerScreen(this, entry))
        }.bounds(centerX - buttonWidth / 2, startY + (buttonHeight + buttonGap) * 3, buttonWidth, buttonHeight).build()
        val packManager = Button.builder(Component.translatable("dlc_manager.pack.manager")) {
            openPackManager()
        }.bounds(centerX - buttonWidth / 2, startY + (buttonHeight + buttonGap) * 4, buttonWidth, buttonHeight).build()
        pushButton = push
        rconButton = rcon
        filesButton = files
        updateManagerButton = updateManager
        packManagerButton = packManager
        addRenderableWidget(push)
        addRenderableWidget(rcon)
        addRenderableWidget(files)
        addRenderableWidget(updateManager)
        addRenderableWidget(packManager)
        if (openedFromIngame) {
            val returnToGame = Button.builder(Component.translatable("menu.returnToGame")) { Minecraft.getInstance().setScreen(null) }
                .bounds(centerX - buttonWidth / 2, height - 22, buttonWidth, buttonHeight)
                .build()
            returnToGameButton = returnToGame
            addRenderableWidget(returnToGame)
        }
    }

    private fun rebuildDisplayEntries() {
        displayEntries.clear()
        val grouped: MutableMap<DlcType, MutableList<DlcServerItem>> = EnumMap(DlcType::class.java)
        for (type in DlcType.values()) {
            grouped[type] = ArrayList()
        }
        for (item in items) {
            val type = mapType(item.type)
            if (!isServerTypeVisible(type)) {
                continue
            }
            grouped[type]!!.add(item)
        }
        if (activeFilter == null) {
            for (type in DlcType.values()) {
                if (!isServerTypeVisible(type)) {
                    continue
                }
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

    private fun panelLeft(): Int = 16

    private fun panelRight(): Int = width - 16

    private fun panelTop(): Int = 28

    private fun panelBottom(): Int = height - 24

    private fun filterTabsY(): Int = panelTop() + 6

    private fun listTop(): Int = panelTop() + 6

    private fun listBottom(): Int = height - 54

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

    private fun isServerTypeVisible(type: DlcType): Boolean {
        return type != DlcType.TACZ_PACK || hasServerTaczMod
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
            if (identifier == null || dependencies.isEmpty()) {
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

    private fun fetchInfo(entry: DlcServerEntry?): DlcServerInfo? {
        return try {
            DlcServerConnection.fetchServerInfo(entry, 5000)
        } catch (_: Exception) {
            null
        }
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

    companion object {




        private fun normalizeServerIdentifier(identifier: String?): String {
            if (identifier == null) {
                return ""
            }
            return identifier.trim().lowercase()
        }
    }

    private var controlsReady = false
    private var pageRevision = 0
    override fun buildPage() {
        if (!controlsReady) { controlsReady = true; initControls() }
        pageRevision = 0

        val list = section(entry?.name.orEmpty(), null)
        val tools = listOf(pushButton to "push", rconButton to "rcon", filesButton to "files", updateManagerButton to "updates", packManagerButton to "packs")
        for ((model, key) in tools) {
            if (model == null) continue
            val line = row(list, model.message.string, tr("dlc_manager.ui.tool_" + key))
            mount(node(line, "div", "row-controls"), model)
        }
        returnToGameButton?.let { mount(footer, it) }

    }
    override fun updatePage() {

        if (pageRevision != 0) refreshPage()
    }

    override fun onClose() { if (shouldCloseOnEsc()) openDlcPage() }
}
