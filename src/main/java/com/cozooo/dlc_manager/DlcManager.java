package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.bootstrap.DlcBootstrap;
import com.cozooo.dlc_manager.platform.DlcPlatformServicesProvider;
import com.cozooo.dlc_manager.server.DlcServerCommands;
import com.cozooo.dlc_manager.world.DlcWorldSync;
//? if fabric {
/*import net.fabricmc.api.ModInitializer;
*///?}
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//?}
//? if neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
*///?}

//? if forge || neoforge {
@Mod(DlcManager.MOD_ID)
//?}
public class DlcManager
//? if fabric {
    /*implements ModInitializer
*///?}
{
    public static final String MOD_ID = "dlc_manager";

    //? if fabric {
    /*@Override
    public void onInitialize() {
        initialize();
    }
    *///?}
    //? if forge || neoforge {
    public DlcManager() {
        initialize();

        //? if forge {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DlcManagerClient.init(modBus);
        }
        //?}
        //? if neoforge {
        /*IEventBus modBus = ModLoadingContext.get().getActiveContainer().getEventBus();
        DlcBootstrap.initializeClientEntrypoint(IEventBus.class, modBus);
        *///?}
    }
    //?}

    private static void initialize() {
        DlcPlatformServicesProvider.init();
        DlcBootstrap.initializeCore();
        DlcWorldSync.register();
        DlcServerCommands.register();
    }
}
