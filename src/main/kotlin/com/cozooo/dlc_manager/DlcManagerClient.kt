package com.cozooo.dlc_manager

import com.cozooo.dlc_manager.client.DlcClientEvents
import com.cozooo.dlc_manager.client.update.DlcUpdateService
//? if fabric {
/*import net.fabricmc.api.ClientModInitializer
*///?}
//? if forge || neoforge {
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen
//?}
//? if forge {
import net.minecraftforge.client.ConfigScreenHandler
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
//?}
//? if neoforge {
/*import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
*///?}

object DlcManagerClient
//? if fabric {
    /*: ClientModInitializer
*///?}
{
    //? if fabric {
    /*override fun onInitializeClient() {
        initializeClient()
    }
    *///?}

    //? if forge || neoforge {
    @JvmStatic
    fun init(modBus: IEventBus) {
        modBus.addListener(DlcManagerClient::onClientSetup)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        registerConfigScreen()
        initializeClient()
    }
    //?}

    private fun initializeClient() {
        DlcUpdateService.ensureUpdateDir()
        DlcClientEvents.register()
    }

    //? if forge {
    private fun registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory::class.java
        ) { ConfigScreenHandler.ConfigScreenFactory { _, parent -> DlcConfigScreen(parent) } }
    }
    //?} else if neoforge {
    /*private fun registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
            IConfigScreenFactory::class.java
        ) { IConfigScreenFactory { _, parent -> DlcConfigScreen(parent) } }
    }
    *///?}
}
