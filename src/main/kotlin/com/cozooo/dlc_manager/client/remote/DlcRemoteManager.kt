package com.cozooo.dlc_manager.client.remote

import com.cozooo.dlc_manager.client.screen.DlcRemoteDownloadScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object DlcRemoteManager {
    @JvmStatic
    fun tryShowFromManager(parent: Screen?): Boolean {
        val missing = DlcRemoteRuntime.pollMissingForManagerPrompt()
        if (missing.isEmpty()) {
            return false
        }
        Minecraft.getInstance().setScreen(DlcRemoteDownloadScreen(parent, missing))
        return true
    }

    @JvmStatic
    fun hasMissing(): Boolean {
        return DlcRemoteRuntime.hasMissing()
    }

    @JvmStatic
    fun hasMissingDlcPacks(): Boolean {
        return DlcRemoteRuntime.hasMissingDlcPacks()
    }

    @JvmStatic
    fun collectMissing(): List<DlcRemoteEntry> {
        return DlcRemoteRuntime.collectMissing()
    }

    @JvmStatic
    fun collectMissingDlcPacks(): List<DlcRemoteEntry> {
        return DlcRemoteRuntime.collectMissingDlcPacks()
    }

    @JvmStatic
    fun updateFileNameKey(entry: DlcRemoteEntry?, fileName: String?) {
        DlcRemoteRuntime.updateFileNameKey(entry, fileName)
    }
}
