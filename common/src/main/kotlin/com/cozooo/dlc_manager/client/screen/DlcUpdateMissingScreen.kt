package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult
import com.cozooo.dlc_manager.core.DlcPaths
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DlcUpdateMissingScreen(
    private val parent: Screen?,
    private val updateResult: UpdateCheckResult,
    missingPaths: List<String?>?,
) : DlcScreenCompat.MouseScroll(Component.translatable("dlc_manager.update.missing_title")) {
    private val missingPaths: List<String?> = missingPaths ?: emptyList()
    private var forceButton: Button? = null
    private var cancelButton: Button? = null
    private var scrollIndex = 0
    private var applying = false

    override fun init() {
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        val force = Button.builder(Component.translatable("dlc_manager.update.force")) { confirmApply() }
            .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build()
        val cancel = Button.builder(Component.translatable("dlc_manager.update.cancel")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(width / 2 + 6, y, buttonWidth, buttonHeight)
            .build()
        forceButton = force
        cancelButton = cancel
        addRenderableWidget(force)
        addRenderableWidget(cancel)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        val centerX = width / 2
        var y = 20
        val titleX = (width - font.width(title)) / 2
        guiGraphics.drawString(font, title, titleX, y, 0xFFFFFF, false)
        y += 16
        guiGraphics.drawCenteredString(font, Component.translatable("dlc_manager.update.missing_desc"), centerX, y, 0xE6E6E6)
        y += 12
        drawMissingList(guiGraphics, y)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun drawMissingList(guiGraphics: GuiGraphics, startY: Int) {
        val listTop = startY
        val listBottom = height - 70
        val lineHeight = 12
        val totalLines = missingPaths.size
        val maxVisible = maxOf(1, (listBottom - listTop) / lineHeight)
        val maxScroll = maxOf(0, totalLines - maxVisible)
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll
        }
        var y = listTop
        val end = minOf(totalLines, scrollIndex + maxVisible)
        for (i in scrollIndex until end) {
            val path = missingPaths[i]
            guiGraphics.drawString(font, "- $path", 24, y, 0xE6E6E6, false)
            y += lineHeight
        }
        drawScrollBar(guiGraphics, listTop, listBottom, totalLines, maxVisible, maxScroll)
    }

    private fun drawScrollBar(guiGraphics: GuiGraphics, top: Int, bottom: Int, totalLines: Int, maxVisible: Int, maxScroll: Int) {
        if (maxScroll <= 0 || totalLines <= maxVisible) {
            return
        }
        val barX = width - 12
        val barWidth = 4
        val barHeight = maxOf(12, (bottom - top) * maxVisible / totalLines)
        val trackHeight = bottom - top
        val maxTrack = maxOf(1, trackHeight - barHeight)
        val barY = top + ((scrollIndex / maxScroll.toFloat()) * maxTrack).toInt()
        guiGraphics.fill(barX, top, barX + barWidth, bottom, 0xFF2B2B2B.toInt())
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF6B6B6B.toInt())
    }

    override fun handleCompatibleMouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        val listTop = 20 + 16 + 12
        val listBottom = height - 70
        val lineHeight = 12
        val totalLines = missingPaths.size
        val maxVisible = maxOf(1, (listBottom - listTop) / lineHeight)
        val maxScroll = maxOf(0, totalLines - maxVisible)
        if (maxScroll > 0) {
            val step = if (deltaY > 0) -1 else 1
            scrollIndex = maxOf(0, minOf(maxScroll, scrollIndex + step))
            return true
        }
        return false
    }

    override fun shouldCloseOnEsc(): Boolean {
        return !applying
    }

    override fun onClose() {
        if (!applying) {
            super.onClose()
        }
    }

    private fun confirmApply() {
        DlcUpdatePackGuard.confirmBeforeUpdate(this, updateResult.updates) { packCheck, uninstallIncompatible ->
            startApply(packCheck, uninstallIncompatible)
        }
    }

    private fun startApply(packCheck: DlcPackUpdateGuard.CheckResult?, uninstallIncompatible: Boolean) {
        if (applying) {
            return
        }
        applying = true
        forceButton?.active = false
        cancelButton?.active = false
        Thread({
            var packSession: DlcPackUpdateCoordinator.UpdateSession? = null
            try {
                val coordinator = DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible)
                val result = DlcUpdateService.applyUpdates(updateResult.updates, true)
                if (result?.isSuccess == true) {
                    packSession.finishAfterSuccessfulUpdate()
                } else {
                    packSession.restoreAfterFailedUpdate()
                }
                Minecraft.getInstance().execute { handleResult(result) }
            } catch (ex: Exception) {
                packSession?.let {
                    try {
                        it.restoreAfterFailedUpdate()
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
        }, "dlc-update-force").start()
    }

    private fun handleResult(result: UpdateApplyResult?) {
        if (result?.isSuccess != true) {
            val component = DlcUpdateService.toFailureComponent(result)
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
            val missing = DlcRequiredManager.collectMissingDownloadsForPrompt()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().setScreen(DlcRequiredDownloadScreen(parent, missing, result))
                return true
            }
        }
        if (result.hasRemoteAdd()) {
            val missing = DlcRemoteManager.collectMissing()
            if (missing.isNotEmpty()) {
                Minecraft.getInstance().setScreen(DlcRemoteDownloadScreen(createSuccessResultScreen(result), missing))
                return true
            }
            return false
        }
        return false
    }

    private fun createSuccessResultScreen(result: UpdateApplyResult): Screen {
        val message = if (result.isHelperScheduled) {
            Component.translatable("dlc_manager.update.deferred_restart")
        } else {
            Component.translatable("dlc_manager.update.success")
        }
        return DlcUpdateResultScreen(parent, true, message)
    }
}
