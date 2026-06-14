package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService
import com.cozooo.dlc_manager.client.update.DlcPackReleaseUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateFilePicker
import com.cozooo.dlc_manager.client.update.DlcUpdateMeta
import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner
import com.cozooo.dlc_manager.client.update.DlcUpdateRuntime
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateRollbackResult
import com.cozooo.dlc_manager.core.DlcPathConfig
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min

class DlcUpdateManagerScreen(
    private val parent: Screen?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.update.manager_title")) {
    private var importButton: Button? = null
    private var urlButton: Button? = null
    private var selfUpdateButton: Button? = null
    private var backButton: Button? = null
    private var scrollIndex = 0
    private var history: List<DlcUpdateMeta?> = emptyList()
    private val rollbackButtons: MutableList<Button> = ArrayList()
    private var rollbackTargets: List<String?> = emptyList()
    private var rowActions: List<DlcUpdateRowPlanner.Action?> = emptyList()
    private var rowTypes: List<DlcUpdateRowPlanner.Type?> = emptyList()
    private var rowDisplayOld: List<String?> = emptyList()
    private var rowDisplayNew: List<String?> = emptyList()
    private var currentVersion: String? = null
    private var baseVersion: String? = null
    private var rollbackFrom: String? = null
    private var rolledBackMode = false
    private var updateCheck: UpdateCheckResult? = null
    private var hasVisibleUpdates = false
    private var applyingUpdates = false

    override fun init() {
        refreshData()
    }

    fun refreshData() {
        val snapshot = DlcUpdateRuntime.loadClientSnapshot(DlcPaths.dlcRoot())
        currentVersion = snapshot.currentVersion
        baseVersion = snapshot.baseVersion
        rollbackFrom = snapshot.rollbackFrom
        rolledBackMode = snapshot.rolledBackMode
        updateCheck = snapshot.updateCheck
        hasVisibleUpdates = false
        val selfHistory = DlcSelfUpdateService.loadHistory()
        val plan = DlcUpdateRowPlanner.planClientRows(
            snapshot.history,
            selfHistory,
            updateCheck,
            rolledBackMode,
            baseVersion,
            currentVersion,
        )
        val rows: MutableList<DlcUpdateMeta?> = ArrayList(plan.rows.size)
        val targets: MutableList<String?> = ArrayList(plan.rows.size)
        val actions: MutableList<DlcUpdateRowPlanner.Action?> = ArrayList(plan.rows.size)
        val types: MutableList<DlcUpdateRowPlanner.Type?> = ArrayList(plan.rows.size)
        val displayOld: MutableList<String?> = ArrayList(plan.rows.size)
        val displayNew: MutableList<String?> = ArrayList(plan.rows.size)
        for (row in plan.rows) {
            rows.add(row.meta)
            targets.add(row.rollbackTarget)
            actions.add(row.action)
            types.add(row.type)
            displayOld.add(row.displayOldVersion)
            displayNew.add(row.displayNewVersion)
        }
        hasVisibleUpdates = plan.hasVisibleUpdates
        history = rows
        rollbackTargets = targets
        rowActions = actions
        rowTypes = types
        rowDisplayOld = displayOld
        rowDisplayNew = displayNew
        buildWidgets()
    }

    private fun buildWidgets() {
        clearWidgets()
        var buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        val spacing = 10
        val maxWidth = width - 40
        val idealWidth = (maxWidth - spacing * 3) / 4
        if (idealWidth > 80 && idealWidth < buttonWidth) {
            buttonWidth = idealWidth
        }
        val totalWidth = buttonWidth * 4 + spacing * 3
        val startX = max(20, (width - totalWidth) / 2)
        val import = Button.builder(Component.translatable("dlc_manager.update.import")) { importUpdate() }
            .bounds(startX, y, buttonWidth, buttonHeight)
            .build()
        val urlX = startX + buttonWidth + spacing
        val url = Button.builder(Component.translatable("dlc_manager.update.import_url")) {
            Minecraft.getInstance().setScreen(DlcUpdateUrlScreen(this))
        }.bounds(urlX, y, buttonWidth, buttonHeight).build()
        val selfX = urlX + buttonWidth + spacing
        val self = Button.builder(Component.translatable("dlc_manager.pack_update.check_button")) { checkPackUpdate() }
            .bounds(selfX, y, buttonWidth, buttonHeight).build()
        self.active = hasPackUpdateRepoConfigured()
        val backX = selfX + buttonWidth + spacing
        val back = Button.builder(Component.translatable("gui.back")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(backX, y, buttonWidth, buttonHeight)
            .build()
        importButton = import
        urlButton = url
        selfUpdateButton = self
        backButton = back
        addRenderableWidget(import)
        addRenderableWidget(url)
        addRenderableWidget(self)
        addRenderableWidget(back)
        rollbackButtons.clear()
        for (i in history.indices) {
            val action = if (i < rowActions.size) rowActions[i] else DlcUpdateRowPlanner.Action.NONE
            val label = when (action) {
                DlcUpdateRowPlanner.Action.MANAGER_ROLLBACK -> Component.translatable("dlc_manager.update.rollback")
                DlcUpdateRowPlanner.Action.REUPDATE -> Component.translatable("dlc_manager.update.reapply")
                DlcUpdateRowPlanner.Action.UPDATE -> Component.translatable("dlc_manager.update.apply")
                else -> Component.translatable("dlc_manager.update.rollback")
            }
            val buttonW = if (action == DlcUpdateRowPlanner.Action.REUPDATE || action == DlcUpdateRowPlanner.Action.UPDATE) 80 else 50
            val rollback = Button.builder(label) { btn ->
                val idx = rollbackButtons.indexOf(btn)
                if (idx >= 0 && idx < rowActions.size) {
                    when (rowActions[idx]) {
                        DlcUpdateRowPlanner.Action.MANAGER_ROLLBACK -> {
                            val target = rollbackTargets[idx]
                            if (!target.isNullOrBlank()) {
                                confirmManagerRollback(target)
                            }
                        }
                        DlcUpdateRowPlanner.Action.ROLLBACK -> {
                            val target = rollbackTargets[idx]
                            if (!target.isNullOrBlank()) {
                                confirmRollback(target)
                            }
                        }
                        DlcUpdateRowPlanner.Action.REUPDATE -> {
                            val meta = if (idx < history.size) history[idx] else null
                            if (meta != null) {
                                confirmReupdate(meta.getBaseOld(), meta.getDlcOld(), meta.getBaseNew(), meta.getDlcNew())
                            }
                        }
                        DlcUpdateRowPlanner.Action.UPDATE -> {
                            val meta = if (idx < history.size) history[idx] else null
                            if (meta != null) {
                                confirmApply(meta.getBaseOld(), meta.getDlcOld(), meta.getBaseNew(), meta.getDlcNew())
                            }
                        }
                        else -> Unit
                    }
                }
            }.bounds(-10000, -10000, buttonW, 16).build()
            rollback.visible = false
            addRenderableWidget(rollback)
            rollbackButtons.add(rollback)
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
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("dlc_manager.update.rollback_info", rollbackFrom, currentPair),
                centerX,
                y,
                0xE6E6E6,
            )
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
        val totalLines = history.size
        val maxVisible = max(1, (listBottom - listTop) / lineHeight)
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        var y = listTop
        val visibleEnd = min(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until visibleEnd) {
            val meta = history[i]
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
            val oldDisplay = if (i < rowDisplayOld.size) rowDisplayOld[i] else normalizeVersion(sourceBase) + "+" + normalizeVersion(sourceDlc)
            val newDisplay = if (i < rowDisplayNew.size) rowDisplayNew[i] else normalizeVersion(targetBase) + "+" + normalizeVersion(targetDlc)
            var label = formatUpdateRowLabel(sourceBase, sourceDlc, targetBase, targetDlc, oldDisplay, newDisplay)
            val type = if (i < rowTypes.size) rowTypes[i] else DlcUpdateRowPlanner.Type.UPDATE
            if (type == DlcUpdateRowPlanner.Type.MANAGER) {
                val dlcPrefix = Component.translatable("dlc_manager.update.dlc_prefix").string
                if (label.startsWith(dlcPrefix)) {
                    label = label.substring(dlcPrefix.length)
                }
                label = Component.translatable("dlc_manager.update.manager_prefix").string + label
            }
            if (i < rowActions.size && rowActions[i] == DlcUpdateRowPlanner.Action.REUPDATE) {
                label += " " + Component.translatable("dlc_manager.update.rolled_back").string
            }
            guiGraphics.drawString(font, label, 24, y + 6, 0xE6E6E6, false)
            val buttonIndex = i
            val buttonWidth = if (
                buttonIndex < rowActions.size &&
                (rowActions[buttonIndex] == DlcUpdateRowPlanner.Action.REUPDATE || rowActions[buttonIndex] == DlcUpdateRowPlanner.Action.UPDATE)
            ) 80 else 50
            val buttonX = width - 24 - buttonWidth
            val buttonY = y + 2
            if (buttonIndex >= 0 && buttonIndex < rollbackButtons.size) {
                val rollback = rollbackButtons[buttonIndex]
                val action = if (buttonIndex < rowActions.size) rowActions[buttonIndex] else DlcUpdateRowPlanner.Action.NONE
                val enabled = action != DlcUpdateRowPlanner.Action.NONE
                rollback.visible = enabled
                rollback.active = enabled
                if (enabled) {
                    rollback.x = buttonX
                    rollback.y = buttonY
                } else {
                    rollback.x = -10000
                    rollback.y = -10000
                }
            }
            y += lineHeight
        }
        for (i in rollbackButtons.indices) {
            if (i < scrollIndex || i >= visibleEnd) {
                val rollback = rollbackButtons[i]
                rollback.visible = false
                rollback.x = -10000
                rollback.y = -10000
            }
        }
        renderChangelogTooltip(guiGraphics, listTop, listBottom, lineHeight, maxVisible, mouseX, mouseY, history)
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll)
    }

    private fun formatUpdateRowLabel(
        sourceBase: String?,
        sourceDlc: String?,
        targetBase: String?,
        targetDlc: String?,
        oldDisplay: String?,
        newDisplay: String?,
    ): String {
        val baseChanged = normalizeVersion(sourceBase) != normalizeVersion(targetBase)
        val dlcChanged = normalizeVersion(sourceDlc) != normalizeVersion(targetDlc)
        if (baseChanged && !dlcChanged) {
            return Component.translatable("dlc_manager.update.base_prefix").string +
                normalizeVersion(sourceBase) + " -> " + normalizeVersion(targetBase)
        }
        if (!baseChanged && dlcChanged) {
            return Component.translatable("dlc_manager.update.dlc_prefix").string +
                normalizeVersion(sourceDlc) + " -> " + normalizeVersion(targetDlc)
        }
        return "$oldDisplay -> $newDisplay"
    }

    private fun renderChangelogTooltip(
        guiGraphics: GuiGraphics,
        listTop: Int,
        listBottom: Int,
        lineHeight: Int,
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
        val lines: MutableList<FormattedCharSequence> = ArrayList()
        lines.addAll(font.split(Component.translatable("dlc_manager.update.changelog_tooltip_title"), maxWidth))
        lines.addAll(DlcMarkdownRenderer.wrapMarkdown(font, changelog, maxWidth))
        guiGraphics.renderTooltip(font, lines, mouseX, mouseY)
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(deltaY)
    }

    private fun handleMouseScrolled(vertical: Double): Boolean {
        val listTop = 20 + 16 + 14 +
            (if (rolledBackMode) 12 else 0) +
            (if (hasVisibleUpdates) 12 else 0)
        val listBottom = height - 60
        val lineHeight = 20
        val totalLines = history.size
        val maxVisible = max(1, (listBottom - listTop) / lineHeight)
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll > 0) {
            val step = if (vertical > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun startRollback(targetVersion: String?) {
        if (targetVersion.isNullOrBlank()) {
            return
        }
        importButton?.active = false
        backButton?.active = false
        Thread({
            val result: UpdateRollbackResult = DlcUpdateService.rollbackTo(targetVersion)
            Minecraft.getInstance().execute {
                if (!result.isSuccess) {
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.rollback_failed")))
                } else {
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, true, Component.translatable("dlc_manager.update.rollback_success")))
                }
            }
        }, "dlc-update-rollback").start()
    }

    private fun startManagerRollback(targetVersion: String?) {
        if (targetVersion.isNullOrBlank()) {
            return
        }
        setApplyingUpdates(true)
        Thread({
            val ok = DlcSelfUpdateService.rollbackToPrevious()
            Minecraft.getInstance().execute {
                setApplyingUpdates(false)
                if (!ok) {
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.rollback_failed")))
                } else {
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, true, Component.translatable("dlc_manager.update.rollback_success")))
                }
            }
        }, "dlc-selfupdate-rollback").start()
    }

    private fun confirmRollback(targetVersion: String) {
        val title = Component.translatable("dlc_manager.update.manager_title")
        val message = Component.translatable("dlc_manager.update.confirm_rollback", targetVersion)
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    if (accepted) {
                        startRollback(targetVersion)
                    } else {
                        Minecraft.getInstance().setScreen(this)
                    }
                },
                title,
                message,
                Component.translatable("dlc_manager.confirm_yes"),
                Component.translatable("dlc_manager.confirm_no"),
            ),
        )
    }

    private fun confirmManagerRollback(targetVersion: String) {
        val title = Component.translatable("dlc_manager.update.manager_title")
        val message = Component.translatable("dlc_manager.update.confirm_rollback", targetVersion)
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    if (accepted) {
                        startManagerRollback(targetVersion)
                    } else {
                        Minecraft.getInstance().setScreen(this)
                    }
                },
                title,
                message,
                Component.translatable("dlc_manager.confirm_yes"),
                Component.translatable("dlc_manager.confirm_no"),
            ),
        )
    }

    private fun startReupdate(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true)
        if (chain.isEmpty()) {
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")))
            return
        }
        setApplyingUpdates(true)
        Thread({
            val result: UpdateApplyResult = DlcUpdateService.applyUpdates(chain, false)
            Minecraft.getInstance().execute { handleReupdateResult(result, null) }
        }, "dlc-update-reapply").start()
    }

    private fun startApply(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val chain = DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true)
        if (chain.isEmpty()) {
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, Component.translatable("dlc_manager.update.failed")))
            return
        }
        setApplyingUpdates(true)
        Thread({
            val result: UpdateApplyResult = DlcUpdateService.applyUpdates(chain, false)
            Minecraft.getInstance().execute { handleReupdateResult(result, null) }
        }, "dlc-update-apply").start()
    }

    private fun confirmReupdate(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val changelog = DlcUpdateService.buildChangelogText(
            DlcUpdateService.resolveUpdateChainTo(baseOld, dlcOld, baseNew, dlcNew, true),
            CrossPlatformRuntime.languageCode(),
        )
        confirmReupdateNow(baseOld, dlcOld, baseNew, dlcNew, changelog)
    }

    private fun confirmApply(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?) {
        val title = Component.translatable("dlc_manager.update.manager_title")
        val message = Component.translatable("dlc_manager.update.confirm_apply")
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    if (accepted) {
                        startApply(baseOld, dlcOld, baseNew, dlcNew)
                    } else {
                        Minecraft.getInstance().setScreen(this)
                    }
                },
                title,
                message,
                Component.translatable("dlc_manager.confirm_yes"),
                Component.translatable("dlc_manager.confirm_no"),
            ),
        )
    }

    private fun confirmReupdateNow(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?, changelog: String?) {
        val title = Component.translatable("dlc_manager.update.manager_title")
        val message = buildReupdateMessage(baseOld, dlcOld, baseNew, dlcNew, changelog)
        Minecraft.getInstance().setScreen(
            ConfirmScreen(
                { accepted ->
                    if (accepted) {
                        startReupdate(baseOld, dlcOld, baseNew, dlcNew)
                    } else {
                        Minecraft.getInstance().setScreen(this)
                    }
                },
                title,
                message,
                Component.translatable("dlc_manager.confirm_yes"),
                Component.translatable("dlc_manager.confirm_no"),
            ),
        )
    }

    private fun buildReupdateMessage(baseOld: String?, dlcOld: String?, baseNew: String?, dlcNew: String?, changelog: String?): Component {
        val compact = compactTransition(baseOld, dlcOld, baseNew, dlcNew)
        val base = Component.translatable("dlc_manager.update.confirm_reapply", compact[0], compact[1])
        if (changelog.isNullOrBlank()) {
            return base
        }
        return Component.empty()
            .append(base)
            .append("\n\n")
            .append(Component.translatable("dlc_manager.update.changelog_tooltip_title"))
            .append("\n")
            .append(DlcMarkdownRenderer.renderMarkdownComponent(changelog))
    }

    private fun handleReupdateResult(result: UpdateApplyResult, check: UpdateCheckResult?) {
        setApplyingUpdates(false)
        if (result.isMissing) {
            val missingCheck = check ?: DlcUpdateService.checkForUpdates(true)
            Minecraft.getInstance().setScreen(DlcUpdateMissingScreen(this, missingCheck, result.missingPaths))
            return
        }
        if (!result.isSuccess) {
            val component = DlcUpdateService.toFailureComponent(result)
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, false, component))
            return
        }
        if (tryShowPostUpdateDownloads(result)) {
            return
        }
        val message = if (result.isHelperScheduled) {
            Component.translatable("dlc_manager.update.deferred_restart")
        } else {
            Component.translatable("dlc_manager.update.success")
        }
        Minecraft.getInstance().setScreen(DlcUpdateResultScreen(this, true, message))
    }

    private fun tryShowPostUpdateDownloads(result: UpdateApplyResult?): Boolean {
        if (result == null || !result.isSuccess) {
            return false
        }
        if (result.hasRequiredAdd()) {
            val missing: List<DlcRequiredEntry> = DlcRequiredManager.collectMissingDownloadsForPrompt()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().setScreen(DlcRequiredDownloadScreen(this, missing, result))
                return true
            }
        }
        if (result.hasRemoteAdd()) {
            val missing: List<DlcRemoteEntry> = DlcRemoteManager.collectMissing()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().setScreen(DlcRemoteDownloadScreen(createSuccessResultScreen(result), missing))
                return true
            }
            return false
        }
        return false
    }

    private fun createSuccessResultScreen(result: UpdateApplyResult?): Screen {
        val message = if (result != null && result.isHelperScheduled) {
            Component.translatable("dlc_manager.update.deferred_restart")
        } else {
            Component.translatable("dlc_manager.update.success")
        }
        return DlcUpdateResultScreen(this, true, message)
    }

    private fun setApplyingUpdates(applying: Boolean) {
        applyingUpdates = applying
        importButton?.active = !applying
        urlButton?.active = !applying
        backButton?.active = !applying
        for (button in rollbackButtons) {
            button.active = !applying
        }
    }

    override fun shouldCloseOnEsc(): Boolean {
        return !applyingUpdates
    }

    override fun onClose() {
        if (!applyingUpdates) {
            super.onClose()
        }
    }

    private fun importUpdate() {
        val picked: Path = DlcUpdateFilePicker.pickUpdateZip("Import Update") ?: return
        val validation = DlcUpdateService.validateImportPackage(picked)
        if (!validation.isValid) {
            showToast(
                Component.translatable("dlc_manager.update.manager_title"),
                DlcUpdateService.toImportValidationComponent(validation),
            )
            return
        }
        if (!DlcUpdateService.importUpdatePackage(picked)) {
            showToast(
                Component.translatable("dlc_manager.update.manager_title"),
                Component.translatable("dlc_manager.update.dlc_root_missing"),
            )
            return
        }
        promptImportedUpdate()
    }

    private fun promptImportedUpdate() {
        val check = DlcUpdateService.checkForUpdates(true)
        if (check.hasUpdates()) {
            Minecraft.getInstance().setScreen(DlcUpdatePromptScreen(this, check))
            return
        }
        showToast(
            Component.translatable("dlc_manager.update.manager_title"),
            Component.translatable("dlc_manager.update.no_updates"),
        )
        refreshData()
    }

    private fun checkPackUpdate() {
        val repoUrl = DlcPathConfig.getUpdateGithubRepoUrl()
        if (repoUrl.isNullOrBlank()) {
            return
        }
        selfUpdateButton?.active = false
        val base = if (baseVersion.isNullOrBlank()) "1.0.0" else baseVersion!!
        val dlc = if (currentVersion.isNullOrBlank()) "1.0.0" else currentVersion!!
        Thread({
            val result = try {
                DlcPackReleaseUpdateService.checkForUpdates(
                    DlcPathConfig.getModpackUrl(),
                    repoUrl,
                    base,
                    dlc,
                    DlcPackReleaseUpdateService.Side.CLIENT,
                )
            } catch (_: Exception) {
                null
            }
            Minecraft.getInstance().execute {
                selfUpdateButton?.active = true
                if (result != null && result.hasUpdate()) {
                    Minecraft.getInstance().setScreen(DlcPackReleaseUpdateScreen(this, result))
                    return@execute
                }
                if (result != null && !result.error.isNullOrBlank()) {
                    showToast(
                        Component.translatable("dlc_manager.update.manager_title"),
                        DlcPackReleaseUpdateService.toCheckFailureComponent(result.error),
                    )
                    return@execute
                }
                showToast(
                    Component.translatable("dlc_manager.update.manager_title"),
                    Component.translatable("dlc_manager.pack_update.no_updates"),
                )
            }
        }, "dlc-pack-update-check-manual").start()
    }

    private fun hasPackUpdateRepoConfigured(): Boolean {
        val repoUrl = DlcPathConfig.getUpdateGithubRepoUrl()
        return !repoUrl.isNullOrBlank()
    }

    private fun showToast(title: Component, desc: Component) {
        val minecraft = Minecraft.getInstance()
        DlcScreenCompat.addOrUpdatePeriodicNotification(minecraft.toasts, title, desc)
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int) {
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return
        }
        val barX = width - 12
        val barWidth = 4
        val barHeight = max(12, (bottom - top) * maxVisible / totalLines)
        val trackHeight = bottom - top
        val maxTrack = max(1, trackHeight - barHeight)
        val barY = top + ((scrollIndex / maxScroll.toFloat()) * maxTrack).toInt()
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF2B2B2B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF6B6B6B.toInt())
    }

    companion object {
        private fun normalizeVersion(value: String?): String {
            return if (value.isNullOrBlank()) "1.0.0" else value.trim()
        }

        private fun compactTransition(oldBase: String?, oldDlc: String?, newBase: String?, newDlc: String?): Array<String> {
            val sourceBase = normalizeVersion(oldBase)
            val sourceDlc = normalizeVersion(oldDlc)
            val targetBase = normalizeVersion(if (newBase.isNullOrBlank()) sourceBase else newBase)
            val targetDlc = normalizeVersion(if (newDlc.isNullOrBlank()) sourceDlc else newDlc)
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
