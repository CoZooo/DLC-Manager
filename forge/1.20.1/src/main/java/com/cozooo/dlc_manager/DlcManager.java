package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.platform.ForgePlatformServices;
import com.cozooo.dlc_manager.server.DlcServerCommands;
import com.cozooo.dlc_manager.world.DlcWorldSync;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(DlcManager.MOD_ID)
public class DlcManager {
    public static final String MOD_ID = "dlc_manager";
    private static final Logger LOGGER = LogUtils.getLogger();

    public DlcManager() {
        ForgePlatformServices.init();
        DlcSelfUpdateService.applyPendingUpdate();
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            DlcPathConfig.loadServer();
        } else {
            DlcPathConfig.loadClient();
        }
        DlcConfig.load();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                Class<?> client = Class.forName("com.cozooo.dlc_manager.DlcManagerClient");
                client.getMethod("init", IEventBus.class).invoke(null, modBus);
            } catch (Throwable ignored) {
                // Best-effort: avoid client class loading on dedicated server.
            }
        });

        MinecraftForge.EVENT_BUS.register(new DlcWorldSync());
        MinecraftForge.EVENT_BUS.addListener(DlcServerCommands::register);
    }
}
