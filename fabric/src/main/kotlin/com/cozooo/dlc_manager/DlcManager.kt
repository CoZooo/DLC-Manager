package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.bootstrap.DlcBootstrap
import com.cozooo.dlc_manager.platform.FabricPlatformServices
import com.cozooo.dlc_manager.server.DlcServerCommands
import com.cozooo.dlc_manager.world.DlcWorldSync
import net.fabricmc.api.ModInitializer

class DlcManager : ModInitializer {
    override fun onInitialize() {
        FabricPlatformServices.init()
        DlcBootstrap.initializeCore()
        DlcWorldSync.register()
        DlcServerCommands.register()
    }

    companion object {
        const val MOD_ID: String = "dlc_manager"
    }
}
