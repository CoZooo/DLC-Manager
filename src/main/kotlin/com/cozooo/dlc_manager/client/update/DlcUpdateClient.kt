package com.cozooo.dlc_manager.client.update

import com.cozooo.dlc_manager.client.screen.DlcUpdateManagerScreen
import com.cozooo.dlc_manager.client.screen.DlcUpdatePromptScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object DlcUpdateClient {
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
