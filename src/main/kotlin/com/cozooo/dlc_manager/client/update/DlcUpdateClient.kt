package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.client.screen.DlcUpdateManagerScreen
import com.cozooo.dlc_manager.client.screen.DlcUpdatePromptScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object DlcUpdateClient {
    private var pendingProcessed = false

    @JvmStatic
    fun startPendingFromTitle(parent: Screen?): Boolean {
        if (pendingProcessed || com.cozooo.dlc_manager.bootstrap.DlcStartupRequirements.usesEarlyInstallation()) return false
        try {
            if (!DlcPendingUpdate.hasPending()) return false
        } catch (exception: java.io.IOException) {
            pendingProcessed = true
            Minecraft.getInstance().setScreen(com.cozooo.dlc_manager.client.screen.DlcUpdateResultScreen(parent, false,
                com.cozooo.dlc_manager.platform.DlcTextCompat.literal(exception.message.orEmpty())))
            return true
        }
        pendingProcessed = true
        val progress = com.cozooo.dlc_manager.client.screen.DlcPendingUpdateScreen(parent)
        Minecraft.getInstance().setScreen(progress)
        Thread({
            var failure: String? = null
            try {
                DlcPendingUpdate.process({ status -> Minecraft.getInstance().execute {
                    progress.setStatus(com.cozooo.dlc_manager.client.core.DlcMessageAdapter.toProgressText(status))
                } }, true)
            } catch (exception: java.io.IOException) { failure = exception.message }
            val error = failure
            Minecraft.getInstance().execute {
                if (showPendingResult(parent)) return@execute
                if (error != null) Minecraft.getInstance().setScreen(com.cozooo.dlc_manager.client.screen.DlcUpdateResultScreen(parent, false,
                    com.cozooo.dlc_manager.platform.DlcTextCompat.literal(error)))
                else Minecraft.getInstance().setScreen(parent)
            }
        }, "dlc-pending-update").start()
        return true
    }

    @JvmStatic
    fun showPendingResult(parent: Screen?): Boolean {
        try {
            val result = DlcPendingUpdate.readResult() ?: return false
            val success = result.get("success").asBoolean
            val restart = result.has("restart_required") && result.get("restart_required").asBoolean
            val message = if (success) com.cozooo.dlc_manager.platform.DlcTextCompat.translatable(
                if (restart) "dlc_manager.update.pending_fabric_restart" else "dlc_manager.update.success")
            else com.cozooo.dlc_manager.platform.DlcTextCompat.translatable(
                if (result.has("rollback_complete") && !result.get("rollback_complete").asBoolean) "dlc_manager.update.pending_recovery_failed" else "dlc_manager.update.pending_failed", result.get("message").asString)
            if (success && !restart) Minecraft.getInstance().setScreen(com.cozooo.dlc_manager.client.screen.DlcSingleButtonScreen(parent,
                com.cozooo.dlc_manager.platform.DlcTextCompat.translatable("dlc_manager.update.title"), message,
                com.cozooo.dlc_manager.platform.DlcTextCompat.translatable("gui.done")))
            else Minecraft.getInstance().setScreen(com.cozooo.dlc_manager.client.screen.DlcUpdateResultScreen(parent, success, message))
            DlcPendingUpdate.acknowledgeResult()
            return true
        } catch (exception: java.io.IOException) {
            return false
        }
    }

    @JvmStatic
    fun tryShowFromTitle(parent: Screen?): Boolean {
        val result = DlcUpdateRuntime.pollTitlePromptCandidate() ?: return false
        showPrompt(parent, result, true)
        return true
    }

    @JvmStatic
    fun isPrompted(): Boolean {
        return DlcUpdateRuntime.isPrompted()
    }

    @JvmStatic
    fun markPrompted() {
        DlcUpdateRuntime.markPrompted()
    }

    @JvmStatic
    fun showPrompt(parent: Screen?, result: DlcUpdateService.UpdateCheckResult?) {
        showPrompt(parent, result, false)
    }

    @JvmStatic
    fun showPrompt(parent: Screen?, result: DlcUpdateService.UpdateCheckResult?, allowSkipThisUpdate: Boolean) {
        DlcUpdateRuntime.markPrompted()
        Minecraft.getInstance().setScreen(DlcUpdatePromptScreen(parent, result, allowSkipThisUpdate))
    }

    @JvmStatic
    fun openUpdateManager(parent: Screen?) {
        if (!DlcUpdateRuntime.isUpdateManagerEnabled()) {
            return
        }
        Minecraft.getInstance().setScreen(DlcUpdateManagerScreen(parent))
    }
}
