package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.bootstrap.DlcBootstrap;
import com.cozooo.dlc_manager.platform.forge.ForgePlatformServices;
import com.cozooo.dlc_manager.server.DlcServerCommands;
import com.cozooo.dlc_manager.world.DlcWorldSync;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DlcManager.MOD_ID)
public class DlcManager {
    public static final String MOD_ID = "dlc_manager";

    public DlcManager() {
        ForgePlatformServices.init();
        DlcBootstrap.initializeCore();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DlcManagerClient.init(modBus);
        }

        DlcWorldSync.register();
        DlcServerCommands.register();
    }
}
