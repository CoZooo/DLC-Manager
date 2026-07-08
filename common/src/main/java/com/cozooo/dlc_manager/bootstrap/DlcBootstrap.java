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
        DlcStateConfig.load();
    }

    public static <T> void initializeClientEntrypoint(Class<T> modBusClass, T modBus) {
        if (DlcPlatformServices.isDedicatedServer()) {
            return;
        }

        try {
            Class<?> client = Class.forName("com.cozooo.dlc_manager.DlcManagerClient");
            client.getMethod("init", modBusClass).invoke(null, modBus);
        } catch (Throwable ignored) {
        }
    }
}
