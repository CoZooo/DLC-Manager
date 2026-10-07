package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.core.DlcTextFormatter
import com.cozooo.dlc_manager.client.server.DlcServerConnection
import com.cozooo.dlc_manager.client.server.DlcServerEntry
import com.cozooo.dlc_manager.client.server.DlcServerPackApplyStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class DlcServerPackApplyScreen(
    private val manager: DlcServerPackManagerScreen,
    private val entry: DlcServerEntry,
    private val order: List<String>,
    private val uninstallIdentifiers: List<String>,
    private val installArchives: List<String>,
) : DlcPageScreen(manager, Component.translatable("dlc_manager.server.pack.manager_title")) {
    @Volatile private var status: DlcServerPackApplyStatus? = null
    @Volatile private var statusText: Component = Component.translatable("dlc_manager.pack.applying")
    @Volatile private var statusDetail: Component? = null
    @Volatile private var running = false
    @Volatile private var finished = false
    @Volatile private var canceling = false
    @Volatile private var paused = false
    @Volatile private var pausing = false
    private var cancelButton: Button? = null
    private var backButton: Button? = null

    private fun initControls() {
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
        statusDetail = operationDetailFor(next.detail)
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

    private fun operationDetailFor(raw: String?): Component? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "dlc_manager.pack.selecting_source") return null
        return when {
            value.startsWith("Downloading ") -> Component.translatable("dlc_manager.pack.downloading_file", value.removePrefix("Downloading "))
            value.startsWith("Resolved ") -> Component.translatable("dlc_manager.pack.resolved_file", value.removePrefix("Resolved "))
            value.startsWith("Installing ") -> Component.translatable("dlc_manager.pack.installing", value.removePrefix("Installing "))
            value.startsWith("Uninstalling installed DLC items") -> Component.translatable("dlc_manager.pack.operation_uninstalling")
            value.startsWith("Uninstalling ") -> Component.translatable("dlc_manager.pack.uninstalling", value.removePrefix("Uninstalling "))
            value.startsWith("Restoring ") -> Component.translatable("dlc_manager.pack.restoring_file", value.removePrefix("Restoring "))
            value.startsWith("Preparing ") -> Component.translatable("dlc_manager.pack.preparing_pack", value.removePrefix("Preparing "))
            value.startsWith("Backing up ") -> Component.translatable("dlc_manager.pack.backing_up_pack", value.removePrefix("Backing up "))
            value == "Applying runtime changes" -> Component.translatable("dlc_manager.pack.applying_runtime")
            value == "Writing metadata" -> Component.translatable("dlc_manager.pack.writing_metadata")
            value.startsWith("Installed ") -> Component.translatable("dlc_manager.pack.installed_pack", value.removePrefix("Installed "))
            value.startsWith("Uninstalled ") -> Component.translatable("dlc_manager.pack.uninstalled_pack", value.removePrefix("Uninstalled "))
            value.startsWith("dlc_manager.") -> Component.translatable(value)
            else -> DlcTextFormatter.parse(value)
        }
    }

    override fun shouldCloseOnEsc(): Boolean = finished

    companion object {
        private const val TIMEOUT_MS = 8000
        private const val STATUS_TIMEOUT_MS = 3000
    }

    override fun buildPage() {
        initControls()
        val body = node(content, "div", "page-dialog")
        live(body, { statusText.string }, "section-title")
        progress(body) { status?.progress?.toDouble() ?: -1.0 }
        live(body, { status?.progress?.takeIf { it >= 0 }?.let { "${(it * 100).toInt()}%" }.orEmpty() }, "page-description")
        live(body, { statusDetail?.string }, "page-message")
        backButton?.let { mount(footer, it).getElement().setAttribute("class", "button danger") }
        cancelButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
    }
    override fun updatePage() { updateButtonState() }

}
