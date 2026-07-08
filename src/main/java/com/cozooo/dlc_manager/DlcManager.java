package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.bootstrap.DlcBootstrap;
import com.cozooo.dlc_manager.platform.DlcPlatformServicesProvider;
import com.cozooo.dlc_manager.server.DlcServerCommands;
import com.cozooo.dlc_manager.world.DlcWorldSync;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
//? if fabric {
/*import net.fabricmc.api.ModInitializer;
*///?}
//? if forge {
import net.minecraftforge.fml.common.Mod;
//?}
//? if neoforge {
/*import net.neoforged.fml.common.Mod;
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
    }
    //?}

    private static void initialize() {
        DlcPlatformServicesProvider.init();
        DlcBootstrap.initializeCore();
        DlcWorldSync.register();
        DlcServerCommands.register();
        EnvExecutor.runInEnv(Env.CLIENT, () -> DlcManagerClient::init);
    }
}
