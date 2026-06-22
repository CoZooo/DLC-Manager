package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerPackApplyStatus
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToInt

class DlcServerPackApplyScreen(
    private val manager: DlcServerPackManagerScreen,
    private val entry: DlcServerEntry,
    private val order: List<String>,
    private val uninstallIdentifiers: List<String>,
    private val installArchives: List<String>,
) : Screen(Component.translatable("dlc_manager.server.pack.manager_title")) {
    @Volatile private var status: DlcServerPackApplyStatus? = null
    @Volatile private var statusText: Component = Component.translatable("dlc_manager.pack.applying")
    @Volatile private var running = false
    @Volatile private var finished = false
    @Volatile private var canceling = false
    @Volatile private var paused = false
    @Volatile private var pausing = false
    private var cancelButton: Button? = null
    private var backButton: Button? = null

    override fun init() {
        val buttonWidth = 104
        val gap = 8
        val y = height - 28
        val startX = width / 2 - buttonWidth - gap / 2
        cancelButton = Button.builder(Component.translatable("dlc_manager.pack.pause_install")) { togglePauseApply() }
            .bounds(startX, y, buttonWidth, 20)
            .build()
        backButton = Button.builder(Component.translatable("dlc_manager.pack.cancel_install")) {
            cancelApply()
        }.bounds(startX + buttonWidth + gap, y, buttonWidth, 20).build()
        addRenderableWidget(cancelButton!!)
        addRenderableWidget(backButton!!)
        if (!running && !finished) {
            startApply()
        }
    }

    private fun startApply() {
        running = true
        CompletableFuture.supplyAsync {
            try {
                DlcServerConnection.startPackApply(entry, TIMEOUT_MS, order, uninstallIdentifiers, installArchives)
            } catch (_: Exception) {
                null
            }
        }.whenComplete { started, _ ->
            if (started == null) {
                finish(false, "start failed")
                return@whenComplete
            }
            updateStatus(started)
            startPolling()
        }
    }

    private fun startPolling() {
        Thread({
            while (running) {
                val next = try {
                    DlcServerConnection.fetchPackApplyStatus(entry, STATUS_TIMEOUT_MS)
                } catch (_: Exception) {
                    null
                }
                if (next != null) {
                    updateStatus(next)
                    if (next.finished) {
                        finish(next.success, next.error ?: next.message)
                        break
                    }
                }
                try {
                    Thread.sleep(250L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "dlc-server-pack-apply-poll").apply { isDaemon = true }.start()
    }

    private fun updateStatus(next: DlcServerPackApplyStatus) {
        status = next
        paused = next.paused
        if (paused) {
            pausing = false
        }
        val message = next.message
        statusText = formatRemoteStatus(message)
        Minecraft.getInstance().execute { updateButtonState() }
    }

    private fun formatRemoteStatus(message: String?): Component {
        if (message.isNullOrBlank()) {
            return Component.translatable("dlc_manager.pack.applying")
        }
        return if (message.startsWith("dlc_manager.")) {
            Component.translatable(message)
        } else {
            DlcTextFormatter.parse(message)
        }
    }

    private fun togglePauseApply() {
        if (canceling || finished) return
        if (paused) {
            paused = false
            pausing = false
            statusText = Component.translatable("dlc_manager.pack.applying")
            updateButtonState()
            CompletableFuture.runAsync {
                try {
                    DlcServerConnection.resumePackApply(entry, TIMEOUT_MS)
                } catch (_: Exception) {
                }
            }
            return
        }
        pausing = true
        statusText = Component.translatable("dlc_manager.pack.pausing")
        updateButtonState()
        CompletableFuture.runAsync {
            try {
                DlcServerConnection.pausePackApply(entry, TIMEOUT_MS)
            } catch (_: Exception) {
            }
        }
    }

    private fun cancelApply() {
        if (canceling || finished) return
        canceling = true
        statusText = Component.translatable("dlc_manager.pack.canceling")
        updateButtonState()
        CompletableFuture.runAsync {
            try {
                DlcServerConnection.cancelPackApply(entry, TIMEOUT_MS)
            } catch (_: Exception) {
            }
        }
    }

    private fun finish(success: Boolean, message: String?) {
        running = false
        finished = true
        Minecraft.getInstance().execute {
            cancelButton?.active = false
            backButton?.active = false
            manager.finishServerPackApply(success, message)
            if (success) {
                manager.showServerRestartPrompt()
            } else {
                Minecraft.getInstance().setScreen(manager)
            }
        }
    }

    private fun updateButtonState() {
        cancelButton?.message = when {
            paused -> Component.translatable("dlc_manager.pack.continue_install")
            pausing -> Component.translatable("dlc_manager.pack.pausing")
            else -> Component.translatable("dlc_manager.pack.pause_install")
        }
        cancelButton?.active = !canceling && !finished
        backButton?.message = if (canceling) Component.translatable("dlc_manager.pack.canceling") else Component.translatable("dlc_manager.pack.cancel_install")
        backButton?.active = !canceling && !finished
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        DlcScreenCompat.renderModalBackground(this, guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF)
        renderOperation(guiGraphics)
        DlcScreenCompat.renderWidgets(this, guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderOperation(guiGraphics: GuiGraphics) {
        val centerX = width / 2
        var y = height / 2 - 44
        val progress = status?.progress ?: -1f
        val progressText = if (progress >= 0f) {
            "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%"
        } else {
            "..."
        }
        guiGraphics.drawCenteredString(font, formatOperationTitle(statusText, progress, progressText), centerX, y, 0xE6E6E6)
        y += 24
        drawProgress(guiGraphics, centerX - 120, y, 240, 8, progress)
        guiGraphics.drawCenteredString(font, Component.literal(progressText), centerX, y + 14, 0xC8C8C8)
    }

    private fun drawProgress(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, value: Float) {
        val filled = if (value >= 0f) (width * value.coerceIn(0f, 1f)).roundToInt() else 0
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B.toInt())
        if (value < 0f) {
            val pulse = ((Util.getMillis() / 12L) % width).toInt()
            guiGraphics.fill(x + pulse.coerceAtMost(width - 24), y, x + (pulse + 24).coerceAtMost(width), y + height, 0xFF66CCFF.toInt())
        } else {
            guiGraphics.fill(x, y, x + filled, y + height, 0xFF66CCFF.toInt())
        }
    }

    private fun formatOperationTitle(message: Component, progress: Float, progressText: String): Component {
        val text = message.string.trimEnd('.', '。', '…')
        return if (progress >= 0f) {
            Component.literal("$text：$progressText")
        } else {
            Component.literal("$text...")
        }
    }

    override fun shouldCloseOnEsc(): Boolean = finished

    companion object {
        private const val TIMEOUT_MS = 8000
        private const val STATUS_TIMEOUT_MS = 3000
    }
}
