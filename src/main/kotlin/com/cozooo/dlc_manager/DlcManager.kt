package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.bootstrap.DlcBootstrap
import com.cozooo.dlc_manager.platform.DlcPlatformServicesProvider
import com.cozooo.dlc_manager.server.DlcServerCommands
import com.cozooo.dlc_manager.world.DlcWorldSync
//? if fabric {
/*import net.fabricmc.api.ModInitializer
*///?}
//? if forge {
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLEnvironment
//?}
//? if neoforge {
/*import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
*///?}

//? if forge || neoforge {
@Mod(DlcManager.MOD_ID)
//?}
class DlcManager
//? if fabric {
    /*: ModInitializer
*///?}
{
    //? if fabric {
    /*override fun onInitialize() {
        initialize()
    }
    *///?}
    //? if forge || neoforge {
    init {
        initialize()

        //? if forge {
        val modBus: IEventBus = FMLJavaModLoadingContext.get().modEventBus
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DlcManagerClient.init(modBus)
        }
        //?}
        //? if neoforge {
        /*val modBus: IEventBus = ModLoadingContext.get().activeContainer.eventBus!!
        DlcBootstrap.initializeClientEntrypoint(IEventBus::class.java, modBus)
        *///?}
    }
    //?}

    companion object {
        const val MOD_ID: String = "dlc_manager"

        private fun initialize() {
            DlcPlatformServicesProvider.init()
            DlcBootstrap.initializeCore()
            DlcWorldSync.register()
            DlcServerCommands.register()
        }
    }
}
