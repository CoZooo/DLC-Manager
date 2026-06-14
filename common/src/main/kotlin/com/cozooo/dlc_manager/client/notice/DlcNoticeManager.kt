package com.cozooo.dlc_manager.client.notice

import com.cozooo.dlc_manager.client.screen.DlcNoticeScreen
import com.cozooo.dlc_manager.core.DlcNoticeConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object DlcNoticeManager {
    @JvmStatic
    fun showFromTitle(parent: Screen?): Boolean {
        val config = DlcNoticeRuntime.consumeTitleNoticeConfig()
        return config != null && openNotice(parent, config)
    }

    @JvmStatic
    fun tryShowFromManager(parent: Screen?): Boolean {
        val config = DlcNoticeRuntime.consumeManagerNoticeConfig()
        return config != null && openNotice(parent, config)
    }

    @JvmStatic
    fun forceShow(parent: Screen?): Boolean {
        val config = DlcNoticeRuntime.forceNoticeConfig()
        return config != null && openNotice(parent, config)
    }

    @JvmStatic
    fun isNoticeEnabled(): Boolean {
        return DlcNoticeRuntime.isNoticeEnabled()
    }

    private fun openNotice(parent: Screen?, config: DlcNoticeConfig): Boolean {
        Minecraft.getInstance().setScreen(DlcNoticeScreen(parent, config))
        return true
    }
}
