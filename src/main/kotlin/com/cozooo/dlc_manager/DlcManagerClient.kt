package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.client.DlcClientEvents
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen
import com.cozooo.dlc_manager.client.update.DlcUpdateService
import dev.architectury.event.events.client.ClientLifecycleEvent
import dev.architectury.platform.Platform

object DlcManagerClient {
    @JvmStatic
    fun init() {
        ClientLifecycleEvent.CLIENT_SETUP.register {
            registerConfigScreen()
            initializeClient()
        }
    }

    private fun initializeClient() {
        DlcUpdateService.ensureUpdateDir()
        DlcClientEvents.register()
    }

    private fun registerConfigScreen() {
        //? if <26.1 {
        Platform.getMod(DlcManager.MOD_ID).registerConfigurationScreen(::DlcConfigScreen)
        //?}
    }
}
