package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerConnection.UpdateResponse
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerUpdateState
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService
import com.cozooo.dlc_manager.client.core.DlcFilePicker
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta
import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class DlcServerUpdateManagerScreen(
    private val parent: Screen?,
    private val entry: DlcServerEntry?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.update.manager_title")) {
    private var importButton: Button? = null
    private var pushUpdateButton: Button? = null
    private var urlButton: Button? = null
    private var packUpdateCheckButton: Button? = null
    private var backButton: Button? = null
    private var scrollIndex = 0
    private var rows: List<DlcUpdateMeta?> = emptyList()
    private var rowActions: List<DlcUpdateRowPlanner.Action?> = emptyList()
    private var rollbackTargets: List<String?> = emptyList()
    private var actionOld: List<String?> = emptyList()
    private var actionNew: List<String?> = emptyList()
    private var displayOld: List<String?> = emptyList()
    private var displayNew: List<String?> = emptyList()
    private val actionButtons: MutableList<Button> = ArrayList()
    private var currentVersion = ""
    private var baseVersion = ""
    private var rollbackFrom: String? = null
    private var rolledBackMode = false
    private var hasVisibleUpdates = false
    private var loading = false
    private var loadFailed = false
    private var applying = false
    private var packUpdateRepoUrl = ""
    private var packModpackUrl = ""

    override fun init() {
        refreshData()
    }

    fun refreshData() {
        loading = true
        loadFailed = false
        applying = false
        rows = emptyList()
        rowActions = emptyList()
        rollbackTargets = emptyList()
        actionOld = emptyList()
        actionNew = emptyList()
        displayOld = emptyList()
        displayNew = emptyList()
        buildWidgets()
        Thread({
            var state: DlcServerUpdateState?
            var shouldCheckPackUpdate = false
            var packUpdateResult: DlcPackReleaseUpdateService.CheckResult? = null
            try {
                state = DlcServerConnection.fetchUpdateState(entry, 8000)
            } catch (_: Exception) {
                state = null
            }
            if (state != null) {
                shouldCheckPackUpdate = PACK_RELEASE_CHECKED_ONCE.compareAndSet(false, true)
            }
            if (state != null &&
                shouldCheckPackUpdate &&
                !state.updateGithubRepo.isNullOrBlank()
            ) {
                packUpdateResult = DlcPackReleaseUpdateService.checkForUpdates(
                    state.modpackUrl,
                    state.updateGithubRepo,
                    state.baseVersion,
                    state.dlcVersion,
                    DlcPackReleaseUpdateService.Side.SERVER,
                )
            }
            val finalState = state
            val finalShouldCheckPackUpdate = shouldCheckPackUpdate
            val finalPackUpdateResult = packUpdateResult
            Minecraft.getInstance().execute {
                loading = false
                if (finalState == null) {
                    loadFailed = true
                    buildWidgets()
                    return@execute
                }
                loadFailed = false
                baseVersion = finalState.baseVersion ?: ""
                currentVersion = finalState.dlcVersion ?: ""
                packUpdateRepoUrl = finalState.updateGithubRepo?.trim() ?: ""
                packModpackUrl = finalState.modpackUrl?.trim() ?: ""
                rollbackFrom = finalState.rollbackFrom
                rolledBackMode = DlcUpdateService.isRollbackMarkerActive(rollbackFrom, baseVersion, currentVersion)
                buildRows(finalState)
                buildWidgets()
                if (finalShouldCheckPackUpdate && finalPackUpdateResult != null && finalPackUpdateResult.hasUpdate()) {
                    Minecraft.getInstance().setScreen(DlcPackReleaseUpdateScreen(this, finalPackUpdateResult, entry))
                }
            }
        }, "dlc-server-update-refresh").start()
    }

    private fun buildRows(state: DlcServerUpdateState) {
        val history = state.history?.filterNotNull()
        val plan = DlcUpdateRowPlanner.planServerRows(history, state.updates, rolledBackMode, baseVersion, currentVersion)
        val list: MutableList<DlcUpdateMeta?> = ArrayList(plan.rows.size)
        val actions: MutableList<DlcUpdateRowPlanner.Action?> = ArrayList(plan.rows.size)
        val targets: MutableList<String?> = ArrayList(plan.rows.size)
        val olds: MutableList<String?> = ArrayList(plan.rows.size)
        val news: MutableList<String?> = ArrayList(plan.rows.size)
        val displayOlds: MutableList<String?> = ArrayList(plan.rows.size)
        val displayNews: MutableList<String?> = ArrayList(plan.rows.size)
        for (row in plan.rows) {
            list.add(row.meta)
            actions.add(row.action)
            targets.add(row.rollbackTarget)
            olds.add(row.oldVersion)
            news.add(row.newVersion)
            displayOlds.add(row.displayOldVersion)
            displayNews.add(row.displayNewVersion)
        }
        hasVisibleUpdates = plan.hasVisibleUpdates
        rows = list
        rowActions = actions
        rollbackTargets = targets
        actionOld = olds
        actionNew = news
        displayOld = displayOlds
        displayNew = displayNews
    }

    private fun buildWidgets() {
        clearWidgets()
        val buttonCount = 5
        val margin = 12
        val spacing = 8
        val availableWidth = max(200, width - margin * 2)
        val buttonWidth = min(110, max(40, (availableWidth - spacing * (buttonCount - 1)) / buttonCount))
        val buttonHeight = 20
        val y = height - 40
        val totalWidth = buttonWidth * buttonCount + spacing * (buttonCount - 1)
        val startX = max(margin, (width - totalWidth) / 2)
        val import = Button.builder(Component.translatable("dlc_manager.update.import")) { importUpdate() }
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build()
        val pushX = startX + buttonWidth + spacing
        val push = Button.builder(Component.translatable("dlc_manager.server.update_push")) { pushUpdates() }
            .bounds(pushX, y, buttonWidth, buttonHeight)
            .build()
        val urlX = pushX + buttonWidth + spacing
        val url = Button.builder(Component.translatable("dlc_manager.update.import_url")) {
            Minecraft.getInstance().setScreen(DlcServerUpdateUrlScreen(this, entry))
        }.bounds(urlX, y, buttonWidth, buttonHeight)
            .build()
        val packCheckX = urlX + buttonWidth + spacing
        val packCheck = Button.builder(Component.translatable("dlc_manager.pack_update.check_button")) { checkPackUpdate() }
            .bounds(packCheckX, y, buttonWidth, buttonHeight)
            .build()
        packCheck.active = packUpdateRepoUrl.isNotBlank()
        val backX = packCheckX + buttonWidth + spacing
        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(backX, y, buttonWidth, buttonHeight)
            .build()
        importButton = import
        pushUpdateButton = push
        urlButton = url
        packUpdateCheckButton = packCheck
        backButton = back
        addRenderableWidget(import)
        addRenderableWidget(push)
        addRenderableWidget(url)
        addRenderableWidget(packCheck)
        addRenderableWidget(back)

        actionButtons.clear()
        for (i in rows.indices) {
            val action = if (i < rowActions.size) rowActions[i] else DlcUpdateRowPlanner.Action.NONE
            val label = when (action) {
                DlcUpdateRowPlanner.Action.REUPDATE -> Component.translatable("dlc_manager.update.reapply")
                DlcUpdateRowPlanner.Action.UPDATE -> Component.translatable("dlc_manager.update.apply")
                else -> Component.translatable("dlc_manager.update.rollback")
            }
            val buttonW = if (action == DlcUpdateRowPlanner.Action.REUPDATE || action == DlcUpdateRowPlanner.Action.UPDATE) 80 else 50
            val actionButton = Button.builder(label) { btn ->
                val idx = actionButtons.indexOf(btn)
                if (idx < 0 || idx >= rowActions.size) {
                    return@builder
                }
                val chosen = rowActions[idx]
                val oldVersion = if (idx < actionOld.size) actionOld[idx] else null
                val newVersion = if (idx < actionNew.size) actionNew[idx] else null
                val rollbackTarget = if (idx < rollbackTargets.size) rollbackTargets[idx] else null
                val meta = if (idx < rows.size) rows[idx] else null
                val baseOld = meta?.getBaseOld()
                val baseNew = meta?.getBaseNew()
                if (chosen == DlcUpdateRowPlanner.Action.ROLLBACK) {
                    confirmRollback(rollbackTarget)
                } else if (chosen == DlcUpdateRowPlanner.Action.REUPDATE) {
                    confirmReupdate(baseOld, oldVersion, baseNew, newVersion)
                } else if (chosen == DlcUpdateRowPlanner.Action.UPDATE) {
                    confirmApply(baseOld, oldVersion, baseNew, newVersion)
                }
            }.bounds(-10000, -10000, buttonW, 16)
                .build()
            actionButton.visible = false
            addRenderableWidget(actionButton)
            actionButtons.add(actionButton)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = 20
        guiGraphics.drawCenteredString(font, title, centerX, y, 0xFFFFFF)
        y += 16
        guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.update.base", baseVersion), centerX, y, 0xC8C8C8)
        y += 14
        guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.update.current", currentVersion), centerX, y, 0xC8C8C8)
        y += 14
        if (rolledBackMode) {
            val currentPair = normalizeVersion(baseVersion) + "+" + normalizeVersion(currentVersion)
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.update.rollback_info", rollbackFrom, currentPair), centerX, y, 0xE6E6E6)
            y += 12
        }
        if (hasVisibleUpdates) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.update.desc"), centerX, y, 0xE6E6E6)
            y += 12
        }
        drawHistoryList(guiGraphics, y, mouseX, mouseY)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun drawHistoryList(guiGraphics: GuiGraphics, startY: Int, mouseX: Int, mouseY: Int) {
        val listTop = startY
        val listBottom = height - 60
        val lineHeight = 20
        val totalLines = rows.size
        val maxVisible = max(1, (listBottom - listTop) / lineHeight)
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        if (loading) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.server.update_checking"), width / 2, (listTop + listBottom) / 2, 0xC8C8C8)
            return
        }
        if (loadFailed) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.server.update_failed"), width / 2, (listTop + listBottom) / 2, 0xFF7777)
            return
        }
        if (rows.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.server.update_empty"), width / 2, (listTop + listBottom) / 2, 0xC8C8C8)
            return
        }
        var y = listTop
        val visibleEnd = min(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until visibleEnd) {
            val meta = rows[i]
            val sourceBase = meta?.getBaseOld()
            val sourceDlc = meta?.getDlcOld()
            var targetBase = meta?.getBaseNew()
            var targetDlc = meta?.getDlcNew()
            if (targetBase.isNullOrBlank()) {
                targetBase = sourceBase
            }
            if (targetDlc.isNullOrBlank()) {
                targetDlc = sourceDlc
            }
            val oldText = if (i < displayOld.size) displayOld[i] else normalizeVersion(sourceBase) + "+" + normalizeVersion(sourceDlc)
            val newText = if (i < displayNew.size) displayNew[i] else normalizeVersion(targetBase) + "+" + normalizeVersion(targetDlc)
            var label = formatUpdateRowLabel(sourceBase, sourceDlc, targetBase, targetDlc, oldText, newText)
            if (i < rowActions.size && rowActions[i] == DlcUpdateRowPlanner.Action.REUPDATE) {
                label += " " + Component.translatable("dlc_manager.update.rolled_back").string
            }
            guiGraphics.drawString(font, label, 24, y + 6, 0xE6E6E6, false)
            val buttonIndex = i
            val buttonWidth = if (buttonIndex < rowActions.size &&
                (rowActions[buttonIndex] == DlcUpdateRowPlanner.Action.REUPDATE || rowActions[buttonIndex] == DlcUpdateRowPlanner.Action.UPDATE)
            ) {
                80
            } else {
                50
            }
            val buttonHeight = 16
            val buttonX = width - 24 - buttonWidth
            val buttonY = y + 2
            if (buttonIndex >= 0 && buttonIndex < actionButtons.size) {
                val actionButton = actionButtons[buttonIndex]
                val action = if (buttonIndex < rowActions.size) rowActions[buttonIndex] else DlcUpdateRowPlanner.Action.NONE
                val enabled = action != DlcUpdateRowPlanner.Action.NONE && !applying
                actionButton.visible = enabled
                actionButton.active = enabled
                if (enabled) {
                    actionButton.x = buttonX
                    actionButton.y = buttonY
                } else {
                    actionButton.x = -10000
                    actionButton.y = -10000
                }
            }
            y += lineHeight
        }
        for (i in actionButtons.indices) {
            if (i < scrollIndex || i >= visibleEnd) {
                val actionButton = actionButtons[i]
                actionButton.visible = false
                actionButton.x = -10000
                actionButton.y = -10000
            }
        }
        renderChangelogTooltip(guiGraphics, listTop, listBottom, lineHeight, totalLines, maxVisible, mouseX, mouseY, rows)
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll)
    }

    private fun formatUpdateRowLabel(
        sourceBase: String?,
        sourceDlc: String?,
        targetBase: String?,
        targetDlc: String?,
        oldText: String?,
        newText: String?,
    ): String {
        val baseChanged = normalizeVersion(sourceBase) != normalizeVersion(targetBase)
        val dlcChanged = normalizeVersion(sourceDlc) != normalizeVersion(targetDlc)
        if (baseChanged && !dlcChanged) {
            return Component.translatable("dlc_manager.update.base_prefix").string + normalizeVersion(sourceBase) + " -> " + normalizeVersion(targetBase)
        }
        if (!baseChanged && dlcChanged) {
            return Component.translatable("dlc_manager.update.dlc_prefix").string + normalizeVersion(sourceDlc) + " -> " + normalizeVersion(targetDlc)
        }
        return "$oldText -> $newText"
    }

    private fun renderChangelogTooltip(
        guiGraphics: GuiGraphics,
        listTop: Int,
        listBottom: Int,
        lineHeight: Int,
        totalLines: Int,
        maxVisible: Int,
        mouseX: Int,
        mouseY: Int,
        rows: List<DlcUpdateMeta?>?,
    ) {
        if (rows.isNullOrEmpty()) {
            return
        }
        if (mouseY < listTop || mouseY > listBottom) {
            return
        }
        val hoverIndex = scrollIndex + max(0, (mouseY - listTop) / lineHeight)
        if (hoverIndex < scrollIndex || hoverIndex >= rows.size || hoverIndex >= scrollIndex + maxVisible) {
            return
        }
        val meta = rows[hoverIndex]
        val lang = CrossPlatformRuntime.languageCode()
        val changelog = meta?.resolveChangelog(lang) ?: ""
        if (changelog.isBlank()) {
            return
        }
        val maxWidth = max(120, width - 60)
        val lines: MutableList<net.minecraft.util.FormattedCharSequence> = ArrayList()
        lines.addAll(font.split(Component.translatable("dlc_manager.update.changelog_tooltip_title"), maxWidth))
        lines.addAll(DlcMarkdownRenderer.wrapMarkdown(font, changelog, maxWidth))
        guiGraphics.renderTooltip(font, lines, mouseX, mouseY)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(deltaY)
    }

    private fun handleMouseScrolled(vertical: Double): Boolean {
        val listTop = 20 + 16 + 14 + if (rolledBackMode) 12 else 0 + if (hasVisibleUpdates) 12 else 0
        val listBottom = height - 60
        val lineHeight = 20
        val totalLines = rows.size
        val maxVisible = max(1, (listBottom - listTop) / lineHeight)
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll > 0) {
            val step = if (vertical > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    private fun confirmRollback(targetVersion: String?) {
        if (targetVersion.isNullOrBlank()) {
            return
        }
        val title = Component.translatable("dlc_manager.update.manager_title")
        val message = Component.translatable("dlc_manager.update.confirm_rollback", targetVersion)
        Minecraft.getInstance().setScreen(
            ConfirmScreen({ accepted ->
                if (accepted) {
                    startRollback(targetVersion)
                } else {
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")),
        )
    }

    private fun confirmReupdate(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val compact = compactTransition(baseOld, dlcOld, baseNew, dlcNew)
        val title = Component.translatable("dlc_manager.update.manager_title")
        val message = Component.translatable("dlc_manager.update.confirm_reapply", compact[0], compact[1])
        Minecraft.getInstance().setScreen(
            ConfirmScreen({ accepted ->
                if (accepted) {
                    startApply(baseOld, dlcOld, baseNew, dlcNew)
                } else {
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")),
        )
    }

    private fun confirmApply(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val title = Component.translatable("dlc_manager.update.manager_title")
        val message = Component.translatable("dlc_manager.update.confirm_apply")
        Minecraft.getInstance().setScreen(
            ConfirmScreen({ accepted ->
                if (accepted) {
                    startApply(baseOld, dlcOld, baseNew, dlcNew)
                } else {
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, Component.translatable("dlc_manager.confirm_yes"), Component.translatable("dlc_manager.confirm_no")),
        )
    }

    private fun startRollback(targetVersion: String) {
        setApplying(true)
        Thread({
            val result: UpdateResponse? = try {
                DlcServerConnection.rollbackUpdate(entry, 8000, targetVersion)
            } catch (_: Exception) {
                null
            }
            Minecraft.getInstance().execute { handleUpdateResult(result) }
        }, "dlc-server-update-rollback").start()
    }

    private fun startApply(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        setApplying(true)
        Thread({
            val result: UpdateResponse? = try {
                DlcServerConnection.applyUpdate(entry, 8000, baseOld, dlcOld, baseNew, dlcNew)
            } catch (_: Exception) {
                null
            }
            Minecraft.getInstance().execute { handleUpdateResult(result) }
        }, "dlc-server-update-apply").start()
    }

    private fun handleUpdateResult(result: UpdateResponse?) {
        setApplying(false)
        if (result == null || !result.ok) {
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.server.update_push_failed")))
            return
        }
        val title = Component.translatable("dlc_manager.update.manager_title")
        val message = Component.translatable("dlc_manager.server.update.restart")
        Minecraft.getInstance().setScreen(
            ConfirmScreen({ accepted ->
                if (accepted) {
                    restartServer()
                } else {
                    refreshData()
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, Component.translatable("dlc_manager.restart_now"), Component.translatable("dlc_manager.restart_later")),
        )
    }

    private fun restartServer() {
        Thread({
            try {
                DlcServerConnection.restartServer(entry, 8000)
            } catch (_: Exception) {
            }
            Minecraft.getInstance().execute {
                refreshData()
                Minecraft.getInstance().setScreen(this)
            }
        }, "dlc-server-restart").start()
    }

    private fun setApplying(value: Boolean) {
        applying = value
        importButton?.active = !value
        urlButton?.active = !value
        pushUpdateButton?.active = !value
        backButton?.active = !value
        for (button in actionButtons) {
            button.active = !value
        }
    }

    private fun importUpdate() {
        if (applying || loading) {
            return
        }
        val zip = DlcFilePicker.pickAnyFile(Component.translatable("dlc_manager.update.import").string)
        if (zip == null) {
            return
        }
        setApplying(true)
        Thread({
            val ok = try {
                DlcServerConnection.importUpdatePackage(entry, 8000, zip)
            } catch (_: Exception) {
                false
            }
            Minecraft.getInstance().execute {
                setApplying(false)
                if (!ok) {
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")))
                } else {
                    refreshData()
                    Minecraft.getInstance().setScreen(this)
                }
            }
        }, "dlc-server-update-import").start()
    }

    private fun checkPackUpdate() {
        if (packUpdateRepoUrl.isBlank()) {
            return
        }
        packUpdateCheckButton?.active = false
        val base = if (baseVersion.isBlank()) "1.0.0" else baseVersion
        val dlc = if (currentVersion.isBlank()) "1.0.0" else currentVersion
        Thread({
            val result: DlcPackReleaseUpdateService.CheckResult? = try {
                DlcPackReleaseUpdateService.checkForUpdates(packModpackUrl, packUpdateRepoUrl, base, dlc, DlcPackReleaseUpdateService.Side.SERVER)
            } catch (_: Exception) {
                null
            }
            Minecraft.getInstance().execute {
                packUpdateCheckButton?.active = packUpdateRepoUrl.isNotBlank()
                if (result != null && result.hasUpdate()) {
                    Minecraft.getInstance().setScreen(DlcPackReleaseUpdateScreen(this, result, entry))
                    return@execute
                }
                if (result != null && !result.error.isNullOrBlank()) {
                    showToast(Component.translatable("dlc_manager.update.manager_title"), DlcPackReleaseUpdateService.toCheckFailureComponent(result.error))
                    return@execute
                }
                showToast(Component.translatable("dlc_manager.update.manager_title"), Component.translatable("dlc_manager.pack_update.no_updates"))
            }
        }, "dlc-server-pack-update-check-manual").start()
    }

    private fun showToast(title: Component, desc: Component) {
        val minecraft = Minecraft.getInstance()
        DlcScreenCompat.addOrUpdatePeriodicNotification(minecraft.toasts, title, desc)
    }

    private fun pushUpdates() {
        if (applying || loading) {
            return
        }
        val updateDir = DlcPathConfig.getDlcRoot().resolve("update")
        if (!Files.isDirectory(updateDir)) {
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.server.update_push_failed")))
            return
        }
        val gameDir = DlcPaths.gameDir()
        if (!updateDir.startsWith(gameDir)) {
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.server.update_push_failed")))
            return
        }
        val relative = gameDir.relativize(updateDir).toString().replace('\\', '/')
        if (relative.isBlank()) {
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")))
            return
        }

        setApplying(true)
        Thread({
            val ok = try {
                DlcServerConnection.push(entry, 20000, emptyList(), listOf(relative), "overwrite")
            } catch (_: IOException) {
                false
            }
            Minecraft.getInstance().execute {
                setApplying(false)
                if (!ok) {
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.server.update_push_failed")))
                } else {
                    refreshData()
                    Minecraft.getInstance().setScreen(this)
                }
            }
        }, "dlc-server-update-push").start()
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int) {
        if (totalLines <= maxVisible) {
            return
        }
        val barX = width - 18
        val barWidth = 6
        val barHeight = max(20, (bottom - top) * maxVisible / max(1, totalLines))
        val trackHeight = bottom - top
        val barY = top + (trackHeight - barHeight) * scrollIndex / max(1, maxScroll)
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF3B3B3B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF9B9B9B.toInt())
    }

    companion object {
        private val PACK_RELEASE_CHECKED_ONCE = AtomicBoolean(false)

        private fun normalizeVersion(value: String?): String {
            return if (value == null || value.isBlank()) "1.0.0" else value.trim()
        }

        private fun compactTransition(oldBase: String?, oldDlc: String?, newBase: String?, newDlc: String?): Array<String> {
            val sourceBase = normalizeVersion(oldBase)
            val sourceDlc = normalizeVersion(oldDlc)
            val targetBase = normalizeVersion(if (newBase == null || newBase.isBlank()) sourceBase else newBase)
            val targetDlc = normalizeVersion(if (newDlc == null || newDlc.isBlank()) sourceDlc else newDlc)
            val baseChanged = sourceBase != targetBase
            val dlcChanged = sourceDlc != targetDlc
            if (baseChanged && dlcChanged) {
                return arrayOf("$sourceBase+$sourceDlc", "$targetBase+$targetDlc")
            }
            if (baseChanged) {
                return arrayOf(sourceBase, targetBase)
            }
            if (dlcChanged) {
                return arrayOf(sourceDlc, targetDlc)
            }
            return arrayOf("$sourceBase+$sourceDlc", "$targetBase+$targetDlc")
        }
    }
}
