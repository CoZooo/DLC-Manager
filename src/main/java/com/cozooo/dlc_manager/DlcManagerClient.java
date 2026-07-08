package com.cozooo.dlc_manager;

import com.cozooo.dlc_manager.client.DlcClientEvents;
import com.cozooo.dlc_manager.client.screen.DlcConfigScreen;
import com.cozooo.dlc_manager.client.update.DlcUpdateService;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.platform.Platform;

public class DlcManagerClient {
    public static void init() {
        ClientLifecycleEvent.CLIENT_SETUP.register(client -> {
            registerConfigScreen();
            initializeClient();
        });
    }

    private static void initializeClient() {
        DlcUpdateService.ensureUpdateDir();
        DlcClientEvents.register();
    }

    private static void registerConfigScreen() {
        Platform.getMod(DlcManager.MOD_ID).registerConfigurationScreen(DlcConfigScreen::new);
    }
}
