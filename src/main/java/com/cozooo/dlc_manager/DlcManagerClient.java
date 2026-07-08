package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.DlcClientEvents;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
//? if fabric {
/*import net.fabricmc.api.ClientModInitializer;
*///?}
//? if forge || neoforge {
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen;
//?}
//? if forge {
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//?}
//? if neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
*///?}

public class DlcManagerClient
//? if fabric {
    /*implements ClientModInitializer
*///?}
{
    //? if fabric {
    /*@Override
    public void onInitializeClient() {
        initializeClient();
    }
    *///?}

    //? if forge || neoforge {
    public static void init(IEventBus modBus) {
        modBus.addListener(DlcManagerClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        registerConfigScreen();
        initializeClient();
    }
    //?}

    private static void initializeClient() {
        DlcUpdateService.ensureUpdateDir();
        DlcClientEvents.register();
    }

    //? if forge {
    private static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new DlcConfigScreen(parent)));
    }
    //?} else if neoforge {
    /*private static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
            () -> (modContainer, parent) -> new DlcConfigScreen(parent));
    }
    *///?}
}
