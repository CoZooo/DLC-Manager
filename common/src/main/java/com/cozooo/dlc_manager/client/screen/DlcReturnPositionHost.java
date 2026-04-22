package com.cozooo.dlc_manager.client.screen;

import net.minecraft.client.gui.screens.Screen;

public interface DlcReturnPositionHost {
    void rememberReturnPosition();

    static void remember(Screen screen) {
        if (screen instanceof DlcReturnPositionHost host) {
            host.rememberReturnPosition();
        }
    }
}
