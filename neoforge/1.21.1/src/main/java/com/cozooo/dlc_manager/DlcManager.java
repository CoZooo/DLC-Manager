package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.platform.NeoForgePlatformServices;
import com.cozooo.dlc_manager.server.DlcServerCommands;
import com.cozooo.dlc_manager.world.DlcWorldSync;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(DlcManager.MOD_ID)
public class DlcManager {
    public static final String MOD_ID = "dlc_manager";

    public DlcManager() {
        System.setProperty("java.awt.headless", "false");
        NeoForgePlatformServices.init();
        DlcSelfUpdateService.applyPendingUpdate();
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            DlcPathConfig.loadServer();
        } else {
            DlcPathConfig.loadClient();
        }
        DlcConfig.load();

        IEventBus modBus = ModLoadingContext.get().getActiveContainer().getEventBus();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                Class<?> client = Class.forName("com.cozooo.dlc_manager.DlcManagerClient");
                client.getMethod("init", IEventBus.class).invoke(null, modBus);
            } catch (Throwable ignored) {
                // Best-effort: avoid client class loading on dedicated server.
            }
        }

        NeoForge.EVENT_BUS.register(new DlcWorldSync());
        NeoForge.EVENT_BUS.addListener(DlcServerCommands::register);
    }
}
