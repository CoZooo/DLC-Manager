package com.cozooo.dlc_manager.bootstrap;

import com.cozooo.dlc_manager.client.selfupdate.DlcSelfUpdateService;
import com.cozooo.dlc_manager.core.DlcStateConfig;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;

public final class DlcBootstrap {
    private DlcBootstrap() {
    }

    public static void initializeCore() {
        DlcSelfUpdateService.applyPendingUpdate();
        if (CrossPlatformRuntime.isDedicatedServer()) {
            DlcPathConfig.loadServer();
        } else {
            DlcPathConfig.loadClient();
        }
        DlcStateConfig.load();
    }

    public static <T> void initializeClientEntrypoint(Class<T> modBusClass, T modBus) {
        if (CrossPlatformRuntime.isDedicatedServer()) {
            return;
        }

        try {
            Class<?> client = Class.forName("com.cozooo.dlc_manager.DlcManagerClient");
            client.getMethod("init", modBusClass).invoke(null, modBus);
        } catch (Throwable ignored) {
        }
    }
}
