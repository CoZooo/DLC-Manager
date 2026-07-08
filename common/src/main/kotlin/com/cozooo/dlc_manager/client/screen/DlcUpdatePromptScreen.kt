package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard
import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredEntry
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter
import com.cozooo.dlc_manager.client.update.DlcUpdatePackage
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult
import com.cozooo.dlc_manager.core.DlcPaths
import com.cozooo.dlc_manager.platform.DlcPlatformServices
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DlcUpdatePromptScreen @JvmOverloads constructor(
    private val parent: Screen?,
    private val result: UpdateCheckResult?,
    private val allowSkipThisUpdate: Boolean = false,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.update.title")) {
    private val forceUpdate = hasHiddenUpdates(result)
    private var applyButton: Button? = null
    private var skipThisUpdateButton: Button? = null
    private var cancelButton: Button? = null
    private var scrollIndex = 0
    private var applying = false
    private var statusText: String? = null
    private var progress = -1f

    override fun init() {
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        if (forceUpdate) {
            val apply = Button.builder(Component.translatable("dlc_manager.update.apply")) { confirmApply() }
                .bounds(width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight)
                .build()
            applyButton = apply
            skipThisUpdateButton = null
            cancelButton = null
            addRenderableWidget(apply)
        } else if (allowSkipThisUpdate) {
            val spacing = 6
            val skipButtonWidth = 108
            val totalWidth = buttonWidth + skipButtonWidth + buttonWidth + spacing * 2
            val startX = width / 2 - totalWidth / 2
            val apply = Button.builder(Component.translatable("dlc_manager.update.apply")) { confirmApply() }
                .bounds(startX, y, buttonWidth, buttonHeight)
                .build()
            val skip = Button.builder(Component.translatable("dlc_manager.self_update.skip_this_update")) { skipThisUpdate() }
                .bounds(startX + buttonWidth + spacing, y, skipButtonWidth, buttonHeight)
                .build()
            val cancel = Button.builder(Component.translatable("dlc_manager.update.cancel")) { Minecraft.getInstance().setScreen(parent) }
                .bounds(startX + buttonWidth + spacing + skipButtonWidth + spacing, y, buttonWidth, buttonHeight)
                .build()
            applyButton = apply
            skipThisUpdateButton = skip
            cancelButton = cancel
            addRenderableWidget(apply)
            addRenderableWidget(skip)
            addRenderableWidget(cancel)
        } else {
            val apply = Button.builder(Component.translatable("dlc_manager.update.apply")) { confirmApply() }
                .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
                .build()
            val cancel = Button.builder(Component.translatable("dlc_manager.update.cancel")) { Minecraft.getInstance().setScreen(parent) }
                .bounds(width / 2 + 6, y, buttonWidth, buttonHeight)
                .build()
            applyButton = apply
            cancelButton = cancel
            skipThisUpdateButton = null
            addRenderableWidget(apply)
            addRenderableWidget(cancel)
        }
    }

    private fun skipThisUpdate() {
        if (result != null && result.hasUpdates()) {
            DlcUpdateService.skipPromptForUpdate(result)
        }
        Minecraft.getInstance().setScreen(parent)
    }

    private fun confirmApply() {
        val title = Component.translatable("dlc_manager.update.title")
        val message = DlcConfirmUtil.buildUpdateConfirmApplyMessage()
        Minecraft.getInstance().setScreen(
            ConfirmScreen({ accepted ->
                if (accepted) {
                    DlcUpdatePackGuard.confirmBeforeUpdate(this, result?.updates) { packCheck, uninstallIncompatible ->
                        startApply(false, packCheck, uninstallIncompatible)
                    }
                } else {
                    Minecraft.getInstance().setScreen(this)
                }
            }, title, message, DlcConfirmUtil.confirmYes(), DlcConfirmUtil.confirmNo()),
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        applyButton?.active = !applying
        cancelButton?.active = !applying
        skipThisUpdateButton?.active = !applying
        val centerX = width / 2
        var y = 20
        val titleX = (width - font.width(title)) / 2
        guiGraphics.drawString(font, title, titleX, y, 0xFFFFFF, false)
        y += 16
        val currentVersion = if (result?.current == null) {
            formatVersionPair("", "")
        } else {
            formatVersionPair(result.current.getBase(), result.current.getDlc())
        }
        guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.update.current_full", currentVersion), centerX, y, 0xC8C8C8)
        y += 14
        if (applying) {
            val text = statusText ?: ""
            guiGraphics.drawCenteredString(font, text, centerX, y, 0xE6E6E6)
            y += 12
            if (progress >= 0f) {
                drawProgressBar(guiGraphics, centerX - 120, y, 240, 8, progress)
                val percent = (max(0f, min(1f, progress)) * 100f).roundToInt()
                guiGraphics.drawCenteredString(font, "$percent%", centerX, y + 12, 0xC8C8C8)
                y += 24
            }
        } else {
            guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.update.desc"), centerX, y, 0xE6E6E6)
            y += 12
        }
        drawUpdateList(guiGraphics, y)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun drawUpdateList(guiGraphics: GuiGraphics, startY: Int) {
        val lines = buildUpdateLines()
        val listTop = startY
        val listBottom = height - 70
        val lineHeight = 12
        val maxVisible = max(1, (listBottom - listTop) / lineHeight)
        val totalLines = lines.size
        val maxScroll = max(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        var y = listTop
        val end = min(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until end) {
            guiGraphics.drawString(font, lines[i], 24, y, 0xE6E6E6, false)
            y += lineHeight
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll)
    }

    private fun buildUpdateLines(): MutableList<FormattedCharSequence> {
        val lines: MutableList<FormattedCharSequence> = ArrayList()
        val updates = result?.updates ?: emptyList()
        if (updates.isEmpty()) {
            return lines
        }
        val lang = DlcPlatformServices.languageCode()
        val maxWidth = max(120, width - 56)
        for (pkg in updates) {
            val updateLine = "- ${formatUpdateLine(pkg)}"
            lines.addAll(font.split(Component.literal(updateLine), maxWidth))
            var changelog = ""
            if (pkg?.getManifest() != null) {
                changelog = pkg.getManifest().resolveChangelog(lang)
            }
            if (changelog.isNotBlank()) {
                lines.addAll(font.split(Component.translatable("dlc_manager.update.changelog_tooltip_title"), maxWidth))
                lines.addAll(DlcMarkdownRenderer.wrapMarkdown(font, changelog, maxWidth))
            }
            lines.add(FormattedCharSequence.EMPTY)
        }
        return lines
    }

    private fun formatUpdateLine(pkg: DlcUpdatePackage?): String {
        if (pkg?.getManifest() == null || pkg.getManifest().getVersion() == null) {
            return ""
        }
        val version = pkg.getManifest().getVersion() ?: return ""
        val oldBase = normalizeVersion(version.getBase())
        val oldDlc = normalizeVersion(version.getDlcOld())
        var newBase = normalizeVersion(version.getBaseNew())
        var newDlc = normalizeVersion(version.getDlcNew())
        if (newBase.isBlank()) {
            newBase = oldBase
        }
        if (newDlc.isBlank()) {
            newDlc = oldDlc
        }
        val compact = compactTransition(oldBase, oldDlc, newBase, newDlc)
        val from = compact[0]
        val to = compact[1]
        if (from == to) {
            return from
        }
        return "$from -> $to"
    }

    private fun formatVersionPair(base: String?, dlc: String?): String {
        return normalizeVersion(base) + "+" + normalizeVersion(dlc)
    }

    private fun compactTransition(oldBase: String?, oldDlc: String?, newBase: String?, newDlc: String?): Array<String> {
        val sourceBase = normalizeVersion(oldBase)
        val sourceDlc = normalizeVersion(oldDlc)
        val targetBase = normalizeVersion(newBase)
        val targetDlc = normalizeVersion(newDlc)
        val baseChanged = sourceBase != targetBase
        val dlcChanged = sourceDlc != targetDlc
        if (baseChanged && dlcChanged) {
            return arrayOf(formatVersionPair(sourceBase, sourceDlc), formatVersionPair(targetBase, targetDlc))
        }
        if (baseChanged) {
            return arrayOf(sourceBase, targetBase)
        }
        if (dlcChanged) {
            return arrayOf(sourceDlc, targetDlc)
        }
        return arrayOf(formatVersionPair(sourceBase, sourceDlc), formatVersionPair(targetBase, targetDlc))
    }

    private fun normalizeVersion(value: String?): String {
        return if (value == null || value.isBlank()) "1.0.0" else value.trim()
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
        val barY = top + (scrollIndex / maxScroll.toFloat() * maxTrack).toInt()
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF2B2B2B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF6B6B6B.toInt())
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        return handleMouseScrolled(deltaY)
    }

    private fun handleMouseScrolled(deltaY: Double): Boolean {
        val listTop = 20 + 16 + 14 + 12
        val listBottom = height - 70
        val lineHeight = 12
        val totalLines = buildUpdateLines().size
        val maxVisible = max(1, (listBottom - listTop) / lineHeight)
        val maxScroll = max(0, totalLines - maxVisible)
        if (maxScroll > 0) {
            val step = if (deltaY > 0) -1 else 1
            scrollIndex = max(0, min(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    override fun shouldCloseOnEsc(): Boolean {
        return !forceUpdate && !applying
    }

    override fun onClose() {
        if (!applying) {
            super.onClose()
        }
    }

    private fun startApply(
        forceMissing: Boolean,
        packCheck: DlcPackUpdateGuard.CheckResult?,
        uninstallIncompatible: Boolean,
    ) {
        if (applying) {
            return
        }
        applying = true
        statusText = Component.translatable("dlc_manager.update.applying").string
        progress = -1f
        applyButton?.active = false
        cancelButton?.active = false
        skipThisUpdateButton?.active = false
        Thread({
            var packSession: DlcPackUpdateCoordinator.UpdateSession? = null
            try {
                val coordinator = DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible)
                val applyResult = DlcUpdateService.applyUpdates(result?.updates, forceMissing) { status, value ->
                    Minecraft.getInstance().execute {
                        statusText = status
                        progress = value
                    }
                }
                if (applyResult != null && applyResult.isSuccess) {
                    packSession.finishAfterSuccessfulUpdate()
                } else {
                    packSession.restoreAfterFailedUpdate()
                }
                Minecraft.getInstance().execute { handleApplyResult(applyResult) }
            } catch (ex: Exception) {
                val session = packSession
                if (session != null) {
                    try {
                        session.restoreAfterFailedUpdate()
                    } catch (restoreError: Exception) {
                        ex.addSuppressed(restoreError)
                    }
                }
                val message = ex.message ?: "Failed to apply update"
                Minecraft.getInstance().execute {
                    applying = false
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(parent, false, Component.literal(message)))
                }
            }
        }, "dlc-update-apply").start()
    }

    private fun drawProgressBar(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, value: Float) {
        val clamped = max(0f, min(1f, value))
        val filled = (width * clamped).toInt()
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B.toInt())
        guiGraphics.fill(x, y, x + filled, y + height, 0xFF4CAF50.toInt())
    }

    private fun handleApplyResult(result: UpdateApplyResult?) {
        if (result != null && result.isMissing) {
            val updateResult = this.result ?: return
            Minecraft.getInstance().setScreen(DlcUpdateMissingScreen(parent, updateResult, result.missingPaths))
            return
        }
        if (result == null || !result.isSuccess) {
            val component = DlcMessageAdapter.toFailureComponent(result)
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(parent, false, component))
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
        Minecraft.getInstance().setScreen(DlcUpdateResultScreen(parent, true, message))
    }

    private fun tryShowPostUpdateDownloads(result: UpdateApplyResult?): Boolean {
        if (result == null || !result.isSuccess) {
            return false
        }
        if (result.hasRequiredAdd()) {
            val missing: List<DlcRequiredEntry> = DlcRequiredManager.collectMissingDownloadsForPrompt()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().setScreen(DlcRequiredDownloadScreen(parent, missing, result))
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
        return DlcUpdateResultScreen(parent, true, message)
    }

    companion object {
        private fun hasHiddenUpdates(result: UpdateCheckResult?): Boolean {
            if (result?.updates == null) {
                return false
            }
            for (pkg in result.updates) {
                if (pkg?.getManifest() != null && pkg.getManifest().isHidden()) {
                    return true
                }
            }
            return false
        }
    }
}
