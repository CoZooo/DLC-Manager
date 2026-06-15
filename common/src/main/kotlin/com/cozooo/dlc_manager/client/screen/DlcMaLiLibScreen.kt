package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.notice.DlcNoticeManager
import com.cozooo.dlc_manager.client.preset.DlcPresetRuntimeState
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry
import com.cozooo.dlc_manager.client.remote.DlcRedirectLoader
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredLoader
import com.cozooo.dlc_manager.client.required.DlcRequiredRuntimeState
import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.update.DlcUpdateClient
import com.cozooo.dlc_manager.core.ApplyMode
import com.cozooo.dlc_manager.core.DlcApplier
import com.cozooo.dlc_manager.core.DlcConfig
import com.cozooo.dlc_manager.core.DlcConfigDlcSupport
import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.core.DlcRuntimeState
import com.cozooo.dlc_manager.core.DlcScanner
import com.cozooo.dlc_manager.core.DlcType
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import fi.dy.masa.malilib.gui.button.ButtonBase
import fi.dy.masa.malilib.gui.button.ButtonGeneric
import fi.dy.masa.malilib.gui.button.IButtonActionListener
import fi.dy.masa.malilib.util.StringUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.repository.PackRepository
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.TreeMap
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

class DlcMaLiLibScreen(private val parent: Screen?) :
    DlcMaLiLibCompat<DlcListRow, DlcListEntry, DlcListWidget>(10, 34),
    DlcReturnPositionHost {
    private val rows: MutableList<DlcListRow> = ArrayList()
    private val items: MutableList<DlcItem> = ArrayList()
    private val allItems: MutableList<DlcItem> = ArrayList()
    private var applyMode = ApplyMode.RESTART_ONLY
    private var hasModItems = false
    private var hasModChanges = false
    private var userSelectedApplyMode = false
    private var savedScrollOffset = 0
    private var savedScrollFilter: DlcType? = null
    private var restoreScrollPending = false

    private var applyButton: ButtonGeneric? = null
    private var backButton: ButtonGeneric? = null
    private var modeButton: ButtonGeneric? = null
    private var configButton: ButtonGeneric? = null
    private var updateButton: ButtonGeneric? = null
    private var presetButton: ButtonGeneric? = null
    private var packButton: ButtonGeneric? = null
    private var serverButton: ButtonGeneric? = null
    private var enableAllButton: ButtonGeneric? = null
    private var disableAllButton: ButtonGeneric? = null
    private var noticeButton: ButtonGeneric? = null
    private var remoteButton: ButtonGeneric? = null
    private var requiredButton: ButtonGeneric? = null
    private var noticeChecked = false
    private var allFilterButton: ButtonGeneric? = null
    private var modsFilterButton: ButtonGeneric? = null
    private var taczFilterButton: ButtonGeneric? = null
    private var resourceFilterButton: ButtonGeneric? = null
    private var dataFilterButton: ButtonGeneric? = null
    private var otherFilterButton: ButtonGeneric? = null

    private var activeFilter: DlcType? = null
    private var footerTwoRows = false
    private val hasTaczMod = CrossPlatformRuntime.isModLoaded("tacz")
    private val presetSnapshot: MutableSet<String> = LinkedHashSet()
    private var presetSnapshotCaptured = false
    private var lastWidth = -1
    private var lastHeight = -1

    init {
        setTitle(StringUtils.translate("dlc_manager.title"))
        setParent(parent)
        setListPosition(18, 42)
    }

    override fun createListWidget(x: Int, y: Int): DlcListWidget {
        return DlcListWidget(x, y, browserWidth, browserHeight, rows)
    }

    override fun getBrowserWidth(): Int = width - 36

    override fun getBrowserHeight(): Int {
        val listTop = getListTop()
        val listBottom = getFooterTop()
        return max(80, listBottom - listTop)
    }

    override fun initGui() {
        if (DlcRemoteManager.tryShowFromManager(this)) {
            return
        }
        if (!noticeChecked) {
            noticeChecked = true
            if (DlcNoticeManager.tryShowFromManager(this)) {
                return
            }
        }
        rebuildRows()
        capturePresetSnapshot()
        reCreateListWidget()
        super.initGui()
        addHeaderButtons()
        addNoticeButton()
        addRemoteButton()
        addRequiredButton()
        addFilterButtons()
        addFooterButtons()
        updateListLayout()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcTooltipContext.clear()
        if (width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            refreshLayoutForResize()
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        DlcTooltipContext.render(guiGraphics, Minecraft.getInstance().font)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean {
        if (isOverAnyButton(mouseX, mouseY)) {
            return true
        }
        return DlcScreenCompat.invokeCompatibleMouseScroll(this, mouseX.toInt(), mouseY.toInt(), deltaX, deltaY)
    }

    private fun isOverAnyButton(mouseX: Double, mouseY: Double): Boolean {
        return isOverButton(enableAllButton, mouseX, mouseY) ||
            isOverButton(disableAllButton, mouseX, mouseY) ||
            isOverButton(noticeButton, mouseX, mouseY) ||
            isOverButton(remoteButton, mouseX, mouseY) ||
            isOverButton(requiredButton, mouseX, mouseY) ||
            isOverButton(allFilterButton, mouseX, mouseY) ||
            isOverButton(modsFilterButton, mouseX, mouseY) ||
            isOverButton(taczFilterButton, mouseX, mouseY) ||
            isOverButton(resourceFilterButton, mouseX, mouseY) ||
            isOverButton(dataFilterButton, mouseX, mouseY) ||
            isOverButton(otherFilterButton, mouseX, mouseY) ||
            isOverButton(modeButton, mouseX, mouseY) ||
            isOverButton(updateButton, mouseX, mouseY) ||
            isOverButton(configButton, mouseX, mouseY) ||
            isOverButton(packButton, mouseX, mouseY) ||
            isOverButton(serverButton, mouseX, mouseY) ||
            isOverButton(applyButton, mouseX, mouseY) ||
            isOverButton(backButton, mouseX, mouseY)
    }

    private fun isOverButton(button: ButtonGeneric?, mouseX: Double, mouseY: Double): Boolean {
        if (button == null) {
            return false
        }
        val x = button.getX()
        val y = button.getY()
        return mouseX >= x && mouseX < x + button.getWidth() && mouseY >= y && mouseY < y + button.getHeight()
    }

    private fun canShowServerButton(): Boolean = DlcPathConfig.isServerSupportOn()

    private fun hasRequiredEntries(): Boolean {
        for (dlcRoot in DlcPathConfig.getDlcRoots()) {
            if (DlcRequiredLoader.load(dlcRoot).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun addHeaderButtons() {
        val labels = getHeaderLabels()
        val showEnable = DlcPathConfig.isEnableAllButtonShown()
        val right = width - 18
        val y = 0
        val h = 12
        val gap = 6
        val enableWidth = max(60, maxTextWidth(labels.enableLabel, labels.enableDone) + 4)
        val disableWidth = max(60, maxTextWidth(labels.disableLabel, labels.disableDone) + 4)
        var disableX = right - disableWidth
        var enableX = disableX - gap - enableWidth
        if (showEnable) {
            if (enableX < 18) {
                enableX = 18
                disableX = enableX + enableWidth + gap
            }
        } else {
            enableX = -1000
        }
        enableAllButton = null
        if (showEnable) {
            enableAllButton = ButtonGeneric(enableX, y, enableWidth, h, labels.enableLabel)
            addButton(enableAllButton, HeaderButtonListener(true))
        }
        disableAllButton = ButtonGeneric(disableX, y, disableWidth, h, labels.disableLabel)
        addButton(disableAllButton, HeaderButtonListener(false))
        updateHeaderButtonsState()
    }

    private fun addNoticeButton() {
        if (!DlcPathConfig.isNoticeDisplayModeManagerButton() || !DlcNoticeManager.isNoticeEnabled()) {
            noticeButton = null
            return
        }
        val label = StringUtils.translate("dlc_manager.notice")
        val buttonWidth = max(36, textRenderer.width(label) + 8)
        val buttonHeight = 12
        val titleWidth = textRenderer.width(titleString)
        var x = 18 + titleWidth + 6
        val y = 1
        var rightLimit = width - 18
        if (enableAllButton != null) {
            rightLimit = min(rightLimit, enableAllButton!!.getX() - 6)
        } else if (disableAllButton != null) {
            rightLimit = min(rightLimit, disableAllButton!!.getX() - 6)
        }
        if (x + buttonWidth > rightLimit) x = rightLimit - buttonWidth
        if (x < 18) x = 18
        noticeButton = ButtonGeneric(x, y, buttonWidth, buttonHeight, label)
        addButton(noticeButton, NoticeButtonListener())
    }

    private fun addRemoteButton() {
        if (!DlcRemoteManager.hasMissing()) {
            remoteButton = null
            return
        }
        val label = StringUtils.translate("dlc_manager.remote.complete")
        val buttonWidth = max(50, textRenderer.width(label) + 8)
        val buttonHeight = 12
        var x = 18 + textRenderer.width(titleString) + 6
        val y = 1
        if (noticeButton != null) {
            x = noticeButton!!.getX() + noticeButton!!.getWidth() + 4
        }
        var rightLimit = width - 18
        if (enableAllButton != null) {
            rightLimit = min(rightLimit, enableAllButton!!.getX() - 6)
        } else if (disableAllButton != null) {
            rightLimit = min(rightLimit, disableAllButton!!.getX() - 6)
        }
        if (x + buttonWidth > rightLimit) x = rightLimit - buttonWidth
        if (x < 18) x = 18
        remoteButton = ButtonGeneric(x, y, buttonWidth, buttonHeight, label)
        addButton(remoteButton, RemoteButtonListener())
    }

    private fun addRequiredButton() {
        if (!hasRequiredEntries()) {
            requiredButton = null
            return
        }
        val label = StringUtils.translate("dlc_manager.required.manage_button")
        val buttonWidth = max(50, textRenderer.width(label) + 8)
        val buttonHeight = 12
        var x = 18 + textRenderer.width(titleString) + 6
        val y = 1
        if (remoteButton != null) {
            x = remoteButton!!.getX() + remoteButton!!.getWidth() + 4
        } else if (noticeButton != null) {
            x = noticeButton!!.getX() + noticeButton!!.getWidth() + 4
        }
        var rightLimit = width - 18
        if (enableAllButton != null) {
            rightLimit = min(rightLimit, enableAllButton!!.getX() - 6)
        } else if (disableAllButton != null) {
            rightLimit = min(rightLimit, disableAllButton!!.getX() - 6)
        }
        if (rightLimit - x < buttonWidth) {
            requiredButton = ButtonGeneric(-9999, -9999, 0, 0, label)
            requiredButton?.setEnabled(false)
            addButton(requiredButton, RequiredButtonListener())
            return
        }
        requiredButton = ButtonGeneric(x, y, buttonWidth, buttonHeight, label)
        addButton(requiredButton, RequiredButtonListener())
    }

    private fun addFilterButtons() {
        val panelLeft = 20
        val panelRight = width - 20
        val y = 19
        val h = 18
        var gap = if (width < 420) 4 else 6
        val count = if (hasTaczMod) 6 else 5
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
        modsFilterButton = ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.MOD))
        addButton(modsFilterButton, FilterButtonListener(DlcType.MOD))
        x += w + gap
        if (hasTaczMod) {
            taczFilterButton = ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.TACZ_PACK))
            addButton(taczFilterButton, FilterButtonListener(DlcType.TACZ_PACK))
            x += w + gap
        }
        resourceFilterButton = ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.RESOURCE_PACK))
        addButton(resourceFilterButton, FilterButtonListener(DlcType.RESOURCE_PACK))
        x += w + gap
        dataFilterButton = ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.DATA_PACK))
        addButton(dataFilterButton, FilterButtonListener(DlcType.DATA_PACK))
        x += w + gap
        otherFilterButton = ButtonGeneric(x, y, w, h, getFilterLabel(DlcType.UNKNOWN))
        addButton(otherFilterButton, FilterButtonListener(DlcType.UNKNOWN))
    }

    private fun addFooterButtons() {
        val y = height - 30
        val margin = 8
        val gap = 2
        val padding = 12
        val left = margin
        val right = width - margin
        val modeWidth = max(90, StringUtils.getStringWidth(getApplyModeLabel()) + padding)
        modeButton = ButtonGeneric(left, y, modeWidth, 20, getApplyModeLabel())
        addButton(modeButton, FooterButtonListener())
        updateButton = null
        if (DlcPathConfig.isUpdateManagerOn() && !DlcPathConfig.isUButtomShown()) {
            val updateWidth = max(80, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.update.manager")) + padding)
            updateButton = ButtonGeneric(left + modeButton!!.getWidth() + gap, y, updateWidth, 20, StringUtils.translate("dlc_manager.update.manager"))
            addButton(updateButton, FooterButtonListener())
        }
        presetButton = null
        if (DlcPathConfig.isPresetFuncOn()) {
            val presetWidth = max(70, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.preset.manager")) + 8)
            presetButton = ButtonGeneric(left + modeButton!!.getWidth() + gap, y, presetWidth, 20, StringUtils.translate("dlc_manager.preset.manager"))
            if (updateButton != null) {
                presetButton!!.setX(updateButton!!.getX() + updateButton!!.getWidth() + gap)
            }
            addButton(presetButton, FooterButtonListener())
        }
        packButton = null
        if (DlcPathConfig.isPackFuncOn() && !DlcPathConfig.isPButtomShown()) {
            val packWidth = max(70, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.pack.manager")) + 8)
            packButton = ButtonGeneric(left + modeButton!!.getWidth() + gap, y, packWidth, 20, StringUtils.translate("dlc_manager.pack.manager"))
            if (updateButton != null) {
                packButton!!.setX(updateButton!!.getX() + updateButton!!.getWidth() + gap)
            }
            if (presetButton != null) {
                packButton!!.setX(presetButton!!.getX() + presetButton!!.getWidth() + gap)
            }
            addButton(packButton, FooterButtonListener())
        }
        configButton = null
        if (DlcPathConfig.isConfigButtonShown()) {
            var configX = left + modeButton!!.getWidth() + gap
            if (updateButton != null) configX += updateButton!!.getWidth() + gap
            if (presetButton != null) configX += presetButton!!.getWidth() + gap
            if (packButton != null) configX += packButton!!.getWidth() + gap
            val configWidth = max(60, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.config")) + padding)
            configButton = ButtonGeneric(configX, y, configWidth, 20, StringUtils.translate("dlc_manager.config"))
            addButton(configButton, FooterButtonListener())
        }
        val applyWidth = max(70, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.apply")) + padding)
        val backWidth = max(70, StringUtils.getStringWidth(getBackButtonLabel()) + padding)
        backButton = ButtonGeneric(right - backWidth, y, backWidth, 20, getBackButtonLabel())
        addButton(backButton, FooterButtonListener())
        applyButton = ButtonGeneric(backButton!!.getX() - gap - applyWidth, y, applyWidth, 20, StringUtils.translate("dlc_manager.apply"))
        addButton(applyButton, FooterButtonListener())
        serverButton = null
        if (canShowServerButton()) {
            val serverWidth = max(80, StringUtils.getStringWidth(StringUtils.translate("dlc_manager.server.manage")) + padding)
            val serverX = applyButton!!.getX() - gap - serverWidth
            serverButton = ButtonGeneric(serverX, y, serverWidth, 20, StringUtils.translate("dlc_manager.server.manage"))
            addButton(serverButton, FooterButtonListener())
        }
        var leftSpanEnd = modeButton!!.getX() + modeButton!!.getWidth()
        if (updateButton != null) leftSpanEnd = max(leftSpanEnd, updateButton!!.getX() + updateButton!!.getWidth())
        if (presetButton != null) leftSpanEnd = max(leftSpanEnd, presetButton!!.getX() + presetButton!!.getWidth())
        if (packButton != null) leftSpanEnd = max(leftSpanEnd, packButton!!.getX() + packButton!!.getWidth())
        if (configButton != null) leftSpanEnd = max(leftSpanEnd, configButton!!.getX() + configButton!!.getWidth())
        var rightStart = applyButton!!.getX()
        if (serverButton != null) rightStart = min(rightStart, serverButton!!.getX())
        footerTwoRows = leftSpanEnd + gap > rightStart
        if (footerTwoRows) {
            val row1Y = height - 52
            val row2Y = height - 30
            modeButton!!.setY(row1Y)
            updateButton?.setY(row1Y)
            presetButton?.setY(row1Y)
            packButton?.setY(row1Y)
            configButton?.setY(row1Y)
            serverButton?.setY(row2Y)
            applyButton!!.setY(row2Y)
            backButton!!.setY(row2Y)
        }
        updateModeAvailability()
        ensureFooterNoOverlap()
    }

    private fun rebuildRows() {
        rows.clear()
        items.clear()
        allItems.clear()
        hasModItems = false
        hasModChanges = false
        userSelectedApplyMode = false
        if (DlcPathConfig.isPresetFuncOn()) {
            DlcPresetRuntimeState.reload()
        } else {
            DlcPresetRuntimeState.clearAll()
        }
        if (activeFilter == null) {
            appendRedirectRows()
        }
        val grouped = DlcScanner().scan()
        val enabledById: MutableMap<String?, Boolean> = HashMap()
        for (type in DlcType.values()) {
            if (type == DlcType.TACZ_PACK && !hasTaczMod) {
                continue
            }
            val typeItems = grouped[type]?.filterNotNull()
            if (typeItems.isNullOrEmpty()) {
                continue
            }
            if (type == DlcType.MOD) {
                hasModItems = true
            }
            for (item in typeItems) {
                val enabled = item.isEnabled()
                enabledById[item.getId()] = enabled
                DlcRuntimeState.ensureBaseline(item.getId(), enabled)
                DlcRuntimeState.getOverride(item.getId())?.let { item.setEnabled(it) }
                allItems.add(item)
            }
            if (activeFilter != null && type != activeFilter) {
                continue
            }
            val visibleItems = typeItems.filter { !it.isHidden() }
            if (visibleItems.isEmpty()) {
                continue
            }
            if (activeFilter == null) {
                rows.add(DlcListRow.groupHeader(type))
            }
            val byPath = groupByRelativeFolder(visibleItems)
            val hasSubDir = byPath.keys.any { it != ROOT_GROUP }
            var firstPathHeader = true
            for ((path, pathItems) in byPath) {
                if (path == ROOT_GROUP) {
                    if (hasSubDir) {
                        rows.add(DlcListRow.pathHeader(resolvePathLabel(path), pathItems, true, firstPathHeader))
                        firstPathHeader = false
                    }
                } else {
                    rows.add(DlcListRow.pathHeader(resolvePathLabel(path), pathItems, false, firstPathHeader))
                    firstPathHeader = false
                }
                for (item in pathItems) {
                    val enabled = enabledById[item.getId()] ?: item.isEnabled()
                    DlcRuntimeState.ensureBaseline(item.getId(), enabled)
                    DlcRuntimeState.getOverride(item.getId())?.let { item.setEnabled(it) }
                    item.setDirty(isItemModified(item))
                    rows.add(DlcListRow.item(item))
                    items.add(item)
                }
            }
        }
        DlcRuntimeState.updateItemIndex(allItems)
        DlcPresetRuntimeState.applyOverridesToRuntime()
        hasModChanges = computeModReloadChanges()
        updateHeaderButtonsState()
    }

    private fun appendRedirectRows() {
        val redirectEntries: MutableList<DlcRedirectEntry> = ArrayList()
        for (dlcRoot in DlcPathConfig.getDlcRoots()) {
            redirectEntries.addAll(DlcRedirectLoader.load(dlcRoot))
        }
        if (redirectEntries.isEmpty()) {
            return
        }
        val lang = Minecraft.getInstance().options.languageCode
        val visible = redirectEntries.filter { !it.isHidden() && it.isAvailableFor(lang) }
        if (visible.isEmpty()) {
            return
        }
        rows.add(DlcListRow.redirectHeader(StringUtils.translate("dlc_manager.redirect.header")))
        for (entry in visible) {
            rows.add(DlcListRow.redirectItem(entry))
        }
    }

    private fun groupByRelativeFolder(items: List<DlcItem>): Map<String, List<DlcItem>> {
        val grouped: MutableMap<String, MutableList<DlcItem>> = TreeMap()
        for (item in items) {
            val label = resolveGroupLabel(item)
            grouped.computeIfAbsent(label) { ArrayList() }.add(item)
        }
        return grouped
    }

    private fun resolveGroupLabel(item: DlcItem): String {
        return try {
            val source = item.getSourcePath() ?: return ROOT_GROUP
            var relative: String? = null
            for (dlcRoot in DlcPathConfig.getDlcRoots()) {
                if (source.startsWith(dlcRoot)) {
                    relative = dlcRoot.relativize(source).toString().replace('\\', '/')
                    break
                }
            }
            if (relative == null) {
                relative = DlcPaths.gameDir().relativize(source).toString().replace('\\', '/')
            }
            val lastSlash = relative.lastIndexOf('/')
            if (lastSlash <= 0) ROOT_GROUP else relative.substring(0, lastSlash)
        } catch (_: Exception) {
            ROOT_GROUP
        }
    }

    private fun resolvePathLabel(raw: String): String {
        return if (raw == ROOT_GROUP) StringUtils.translate("dlc_manager.group.root") else raw
    }

    private fun getApplyModeLabel(): String {
        val modeName = StringUtils.translate(applyMode.getLabelKey())
        return StringUtils.translate("dlc_manager.apply_mode_label", modeName)
    }

    private fun getFilterLabel(type: DlcType?): String {
        val active = (type == null && activeFilter == null) || (type != null && type == activeFilter)
        val base = if (type == null) StringUtils.translate("dlc_manager.filter.all") else StringUtils.translate(type.getDisplayKey())
        return if (active) "[$base]" else base
    }

    private fun toggleApplyMode() {
        if (hasModChanges) {
            applyMode = ApplyMode.RESTART_ONLY
            updateModeAvailability()
            return
        }
        applyMode = if (applyMode == ApplyMode.RESTART_ONLY) ApplyMode.TRY_HOT_RELOAD else ApplyMode.RESTART_ONLY
        userSelectedApplyMode = true
        modeButton?.setDisplayString(getApplyModeLabel())
    }

    private fun setFilter(type: DlcType?) {
        syncVisibleItemOverrides()
        activeFilter = type
        allFilterButton?.setDisplayString(getFilterLabel(null))
        modsFilterButton?.setDisplayString(getFilterLabel(DlcType.MOD))
        taczFilterButton?.setDisplayString(getFilterLabel(DlcType.TACZ_PACK))
        resourceFilterButton?.setDisplayString(getFilterLabel(DlcType.RESOURCE_PACK))
        dataFilterButton?.setDisplayString(getFilterLabel(DlcType.DATA_PACK))
        otherFilterButton?.setDisplayString(getFilterLabel(DlcType.UNKNOWN))
        rebuildRows()
        reCreateListWidget()
        updateListLayout()
        updateHeaderButtonsLayout()
        updateNoticeButtonLayout()
        updateRemoteButtonLayout()
        updateRequiredButtonLayout()
        updateModeAvailability()
    }

    private fun syncVisibleItemOverrides() {
        for (item in items) {
            val id = item.getId()
            if (id.isNullOrBlank()) {
                continue
            }
            DlcRuntimeState.setOverride(id, item.isEnabled())
        }
    }

    private fun applyChanges() {
        val applyNeeded = computeApplyChanges()
        var modReloadNeeded = computeModReloadChanges()
        var otherReloadNeeded = computeNonModReloadChanges()
        val requiredPending = DlcRequiredRuntimeState.hasPendingChanges()
        val presetChanged = DlcPathConfig.isPresetFuncOn() && hasPresetChanges()
        if (!applyNeeded && !modReloadNeeded && !otherReloadNeeded) {
            if (presetChanged) {
                DlcPresetRuntimeState.syncStatusFromRuntime()
            }
            if (requiredPending) {
                showRequiredRestartPrompt()
            } else {
                Minecraft.getInstance().setScreen(parent)
            }
            return
        }
        var applyResult: DlcApplier.ApplyResult? = null
        if (applyNeeded) {
            applyResult = DlcApplier.apply(allItems, applyMode)
            if (applyResult.requiresRestart()) {
                modReloadNeeded = true
            }
            if (applyResult.helperScheduled()) {
                modReloadNeeded = true
                otherReloadNeeded = false
            }
            updateResourcePackSelection()
            if (shouldShowApplyFailureScreen(applyResult)) {
                showApplyFailureScreen(applyResult)
                return
            }
        }
        if (DlcPathConfig.isPresetFuncOn() && (presetChanged || applyNeeded)) {
            DlcPresetRuntimeState.syncStatusFromRuntime()
        }
        if (!modReloadNeeded && !otherReloadNeeded) {
            if (requiredPending) {
                showRequiredRestartPrompt()
            } else {
                Minecraft.getInstance().setScreen(parent)
            }
            return
        }
        val helperScheduled = applyResult?.helperScheduled() == true
        val message = when {
            helperScheduled -> "dlc_manager.apply.deferred_restart"
            modReloadNeeded -> "dlc_manager.message.restart"
            otherReloadNeeded && applyMode == ApplyMode.TRY_HOT_RELOAD -> "dlc_manager.message.reload_prompt"
            else -> "dlc_manager.message.reopen_world"
        }
        val minecraft = Minecraft.getInstance()
        if (applyMode == ApplyMode.RESTART_ONLY) {
            minecraft.setScreen(
                ConfirmScreen(
                    { accepted -> if (accepted) minecraft.stop() else minecraft.setScreen(parent) },
                    Component.translatable("dlc_manager.title"),
                    Component.translatable(message),
                    Component.translatable("dlc_manager.restart_now"),
                    Component.translatable("dlc_manager.restart_later"),
                )
            )
        } else if (applyMode == ApplyMode.TRY_HOT_RELOAD) {
            minecraft.setScreen(
                ConfirmScreen(
                    { accepted ->
                        if (accepted) {
                            val future = tryReloadResources()
                            future.thenRun {
                                minecraft.execute {
                                    updateBaselineAfterReload()
                                    minecraft.setScreen(parent)
                                }
                            }
                        } else {
                            minecraft.setScreen(parent)
                        }
                    },
                    Component.translatable("dlc_manager.title"),
                    Component.translatable(message),
                    Component.translatable("dlc_manager.reload_now"),
                    Component.translatable("dlc_manager.reload_later"),
                )
            )
        } else {
            minecraft.setScreen(
                ConfirmScreen(
                    { minecraft.setScreen(parent) },
                    Component.translatable("dlc_manager.title"),
                    Component.translatable(message),
                )
            )
        }
    }

    private fun showRequiredRestartPrompt() {
        val minecraft = Minecraft.getInstance()
        minecraft.setScreen(
            ConfirmScreen(
                { accepted -> if (accepted) minecraft.stop() else minecraft.setScreen(parent) },
                Component.translatable("dlc_manager.title"),
                Component.translatable("dlc_manager.required.restart_prompt"),
                Component.translatable("dlc_manager.restart_now"),
                Component.translatable("dlc_manager.restart_later"),
            )
        )
    }

    private fun shouldShowApplyFailureScreen(result: DlcApplier.ApplyResult?): Boolean = hasFailureMessage(result)

    private fun hasFailureMessage(result: DlcApplier.ApplyResult?): Boolean {
        val messages = result?.messages() ?: return false
        return messages.any { it.startsWith("Failed to ") }
    }

    private fun showApplyFailureScreen(result: DlcApplier.ApplyResult?) {
        Minecraft.getInstance().setScreen(
            DlcSingleButtonScreen(
                parent,
                Component.translatable("dlc_manager.server.apply_failed"),
                Component.literal(joinFailureMessages(result)),
                Component.translatable("dlc_manager.back"),
            )
        )
    }

    private fun joinFailureMessages(result: DlcApplier.ApplyResult?): String {
        val messages = result?.messages() ?: return ""
        return messages.filter { it.startsWith("Failed to ") }.joinToString("\n")
    }

    private inner class FooterButtonListener : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            when (button) {
                backButton -> returnToMainMenu()
                applyButton -> applyChanges()
                updateButton -> {
                    rememberListScroll()
                    DlcUpdateClient.openUpdateManager(this@DlcMaLiLibScreen)
                }
                presetButton -> {
                    rememberListScroll()
                    Minecraft.getInstance().setScreen(DlcPresetManagerScreen(this@DlcMaLiLibScreen))
                }
                packButton -> {
                    rememberListScroll()
                    Minecraft.getInstance().setScreen(DlcPackManagerScreen(this@DlcMaLiLibScreen))
                }
                configButton -> {
                    rememberListScroll()
                    Minecraft.getInstance().setScreen(DlcConfigScreen(this@DlcMaLiLibScreen))
                }
                serverButton -> {
                    rememberListScroll()
                    Minecraft.getInstance().setScreen(DlcServerListScreen(this@DlcMaLiLibScreen))
                }
                modeButton -> toggleApplyMode()
            }
        }
    }

    override fun drawContents(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val panelLeft = 16
        val panelRight = width - 16
        val panelTop = 16
        val headerHeight = 22
        val panelBottom = height - 16
        val footerTop = getFooterTop()
        guiGraphics.fill(panelLeft, panelTop, panelRight, footerTop, -0x33f0eeec)
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, -0xd5d0ca)
        guiGraphics.fill(panelLeft, footerTop - 1, panelRight, footerTop, -0xd5d0ca)
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 2, footerTop, -0xd5d0ca)
        guiGraphics.fill(panelRight - 1, panelTop, panelRight, footerTop, -0xd5d0ca)
        guiGraphics.fill(panelLeft, panelTop + headerHeight, panelRight, panelTop + headerHeight + 1, -0xe2ddd7)
        guiGraphics.fill(panelLeft, footerTop, panelRight, footerTop + 1, -0xe2ddd7)
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 120, panelTop + 2, -0xb37b01)
        super.drawContents(guiGraphics, mouseX, mouseY, partialTick)
        drawTitle(guiGraphics, mouseX, mouseY, partialTick)
        updateModeAvailability()
        updateHeaderButtonsState()
    }

    private fun getFooterTop(): Int {
        val panelBottom = height - 16
        var footerTop = panelBottom - if (footerTwoRows) 38 else 16
        var buttonTop = Int.MAX_VALUE
        modeButton?.let { buttonTop = min(buttonTop, it.getY()) }
        updateButton?.let { buttonTop = min(buttonTop, it.getY()) }
        configButton?.let { buttonTop = min(buttonTop, it.getY()) }
        serverButton?.let { buttonTop = min(buttonTop, it.getY()) }
        presetButton?.let { buttonTop = min(buttonTop, it.getY()) }
        packButton?.let { buttonTop = min(buttonTop, it.getY()) }
        applyButton?.let { buttonTop = min(buttonTop, it.getY()) }
        backButton?.let { buttonTop = min(buttonTop, it.getY()) }
        if (buttonTop != Int.MAX_VALUE) {
            footerTop = min(footerTop, buttonTop - 2)
        }
        return footerTop
    }

    override fun drawTitle(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.drawString(textRenderer, titleString, 18, 6, 0xFFFFFF, false)
    }

    private fun getListTop(): Int {
        val panelTop = 16
        val headerHeight = 22
        val filterHeight = 20
        val base = panelTop + headerHeight + filterHeight - 8
        return base - 8
    }

    private fun updateModeAvailability() {
        val modChanges = computeModReloadChanges()
        val otherChanges = computeNonModReloadChanges()
        hasModChanges = modChanges
        if (modChanges) {
            applyMode = ApplyMode.RESTART_ONLY
            userSelectedApplyMode = false
        } else if (otherChanges) {
            if (!userSelectedApplyMode) {
                applyMode = ApplyMode.TRY_HOT_RELOAD
            }
        } else if (!userSelectedApplyMode) {
            applyMode = ApplyMode.RESTART_ONLY
        }
        modeButton?.setEnabled(!modChanges)
        modeButton?.setDisplayString(getApplyModeLabel())
        updateBackButtonLabel()
    }

    private fun getBackButtonLabel(): String {
        val hasChanges = computeApplyChanges() || hasPresetChanges()
        return if (hasChanges) StringUtils.translate("dlc_manager.back_discard") else StringUtils.translate("dlc_manager.back")
    }

    private fun updateBackButtonLabel() {
        backButton?.setDisplayString(getBackButtonLabel())
        backButton?.setEnabled(true)
    }

    private fun computeModReloadChanges(): Boolean {
        for (item in allItems) {
            if (item.getType() != DlcType.MOD && !DlcConfigDlcSupport.isConfigType(item)) {
                continue
            }
            if (DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                return true
            }
        }
        return false
    }

    private fun computeNonModReloadChanges(): Boolean {
        for (item in allItems) {
            if (item.getType() == DlcType.MOD || DlcConfigDlcSupport.isConfigType(item)) {
                continue
            }
            if (DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                return true
            }
        }
        return false
    }

    private fun isItemModified(item: DlcItem): Boolean = hasPendingApplyChange(item)

    private fun computeApplyChanges(): Boolean {
        if (DlcPresetRuntimeState.hasPendingFileConflictChanges()) {
            return true
        }
        for (item in allItems) {
            if (hasPendingApplyChange(item)) {
                return true
            }
        }
        return false
    }

    private fun hasPendingApplyChange(item: DlcItem?): Boolean {
        if (item == null || item.getId().isNullOrBlank()) {
            return false
        }
        val state = DlcConfig.getState(item.getId())
        val appliedEnabled = state != null && state.enabled
        return appliedEnabled != item.isEnabled()
    }

    private fun updateListLayout() {
        setListPosition(18, getListTop())
        listWidget?.setSize(browserWidth, browserHeight)
        listWidget?.initGui()
        restoreListScroll()
    }

    fun refreshAfterStateChange() {
        for (item in allItems) {
            DlcRuntimeState.getOverride(item.getId())?.let { item.setEnabled(it) }
        }
        for (item in items) {
            item.setDirty(isItemModified(item))
        }
        updateHeaderButtonsState()
        updateModeAvailability()
        updateListLayout()
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        lastWidth = width
        lastHeight = height
        refreshLayoutForResize()
    }

    private fun refreshLayoutForResize() {
        clearButtons()
        addHeaderButtons()
        addNoticeButton()
        addRemoteButton()
        addRequiredButton()
        addFilterButtons()
        addFooterButtons()
        updateListLayout()
    }

    private fun ensureFooterNoOverlap() {
        val mode = modeButton ?: return
        val apply = applyButton ?: return
        val back = backButton ?: return
        val minGap = 6
        var leftEnd = mode.getX() + mode.getWidth()
        updateButton?.let { leftEnd = max(leftEnd, it.getX() + it.getWidth()) }
        presetButton?.let { leftEnd = max(leftEnd, it.getX() + it.getWidth()) }
        packButton?.let { leftEnd = max(leftEnd, it.getX() + it.getWidth()) }
        configButton?.let { leftEnd = max(leftEnd, it.getX() + it.getWidth()) }
        var rightStart = apply.getX()
        serverButton?.let { rightStart = min(rightStart, it.getX()) }
        if (rightStart - leftEnd < minGap) {
            val row1Y = height - 52
            val row2Y = height - 30
            mode.setY(row1Y)
            updateButton?.setY(row1Y)
            presetButton?.setY(row1Y)
            packButton?.setY(row1Y)
            configButton?.setY(row1Y)
            serverButton?.setY(row2Y)
            apply.setY(row2Y)
            back.setY(row2Y)
            footerTwoRows = true
        }
    }

    private fun tryReloadResources(): CompletableFuture<Void> {
        val minecraft = Minecraft.getInstance()
        return try {
            DlcScreenCompat.addOrUpdatePeriodicNotification(
                minecraft.toasts,
                Component.translatable("dlc_manager.toast.reload_title"),
                Component.translatable("dlc_manager.toast.reload_start"),
            )
            val future = minecraft.reloadResourcePacks()
            future.thenRun {
                minecraft.execute {
                    DlcScreenCompat.addOrUpdatePeriodicNotification(
                        minecraft.toasts,
                        Component.translatable("dlc_manager.toast.reload_title"),
                        Component.translatable("dlc_manager.toast.reload_done"),
                    )
                }
            }
            future
        } catch (_: Throwable) {
            CompletableFuture.completedFuture(null)
        }
    }

    private fun updateBaselineAfterReload() {
        for (item in items) {
            if (item.getType() == DlcType.MOD) {
                continue
            }
            DlcRuntimeState.updateBaseline(item.getId(), item.isEnabled())
        }
    }

    private fun updateResourcePackSelection() {
        val minecraft = Minecraft.getInstance()
        val repo = minecraft.resourcePackRepository
        try {
            repo.reload()
            val selected: MutableSet<String> = LinkedHashSet(repo.selectedIds)
            for (item in items) {
                if (item.getType() != DlcType.RESOURCE_PACK) {
                    continue
                }
                val sourcePath = item.getSourcePath() ?: continue
                val fileName = sourcePath.fileName.toString()
                var packId = resolvePackId(repo, fileName)
                if (item.isEnabled() && packId == null) {
                    val target = minecraft.resourcePackDirectory.resolve(fileName)
                    copyRecursive(sourcePath, target)
                    repo.reload()
                    packId = resolvePackId(repo, fileName)
                }
                if (packId == null) {
                    continue
                }
                if (item.isEnabled()) {
                    selected.add(packId)
                } else {
                    selected.remove(packId)
                }
            }
            repo.setSelected(selected)
            minecraft.options.resourcePacks = ArrayList(selected)
            minecraft.options.save()
        } catch (_: Throwable) {
        }
    }

    private fun resolvePackId(repo: PackRepository, fileName: String): String? {
        val fileId = "file/$fileName"
        if (repo.isAvailable(fileId)) {
            return fileId
        }
        if (repo.isAvailable(fileName)) {
            return fileName
        }
        for (id in repo.availableIds) {
            if (id.endsWith("/$fileName") || id.endsWith(fileName)) {
                return id
            }
        }
        return null
    }

    @Throws(java.io.IOException::class)
    private fun copyRecursive(source: Path, target: Path) {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(
                source,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = source.relativize(dir)
                        Files.createDirectories(target.resolve(relative))
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val relative = source.relativize(file)
                        Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } else {
            Files.createDirectories(target.parent)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private inner class FilterButtonListener(private val filter: DlcType?) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            setFilter(filter)
        }
    }

    private inner class NoticeButtonListener : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            rememberListScroll()
            DlcNoticeManager.forceShow(this@DlcMaLiLibScreen)
        }
    }

    private inner class RemoteButtonListener : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            val missing = DlcRemoteManager.collectMissing()
            if (missing.isEmpty()) {
                return
            }
            rememberListScroll()
            Minecraft.getInstance().setScreen(DlcRemoteDownloadScreen(this@DlcMaLiLibScreen, missing))
        }
    }

    private inner class RequiredButtonListener : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            rememberListScroll()
            Minecraft.getInstance().setScreen(DlcRequiredManagerScreen(this@DlcMaLiLibScreen))
        }
    }

    private inner class HeaderButtonListener(private val enable: Boolean) : IButtonActionListener {
        override fun actionPerformedWithButton(button: ButtonBase, mouseButton: Int) {
            val activeFilterLabel = activeFilter?.let { Component.translatable(it.getDisplayKey()) }
            val message = DlcConfirmUtil.buildHeaderConfirmMessage(enable, activeFilterLabel)
            rememberListScroll()
            Minecraft.getInstance().setScreen(
                ConfirmScreen(
                    { accepted ->
                        Minecraft.getInstance().setScreen(this@DlcMaLiLibScreen)
                        if (!accepted) {
                            return@ConfirmScreen
                        }
                        val actionItems = getActionItems()
                        if (enable) {
                            val targets = actionItems.filter {
                                it.AllowEnableByAll() && !DlcRuntimeState.hasMissingDependencies(it) && !it.isEnabled()
                            }
                            if (targets.isNotEmpty()) {
                                val scopeLabel = if (activeFilter == null) {
                                    Component.translatable("dlc_manager.scope.all")
                                } else {
                                    Component.translatable("dlc_manager.scope.group")
                                }
                                handleBatchEnable(targets, scopeLabel)
                            }
                        } else {
                            disableItemsWithDependents(actionItems)
                            updateModeAvailability()
                        }
                    },
                    Component.translatable("dlc_manager.title"),
                    message,
                    DlcConfirmUtil.confirmYes(),
                    DlcConfirmUtil.confirmNo(),
                )
            )
        }
    }

    private fun updateHeaderButtonsState() {
        val disableButton = disableAllButton ?: return
        val actionItems = getActionItems()
        val any = actionItems.isNotEmpty()
        var anyEligible = false
        var allEligibleEnabled = true
        var allDisabled = true
        for (item in actionItems) {
            if (item.isEnabled()) {
                allDisabled = false
            }
            if (item.AllowEnableByAll() && !DlcRuntimeState.hasMissingDependencies(item)) {
                anyEligible = true
                if (!item.isEnabled()) {
                    allEligibleEnabled = false
                }
            }
            if (!allDisabled && !allEligibleEnabled && anyEligible) {
                break
            }
        }
        val labels = getHeaderLabels()
        val enableButton = enableAllButton
        if (!any) {
            enableButton?.setEnabled(false)
            enableButton?.setDisplayString(labels.enableLabel)
            disableButton.setEnabled(false)
            disableButton.setDisplayString(labels.disableLabel)
            return
        }
        if (enableButton != null) {
            if (!anyEligible) {
                enableButton.setEnabled(false)
                enableButton.setDisplayString(labels.enableLabel)
            } else if (allEligibleEnabled) {
                enableButton.setEnabled(false)
                enableButton.setDisplayString(labels.enableDone)
            } else {
                enableButton.setEnabled(true)
                enableButton.setDisplayString(labels.enableLabel)
            }
        }
        if (allDisabled) {
            disableButton.setEnabled(false)
            disableButton.setDisplayString(labels.disableDone)
        } else {
            disableButton.setEnabled(true)
            disableButton.setDisplayString(labels.disableLabel)
        }
    }

    private fun getActionItems(): List<DlcItem> {
        if (activeFilter == null) {
            return ArrayList(allItems)
        }
        return allItems.filter { it.getType() == activeFilter }
    }

    private fun updateHeaderButtonsLayout() {
        val disableButton = disableAllButton ?: return
        val labels = getHeaderLabels()
        val showEnable = DlcPathConfig.isEnableAllButtonShown()
        val right = width - 18
        val y = 0
        val h = 12
        val gap = 6
        val enableWidth = max(60, maxTextWidth(labels.enableLabel, labels.enableDone) + 4)
        val disableWidth = max(60, maxTextWidth(labels.disableLabel, labels.disableDone) + 4)
        var disableX = right - disableWidth
        var enableX = disableX - gap - enableWidth
        if (showEnable) {
            if (enableX < 18) {
                enableX = 18
                disableX = enableX + enableWidth + gap
            }
        } else {
            enableX = -1000
        }
        enableAllButton?.setX(enableX)
        enableAllButton?.setY(y)
        enableAllButton?.setWidth(enableWidth)
        enableAllButton?.setHeight(h)
        disableButton.setX(disableX)
        disableButton.setY(y)
        disableButton.setWidth(disableWidth)
        disableButton.setHeight(h)
    }

    private fun updateNoticeButtonLayout() {
        val button = noticeButton ?: return
        val label = StringUtils.translate("dlc_manager.notice")
        val buttonWidth = max(36, textRenderer.width(label) + 8)
        val buttonHeight = 12
        var x = 18 + textRenderer.width(titleString) + 6
        val y = 1
        var rightLimit = width - 18
        if (enableAllButton != null) {
            rightLimit = min(rightLimit, enableAllButton!!.getX() - 6)
        } else if (disableAllButton != null) {
            rightLimit = min(rightLimit, disableAllButton!!.getX() - 6)
        }
        if (x + buttonWidth > rightLimit) x = rightLimit - buttonWidth
        if (x < 18) x = 18
        button.setX(x)
        button.setY(y)
        button.setWidth(buttonWidth)
        button.setHeight(buttonHeight)
    }

    private fun updateRemoteButtonLayout() {
        val button = remoteButton ?: return
        val label = StringUtils.translate("dlc_manager.remote.complete")
        val buttonWidth = max(50, textRenderer.width(label) + 8)
        val buttonHeight = 12
        var x = 18 + textRenderer.width(titleString) + 6
        val y = 1
        if (noticeButton != null) {
            x = noticeButton!!.getX() + noticeButton!!.getWidth() + 4
        }
        var rightLimit = width - 18
        if (enableAllButton != null) {
            rightLimit = min(rightLimit, enableAllButton!!.getX() - 6)
        } else if (disableAllButton != null) {
            rightLimit = min(rightLimit, disableAllButton!!.getX() - 6)
        }
        if (x + buttonWidth > rightLimit) x = rightLimit - buttonWidth
        if (x < 18) x = 18
        button.setX(x)
        button.setY(y)
        button.setWidth(buttonWidth)
        button.setHeight(buttonHeight)
    }

    private fun updateRequiredButtonLayout() {
        val button = requiredButton ?: return
        val label = StringUtils.translate("dlc_manager.required.manage_button")
        val buttonWidth = max(50, textRenderer.width(label) + 8)
        val buttonHeight = 12
        var x = 18 + textRenderer.width(titleString) + 6
        val y = 1
        if (remoteButton != null) {
            x = remoteButton!!.getX() + remoteButton!!.getWidth() + 4
        } else if (noticeButton != null) {
            x = noticeButton!!.getX() + noticeButton!!.getWidth() + 4
        }
        var rightLimit = width - 18
        if (enableAllButton != null) {
            rightLimit = min(rightLimit, enableAllButton!!.getX() - 6)
        } else if (disableAllButton != null) {
            rightLimit = min(rightLimit, disableAllButton!!.getX() - 6)
        }
        if (rightLimit - x < buttonWidth) {
            button.setEnabled(false)
            button.setX(-9999)
            button.setY(-9999)
            button.setWidth(0)
            button.setHeight(0)
            return
        }
        button.setEnabled(true)
        button.setDisplayString(label)
        button.setX(x)
        button.setY(y)
        button.setWidth(buttonWidth)
        button.setHeight(buttonHeight)
    }

    private fun handleBatchEnable(targets: List<DlcItem>, scopeLabel: Component) {
        val conflicts = DlcRuntimeState.collectConflicts(targets)
        if (conflicts.getLines().isNotEmpty()) {
            val filteredTargets = filterConflictTargets(targets, conflicts)
            showBatchConflictSkipPrompt(conflicts.getLines()) {
                if (filteredTargets.isEmpty()) {
                    updateModeAvailability()
                    return@showBatchConflictSkipPrompt
                }
                continueBatchEnable(filteredTargets, scopeLabel)
            }
            return
        }
        continueBatchEnable(targets, scopeLabel)
    }

    private fun continueBatchEnable(targets: List<DlcItem>?, scopeLabel: Component) {
        if (targets.isNullOrEmpty()) {
            updateModeAvailability()
            return
        }
        val deps = DlcRuntimeState.collectMissingDependencies(targets)
        if (deps.getLines().isNotEmpty()) {
            showBatchDependencyPrompt(deps.getLines(), deps.getItemsToEnable(), targets, scopeLabel)
            return
        }
        val alarmItems = targets.filter { it.isAlarm() }
        if (alarmItems.isNotEmpty()) {
            rememberListScroll()
            val tip = DlcConfirmUtil.buildBatchAlarmTip(alarmItems)
            Minecraft.getInstance().setScreen(
                DlcAlarmConfirmScreen(this, tip) {
                    enableItems(targets, true)
                    refreshAfterStateChange()
                }
            )
            return
        }
        enableItems(targets, true)
        updateModeAvailability()
    }

    private fun filterConflictTargets(targets: List<DlcItem>?, conflicts: DlcRuntimeState.ConflictBatchResult?): List<DlcItem> {
        if (targets.isNullOrEmpty()) {
            return emptyList()
        }
        val targetIds = HashSet<String>()
        for (item in targets) {
            val identifier = item.getIdentifier()
            if (!identifier.isNullOrBlank()) {
                targetIds.add(identifier)
            }
        }
        if (targetIds.isEmpty()) {
            return emptyList()
        }
        if (conflicts == null || conflicts.getLines().isEmpty()) {
            return targets
        }
        val skipped = HashSet<String>()
        for (line in conflicts.getLines()) {
            val left = line.getLeftName()
            val right = line.getRightName()
            if (targetIds.contains(left)) {
                skipped.add(left)
            }
            if (targetIds.contains(right)) {
                skipped.add(right)
            }
        }
        if (skipped.isEmpty()) {
            return targets
        }
        return targets.filter { item ->
            val identifier = item.getIdentifier()
            identifier == null || !skipped.contains(identifier)
        }
    }

    private fun showBatchConflictSkipPrompt(lines: List<DlcRuntimeState.ConflictLine>, onSkipConfirm: Runnable?) {
        val message = DlcConfirmUtil.buildConflictMessage(lines)
        val title = Component.translatable("dlc_manager.batch.conflicts_title").withStyle(ChatFormatting.DARK_RED)
        rememberListScroll()
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    if (!accepted) {
                        return@ConfirmScreen
                    }
                    onSkipConfirm?.run()
                },
                title,
                message,
                Component.translatable("dlc_manager.batch.conflicts_skip_enable"),
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun enableItems(items: Collection<DlcItem>?, enabled: Boolean) {
        if (items.isNullOrEmpty()) {
            return
        }
        for (item in items) {
            item.setEnabled(enabled)
            DlcRuntimeState.setOverride(item.getId(), enabled)
        }
    }

    private fun disableItemsWithDependents(items: Collection<DlcItem>?) {
        if (items.isNullOrEmpty()) {
            return
        }
        val toDisable = LinkedHashSet<DlcItem>()
        for (item in items) {
            toDisable.addAll(DlcRuntimeState.collectDependentsRecursive(item))
        }
        enableItems(toDisable, false)
        val hiddenDeps = DlcRuntimeState.collectHiddenDependenciesToDisable(toDisable)
        enableItems(hiddenDeps, false)
    }

    private fun showBatchDependencyPrompt(
        lines: List<DlcRuntimeState.DependencyLine>,
        depsToEnable: List<DlcItem>?,
        targets: List<DlcItem>?,
        scopeLabel: Component,
    ) {
        val message = DlcConfirmUtil.buildDependencyMessage(lines)
        val title = Component.translatable("dlc_manager.batch.dependencies_title")
        val enableLabel = Component.translatable("dlc_manager.batch.enable_deps", scopeLabel)
        rememberListScroll()
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    Minecraft.getInstance().setScreen(this)
                    if (!accepted) {
                        return@ConfirmScreen
                    }
                    val planned = ArrayList<DlcItem>()
                    if (depsToEnable != null) {
                        planned.addAll(depsToEnable)
                    }
                    if (targets != null) {
                        planned.addAll(targets)
                    }
                    val conflicts = DlcRuntimeState.collectConflicts(planned)
                    if (conflicts.getLines().isNotEmpty()) {
                        val filteredTargets = filterConflictTargets(planned, conflicts)
                        if (filteredTargets.isEmpty()) {
                            refreshAfterStateChange()
                            return@ConfirmScreen
                        }
                        showBatchConflictSkipPrompt(conflicts.getLines()) {
                            enableItems(filteredTargets, true)
                            refreshAfterStateChange()
                        }
                        return@ConfirmScreen
                    }
                    enableItems(planned, true)
                    refreshAfterStateChange()
                },
                title,
                message,
                enableLabel,
                DlcConfirmUtil.confirmNo(),
            )
        )
    }

    private fun getHeaderLabels(): HeaderLabels {
        if (activeFilter == null) {
            return HeaderLabels(
                StringUtils.translate("dlc_manager.enable_all"),
                StringUtils.translate("dlc_manager.disable_all"),
                StringUtils.translate("dlc_manager.all_enabled"),
                StringUtils.translate("dlc_manager.all_disabled"),
            )
        }
        val typeName = StringUtils.translate(activeFilter!!.getDisplayKey())
        return HeaderLabels(
            StringUtils.translate("dlc_manager.enable_all_type", typeName),
            StringUtils.translate("dlc_manager.disable_all_type", typeName),
            StringUtils.translate("dlc_manager.all_enabled_type", typeName),
            StringUtils.translate("dlc_manager.all_disabled_type", typeName),
        )
    }

    override fun closeGui(closeInGame: Boolean) {
        if (hasPresetChanges()) {
            DlcPresetRuntimeState.restoreEnabledPresets(presetSnapshot)
        }
        DlcRuntimeState.clearOverrides()
        super.closeGui(closeInGame)
    }

    private fun returnToMainMenu() {
        if (hasPresetChanges()) {
            DlcPresetRuntimeState.restoreEnabledPresets(presetSnapshot)
        }
        DlcRuntimeState.clearOverrides()
        Minecraft.getInstance().setScreen(TitleScreen())
    }

    private fun capturePresetSnapshot() {
        if (presetSnapshotCaptured) {
            return
        }
        if (!DlcPathConfig.isPresetFuncOn()) {
            presetSnapshot.clear()
            presetSnapshotCaptured = true
            return
        }
        presetSnapshot.clear()
        presetSnapshot.addAll(DlcPresetRuntimeState.getEnabledPresetIds())
        presetSnapshotCaptured = true
    }

    private fun hasPresetChanges(): Boolean {
        if (!DlcPathConfig.isPresetFuncOn() || !presetSnapshotCaptured) {
            return false
        }
        return DlcPresetRuntimeState.getEnabledPresetIds() != presetSnapshot
    }

    override fun rememberReturnPosition() {
        rememberListScroll()
    }

    fun rememberListScroll() {
        val scrollbar = listWidget?.scrollbar ?: return
        savedScrollOffset = scrollbar.value
        savedScrollFilter = activeFilter
        restoreScrollPending = true
    }

    private fun restoreListScroll() {
        if (!restoreScrollPending) {
            return
        }
        val scrollbar = listWidget?.scrollbar ?: return
        if (savedScrollFilter != activeFilter) {
            restoreScrollPending = false
            return
        }
        scrollbar.value = min(savedScrollOffset, scrollbar.maxValue)
        restoreScrollPending = false
    }

    private fun maxTextWidth(a: String, b: String): Int = max(textRenderer.width(a), textRenderer.width(b))

    private data class HeaderLabels(
        val enableLabel: String,
        val disableLabel: String,
        val enableDone: String,
        val disableDone: String,
    )

    companion object {
        private const val ROOT_GROUP = "__root__"

        @JvmStatic
        fun refreshCurrentOpenScreen() {
            val minecraft = Minecraft.getInstance()
            val screen = minecraft?.screen
            if (screen is DlcMaLiLibScreen) {
                screen.refreshAfterStateChange()
            }
        }

        @JvmStatic
        fun getPendingSuffix(item: DlcItem): String {
            if (!DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled())) {
                return ""
            }
            return if (item.getType() == DlcType.MOD || DlcConfigDlcSupport.isConfigType(item)) {
                StringUtils.translate("dlc_manager.pending.restart")
            } else {
                StringUtils.translate("dlc_manager.pending.reload")
            }
        }
    }
}
