package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.DlcClientEvents;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
//? if fabric {
/*import net.fabricmc.api.ClientModInitializer;
*///?}
//? if forge || neoforge {
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen;
import dev.architectury.platform.Platform;
//?}
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//?}
//? if neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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

    //? if forge || neoforge {
    private static void registerConfigScreen() {
        Platform.getMod(DlcManager.MOD_ID).registerConfigurationScreen(DlcConfigScreen::new);
    }
    //?}
}
