package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.platform.ForgePlatformServices;
import com.cozooo.dlc_manager.world.DlcWorldSync;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DlcManager.MOD_ID)
public class DlcManager {
    public static final String MOD_ID = "dlc_manager";

    public DlcManager() {
        ForgePlatformServices.init();
        DlcPathConfig.load();
        DlcConfig.load();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        DlcManagerClient.init(modBus);

        MinecraftForge.EVENT_BUS.register(new DlcWorldSync());
    }
}
