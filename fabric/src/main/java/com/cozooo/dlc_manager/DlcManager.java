package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.bootstrap.DlcBootstrap;
import com.cozooo.dlc_manager.platform.fabric.FabricPlatformServices;
import com.cozooo.dlc_manager.server.DlcServerCommands;
import com.cozooo.dlc_manager.world.DlcWorldSync;
import net.fabricmc.api.ModInitializer;

public class DlcManager implements ModInitializer {
    public static final String MOD_ID = "dlc_manager";

    @Override
    public void onInitialize() {
        FabricPlatformServices.init();
        DlcBootstrap.initializeCore();
        DlcWorldSync.register();
        DlcServerCommands.register();
    }
}
