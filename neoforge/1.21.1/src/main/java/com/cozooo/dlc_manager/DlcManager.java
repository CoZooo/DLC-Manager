package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.platform.NeoForgePlatformServices;
import com.cozooo.dlc_manager.world.DlcWorldSync;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;

@Mod(DlcManager.MOD_ID)
public class DlcManager {
    public static final String MOD_ID = "dlc_manager";

    public DlcManager() {
        System.setProperty("java.awt.headless", "false");
        NeoForgePlatformServices.init();
        DlcPathConfig.load();
        DlcConfig.load();

        IEventBus modBus = ModLoadingContext.get().getActiveContainer().getEventBus();
        DlcManagerClient.init(modBus);

        NeoForge.EVENT_BUS.register(new DlcWorldSync());
    }
}
