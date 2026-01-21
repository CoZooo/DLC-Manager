package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.platform.FabricPlatformServices;
import com.cozooo.dlc_manager.world.DlcWorldSync;
import net.fabricmc.api.ModInitializer;

public class DlcManager implements ModInitializer {
    public static final String MOD_ID = "dlc_manager";

    @Override
    public void onInitialize() {
        System.setProperty("java.awt.headless", "false");
        FabricPlatformServices.init();
        DlcPathConfig.load();
        DlcConfig.load();
        DlcWorldSync.register();
    }
}
