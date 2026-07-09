package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.preset.DlcPresetStatus
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerInfo
import com.cozooo.dlc_manager.client.server.DlcServerItem
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcType
import fi.dy.masa.malilib.gui.GuiListBase
import fi.dy.masa.malilib.gui.button.ButtonBase
import fi.dy.masa.malilib.gui.button.ButtonGeneric
import fi.dy.masa.malilib.gui.button.IButtonActionListener
import fi.dy.masa.malilib.util.StringUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import java.util.ArrayDeque
import java.util.Collections
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

class DlcServerMaLiLibScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
    info: DlcServerInfo?,
    private val openedFromIngame: Boolean,
) : GuiListBase<DlcServerListRow, DlcServerListEntry, DlcServerListWidget>(10, 34), DlcReturnPositionHost {
    constructor(parent: Screen?, entry: DlcServerEntry?, info: DlcServerInfo?) : this(
        parent,
        entry,
        info,
        parent is PauseScreen,
    )

    private val rows: MutableList<DlcServerListRow> = ArrayList()
    private val items: MutableList<DlcServerItem> = ArrayList()
    private val itemsByIdentifier: MutableMap<String, DlcServerItem> = LinkedHashMap()
    private val baseline: MutableMap<String?, Boolean> = HashMap()
    private var serverBaseVersion = ""
    private var serverDlcVersion = ""
    private val presetSnapshot: MutableSet<String> = LinkedHashSet()
    private var presetSnapshotCaptured = false
    private val presetAppliedIdentifiers: MutableSet<String> = LinkedHashSet()
    private val presetStatusById: MutableMap<String, DlcPresetStatus> = LinkedHashMap()
    private var savedScrollOffset = 0
    private var savedScrollFilter: DlcType? = null
    private var restoreScrollPending = false

    private var applyButton: ButtonGeneric? = null
    private var backButton: ButtonGeneric? = null
    private var presetButton: ButtonGeneric? = null
    private var enableAllButton: ButtonGeneric? = null
    private var disableAllButton: ButtonGeneric? = null
    private var configButton: ButtonGeneric? = null
    private var serverToolsButton: ButtonGeneric? = null
    private var dlcTabButton: ButtonGeneric? = null
    private var requiredButton: ButtonGeneric? = null
    private var missingButton: ButtonGeneric? = null

    private var allFilterButton: ButtonGeneric? = null
    private var modsFilterButton: ButtonGeneric? = null
    private var taczFilterButton: ButtonGeneric? = null
    private var resourceFilterButton: ButtonGeneric? = null
    private var dataFilterButton: ButtonGeneric? = null
    private var otherFilterButton: ButtonGeneric? = null
    private val filterButtons: MutableMap<DlcType, ButtonGeneric> = EnumMap(DlcType::class.java)
    private var activeFilter: DlcType? = null
    private var applying = false
    private var remoteMissingCount = 0
    private var requiredMissingCount = 0
    private var hasRequiredEntries = false
    private var promptedRemoteDownload = false
    private var hasServerTaczMod = false

    init {
        setTitle("")
        setParent(parent)
        setListPosition(18, 34)
        setInfo(info)
    }

    override fun createListWidget(x: Int, y: Int): DlcServerListWidget {
        return object : DlcServerListWidget(x, y, browserWidth, browserHeight, rows, this) {
            override fun createListEntryWidget(
                x: Int,
                y: Int,
                listIndex: Int,
                isOdd: Boolean,
                entry: DlcServerListRow,
            ): DlcServerListEntry {
                return DlcServerListEntry(
                    x,
                    y,
                    browserEntryWidth,
                    getBrowserEntryHeightFor(entry),
                    isOdd,
                    entry,
                    listIndex,
                    this@DlcServerMaLiLibScreen,
                )
            }
        }
    }

    override fun getBrowserWidth(): Int = width - 36

    override fun getBrowserHeight(): Int {
        val listTop = getListTop()
        val listBottom = getFooterTop()
        return max(80, listBottom - listTop)
    }

    override fun initGui() {
        promptRemoteDownloadIfMissing()
        rebuildRows()
        capturePresetSnapshot()
        reCreateListWidget()
        super.initGui()
        addHeaderButtons()
        addFilterButtons()
        addFooterButtons()
        updateListLayout()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcTooltipContext.clear()
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderTitle(guiGraphics)
        DlcTooltipContext.render(guiGraphics, Minecraft.getInstance().font)
    }

    private fun setInfo(info: DlcServerInfo?) {
        items.clear()
        itemsByIdentifier.clear()
        baseline.clear()
        remoteMissingCount = info?.remoteMissing ?: 0
        requiredMissingCount = info?.requiredMissing ?: 0
        hasRequiredEntries = info?.hasRequired == true
        hasServerTaczMod = info?.hasTaczMod == true
        if (!hasServerTaczMod && activeFilter == DlcType.TACZ_PACK) {
            activeFilter = null
        }
        serverBaseVersion = info?.baseVersion ?: ""
        serverDlcVersion = info?.dlcVersion ?: ""
        for (item in info?.items ?: emptyList()) {
            items.add(item)
            baseline[item.id] = item.enabled
            val identifier = item.identifier
            if (!identifier.isNullOrBlank()) {
                val key = normalizeIdentifier(identifier)
                if (key.isNotBlank()) {
                    itemsByIdentifier[key] = item
                }
            }
        }
        applyPresetOverrides()
    }

    private fun rebuildRows() {
        rows.clear()
        val grouped: MutableMap<DlcType, MutableList<DlcServerItem>> = EnumMap(DlcType::class.java)
        for (item in items) {
            val type = mapType(item.type) ?: DlcType.UNKNOWN
            if (!isServerTypeVisible(type)) {
                continue
            }
            grouped.computeIfAbsent(type) { ArrayList() }.add(item)
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
                rows.add(DlcServerListRow.groupHeader(type))
                addGroupedRows(group)
            }
        } else {
            val group = grouped[activeFilter]
            if (!group.isNullOrEmpty()) {
                addGroupedRows(group)
            }
        }
    }

    private fun addGroupedRows(group: List<DlcServerItem>) {
        val byPath = groupByRelativeFolder(group)
        val hasSubDir = byPath.keys.any { key -> key != "__root__" }
        var firstPathHeader = true
        for ((path, pathItems) in byPath) {
            if (path == "__root__") {
                if (hasSubDir) {
                    rows.add(DlcServerListRow.pathHeader(resolvePathLabel(path), pathItems, true, firstPathHeader))
                    firstPathHeader = false
                }
            } else {
                rows.add(DlcServerListRow.pathHeader(resolvePathLabel(path), pathItems, false, firstPathHeader))
                firstPathHeader = false
            }
            for (item in pathItems) {
                rows.add(DlcServerListRow.item(item))
            }
        }
    }

    private fun addHeaderButtons() {
        val right = width - 2
        val y = 0
        val h = 14
        val gap = 6

        val configLabel = StringUtils.translate("dlc_manager.server.config")
        val configWidth = max(60, StringUtils.getStringWidth(configLabel) + 12)
        val configX = right - configWidth
        configButton = ButtonGeneric(configX, y, configWidth, h, configLabel)
        addButton(configButton, SimpleButtonListener { openConfig() })

        val serverLabel = StringUtils.translate("dlc_manager.server.tab.servers")
        val serverWidth = max(60, StringUtils.getStringWidth(serverLabel) + 12)
        val serverX = configX - gap - serverWidth
        serverToolsButton = ButtonGeneric(serverX, y, serverWidth, h, serverLabel)
        addButton(serverToolsButton, SimpleButtonListener { openServerTools() })

        val dlcLabel = StringUtils.translate("dlc_manager.server.tab.dlc")
        val dlcWidth = max(48, StringUtils.getStringWidth(dlcLabel) + 12)
        val dlcX = serverX - gap - dlcWidth
        dlcTabButton = ButtonGeneric(dlcX, y, dlcWidth, h, dlcLabel)
        dlcTabButton?.setEnabled(false)
        addButton(dlcTabButton, SimpleButtonListener {})

        val requiredLabel = StringUtils.translate("dlc_manager.required.manage_title")
        val requiredWidth = max(60, StringUtils.getStringWidth(requiredLabel) + 12)
        requiredButton = ButtonGeneric(8, 2, requiredWidth, h, requiredLabel)
        addButton(requiredButton, SimpleButtonListener { openRequiredManager() })
        if (!shouldShowRequiredButton()) {
            requiredButton?.setEnabled(false)
            requiredButton?.setX(-9999)
            requiredButton?.setY(-9999)
        }
        if (remoteMissingCount > 0) {
            val missingLabel = StringUtils.translate("dlc_manager.server.remote_missing_button")
            val missingWidth = max(60, StringUtils.getStringWidth(missingLabel) + 12)
            missingButton = ButtonGeneric(8, 2, missingWidth, h, missingLabel)
            addButton(missingButton, SimpleButtonListener { startRemoteDownload() })
        } else {
            missingButton = null
        }
    }

    private fun addFilterButtons() {
        filterButtons.clear()
        modsFilterButton = null
        taczFilterButton = null
        resourceFilterButton = null
        dataFilterButton = null
        otherFilterButton = null
        val panelLeft = 20
        val panelRight = width - 20
        val y = 19
        val h = 18
        var gap = if (width < 420) 4 else 6
        val visibleTypes = visibleFilterTypes()
        val count = visibleTypes.size + 1
        val available = panelRight - panelLeft
        var totalGap = gap * (count - 1)
        var w = max(40, (available - totalGap) / count)
        if (w * count + totalGap > available) {
            gap = 2
            totalGap = gap * (count - 1)
            w = max(32, (available - totalGap) / count)
        }
        var x = panelLeft
        allFilterButton = ButtonGeneric(x, y, w, h, getFilterLabel(null))
        addButton(allFilterButton, FilterButtonListener(null))
        x += w + gap
        for (type in visibleTypes) {
            val button = ButtonGeneric(x, y, w, h, getFilterLabel(type))
            addButton(button, FilterButtonListener(type))
            filterButtons[type] = button
            when (type) {
                DlcType.MOD -> modsFilterButton = button
                DlcType.TACZ_PACK -> taczFilterButton = button
                DlcType.RESOURCE_PACK -> resourceFilterButton = button
                DlcType.DATA_PACK -> dataFilterButton = button
                DlcType.UNKNOWN -> otherFilterButton = button
                DlcType.DLC_PACK -> {}
            }
            x += w + gap
        }
        updateFilterButtons()
    }

    private fun addFooterButtons() {
        val gap = 4
        val rightGap = 2
        val totalButtons = 5
        val buttonWidth = min(80, (width - 16 - gap * (totalButtons - 1)) / totalButtons)
        val buttonHeight = 20
        val buttonY = height - 28
        val leftX = 6
        val rightX = width - 6
        enableAllButton = ButtonGeneric(leftX, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.enable_all"))
        disableAllButton = ButtonGeneric(leftX + buttonWidth + gap, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.disable_all"))
        addButton(enableAllButton, SimpleButtonListener { setAllEnabled(true) })
        addButton(disableAllButton, SimpleButtonListener { setAllEnabled(false) })
        backButton = ButtonGeneric(rightX - buttonWidth, buttonY, buttonWidth, buttonHeight, StringUtils.translate("gui.back"))
        applyButton = ButtonGeneric(rightX - buttonWidth * 2 - rightGap, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.apply"))
        presetButton = ButtonGeneric(applyButton!!.getX() - rightGap - buttonWidth, buttonY, buttonWidth, buttonHeight, StringUtils.translate("dlc_manager.preset.manager"))
        addButton(presetButton, SimpleButtonListener { openPreset() })
        addButton(applyButton, SimpleButtonListener { applyChanges() })
        addButton(backButton, SimpleButtonListener { openServerList() })
        updateApplyState()
    }

    private fun updateFilterButtons() {
        allFilterButton?.setDisplayString(getFilterLabel(null))
        allFilterButton?.setEnabled(activeFilter != null)
        modsFilterButton?.setDisplayString(getFilterLabel(DlcType.MOD))
        taczFilterButton?.setDisplayString(getFilterLabel(DlcType.TACZ_PACK))
        resourceFilterButton?.setDisplayString(getFilterLabel(DlcType.RESOURCE_PACK))
        dataFilterButton?.setDisplayString(getFilterLabel(DlcType.DATA_PACK))
        otherFilterButton?.setDisplayString(getFilterLabel(DlcType.UNKNOWN))
        for ((type, button) in filterButtons) {
            button.setEnabled(activeFilter != type)
        }
    }

    private fun visibleFilterTypes(): List<DlcType> {
        return DlcType.values().filter { it != DlcType.DLC_PACK && isServerTypeVisible(it) }
    }

    private fun isServerTypeVisible(type: DlcType): Boolean {
        return type != DlcType.TACZ_PACK || hasServerTaczMod
    }

    private fun openConfig() {
        val serverEntry = entry ?: return
        Minecraft.getInstance().setScreen(DlcServerConfigMaLiLibScreen(this, serverEntry))
    }

    private fun openServerTools() {
        Minecraft.getInstance().setScreen(DlcServerManagerScreen(this, entry, buildInfoSnapshot(), DlcServerManagerScreen.Tab.SERVERS, openedFromIngame))
    }

    private fun openRequiredManager() {
        Minecraft.getInstance().setScreen(DlcServerRequiredManagerScreen(this, entry))
    }

    private fun openServerList() {
        if (hasPresetChanges()) {
            DlcPresetRuntimeState.restoreEnabledPresets(presetSnapshot)
            presetAppliedIdentifiers.clear()
            applyPresetOverrides()
        }
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent)
            return
        }
        if (openedFromIngame) {
            Minecraft.getInstance().setScreen(null)
            return
        }
        Minecraft.getInstance().setScreen(DlcServerListScreen(null))
    }

    fun getRootParent(): Screen? = parent

    fun getItemByIdentifier(identifier: String?): DlcServerItem? {
        if (identifier.isNullOrBlank()) {
            return null
        }
        return itemsByIdentifier[normalizeIdentifier(identifier)]
    }

    fun getItemsSnapshot(): List<DlcServerItem> = ArrayList(items)

    private fun buildInfoSnapshot(): DlcServerInfo {
        val info = DlcServerInfo()
        info.items = ArrayList(items)
        info.remoteMissing = remoteMissingCount
        info.requiredMissing = requiredMissingCount
        info.hasRequired = hasRequiredEntries
        info.hasTaczMod = hasServerTaczMod
        info.baseVersion = serverBaseVersion
        info.dlcVersion = serverDlcVersion
        return info
    }

    private fun openPreset() {
        capturePresetSnapshot()
        Minecraft.getInstance().setScreen(object : DlcPresetManagerScreen(this@DlcServerMaLiLibScreen) {
            override fun onClose() {
                refreshAfterPresetChange()
                super.onClose()
            }
        })
    }

    private fun setAllEnabled(enabled: Boolean) {
        for (item in items) {
            if (activeFilter != null && mapType(item.type) != activeFilter) {
                continue
            }
            item.enabled = enabled
        }
        updateApplyState()
    }

    fun handleToggle(item: DlcServerItem?, button: ButtonGeneric?) {
        if (item == null) {
            return
        }
        if (item.enabled) {
            disableWithDependents(item)
            updateApplyState()
            return
        }
        if (item.alarm) {
            val tip = getTooltipText(item)
            rememberListScroll()
            Minecraft.getInstance().setScreen(DlcAlarmConfirmScreen(this, tip, Runnable { continueEnableWithChecks(item) }))
            return
        }
        continueEnableWithChecks(item)
    }

    private fun continueEnableWithChecks(item: DlcServerItem?) {
        if (item == null) return
        val conflicts = checkConflicts(item)
        if (conflicts.conflicts.isNotEmpty()) {
            val message = DlcConfirmUtil.buildConflictPromptMessage(conflicts.conflictNames)
            rememberListScroll()
            Minecraft.getInstance().setScreen(
                ConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(this)
                        if (accepted) {
                            continueEnableAfterConflictResolution(item)
                        }
                    },
                    Component.translatable("dlc_manager.conflict.word"),
                    message,
                    Component.translatable("dlc_manager.conflict.resolve"),
                    Component.translatable("gui.cancel"),
                ),
            )
            return
        }
        val deps = checkDependencies(item)
        if (deps.missing.isNotEmpty()) {
            val message = DlcConfirmUtil.buildDependencyPromptMessage(deps.missingNames)
            rememberListScroll()
            Minecraft.getInstance().setScreen(
                ConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(this)
                        if (accepted) {
                            for (dep in deps.missingItems) {
                                dep.enabled = true
                            }
                            item.enabled = true
                            updateApplyState()
                        }
                    },
                    Component.translatable("dlc_manager.dependency.enable"),
                    message,
                    Component.translatable("dlc_manager.dependency.enable"),
                    Component.translatable("gui.cancel"),
                ),
            )
            return
        }
        item.enabled = true
        updateApplyState()
    }

    private fun continueEnableAfterConflictResolution(item: DlcServerItem) {
        var guard = 0
        while (guard++ < 64) {
            val conflicts = checkConflicts(item)
            if (conflicts.conflicts.isEmpty() || conflicts.conflictItems.isEmpty()) {
                break
            }
            disableItemsWithDependents(conflicts.conflictItems)
        }

        val deps = checkDependencies(item)
        if (deps.missing.isNotEmpty()) {
            val message = DlcConfirmUtil.buildDependencyPromptMessage(deps.missingNames)
            rememberListScroll()
            Minecraft.getInstance().setScreen(
                ConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(this)
                        if (accepted) {
                            for (dep in deps.missingItems) {
                                dep.enabled = true
                            }
                            item.enabled = true
                            updateApplyState()
                        }
                    },
                    Component.translatable("dlc_manager.dependency.enable"),
                    message,
                    Component.translatable("dlc_manager.dependency.enable"),
                    Component.translatable("gui.cancel"),
                ),
            )
            return
        }
        item.enabled = true
        updateApplyState()
    }

    private fun disableWithDependents(item: DlcServerItem?) {
        if (item == null) return
        disableItemsWithDependents(listOf(item))
    }

    fun disableItemsWithDependents(itemsToDisable: Collection<DlcServerItem>?) {
        if (itemsToDisable.isNullOrEmpty()) {
            return
        }
        val toDisable: MutableSet<DlcServerItem> = LinkedHashSet()
        for (item in itemsToDisable) {
            toDisable.addAll(collectDependentsRecursive(item))
        }
        for (target in toDisable) {
            target.enabled = false
        }
    }

    private fun collectDependentsRecursive(root: DlcServerItem?): Set<DlcServerItem> {
        val result: MutableSet<DlcServerItem> = LinkedHashSet()
        if (root == null) {
            return result
        }
        val stack = ArrayDeque<DlcServerItem>()
        stack.add(root)
        while (!stack.isEmpty()) {
            val current = stack.removeFirst()
            if (!result.add(current)) {
                continue
            }
            val currentId = normalizeIdentifier(current.identifier)
            if (currentId.isBlank()) {
                continue
            }
            for (item in items) {
                val deps = item.dependencies
                if (deps.isNullOrEmpty()) {
                    continue
                }
                for (dep in deps) {
                    if (dep.isNullOrBlank()) {
                        continue
                    }
                    if (normalizeIdentifier(dep) == currentId) {
                        stack.add(item)
                        break
                    }
                }
            }
        }
        return result
    }

    fun updateApplyState() {
        val apply = applyButton ?: return
        var dirty = false
        for (item in items) {
            val baselineValue = baseline[item.id] ?: false
            if (baselineValue != item.enabled) {
                dirty = true
                break
            }
        }
        apply.setEnabled(dirty && !applying)
        backButton?.setEnabled(!dirty && !applying && requiredMissingCount <= 0)
    }

    fun isDirty(item: DlcServerItem?): Boolean {
        if (item == null) return false
        val baselineValue = baseline[item.id] ?: false
        return baselineValue != item.enabled
    }

    fun getPendingSuffix(item: DlcServerItem?): String {
        if (!isDirty(item)) {
            return ""
        }
        return if (mapType(item?.type) == DlcType.MOD) {
            StringUtils.translate("dlc_manager.pending.restart")
        } else {
            StringUtils.translate("dlc_manager.pending.reload")
        }
    }

    fun resolveDisplayName(item: DlcServerItem?): String {
        if (item == null) return ""
        val displayNames = item.displayNames
        if (!displayNames.isNullOrEmpty()) {
            val lang = Minecraft.getInstance().options.languageCode
            if (displayNames.containsKey(lang)) {
                return displayNames[lang] ?: ""
            }
            if (displayNames.containsKey("en_us")) {
                return displayNames["en_us"] ?: ""
            }
            return displayNames.values.firstOrNull() ?: (item.identifier ?: "")
        }
        if (!item.name.isNullOrBlank()) {
            return item.name!!
        }
        return item.identifier ?: ""
    }

    fun buildTooltipLines(font: Font, item: DlcServerItem?, maxWidth: Int): List<FormattedCharSequence> {
        val lines = ArrayList<FormattedCharSequence>()
        val tooltip = getTooltipText(item)
        if (!tooltip.isNullOrBlank()) {
            lines.addAll(DlcTextFormatter.splitMultiline(font, tooltip, maxWidth))
        }
        val deps = collectDependencyNamesRecursive(item)
        val conflicts = item?.conflicts ?: emptyList()
        val missing = collectMissingDependencyIdentifiers(item)
        val presetBlockers = DlcPresetRuntimeState.getDlcBlockerNames(item?.identifier, Minecraft.getInstance().options.languageCode)
        if (presetBlockers.isNotEmpty()) {
            if (lines.isNotEmpty()) lines.add(FormattedCharSequence.EMPTY)
            for (blocker in presetBlockers) {
                val line = Component.translatable("dlc_manager.preset.conflict", blocker).withStyle(ChatFormatting.DARK_RED)
                lines.addAll(font.split(line, maxWidth))
            }
        }
        if (missing.isNotEmpty()) {
            if (lines.isNotEmpty()) lines.add(FormattedCharSequence.EMPTY)
            val missingLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.missing_dependencies").withStyle(ChatFormatting.RED))
                .append(":")
            lines.addAll(font.split(missingLabel, maxWidth))
            for (dep in missing) {
                lines.addAll(font.split(DlcTextFormatter.parse(resolveDisplayName(resolveDependencyItem(dep))), maxWidth))
            }
        }
        if (deps.isNotEmpty()) {
            if (lines.isNotEmpty()) lines.add(FormattedCharSequence.EMPTY)
            val depLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.dependencies").withStyle(ChatFormatting.GOLD))
                .append(":")
            lines.addAll(font.split(depLabel, maxWidth))
            for (dep in deps) {
                lines.addAll(font.split(DlcTextFormatter.parse(resolveDisplayName(resolveDependencyItem(dep))), maxWidth))
            }
        }
        if (conflicts.isNotEmpty()) {
            if (lines.isNotEmpty()) lines.add(FormattedCharSequence.EMPTY)
            val conflictLabel = Component.empty()
                .append(Component.translatable("dlc_manager.tooltip.conflicts").withStyle(ChatFormatting.DARK_RED))
                .append(":")
            lines.addAll(font.split(conflictLabel, maxWidth))
            for (conflict in conflicts) {
                lines.addAll(font.split(DlcTextFormatter.parse(resolveDisplayName(resolveDependencyItem(conflict))), maxWidth))
            }
        }
        return lines
    }

    private fun getTooltipText(item: DlcServerItem?): String? {
        val tips = item?.tips
        if (tips.isNullOrEmpty()) {
            return null
        }
        val lang = Minecraft.getInstance().options.languageCode
        if (tips.containsKey(lang)) {
            return tips[lang]
        }
        if (tips.containsKey("en_us")) {
            return tips["en_us"]
        }
        return tips.values.firstOrNull()
    }

    private fun collectDependencyNamesRecursive(item: DlcServerItem?): List<String> {
        val deps = item?.dependencies ?: return emptyList()
        val result: LinkedHashSet<String> = LinkedHashSet()
        val queue = ArrayDeque(deps)
        while (!queue.isEmpty()) {
            val id = queue.poll()
            if (id.isNullOrBlank()) {
                continue
            }
            if (result.add(id)) {
                val dep = resolveDependencyItem(id)
                val depDeps = dep?.dependencies
                if (depDeps != null) {
                    queue.addAll(depDeps)
                }
            }
        }
        return ArrayList(result)
    }

    private fun collectMissingDependencyIdentifiers(item: DlcServerItem?): Set<String> {
        val deps = item?.dependencies
        if (deps.isNullOrEmpty()) {
            return emptySet()
        }
        val missing: MutableSet<String> = LinkedHashSet()
        for (depId in deps) {
            if (depId.isNullOrBlank()) continue
            if (resolveDependencyItem(depId) == null) {
                missing.add(depId)
            }
        }
        return missing
    }

    private fun checkDependencies(item: DlcServerItem?): DependencyCheck {
        val check = DependencyCheck()
        if (item?.dependencies == null) {
            return check
        }
        val visited: MutableSet<String> = LinkedHashSet()
        val queue = ArrayDeque<DlcServerItem>()
        queue.add(item)
        while (!queue.isEmpty()) {
            val current = queue.poll()
            val identifier = current.identifier
            if (identifier.isNullOrBlank()) {
                continue
            }
            val currentId = normalizeIdentifier(identifier)
            if (currentId.isBlank() || !visited.add(currentId)) {
                continue
            }
            val dependencies = current.dependencies
            if (dependencies.isNullOrEmpty()) {
                continue
            }
            for (depId in dependencies) {
                if (depId.isNullOrBlank()) continue
                val dep = resolveDependencyItem(depId)
                if (dep == null || !dep.enabled) {
                    val depKey = normalizeIdentifier(depId)
                    if (!check.missing.contains(depKey)) {
                        check.missing.add(depKey)
                        check.missingNames.add(resolveDisplayName(dep))
                    }
                    if (dep != null && !check.missingItems.contains(dep)) {
                        check.missingItems.add(dep)
                    }
                }
                if (dep != null) {
                    queue.add(dep)
                }
            }
        }
        return check
    }

    private fun checkConflicts(item: DlcServerItem?): ConflictCheck {
        val check = ConflictCheck()
        if (item == null) {
            return check
        }
        val plannedIds: MutableSet<String> = LinkedHashSet()
        val visited: MutableSet<String> = LinkedHashSet()
        val queue = ArrayDeque<DlcServerItem>()
        queue.add(item)
        while (!queue.isEmpty()) {
            val current = queue.poll()
            val identifier = current.identifier
            if (identifier.isNullOrBlank()) {
                continue
            }
            val currentId = normalizeIdentifier(identifier)
            if (currentId.isBlank() || !visited.add(currentId)) {
                continue
            }
            plannedIds.add(currentId)
            val dependencies = current.dependencies
            for (depId in dependencies) {
                if (depId.isNullOrBlank()) continue
                val dep = resolveDependencyItem(depId)
                if (dep != null) {
                    queue.add(dep)
                }
            }
        }

        for (plannedId in plannedIds) {
            val planned = itemsByIdentifier[plannedId]
            val conflicts = planned?.conflicts
            if (planned == null || conflicts.isNullOrEmpty()) {
                continue
            }
            for (conflictId in conflicts) {
                if (conflictId.isNullOrBlank()) continue
                val conflictKey = normalizeIdentifier(conflictId)
                if (conflictKey.isBlank() || plannedIds.contains(conflictKey)) {
                    continue
                }
                val conflict = resolveDependencyItem(conflictId)
                if (conflict != null && conflict.enabled && !check.conflicts.contains(conflictKey)) {
                    check.conflicts.add(conflictKey)
                    check.conflictItems.add(conflict)
                    check.conflictNames.add(resolveDisplayName(conflict))
                }
            }
        }

        for (existing in items) {
            val identifier = existing.identifier
            if (identifier.isNullOrBlank() || !existing.enabled) {
                continue
            }
            val existingKey = normalizeIdentifier(identifier)
            if (existingKey.isBlank() || plannedIds.contains(existingKey)) {
                continue
            }
            val conflicts = existing.conflicts
            if (conflicts.isNullOrEmpty()) {
                continue
            }
            var hit = false
            for (conflictId in conflicts) {
                if (conflictId.isNullOrBlank()) continue
                val conflictKey = normalizeIdentifier(conflictId)
                if (conflictKey.isNotBlank() && plannedIds.contains(conflictKey)) {
                    hit = true
                    break
                }
            }
            if (hit && !check.conflicts.contains(existingKey)) {
                check.conflicts.add(existingKey)
                check.conflictItems.add(existing)
                check.conflictNames.add(resolveDisplayName(existing))
            }
        }
        return check
    }

    private fun resolveDependencyItem(depId: String?): DlcServerItem? {
        if (depId.isNullOrBlank()) {
            return null
        }
        val key = normalizeIdentifier(depId)
        if (key.isBlank()) {
            return null
        }
        return itemsByIdentifier[key]
    }

    private fun applyChanges() {
        if (applying) {
            return
        }
        applying = true
        updateApplyState()
        val changed: MutableList<DlcServerItem> = ArrayList()
        for (item in items) {
            val baselineValue = baseline[item.id] ?: false
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
                        if (DlcConfig.isPresetFuncOn()) {
                            syncPresetStatusFromItems()
                            updatePresetSnapshot()
                        }
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

    private fun applyPresetOverrides() {
        if (!DlcConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.clearAll()
            presetAppliedIdentifiers.clear()
            presetStatusById.clear()
            return
        }
        if (DlcPresetRuntimeState.getPresets().isEmpty()) {
            DlcPresetRuntimeState.reload()
        }
        val entries = DlcPresetRuntimeState.getPresets()
        val enabledPresets = DlcPresetRuntimeState.getEnabledPresetIds()
        updatePresetStatusFromItems()
        if (enabledPresets.isEmpty() || entries.isEmpty()) {
            if (presetAppliedIdentifiers.isNotEmpty()) {
                for (identifier in presetAppliedIdentifiers) {
                    val item = getItemByIdentifier(identifier)
                    if (item != null) {
                        item.enabled = false
                    }
                }
                presetAppliedIdentifiers.clear()
            }
            return
        }
        val byId: MutableMap<String, DlcPresetEntry> = HashMap()
        for (presetEntry in entries) {
            val id = presetEntry.getId()
            if (id != null) {
                byId[id] = presetEntry
            }
        }
        val desiredEnable: MutableSet<String> = LinkedHashSet()
        var desiredDisable: MutableSet<String> = LinkedHashSet()
        val enableCache: MutableMap<String, Set<String>> = LinkedHashMap()
        val conflictCache: MutableMap<String, Set<String>> = LinkedHashMap()
        for (id in enabledPresets) {
            val preset = byId[id] ?: continue
            val partial = preset.getStatus() == DlcPresetStatus.PART
            for (enable in resolvePresetEnableIdentifiers(preset.getId(), byId, enableCache, LinkedHashSet())) {
                val key = normalizeIdentifier(enable)
                if (key.isBlank()) continue
                if (partial) {
                    val item = getItemByIdentifier(key)
                    if (item != null && item.enabled) {
                        desiredEnable.add(key)
                    }
                } else {
                    desiredEnable.add(key)
                }
            }
            for (conflict in resolvePresetConflictIdentifiers(preset.getId(), byId, enableCache, conflictCache, LinkedHashSet(), LinkedHashSet())) {
                val key = normalizeIdentifier(conflict)
                if (key.isNotBlank()) {
                    desiredDisable.add(key)
                }
            }
        }
        desiredDisable.addAll(collectInfoConflicts(desiredEnable))
        desiredDisable = expandDisabledByDependents(desiredDisable).toMutableSet()
        desiredEnable.removeAll(desiredDisable)
        val desiredAll: MutableSet<String> = LinkedHashSet(desiredEnable)
        desiredAll.addAll(desiredDisable)

        val toDisable: MutableSet<String> = LinkedHashSet(presetAppliedIdentifiers)
        toDisable.removeAll(desiredAll)
        for (identifier in desiredEnable) {
            getItemByIdentifier(identifier)?.enabled = true
        }
        for (identifier in desiredDisable) {
            getItemByIdentifier(identifier)?.enabled = false
        }
        for (identifier in toDisable) {
            val item = getItemByIdentifier(identifier)
            if (item != null) {
                item.enabled = baseline[item.id] ?: false
            }
        }
        presetAppliedIdentifiers.clear()
        presetAppliedIdentifiers.addAll(desiredAll)
    }

    private fun collectInfoConflicts(enabledIdentifiers: Set<String>?): MutableSet<String> {
        val result: MutableSet<String> = LinkedHashSet()
        if (enabledIdentifiers.isNullOrEmpty()) {
            return result
        }
        for (identifier in enabledIdentifiers) {
            if (identifier.isBlank()) continue
            val item = getItemByIdentifier(identifier)
            val conflicts = item?.conflicts
            if (conflicts.isNullOrEmpty()) continue
            for (conflict in conflicts) {
                val key = normalizeIdentifier(conflict)
                if (key.isBlank()) continue
                if (enabledIdentifiers.contains(key)) continue
                result.add(key)
            }
        }
        return result
    }

    private fun expandDisabledByDependents(disabledIdentifiers: Set<String>?): MutableSet<String> {
        val expanded: MutableSet<String> = LinkedHashSet()
        if (disabledIdentifiers.isNullOrEmpty()) {
            return expanded
        }
        val queue = ArrayDeque<DlcServerItem>()
        for (identifier in disabledIdentifiers) {
            if (identifier.isBlank()) continue
            expanded.add(identifier)
            val root = getItemByIdentifier(identifier)
            if (root != null) {
                queue.add(root)
            }
        }
        while (!queue.isEmpty()) {
            val current = queue.poll()
            val currentIdentifier = current.identifier
            if (currentIdentifier.isNullOrBlank()) continue
            for (dependent in collectDependentsRecursive(current)) {
                val dependentIdentifier = dependent.identifier
                if (dependentIdentifier.isNullOrBlank()) continue
                val key = normalizeIdentifier(dependentIdentifier)
                if (key.isNotBlank() && expanded.add(key)) {
                    queue.add(dependent)
                }
            }
        }
        return expanded
    }

    fun refreshAfterPresetChange() {
        applyPresetOverrides()
        rebuildRows()
        reCreateListWidget()
        updateListLayout()
        updateApplyState()
    }

    fun getPresetStatus(presetId: String?): DlcPresetStatus {
        if (presetId.isNullOrBlank()) {
            return DlcPresetStatus.OFF
        }
        updatePresetStatusFromItems()
        return presetStatusById.getOrDefault(presetId, DlcPresetStatus.OFF)
    }

    private fun updatePresetStatusFromItems(): MutableMap<String, DlcPresetStatus> {
        presetStatusById.clear()
        if (!DlcConfig.isPresetFuncOn()) {
            return presetStatusById
        }
        val entries = DlcPresetRuntimeState.getPresets()
        val enabledPresets = DlcPresetRuntimeState.getEnabledPresetIds()
        val byId: MutableMap<String, DlcPresetEntry> = LinkedHashMap()
        for (entry in entries) {
            val id = entry.getId()
            if (!id.isNullOrBlank()) {
                byId[id] = entry
            }
        }
        val enableCache: MutableMap<String, Set<String>> = LinkedHashMap()
        for (entry in entries) {
            val status = if (!enabledPresets.contains(entry.getId())) {
                DlcPresetStatus.OFF
            } else {
                var allEnabled = true
                for (identifier in resolvePresetEnableIdentifiers(entry.getId(), byId, enableCache, LinkedHashSet())) {
                    if (identifier.isBlank()) continue
                    val item = resolveDependencyItem(identifier)
                    if (item == null || !item.enabled) {
                        allEnabled = false
                        break
                    }
                }
                if (allEnabled) DlcPresetStatus.ON else DlcPresetStatus.PART
            }
            val id = entry.getId()
            if (id != null) {
                presetStatusById[id] = status
            }
        }
        return presetStatusById
    }

    private fun resolvePresetEnableIdentifiers(
        presetId: String?,
        byId: Map<String, DlcPresetEntry>,
        cache: MutableMap<String, Set<String>>,
        stack: MutableSet<String>,
    ): Set<String> {
        if (presetId.isNullOrBlank()) return emptySet()
        cache[presetId]?.let { return it }
        if (!stack.add(presetId)) return emptySet()
        val preset = byId[presetId]
        if (preset == null) {
            stack.remove(presetId)
            return emptySet()
        }
        val result: MutableSet<String> = LinkedHashSet()
        for (token in preset.getEnable()) {
            val value = token?.trim() ?: ""
            if (value.isBlank()) continue
            val refPresetId = resolvePresetReference(value, byId)
            if (refPresetId != null) {
                result.addAll(resolvePresetEnableIdentifiers(refPresetId, byId, cache, stack))
            } else {
                result.add(value)
            }
        }
        stack.remove(presetId)
        val unmodifiable = Collections.unmodifiableSet(LinkedHashSet(result))
        cache[presetId] = unmodifiable
        return unmodifiable
    }

    private fun resolvePresetConflictIdentifiers(
        presetId: String?,
        byId: Map<String, DlcPresetEntry>,
        enableCache: MutableMap<String, Set<String>>,
        conflictCache: MutableMap<String, Set<String>>,
        stack: MutableSet<String>,
        enableStack: MutableSet<String>,
    ): Set<String> {
        if (presetId.isNullOrBlank()) return emptySet()
        conflictCache[presetId]?.let { return it }
        if (!stack.add(presetId)) return emptySet()
        val preset = byId[presetId]
        if (preset == null) {
            stack.remove(presetId)
            return emptySet()
        }
        val result: MutableSet<String> = LinkedHashSet()
        for (token in preset.getConflicts()) {
            val value = token?.trim() ?: ""
            if (value.isBlank()) continue
            val refPresetId = resolvePresetReference(value, byId)
            if (refPresetId != null) {
                result.addAll(resolvePresetEnableIdentifiers(refPresetId, byId, enableCache, enableStack))
            } else {
                result.add(value)
            }
        }
        stack.remove(presetId)
        val unmodifiable = Collections.unmodifiableSet(LinkedHashSet(result))
        conflictCache[presetId] = unmodifiable
        return unmodifiable
    }

    private fun resolvePresetReference(token: String?, byId: Map<String, DlcPresetEntry>): String? {
        if (token.isNullOrBlank()) return null
        var forcedPreset = false
        var value = token.trim()
        if (value.regionMatches(0, PRESET_REF_PREFIX, 0, PRESET_REF_PREFIX.length, ignoreCase = true)) {
            forcedPreset = true
            value = value.substring(PRESET_REF_PREFIX.length).trim()
            if (value.isBlank()) return null
        }
        val presetId = findPresetId(value, byId) ?: return null
        if (!forcedPreset && getItemByIdentifier(value) != null) {
            return null
        }
        return presetId
    }

    private fun findPresetId(value: String, byId: Map<String, DlcPresetEntry>): String? {
        val exact = byId[value]
        if (!exact?.getId().isNullOrBlank()) {
            return exact!!.getId()
        }
        for (entry in byId.values) {
            val id = entry.getId()
            if (id.isNullOrBlank()) continue
            if (id.equals(value, ignoreCase = true)) return id
            val fileName = entry.getFileName()
            if (!fileName.isNullOrBlank() && fileName.equals(value, ignoreCase = true)) {
                return id
            }
        }
        return null
    }

    private fun syncPresetStatusFromItems() {
        val statuses = updatePresetStatusFromItems()
        DlcPresetRuntimeState.syncStatusFromExternal(statuses)
    }

    private fun capturePresetSnapshot() {
        if (presetSnapshotCaptured) return
        updatePresetSnapshot()
    }

    private fun updatePresetSnapshot() {
        presetSnapshot.clear()
        if (DlcConfig.isPresetFuncOn()) {
            presetSnapshot.addAll(DlcPresetRuntimeState.getEnabledPresetIds())
        }
        presetSnapshotCaptured = true
    }

    private fun hasPresetChanges(): Boolean {
        if (!DlcConfig.isPresetFuncOn()) return false
        if (!presetSnapshotCaptured) return false
        return DlcPresetRuntimeState.getEnabledPresetIds() != presetSnapshot
    }

    private fun promptRemoteDownloadIfMissing() {
        if (remoteMissingCount <= 0) {
            REMOTE_PROMPTED.remove(getPromptKey())
            return
        }
        if (promptedRemoteDownload || REMOTE_PROMPTED.contains(getPromptKey())) {
            return
        }
        promptedRemoteDownload = true
        REMOTE_PROMPTED.add(getPromptKey())
        startRemoteDownload()
    }

    private fun getPromptKey(): String = entry?.getKey() ?: ""

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
                        rebuildRows()
                        reCreateListWidget()
                        updateListLayout()
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

    private fun updateListLayout() {
        setListPosition(18, getListTop())
        val widget = listWidget
        if (widget != null) {
            widget.setSize(browserWidth, browserHeight)
            widget.initGui()
        }
        restoreListScroll()
    }

    private fun getListTop(): Int = 34

    private fun getFooterTop(): Int = height - 36

    private fun typeId(type: DlcType): String {
        return when (type) {
            DlcType.MOD -> "mod"
            DlcType.RESOURCE_PACK -> "resource"
            DlcType.DATA_PACK -> "data"
            DlcType.TACZ_PACK -> "tacz"
            DlcType.DLC_PACK -> "dlcpack"
            DlcType.UNKNOWN -> "other"
        }
    }

    override fun rememberReturnPosition() {
        rememberListScroll()
    }

    private fun rememberListScroll() {
        val widget = listWidget
        if (widget == null || widget.scrollbar == null) return
        savedScrollOffset = widget.scrollbar.value
        savedScrollFilter = activeFilter
        restoreScrollPending = true
    }

    private fun restoreListScroll() {
        if (!restoreScrollPending) return
        val widget = listWidget
        if (widget == null || widget.scrollbar == null) return
        if (savedScrollFilter != activeFilter) {
            restoreScrollPending = false
            return
        }
        val scrollBar = widget.scrollbar
        scrollBar.value = min(savedScrollOffset, scrollBar.maxValue)
        restoreScrollPending = false
    }

    private fun renderTitle(guiGraphics: GuiGraphics) {
        val title = StringUtils.translate("dlc_manager.server.title")
        val base = serverBaseVersion
        val dlc = serverDlcVersion
        var version = ""
        if (base.isNotBlank() || dlc.isNotBlank()) {
            version = if (dlc.isBlank()) base else if (base.isBlank()) dlc else "$base+$dlc"
        }
        val titleX = 8
        val titleY = 6
        guiGraphics.drawString(Minecraft.getInstance().font, title, titleX, titleY, 0xFFFFFF, false)
        if (version.isNotBlank()) {
            val titleWidth = Minecraft.getInstance().font.width(title)
            guiGraphics.drawString(Minecraft.getInstance().font, version, titleX + titleWidth + 6, titleY, 0xC8C8C8, false)
        }
        val textWidth = Minecraft.getInstance().font.width(title) + if (version.isBlank()) 0 else 6 + Minecraft.getInstance().font.width(version)
        val nextX = titleX + textWidth + 8
        val showRequired = shouldShowRequiredButton()
        requiredButton?.let { button ->
            if (!showRequired) {
                button.setEnabled(false)
                button.setX(-9999)
                button.setY(-9999)
            } else {
                button.setEnabled(true)
                val maxX = width - 10 - button.getWidth() - (missingButton?.let { it.getWidth() + 6 } ?: 0)
                button.setX(min(nextX, max(titleX, maxX)))
                button.setY(2)
            }
        }
        missingButton?.let { button ->
            val baseX = if (showRequired && requiredButton != null) {
                requiredButton!!.getX() + requiredButton!!.getWidth() + 6
            } else {
                nextX
            }
            val missMax = width - 10 - button.getWidth()
            button.setX(min(baseX, max(titleX, missMax)))
            button.setY(2)
        }
    }

    private fun shouldShowRequiredButton(): Boolean = hasRequiredEntries || requiredMissingCount > 0

    private fun getFilterLabel(type: DlcType?): String {
        val active = (type == null && activeFilter == null) || (type != null && type == activeFilter)
        val base = if (type == null) {
            StringUtils.translate("dlc_manager.filter.all")
        } else {
            StringUtils.translate(type.getDisplayKey())
        }
        return if (active) "[$base]" else base
    }

    private fun groupByRelativeFolder(items: List<DlcServerItem>): Map<String, List<DlcServerItem>> {
        val grouped: MutableMap<String, MutableList<DlcServerItem>> = java.util.TreeMap()
        for (item in items) {
            val label = if (item.path.isNullOrBlank()) "__root__" else item.path!!
            grouped.computeIfAbsent(label) { ArrayList() }.add(item)
        }
        return grouped
    }

    private fun resolvePathLabel(raw: String): String {
        if (raw == "__root__") {
            return StringUtils.translate("dlc_manager.group.root")
        }
        return raw
    }

    private class DependencyCheck {
        val missing: MutableList<String> = ArrayList()
        val missingNames: MutableList<String> = ArrayList()
        val missingItems: MutableList<DlcServerItem> = ArrayList()
    }

    private class ConflictCheck {
        val conflicts: MutableList<String> = ArrayList()
        val conflictNames: MutableList<String> = ArrayList()
        val conflictItems: MutableList<DlcServerItem> = ArrayList()
    }

    private class SimpleButtonListener(private val action: Runnable) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            action.run()
        }
    }

    private inner class FilterButtonListener(private val filter: DlcType?) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            activeFilter = if (filter != null && !isServerTypeVisible(filter)) null else filter
            rebuildRows()
            reCreateListWidget()
            updateFilterButtons()
            updateListLayout()
            val widget = listWidget
            if (widget != null && widget.scrollbar != null) {
                widget.scrollbar.value = 0
            }
        }
    }

    companion object {
        private const val PRESET_REF_PREFIX = "preset:"
        private val REMOTE_PROMPTED: MutableSet<String> = HashSet()

        private fun normalizeIdentifier(identifier: String?): String {
            if (identifier == null) return ""
            return identifier.trim().lowercase(Locale.ROOT)
        }

        private fun mapType(type: String?): DlcType? {
            if (type == null) return null
            val lower = type.lowercase(Locale.ROOT)
            if (lower.contains("datapack") || lower == "data_pack" || lower == "data") return DlcType.DATA_PACK
            if (lower.contains("resourcepack") || lower == "resource_pack" || lower == "resource") return DlcType.RESOURCE_PACK
            if (lower.contains("tacz")) return DlcType.TACZ_PACK
            if (lower == "mods" || lower == "mod") return DlcType.MOD
            for (dlcType in DlcType.values()) {
                if (dlcType == DlcType.DLC_PACK) {
                    continue
                }
                val id = when (dlcType) {
                    DlcType.MOD -> "mod"
                    DlcType.RESOURCE_PACK -> "resource"
                    DlcType.DATA_PACK -> "data"
                    DlcType.TACZ_PACK -> "tacz"
                    DlcType.DLC_PACK -> "dlcpack"
                    DlcType.UNKNOWN -> "other"
                }
                if (id.equals(type, ignoreCase = true) || dlcType.name.equals(type, ignoreCase = true)) {
                    return dlcType
                }
            }
            return DlcType.UNKNOWN
        }
    }
}
