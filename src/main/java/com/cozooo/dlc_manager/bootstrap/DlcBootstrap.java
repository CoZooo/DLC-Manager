package com.cozooo.dlc_manager.bootstrap;

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.core.DlcStateConfig;
import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;

public final class DlcBootstrap {
    private DlcBootstrap() {
    }

    public static void initializeCore() {
        DlcSelfUpdateService.applyPendingUpdate();
        if (DlcPlatformServices.isDedicatedServer()) {
            DlcConfig.loadServer();
        } else {
            DlcConfig.loadClient();
        }
        DlcStartupRequirements.verifyBeforeGameLoad();
        DlcStateConfig.load();
    }
}
