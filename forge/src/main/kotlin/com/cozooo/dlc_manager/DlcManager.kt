package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.bootstrap.DlcBootstrap
import com.cozooo.dlc_manager.platform.forge.ForgePlatformServices
import com.cozooo.dlc_manager.server.DlcServerCommands
import com.cozooo.dlc_manager.world.DlcWorldSync
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLEnvironment

@Mod(DlcManager.MOD_ID)
class DlcManager {
    init {
        ForgePlatformServices.init()
        DlcBootstrap.initializeCore()

        val modBus = FMLJavaModLoadingContext.get().modEventBus
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DlcManagerClient.init(modBus)
        }

        DlcWorldSync.register()
        DlcServerCommands.register()
    }

    companion object {
        const val MOD_ID: String = "dlc_manager"
    }
}
