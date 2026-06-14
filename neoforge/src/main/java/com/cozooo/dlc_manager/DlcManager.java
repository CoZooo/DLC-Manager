package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.bootstrap.DlcBootstrap;
import com.cozooo.dlc_manager.platform.NeoForgePlatformServices;
import com.cozooo.dlc_manager.server.DlcServerCommands;
import com.cozooo.dlc_manager.world.DlcWorldSync;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;

@Mod(DlcManager.MOD_ID)
public class DlcManager {
    public static final String MOD_ID = "dlc_manager";

    public DlcManager() {
        NeoForgePlatformServices.init();
        DlcBootstrap.initializeCore();

        IEventBus modBus = ModLoadingContext.get().getActiveContainer().getEventBus();
        DlcBootstrap.initializeClientEntrypoint(IEventBus.class, modBus);

        DlcWorldSync.register();
        DlcServerCommands.register();
    }
}
