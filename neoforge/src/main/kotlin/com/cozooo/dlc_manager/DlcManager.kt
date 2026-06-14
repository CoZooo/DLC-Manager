package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.bootstrap.DlcBootstrap
import com.cozooo.dlc_manager.platform.NeoForgePlatformServices
import com.cozooo.dlc_manager.server.DlcServerCommands
import com.cozooo.dlc_manager.world.DlcWorldSync
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod

@Mod(DlcManager.MOD_ID)
class DlcManager {
    init {
        NeoForgePlatformServices.init()
        DlcBootstrap.initializeCore()

        val modBus = ModLoadingContext.get().activeContainer.eventBus!!
        DlcBootstrap.initializeClientEntrypoint(IEventBus::class.java, modBus)

        DlcWorldSync.register()
        DlcServerCommands.register()
    }

    companion object {
        const val MOD_ID: String = "dlc_manager"
    }
}
