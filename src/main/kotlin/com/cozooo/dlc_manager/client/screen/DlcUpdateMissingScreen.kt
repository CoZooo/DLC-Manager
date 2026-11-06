package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.client.screen.DlcScreenCompat

import com.cozooo.dlc_manager.platform.DlcTextCompat

import com.cozooo.dlc_manager.client.pack.DlcPackUpdateCoordinator
import com.cozooo.dlc_manager.client.pack.DlcPackUpdateGuard
import com.cozooo.dlc_manager.client.remote.DlcRemoteManager
import com.cozooo.dlc_manager.client.required.DlcRequiredManager
import com.cozooo.dlc_manager.client.core.DlcMessageAdapter
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateApplyResult
import com.cozooo.dlc_manager.client.update.DlcUpdateService.UpdateCheckResult
import com.cozooo.dlc_manager.core.DlcPaths
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class DlcUpdateMissingScreen(
    private val parent: Screen?,
    private val updateResult: UpdateCheckResult,
    missingPaths: List<String?>?,
) : DlcUpdatePageScreen(DlcUiPage.UPDATE_MISSING, parent, DlcTextCompat.translatable("dlc_manager.update.missing_title")) {
    private val missingPaths: List<String?> = missingPaths ?: emptyList()
    private var forceButton: Button? = null
    private var cancelButton: Button? = null
    private var scrollIndex = 0
    private var updateStatus = ""
    private var applying = false

    private fun initControls() {
        val buttonWidth = 120
        val buttonHeight = 20
        val y = height - 40
        val force = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.force")) { confirmApply() }
            .bounds(width / 2 - buttonWidth - 6, y, buttonWidth, buttonHeight)
            .build()
        val cancel = DlcScreenCompat.buttonBuilder(DlcTextCompat.translatable("dlc_manager.update.cancel")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(width / 2 + 6, y, buttonWidth, buttonHeight)
            .build()
        forceButton = force
        cancelButton = cancel
        addRenderableWidget(force)
        addRenderableWidget(cancel)
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
        updateStatus = tr("dlc_manager.update.applying")
        forceButton?.active = false
        cancelButton?.active = false
        refreshPage()
        Thread({
            var packSession: DlcPackUpdateCoordinator.UpdateSession? = null
            try {
                val coordinator = DlcPackUpdateCoordinator(DlcPaths.gameDir(), DlcPaths.dlcRoot())
                if (!DlcUpdateService.willDefer(updateResult.updates)) packSession = coordinator.prepareForUpdate(packCheck, uninstallIncompatible)
                val result = DlcUpdateService.applyUpdates(updateResult.updates, true) { status, _ ->
                    Minecraft.getInstance().execute { updateStatus = DlcMessageAdapter.toProgressText(status) }
                }
                if (result?.isSuccess == true) {
                    packSession?.finishAfterSuccessfulUpdate()
                } else {
                    packSession?.restoreAfterFailedUpdate()
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
                    Minecraft.getInstance().setScreen(DlcUpdateResultScreen(parent, false, DlcTextCompat.literal(message)))
                }
            }
        }, "dlc-update-force").start()
    }

    private fun handleResult(result: UpdateApplyResult?) {
        if (result?.isSuccess != true) {
            val component = DlcMessageAdapter.toFailureComponent(result)
            Minecraft.getInstance().setScreen(DlcUpdateResultScreen(parent, false, component))
            return
        }
        if (tryShowPostUpdateDownloads(result)) {
            return
        }
        val message = if (result.isPendingUpdate) {
            DlcTextCompat.translatable("dlc_manager.update.pending_restart")
        } else if (result.isHelperScheduled) {
            DlcTextCompat.translatable("dlc_manager.update.deferred_restart")
        } else {
            DlcTextCompat.translatable("dlc_manager.update.success")
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
        val message = if (result.isPendingUpdate) {
            DlcTextCompat.translatable("dlc_manager.update.pending_restart")
        } else if (result.isHelperScheduled) {
            DlcTextCompat.translatable("dlc_manager.update.deferred_restart")
        } else {
            DlcTextCompat.translatable("dlc_manager.update.success")
        }
        return DlcUpdateResultScreen(parent, true, message)
    }

    override fun buildPage() {
        if (applying) {
            val current = updateResult.current
            val base = current?.getBase()?.takeUnless { it.isBlank() } ?: "1.0.0"
            val dlc = current?.getDlc()?.takeUnless { it.isBlank() }
            renderExecution(
                { updateStatus },
                if (dlc == null) "dlc_manager.update.current_version_single" else "dlc_manager.update.base",
                base,
                if (dlc == null) null else "dlc_manager.update.current",
                dlc,
                java.util.function.DoubleSupplier { -1.0 }
            )
            return
        }
        initControls()
        live(content, { if (applying) updateStatus else "" }, "page-description")
        val list = section("", tr("dlc_manager.update.missing_desc"))
        for (path in missingPaths) row(list, path, "")
        cancelButton?.let { mount(footer, it) }
        forceButton?.let { mount(footer, it).getElement().setAttribute("class", "button primary") }
    }

}
